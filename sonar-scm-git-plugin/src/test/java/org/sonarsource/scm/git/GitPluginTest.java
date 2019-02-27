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

import org.junit.Test;
import org.sonar.api.Plugin;
import org.sonar.api.SonarQubeSide;
import org.sonar.api.SonarRuntime;
import org.sonar.api.internal.PluginContextImpl;
import org.sonar.api.internal.SonarRuntimeImpl;
import org.sonar.api.utils.Version;

import static org.assertj.core.api.Assertions.assertThat;

public class GitPluginTest {

  @Test
  public void getExtensions_before_7_7() {
    SonarRuntime runtime = SonarRuntimeImpl.forSonarQube(Version.create(5, 6), SonarQubeSide.SCANNER);
    Plugin.Context context = new PluginContextImpl.Builder().setSonarRuntime(runtime).build();
    new GitPlugin().define(context);
    assertThat(context.getExtensions()).hasSize(3);
  }

  @Test
  public void getExtensions_after_7_7() {
    SonarRuntime runtime = SonarRuntimeImpl.forSonarQube(Version.create(7, 7), SonarQubeSide.SCANNER);
    Plugin.Context context = new PluginContextImpl.Builder().setSonarRuntime(runtime).build();
    new GitPlugin().define(context);
    assertThat(context.getExtensions()).hasSize(4);
  }
}
