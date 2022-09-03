/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.java.debugger.impl.ui.impl;

import java.awt.BorderLayout;

import javax.swing.JComponent;
import javax.swing.JPanel;

import javax.annotation.Nonnull;
import com.intellij.java.debugger.impl.DebuggerContextImpl;
import com.intellij.java.debugger.impl.DebuggerContextListener;
import com.intellij.java.debugger.impl.DebuggerSession;
import com.intellij.java.debugger.impl.DebuggerStateManager;
import com.intellij.java.debugger.impl.ui.DebuggerView;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.ShortcutSet;
import consulo.project.Project;
import consulo.disposer.CompositeDisposable;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;

public abstract class UpdatableDebuggerView extends JPanel implements DebuggerView
{
	private final Project myProject;
	private final DebuggerStateManager myStateManager;
	private volatile boolean myRefreshNeeded = true;
	private final CompositeDisposable myDisposables = new CompositeDisposable();
	private volatile boolean myUpdateEnabled;

	protected UpdatableDebuggerView(final Project project, final DebuggerStateManager stateManager)
	{
		setLayout(new BorderLayout());
		myProject = project;
		myStateManager = stateManager;

		final DebuggerContextListener contextListener = new DebuggerContextListener()
		{
			@Override
			public void changeEvent(@Nonnull DebuggerContextImpl newContext, DebuggerSession.Event event)
			{
				UpdatableDebuggerView.this.changeEvent(newContext, event);
			}
		};
		myStateManager.addListener(contextListener);

		registerDisposable(new Disposable()
		{
			@Override
			public void dispose()
			{
				myStateManager.removeListener(contextListener);
			}
		});

	}

	protected void changeEvent(final DebuggerContextImpl newContext, final DebuggerSession.Event event)
	{
		if(newContext.getDebuggerSession() != null)
		{
			rebuildIfVisible(event);
		}
	}

	protected final boolean isUpdateEnabled()
	{
		return myUpdateEnabled || isShowing();
	}

	@Override
	public final void setUpdateEnabled(final boolean enabled)
	{
		myUpdateEnabled = enabled;
	}

	@Override
	public final boolean isRefreshNeeded()
	{
		return myRefreshNeeded;
	}

	public final void rebuildIfVisible(final DebuggerSession.Event event)
	{
		if(isUpdateEnabled())
		{
			myRefreshNeeded = false;
			rebuild(event);
		}
		else
		{
			myRefreshNeeded = true;
		}
	}

	protected abstract void rebuild(DebuggerSession.Event event);

	protected final void registerDisposable(Disposable disposable)
	{
		myDisposables.add(disposable);
	}

	@Nonnull
	public DebuggerContextImpl getContext()
	{
		return myStateManager.getContext();
	}

	protected final Project getProject()
	{
		return myProject;
	}

	public DebuggerStateManager getContextManager()
	{
		return myStateManager;
	}

	public void dispose()
	{
		Disposer.dispose(myDisposables);
	}

	protected void overrideShortcut(final JComponent forComponent, final String actionId, final ShortcutSet shortcutSet)
	{
		final AnAction action = ActionManager.getInstance().getAction(actionId);
		action.registerCustomShortcutSet(shortcutSet, forComponent);
		registerDisposable(new Disposable()
		{
			@Override
			public void dispose()
			{
				action.unregisterCustomShortcutSet(forComponent);
			}
		});
	}
}
