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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import org.sonar.api.batch.scm.BlameCommand;
import org.sonar.api.batch.scm.ScmProvider;
import org.sonar.api.utils.MessageException;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.scm.git.AnalysisWarningsWrapper;
import org.sonarsource.scm.git.ChangedLinesComputer;
import org.sonarsource.scm.git.GitBlameCommand;

public class NativeGitScmProviderBefore77 extends ScmProvider {

  private static final Logger LOG = Loggers.get(NativeGitScmProviderBefore77.class);

  private final GitBlameCommand gitBlameCommand;
  private final AnalysisWarningsWrapper analysisWarnings;

  public NativeGitScmProviderBefore77(GitBlameCommand gitBlameCommand, AnalysisWarningsWrapper analysisWarnings) {
    this.gitBlameCommand = gitBlameCommand;
    this.analysisWarnings = analysisWarnings;
  }

  @Override
  public String key() {
    return "git";
  }

  @Override
  public boolean supports(File baseDir) {
    try {
      Process gitRevParse = new ProcessBuilder(NativeGitUtils.getGitExecutable(), "rev-parse", "--is-inside-work-tree").directory(baseDir).start();
      try (BufferedReader stdout = new BufferedReader(new InputStreamReader(gitRevParse.getInputStream()))) {
        return "true".equals(stdout.readLine()) && (gitRevParse.waitFor() == 0);
      }
    } catch (MessageException e) {
      LOG.debug("Failed to get git executable", e);
    } catch (IOException e) {
      LOG.warn("Failed to invoke native git executable", e);
    } catch (InterruptedException e) {
      LOG.info("Git rev-parse interrupted");
    }
    return false;
  }

  @Override
  public BlameCommand blameCommand() {
    return this.gitBlameCommand;
  }

  @CheckForNull
  @Override
  public Set<Path> branchChangedFiles(String targetBranchName, Path rootBaseDir) {
    ProcessBuilder processBuilder = NativeGitUtils.getVerifiedProcessBuilder(rootBaseDir);
    String targetRef = resolveTargetRef(targetBranchName, processBuilder);
    if (targetRef == null) {
      return null;
    }

    try {
      Process gitDiff = processBuilder.command(NativeGitUtils.getGitExecutable(), "-c", "core.quotepath=false", "diff", "--name-only", "--relative", "--no-renames", "--diff-filter=AM", targetRef + "...").start();
      try (BufferedReader stdout = new BufferedReader(new InputStreamReader(gitDiff.getInputStream()))) {
        Set<Path> result = stdout.lines().map(rootBaseDir::resolve).collect(Collectors.toSet());
        return (gitDiff.waitFor() == 0) ? result : null;
      }
    } catch (IOException e) {
      LOG.warn("Failed to invoke native git executable", e);
    } catch (InterruptedException e) {
      LOG.info("Git diff interrupted");
    }
    return null;
  }

  @CheckForNull
  @Override
  public Map<Path, Set<Integer>> branchChangedLines(String targetBranchName, Path projectBaseDir, Set<Path> changedFiles) {
    ProcessBuilder processBuilder = NativeGitUtils.getVerifiedProcessBuilder(projectBaseDir);
    String targetRef = resolveTargetRef(targetBranchName, processBuilder);
    if (targetRef == null) {
      return null;
    }

    Map<Path, Set<Integer>> changedLines = new HashMap<>();

    for (Path path : changedFiles) {
      ChangedLinesComputer computer = new ChangedLinesComputer();

      try {
        String relativeFilePath = projectBaseDir.relativize(path).toString().replace('\\', '/');
        Process gitDiff = processBuilder.command(NativeGitUtils.getGitExecutable(), "diff", "-w", targetRef , "--", relativeFilePath).start();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = gitDiff.getInputStream().read(buffer, 0, 8192)) >= 0) {
          computer.receiver().write(buffer, 0, read);
        }
        if ((gitDiff.waitFor() == 0) && (computer.changedLines().size() > 0)) {
          changedLines.put(path, computer.changedLines());
        }
      } catch (IOException e) {
        LOG.warn("Failed to invoke native git executable", e);
      } catch (InterruptedException e) {
        LOG.info("Git diff interrupted");
      }
    }

    return changedLines;
  }

  @CheckForNull
  private String resolveTargetRef(String targetBranchName, ProcessBuilder processBuilder) {
    try {
      String targetRef = "refs/heads/" + targetBranchName;
      Process gitRevParse = processBuilder.command(NativeGitUtils.getGitExecutable(), "rev-parse", "--quiet", "--verify", targetRef).start();
      if (gitRevParse.waitFor() == 0) {
        return targetRef;
      }
      targetRef = "refs/remotes/origin/" + targetBranchName;
      gitRevParse = processBuilder.command(NativeGitUtils.getGitExecutable(), "rev-parse", "--quiet", "--verify", targetRef).start();
      if (gitRevParse.waitFor() == 0) {
        return targetRef;
      }
    } catch (IOException e) {
      LOG.warn("Failed to invoke native git executable", e);
    } catch (InterruptedException e) {
      LOG.info("Git rev-parse interrupted");
    }

    LOG.warn("Could not find ref: {} in refs/heads or refs/remotes/origin", targetBranchName);
    analysisWarnings.addUnique(String.format("Could not find ref '%s' in refs/heads or refs/remotes/origin. "
      + "You may see unexpected issues and changes. "
      + "Please make sure to fetch this ref before pull request analysis.", targetBranchName));
    return null;
  }

  @Override
  public Path relativePathFromScmRoot(Path path) {
    return NativeGitUtils.getRelativePathFromScmRoot(path);
  }

  @Override
  public String revisionId(Path path) {
    try {
      File pathDirectory = Files.isDirectory(path) ? path.toFile() : path.getParent().toFile();
      Process gitRevParse = NativeGitUtils.getVerifiedProcessBuilder(pathDirectory.toPath()).command(NativeGitUtils.getGitExecutable(), "rev-parse", "--quiet", "--verify", "HEAD").start();
      try (BufferedReader stdout = new BufferedReader(new InputStreamReader(gitRevParse.getInputStream()))) {
        String revisionId = stdout.readLine();
        return (gitRevParse.waitFor() == 0) ? revisionId : null;
      }
    } catch (IOException e) {
      LOG.warn("Failed to invoke native git executable", e);
    } catch (InterruptedException e) {
      LOG.info("Git rev-parse interrupted");
    }
    return null;
  }
}
