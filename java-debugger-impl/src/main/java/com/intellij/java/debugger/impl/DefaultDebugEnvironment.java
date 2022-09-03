/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.debugger.impl;

import java.util.Comparator;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.intellij.java.debugger.impl.settings.DebuggerSettings;
import consulo.execution.ExecutionResult;
import consulo.execution.configuration.RunProfileState;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.process.ExecutionException;
import com.intellij.java.execution.configurations.JavaCommandLine;
import com.intellij.java.execution.configurations.RemoteConnection;
import com.intellij.java.execution.configurations.RemoteState;
import consulo.execution.configuration.RunProfile;
import consulo.content.scope.SearchScopeProvider;
import consulo.project.Project;
import consulo.content.bundle.Sdk;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.scope.DelegatingGlobalSearchScope;
import consulo.language.psi.scope.GlobalSearchScope;

public class DefaultDebugEnvironment implements DebugEnvironment
{
	private final GlobalSearchScope mySearchScope;
	private final RemoteConnection myRemoteConnection;
	private final long myPollTimeout;
	private final ExecutionEnvironment environment;
	private final RunProfileState state;
	private final boolean myNeedParametersSet;

	public DefaultDebugEnvironment(@Nonnull ExecutionEnvironment environment, @Nonnull RunProfileState state, RemoteConnection remoteConnection, boolean pollConnection)
	{
		this(environment, state, remoteConnection, pollConnection ? LOCAL_START_TIMEOUT : 0);
	}

	public DefaultDebugEnvironment(@Nonnull ExecutionEnvironment environment, @Nonnull RunProfileState state, RemoteConnection remoteConnection, long pollTimeout)
	{
		this.environment = environment;
		this.state = state;
		myRemoteConnection = remoteConnection;
		myPollTimeout = pollTimeout;

		mySearchScope = createSearchScope(environment.getProject(), environment.getRunProfile());
		myNeedParametersSet = remoteConnection.isServerMode() && remoteConnection.isUseSockets() && "0".equals(remoteConnection.getAddress());
	}

	private static GlobalSearchScope createSearchScope(@Nonnull Project project, @Nullable RunProfile runProfile)
	{
		GlobalSearchScope scope = SearchScopeProvider.createSearchScope(project, runProfile);
		if(scope.equals(GlobalSearchScope.allScope(project)))
		{
			// prefer sources over class files
			return new DelegatingGlobalSearchScope(scope)
			{
				final ProjectFileIndex myProjectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
				final Comparator<VirtualFile> myScopeComparator = Comparator.comparing(myProjectFileIndex::isInSource).thenComparing(myProjectFileIndex::isInLibrarySource).thenComparing
						(super::compare);

				@Override
				public int compare(@Nonnull VirtualFile file1, @Nonnull VirtualFile file2)
				{
					return myScopeComparator.compare(file1, file2);
				}
			};
		}
		return scope;
	}

	@Override
	public ExecutionResult createExecutionResult() throws ExecutionException
	{
		// debug port may have changed, reinit parameters just in case
		if(myNeedParametersSet && state instanceof JavaCommandLine)
		{
			DebuggerManagerImpl.createDebugParameters(((JavaCommandLine) state).getJavaParameters(), true, DebuggerSettings.SOCKET_TRANSPORT, myRemoteConnection.getAddress(), false);
		}
		return state.execute(environment.getExecutor(), environment.getRunner());
	}

	@Nonnull
	@Override
	public GlobalSearchScope getSearchScope()
	{
		return mySearchScope;
	}

	@Override
	public boolean isRemote()
	{
		return state instanceof RemoteState;
	}

	@Override
	public RemoteConnection getRemoteConnection()
	{
		return myRemoteConnection;
	}

	@Override
	public long getPollTimeout()
	{
		return myPollTimeout;
	}

	@Override
	public String getSessionName()
	{
		return environment.getRunProfile().getName();
	}

	@Nullable
	@Override
	public Sdk getAlternativeJre()
	{
		return AlternativeJreClassFinder.getAlternativeJre(environment.getRunProfile());
	}

	@Nullable
	@Override
	public Sdk getRunJre()
	{
		if(state instanceof JavaCommandLine)
		{
			try
			{
				return ((JavaCommandLine) state).getJavaParameters().getJdk();
			}
			catch(ExecutionException ignore)
			{
			}
		}
		return null;
	}
}
