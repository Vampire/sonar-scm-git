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
package org.sonarsource.scm.git.jgit;

import org.eclipse.jgit.api.DiffCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.junit.Test;
import org.sonar.api.batch.scm.ScmProvider;
import org.sonar.api.internal.google.common.collect.ImmutableMap;
import org.sonar.api.internal.google.common.collect.ImmutableSet;
import org.sonarsource.scm.git.AbstractGitScmProviderBefore77Test;
import org.sonarsource.scm.git.AnalysisWarningsWrapper;
import org.sonarsource.scm.git.GitBlameCommand;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Collections;

import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class JGitScmProviderBefore77Test extends AbstractGitScmProviderBefore77Test {

  @Test
  public void branchChangedFiles_should_return_null_on_io_errors_of_repo_builder() {
    JGitScmProviderBefore77 provider = new JGitScmProviderBefore77(mockCommand(), analysisWarnings) {
      @Override
      Repository buildRepo(Path basedir) throws IOException {
        throw new IOException();
      }
    };
    assertThat(provider.branchChangedFiles("branch", worktree)).isNull();
    verifyZeroInteractions(analysisWarnings);
  }

  @Test
  public void branchChangedFiles_should_return_null_if_repo_exactref_is_null() throws IOException {
    Repository repository = mock(Repository.class);
    RefDatabase refDatabase = mock(RefDatabase.class);
    when(repository.getRefDatabase()).thenReturn(refDatabase);
    when(refDatabase.getRef("branch")).thenReturn(null);

    ScmProvider provider = new JGitScmProviderBefore77(mockCommand(), analysisWarnings) {
      @Override
      Repository buildRepo(Path basedir) {
        return repository;
      }
    };
    assertThat(provider.branchChangedFiles("branch", worktree)).isNull();

    String warning = "Could not find ref 'branch' in refs/heads or refs/remotes/origin."
      + " You may see unexpected issues and changes. Please make sure to fetch this ref before pull request analysis.";
    verify(analysisWarnings).addUnique(warning);
  }

  @Test
  public void branchChangedFiles_should_return_null_on_io_errors_of_RevWalk() throws IOException {
    RevWalk walk = mock(RevWalk.class);
    when(walk.parseCommit(any())).thenThrow(new IOException());

    ScmProvider provider = new JGitScmProviderBefore77(mockCommand(), analysisWarnings) {
      @Override
      RevWalk newRevWalk(Repository repo) {
        return walk;
      }
    };
    assertThat(provider.branchChangedFiles("branch", worktree)).isNull();
  }

  @Test
  public void branchChangedFiles_should_return_null_on_git_api_errors() throws GitAPIException {
    DiffCommand diffCommand = mock(DiffCommand.class);
    when(diffCommand.setShowNameAndStatusOnly(anyBoolean())).thenReturn(diffCommand);
    when(diffCommand.setOldTree(any())).thenReturn(diffCommand);
    when(diffCommand.setNewTree(any())).thenReturn(diffCommand);
    when(diffCommand.call()).thenThrow(mock(GitAPIException.class));

    Git git = mock(Git.class);
    when(git.diff()).thenReturn(diffCommand);

    ScmProvider provider = new JGitScmProviderBefore77(mockCommand(), analysisWarnings) {
      @Override
      Git newGit(Repository repo) {
        return git;
      }
    };
    assertThat(provider.branchChangedFiles("master", worktree)).isNull();
    verify(diffCommand).call();
  }

  @Test
  public void branchChangedLines_omits_files_with_git_api_errors() throws IOException {
    DiffEntry diffEntry = mock(DiffEntry.class);
    when(diffEntry.getChangeType()).thenReturn(DiffEntry.ChangeType.MODIFY);

    DiffFormatter diffFormatter = mock(DiffFormatter.class);
    when(diffFormatter.scan(any(AbstractTreeIterator.class), any()))
            .thenReturn(Collections.singletonList(diffEntry))
            .thenThrow(mock(IOException.class));

    ScmProvider provider = new JGitScmProviderBefore77(mockCommand(), analysisWarnings) {
      @Override
      DiffFormatter newDiffFormatter(OutputStream out) {
        return diffFormatter;
      }
    };
    assertThat(provider.branchChangedLines("master", worktree,
      ImmutableSet.of(worktree.resolve("foo"), worktree.resolve("bar"))))
        .isEqualTo(ImmutableMap.of(worktree.resolve("foo"), emptySet()));
    verify(diffFormatter, times(2)).scan(any(AbstractTreeIterator.class), any());
  }

  @Test
  public void branchChangedLines_returns_null_on_io_errors_of_repo_builder() {
    ScmProvider provider = new JGitScmProviderBefore77(mockCommand(), analysisWarnings) {
      @Override
      Repository buildRepo(Path basedir) throws IOException {
        throw new IOException();
      }
    };
    assertThat(provider.branchChangedLines("branch", worktree, emptySet())).isNull();
  }

  protected JGitScmProviderBefore77 newGitScmProviderBefore77(GitBlameCommand gitBlameCommand, AnalysisWarningsWrapper analysisWarnings) {
    return new JGitScmProviderBefore77(gitBlameCommand, analysisWarnings);
  }
}
