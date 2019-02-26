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
package org.sonarsource.scm.git;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

public class IncludedFilesRepository {

  private static final Logger LOG = Loggers.get(IncludedFilesRepository.class);
  private final Set<Path> includedFiles = new HashSet<>();

  public IncludedFilesRepository(Path baseDir) {
    indexFiles(baseDir);
    LOG.debug("{} non excluded files in this Git repository", includedFiles.size());
  }

  public boolean contains(Path absolutePath) {
    return includedFiles.contains(absolutePath);
  }

  private void indexFiles(Path baseDir) {
    try {
      Process gitLsFiles = GitUtils.getVerifiedProcessBuilder(baseDir).command(GitUtils.getGitExecutable(), "ls-files", "-c", "-o", "--exclude-standard").start();
      try (BufferedReader stdout = new BufferedReader(new InputStreamReader(gitLsFiles.getInputStream()))) {
        stdout.lines().map(baseDir::resolve).forEach(includedFiles::add);
      }
      gitLsFiles.waitFor();
    } catch (IOException e) {
      LOG.warn("Failed to invoke native git executable", e);
    } catch (InterruptedException e) {
      LOG.info("Git ls-files interrupted");
    }
  }
}
