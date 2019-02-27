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

import org.sonar.api.Plugin;
import org.sonar.api.utils.Version;

public final class GitPlugin implements Plugin {
  @Override
  public void define(Context context) {
    GitUtils.determineGitExecutable(context.getBootConfiguration());
    context.addExtensions(
      GitBlameCommand.class,
      AnalysisWarningsSupport.getAnalysisWarningsWrapper(context.getRuntime()));
    if (context.getRuntime().getApiVersion().isGreaterThanOrEqual(Version.create(7, 7))) {
      context.addExtensions(GitScmProvider.class,
        GitIgnoreCommand.class);
    } else {
      context.addExtension(GitScmProviderBefore77.class);
    }
  }
}
