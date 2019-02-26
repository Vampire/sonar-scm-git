/*
 * SonarQube :: Plugins :: SCM :: Git
 * Copyright (C) 2014-2019 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonarsource.scm.git.nativegit;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.scm.BlameCommand;
import org.sonar.api.batch.scm.BlameLine;
import org.sonar.api.scan.filesystem.PathResolver;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.scm.git.AnalysisWarningsWrapper;
import org.sonarsource.scm.git.GitThreadFactory;

public class NativeGitBlameCommand extends BlameCommand {

  private static final Logger LOG = Loggers.get(NativeGitBlameCommand.class);

  private final PathResolver pathResolver;
  private final AnalysisWarningsWrapper analysisWarnings;

  public NativeGitBlameCommand(PathResolver pathResolver, AnalysisWarningsWrapper analysisWarnings) {
    this.pathResolver = pathResolver;
    this.analysisWarnings = analysisWarnings;
  }

  @Override
  public void blame(BlameInput input, BlameOutput output) {
    File baseDir = input.fileSystem().baseDir();
    try {
      Process gitRevParse = NativeGitUtils.getVerifiedProcessBuilder(baseDir.toPath()).command(NativeGitUtils.getGitExecutable(), "rev-parse", "--is-shallow-repository").start();
      try (BufferedReader stdout = new BufferedReader(new InputStreamReader(gitRevParse.getInputStream()))) {
        if ("true".equals(stdout.readLine()) && (gitRevParse.waitFor() == 0)) {
          LOG.warn("Shallow clone detected, no blame information will be provided. "
            + "You can convert to non-shallow with 'git fetch --unshallow'.");
          analysisWarnings.addUnique("Shallow clone detected during the analysis. "
            + "Some files will miss SCM information. This will affect features like auto-assignment of issues. "
            + "Please configure your build to disable shallow clone.");
          return;
        }
      }
    } catch (IOException e) {
      LOG.warn("Failed to invoke native git executable", e);
    } catch (InterruptedException e) {
      LOG.info("Git rev-parse interrupted");
    }


    Stream<InputFile> stream = StreamSupport.stream(input.filesToBlame().spliterator(), true);
    ForkJoinPool forkJoinPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors(), new GitThreadFactory(), null, false);
    forkJoinPool.submit(() -> stream.forEach(inputFile -> blame(output, baseDir, inputFile)));
    try {
      forkJoinPool.shutdown();
      forkJoinPool.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      LOG.info("Git blame interrupted");
    }
  }

  private void blame(BlameOutput output, File baseDir, InputFile inputFile) {
    LOG.debug("Blame file {}", inputFile.toString());

    try {
      Map<Integer, BlameLine> blameLines = new HashMap<>(Math.max(inputFile.lines(), 16));
      String relativeFilePath = baseDir.toPath().relativize(Paths.get(inputFile.uri())).toString().replace('\\', '/');
      Process gitBlame = NativeGitUtils.getVerifiedProcessBuilder(baseDir.toPath()).command(NativeGitUtils.getGitExecutable(), "blame", "--incremental", "-w", "--", relativeFilePath).start();
      try (BufferedReader stdout = new BufferedReader(new InputStreamReader(gitBlame.getInputStream()))) {
        String[] currentRevision = new String[1];
        IntStream[] currentLines = new IntStream[1];
        Map<String, Long> rawCommitDates = new HashMap<>();
        Map<String, Date> commitDates = new HashMap<>();
        Map<String, String> commitAuthors = new HashMap<>();
        stdout.lines().forEachOrdered(outputLine -> {
          String[] lineParts = outputLine.split(" ", 4);
          switch(lineParts[0]) {
            case "committer-mail":
              commitAuthors.put(currentRevision[0], lineParts[1].substring(1, lineParts[1].length() - 1));
              break;

            case "committer-time":
              rawCommitDates.put(currentRevision[0], Long.valueOf(lineParts[1]));
              break;

            case "committer-tz":
              Long rawCommitDate = rawCommitDates.get(currentRevision[0]);
              ZoneOffset committerTimeZone = ZoneOffset.of(lineParts[1]);
              LocalDateTime localDateTime = LocalDateTime.ofEpochSecond(rawCommitDate, 0, committerTimeZone);
              commitDates.put(currentRevision[0], Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant()));
              break;

            case "filename":
              String revision = currentRevision[0];
              Date date = commitDates.get(revision);
              String author = commitAuthors.get(revision);
              currentLines[0].forEach(line -> blameLines.put(line, new BlameLine(date, revision).author(author)));
              currentRevision[0] = null;
              break;

            default:
              if (currentRevision[0] == null) {
                currentRevision[0] = lineParts[0];
                int startLine = Integer.parseInt(lineParts[2]);
                currentLines[0] = IntStream.range(startLine, startLine + Integer.parseInt(lineParts[3]));
              }
          }
        });
      }
      if (gitBlame.waitFor() == 0) {
        List<BlameLine> lineResults = blameLines.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
        BlameLine lastLine = lineResults.get(lineResults.size() - 1);
        IntStream.range(lineResults.size(), inputFile.lines()).forEach(i -> lineResults.add(lastLine));
        output.blameResult(inputFile, lineResults);
      }
    } catch (IOException e) {
      LOG.warn("Failed to invoke native git executable", e);
    } catch (InterruptedException e) {
      LOG.info("Git blame interrupted");
    }
  }
}
