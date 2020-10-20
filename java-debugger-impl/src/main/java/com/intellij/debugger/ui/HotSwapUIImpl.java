/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.debugger.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import com.intellij.CommonBundle;
import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.impl.DebuggerManagerListener;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.HotSwapFile;
import com.intellij.debugger.impl.HotSwapManager;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.compiler.CompilationStatusListener;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.compiler.CompilerTopics;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;

/**
 * User: lex
 * Date: Oct 2, 2003
 * Time: 6:00:55 PM
 */
@Singleton
public class HotSwapUIImpl extends HotSwapUI
{
	public static class Listener implements DebuggerManagerListener
	{
		private MessageBusConnection myConn = null;
		private int mySessionCount = 0;

		@Override
		public void sessionAttached(DebuggerSession session)
		{
			if(mySessionCount++ == 0)
			{
				Project project = session.getProject();

				myConn = project.getMessageBus().connect();
				myConn.subscribe(CompilerTopics.COMPILATION_STATUS, new MyCompilationStatusListener(project));
			}
		}

		@Override
		public void sessionDetached(DebuggerSession session)
		{
			mySessionCount = Math.max(0, mySessionCount - 1);
			if(mySessionCount == 0)
			{
				final MessageBusConnection conn = myConn;
				if(conn != null)
				{
					conn.disconnect();
					myConn = null;
				}
			}
		}
	}

	private final List<HotSwapVetoableListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
	private boolean myAskBeforeHotswap = true;
	private final Project myProject;
	private boolean myPerformHotswapAfterThisCompilation = true;

	@Inject
	public HotSwapUIImpl(final Project project)
	{
		myProject = project;
	}

	@Override
	public void addListener(HotSwapVetoableListener listener)
	{
		myListeners.add(listener);
	}

	@Override
	public void removeListener(HotSwapVetoableListener listener)
	{
		myListeners.remove(listener);
	}

	private boolean shouldDisplayHangWarning(DebuggerSettings settings, List<DebuggerSession> sessions)
	{
		if(!settings.HOTSWAP_HANG_WARNING_ENABLED)
		{
			return false;
		}
		// todo: return false if yourkit agent is inactive
		for(DebuggerSession session : sessions)
		{
			if(session.isPaused())
			{
				return true;
			}
		}
		return false;
	}

	private void hotSwapSessions(final List<DebuggerSession> sessions, @javax.annotation.Nullable final Map<String, List<String>> generatedPaths)
	{
		final boolean shouldAskBeforeHotswap = myAskBeforeHotswap;
		myAskBeforeHotswap = true;

		final DebuggerSettings settings = DebuggerSettings.getInstance();
		final String runHotswap = settings.RUN_HOTSWAP_AFTER_COMPILE;
		final boolean shouldDisplayHangWarning = shouldDisplayHangWarning(settings, sessions);

		if(shouldAskBeforeHotswap && DebuggerSettings.RUN_HOTSWAP_NEVER.equals(runHotswap))
		{
			return;
		}

		final boolean shouldPerformScan = true;

		final HotSwapProgressImpl findClassesProgress;
		if(shouldPerformScan)
		{
			findClassesProgress = new HotSwapProgressImpl(myProject);
		}
		else
		{
			boolean createProgress = false;
			for(DebuggerSession session : sessions)
			{
				if(session.isModifiedClassesScanRequired())
				{
					createProgress = true;
					break;
				}
			}
			findClassesProgress = createProgress ? new HotSwapProgressImpl(myProject) : null;
		}

		ApplicationManager.getApplication().executeOnPooledThread(() -> {
			final Map<DebuggerSession, Map<String, HotSwapFile>> modifiedClasses;
			if(shouldPerformScan)
			{
				modifiedClasses = scanForModifiedClassesWithProgress(sessions, findClassesProgress);
			}
			else
			{
				final List<DebuggerSession> toScan = new ArrayList<>();
				final List<DebuggerSession> toUseGenerated = new ArrayList<>();
				for(DebuggerSession session : sessions)
				{
					(session.isModifiedClassesScanRequired() ? toScan : toUseGenerated).add(session);
					session.setModifiedClassesScanRequired(false);
				}
				modifiedClasses = new HashMap<>();
				if(!toUseGenerated.isEmpty())
				{
					modifiedClasses.putAll(HotSwapManager.findModifiedClasses(toUseGenerated, generatedPaths));
				}
				if(!toScan.isEmpty())
				{
					modifiedClasses.putAll(scanForModifiedClassesWithProgress(toScan, findClassesProgress));
				}
			}

			final Application application = ApplicationManager.getApplication();
			if(modifiedClasses.isEmpty())
			{
				final String message = DebuggerBundle.message("status.hotswap.uptodate");
				HotSwapProgressImpl.NOTIFICATION_GROUP.createNotification(message, NotificationType.INFORMATION).notify(myProject);
				return;
			}

			application.invokeLater(() -> {
				if(shouldAskBeforeHotswap && !DebuggerSettings.RUN_HOTSWAP_ALWAYS.equals(runHotswap))
				{
					final RunHotswapDialog dialog = new RunHotswapDialog(myProject, sessions, shouldDisplayHangWarning);
					dialog.show();
					if(!dialog.isOK())
					{
						for(DebuggerSession session : modifiedClasses.keySet())
						{
							session.setModifiedClassesScanRequired(true);
						}
						return;
					}
					final Set<DebuggerSession> toReload = new HashSet<>(dialog.getSessionsToReload());
					for(DebuggerSession session : modifiedClasses.keySet())
					{
						if(!toReload.contains(session))
						{
							session.setModifiedClassesScanRequired(true);
						}
					}
					modifiedClasses.keySet().retainAll(toReload);
				}
				else
				{
					if(shouldDisplayHangWarning)
					{
						final int answer = Messages.showCheckboxMessageDialog(DebuggerBundle.message("hotswap.dialog.hang.warning"), DebuggerBundle
								.message("hotswap.dialog.title"), new String[]{
								"Perform &Reload Classes",
								"&Skip Reload Classes"
						}, CommonBundle.message("dialog.options.do.not.show"), false, 1, 1, Messages.getWarningIcon(), (exitCode, cb) -> {
							settings.HOTSWAP_HANG_WARNING_ENABLED = !cb.isSelected();
							return exitCode == DialogWrapper.OK_EXIT_CODE ? exitCode : DialogWrapper.CANCEL_EXIT_CODE;
						});
						if(answer == DialogWrapper.CANCEL_EXIT_CODE)
						{
							for(DebuggerSession session : modifiedClasses.keySet())
							{
								session.setModifiedClassesScanRequired(true);
							}
							return;
						}
					}
				}

				if(!modifiedClasses.isEmpty())
				{
					final HotSwapProgressImpl progress = new HotSwapProgressImpl(myProject);
					application.executeOnPooledThread(() -> reloadModifiedClasses(modifiedClasses, progress));
				}
			}, ModalityState.NON_MODAL);
		});
	}

