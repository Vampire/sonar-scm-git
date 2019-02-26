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

import org.sonar.api.batch.scm.BlameCommand;
import org.sonar.api.batch.scm.IgnoreCommand;
import org.sonar.api.batch.scm.ScmProvider;
import org.sonarsource.scm.git.jgit.JGitScmProvider;
import org.sonarsource.scm.git.nativegit.NativeGitScmProvider;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

public class GitScmProvider extends ScmProvider {

  private final ScmProvider delegate;

  public GitScmProvider(GitBlameCommand gitBlameCommand, AnalysisWarningsWrapper analysisWarnings, GitIgnoreCommand gitIgnoreCommand) {
    if (GitUtils.useJGit()) {
      delegate = new JGitScmProvider(gitBlameCommand, analysisWarnings, gitIgnoreCommand);
    } else {
      delegate = new NativeGitScmProvider(gitBlameCommand, analysisWarnings, gitIgnoreCommand);
    }
  }

  @Override
  public String key() {
    return delegate.key();
  }

  @Override
  public boolean supports(File baseDir) {
    return delegate.supports(baseDir);
  }

  @Override
  public BlameCommand blameCommand() {
    return delegate.blameCommand();
  }

  @Override
  public Set<Path> branchChangedFiles(String targetBranchName, Path rootBaseDir) {
    return delegate.branchChangedFiles(targetBranchName, rootBaseDir);
  }

  @Override
  public Map<Path, Set<Integer>> branchChangedLines(String targetBranchName, Path projectBaseDir, Set<Path> changedFiles) {
    return delegate.branchChangedLines(targetBranchName, projectBaseDir, changedFiles);
  }

  @Override
  public Path relativePathFromScmRoot(Path path) {
    return delegate.relativePathFromScmRoot(path);
  }

  @Override
  public String revisionId(Path path) {
    return delegate.revisionId(path);
  }

  @Override
  public IgnoreCommand ignoreCommand() {
    return delegate.ignoreCommand();
  }

}
