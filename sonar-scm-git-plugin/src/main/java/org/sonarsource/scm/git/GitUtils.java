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

import org.sonar.api.config.Configuration;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.scm.git.nativegit.NativeGitUtils;

import static java.util.Objects.requireNonNull;

public class GitUtils {

  private static final Logger LOG = Loggers.get(GitUtils.class);

  private static volatile Boolean useJGit;

  private GitUtils() {
  }

  static void determineUseJGit(Configuration configuration) {
    if ((configuration != null) && configuration.hasKey(NativeGitUtils.GIT_EXECUTABLE_PROPERTY)) {
      useJGit = false;
      LOG.info("Using native git, because it is configured via property '{}'.", NativeGitUtils.GIT_EXECUTABLE_PROPERTY);
    } else if (NativeGitUtils.verifyGitExecutable(NativeGitUtils.GIT_EXECUTABLE_DEFAULT)) {
      useJGit = false;
      LOG.info("Using native git, because default native git executable '{}' was found.", NativeGitUtils.GIT_EXECUTABLE_DEFAULT);
    } else {
      useJGit = true;
      LOG.info("Using JGit, because default native git executable '{}' is not found.", NativeGitUtils.GIT_EXECUTABLE_DEFAULT);
    }
  }

  static boolean useJGit() {
    return requireNonNull(GitUtils.useJGit, "Call determineUseJGit first");
  }
}
