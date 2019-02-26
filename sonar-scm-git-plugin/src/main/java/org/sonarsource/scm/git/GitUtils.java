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
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;

import org.sonar.api.config.Configuration;
import org.sonar.api.utils.MessageException;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import static java.util.Objects.requireNonNull;

public class GitUtils {

  private static final Logger LOG = Loggers.get(GitUtils.class);

  private static final String GIT_EXECUTABLE_DEFAULT = "git";

  private static final String GIT_EXECUTABLE_PROPERTY = "sonar.git.executable";

  private static volatile String gitExecutable;

  private static AtomicBoolean gitExecutableVerified = new AtomicBoolean();

  private GitUtils() {
  }

  static void determineGitExecutable(Configuration configuration) {
    if ((configuration != null) && configuration.hasKey(GIT_EXECUTABLE_PROPERTY)) {
      String gitExecutable = configuration.get(GIT_EXECUTABLE_PROPERTY).get();
      File file = new File(gitExecutable);
      if (file.exists()) {
        LOG.info("Using Git executable '{}' from property '{}'.", file.getAbsoluteFile(), GIT_EXECUTABLE_PROPERTY);
        GitUtils.gitExecutable = gitExecutable;
      } else {
        LOG.error("Provided Git executable file does not exist. Property '{}' was set to '{}'", GIT_EXECUTABLE_PROPERTY, gitExecutable);
        throw MessageException.of("Provided Git executable file does not exist.");
      }
    } else {
      LOG.debug("Using default Git executable: '{}'.", GIT_EXECUTABLE_DEFAULT);
      GitUtils.gitExecutable = GIT_EXECUTABLE_DEFAULT;
    }
  }

  static String getGitExecutable() {
    String gitExecutable = requireNonNull(GitUtils.gitExecutable, "Call determineGitExecutable first");
    if (gitExecutableVerified.compareAndSet(false, true)) {
      boolean valid = false;
      try {
        if (new ProcessBuilder(gitExecutable, "--version").start().waitFor() == 0) {
          valid = true;
        }
      } catch (IOException e) {
        LOG.warn("Failed to invoke native git executable", e);
      } catch (InterruptedException e) {
        LOG.info("Git --version interrupted");
      }
      if (!valid) {
        throw MessageException.of("Native Git at '" + gitExecutable + "' seems not to be runnable, please configure a valid Git executable using property '" + GIT_EXECUTABLE_PROPERTY + "'.");
      }
    }
    return gitExecutable;
  }

  static ProcessBuilder getVerifiedProcessBuilder(Path basedir) {
    if (!Files.isDirectory(basedir)) {
      throw MessageException.of("Not inside a Git work tree: " + basedir);
    }
    ProcessBuilder processBuilder = new ProcessBuilder().directory(basedir.toFile());
    try {
      Process gitRevParse = processBuilder.command(GitUtils.getGitExecutable(), "rev-parse", "--is-inside-work-tree").start();
      try (BufferedReader stdout = new BufferedReader(new InputStreamReader(gitRevParse.getInputStream()))) {
        if (!"true".equals(stdout.readLine()) || (gitRevParse.waitFor() != 0)) {
          throw MessageException.of("Not inside a Git work tree: " + basedir);
        }
      }
    } catch (IOException e) {
      LOG.warn("Failed to invoke native git executable", e);
    } catch (InterruptedException e) {
      LOG.info("Git rev-parse interrupted");
    }
    processBuilder.command(new String[0]);
    return processBuilder;
  }

  static Path getRelativePathFromScmRoot(Path path) {
    try {
      File pathDirectory = Files.isDirectory(path) ? path.toFile() : path.getParent().toFile();
      Process gitRevParse = getVerifiedProcessBuilder(pathDirectory.toPath()).command(GitUtils.getGitExecutable(), "rev-parse", "--show-toplevel").start();
      try (BufferedReader stdout = new BufferedReader(new InputStreamReader(gitRevParse.getInputStream()))) {
        String scmRoot = stdout.readLine();
        if ((scmRoot != null) && (scmRoot.startsWith("/cygdrive/"))) {
          scmRoot = scmRoot.replaceFirst("(?i)^/cygdrive/(?<drive>[A-Z])", "${drive}:");
        }
        return (gitRevParse.waitFor() == 0) ? Paths.get(scmRoot).relativize(path) : null;
      }
    } catch (IOException e) {
      LOG.warn("Failed to invoke native git executable", e);
    } catch (InterruptedException e) {
      LOG.info("Git rev-parse interrupted");
    }
    return null;
  }
}
