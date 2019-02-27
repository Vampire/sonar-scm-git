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

import java.util.Objects;

public class GitScmProvider extends GitScmProviderBefore77 {

  private final GitIgnoreCommand gitIgnoreCommand;

  public GitScmProvider(GitBlameCommand gitBlameCommand, AnalysisWarningsWrapper analysisWarnings, GitIgnoreCommand gitIgnoreCommand) {
    super(gitBlameCommand, analysisWarnings);
    this.gitIgnoreCommand = gitIgnoreCommand;
  }

  @Override
  public GitIgnoreCommand ignoreCommand() {
    return Objects.requireNonNull(gitIgnoreCommand, "This method should never be called before SQ 7.6");
  }

}