	private static Map<DebuggerSession, Map<String, HotSwapFile>> scanForModifiedClassesWithProgress(final List<DebuggerSession> sessions,
			final HotSwapProgressImpl progress)
	{
		final Ref<Map<DebuggerSession, Map<String, HotSwapFile>>> result = Ref.create(null);
		ProgressManager.getInstance().runProcess(() -> {
			try
			{
				result.set(HotSwapManager.scanForModifiedClasses(sessions, progress));
			}
			finally
			{
				progress.finished();
			}
		}, progress.getProgressIndicator());
		return result.get();
	}

	private static void reloadModifiedClasses(final Map<DebuggerSession, Map<String, HotSwapFile>> modifiedClasses,
			final HotSwapProgressImpl progress)
	{
		ProgressManager.getInstance().runProcess(() -> {
			HotSwapManager.reloadModifiedClasses(modifiedClasses, progress);
			progress.finished();
		}, progress.getProgressIndicator());
	}

	@Override
	public void reloadChangedClasses(final DebuggerSession session, boolean compileBeforeHotswap)
	{
		dontAskHotswapAfterThisCompilation();
		if(compileBeforeHotswap)
		{
			CompilerManager.getInstance(session.getProject()).make(null);
		}
		else
		{
			if(session.isAttached())
			{
				hotSwapSessions(Collections.singletonList(session), null);
			}
		}
	}

	@Override
	public void dontPerformHotswapAfterThisCompilation()
	{
		myPerformHotswapAfterThisCompilation = false;
	}

	public void dontAskHotswapAfterThisCompilation()
	{
		myAskBeforeHotswap = false;
	}

	private static class MyCompilationStatusListener implements CompilationStatusListener
	{
		private final AtomicReference<Map<String, List<String>>> myGeneratedPaths = new AtomicReference<>(new HashMap<>());
		private Project myProject;

		public MyCompilationStatusListener(Project project)
		{
			myProject = project;
		}

		public void fileGenerated(String outputRoot, String relativePath)
		{
			if(StringUtil.endsWith(relativePath, ".class"))
			{
				// collect only classes
				final Map<String, List<String>> map = myGeneratedPaths.get();
				List<String> paths = map.get(outputRoot);
				if(paths == null)
				{
					paths = new ArrayList<>();
					map.put(outputRoot, paths);
				}
				paths.add(relativePath);
			}
		}

		@Override
		public void compilationFinished(boolean aborted, int errors, int warnings, CompileContext compileContext)
		{
			final Map<String, List<String>> generated = myGeneratedPaths.getAndSet(new HashMap<>());
			if(myProject.isDisposed())
			{
				return;
			}

			HotSwapUIImpl hotSwapUI = (HotSwapUIImpl) HotSwapUI.getInstance(myProject);

			if(errors == 0 && !aborted && hotSwapUI.myPerformHotswapAfterThisCompilation)
			{
				for(HotSwapVetoableListener listener : hotSwapUI.myListeners)
				{
					if(!listener.shouldHotSwap(compileContext))
					{
						return;
					}
				}

				final List<DebuggerSession> sessions = new ArrayList<>();
				Collection<DebuggerSession> debuggerSessions = DebuggerManagerEx.getInstanceEx(myProject).getSessions();
				for(final DebuggerSession debuggerSession : debuggerSessions)
				{
					if(debuggerSession.isAttached() && debuggerSession.getProcess().canRedefineClasses())
					{
						sessions.add(debuggerSession);
					}
				}
				if(!sessions.isEmpty())
				{
					hotSwapUI.hotSwapSessions(sessions, generated);
				}
			}
			hotSwapUI.myPerformHotswapAfterThisCompilation = true;
		}
	}
}
