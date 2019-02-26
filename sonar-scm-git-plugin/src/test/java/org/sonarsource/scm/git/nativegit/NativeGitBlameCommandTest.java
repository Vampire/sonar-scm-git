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

import org.junit.BeforeClass;
import org.sonar.api.config.Configuration;
import org.sonar.api.scan.filesystem.PathResolver;
import org.sonarsource.scm.git.AnalysisWarningsWrapper;
import org.sonarsource.scm.git.AbstractGitBlameCommandTest;

import static org.mockito.Mockito.mock;

public class NativeGitBlameCommandTest extends AbstractGitBlameCommandTest {

  @BeforeClass
  public static void determineGitExecutable() {
    NativeGitUtils.determineGitExecutable(mock(Configuration.class));
  }

  @Override
  protected NativeGitBlameCommand newGitBlameCommand(PathResolver pathResolver, AnalysisWarningsWrapper analysisWarnings) {
    return new NativeGitBlameCommand(pathResolver, analysisWarnings);
  }
}
