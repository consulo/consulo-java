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

/*
 * @author Eugene Zhuravlev
 */
package com.intellij.debugger.ui.impl;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.KeyEvent;

import javax.swing.KeyStroke;

import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.DebuggerStateManager;
import com.intellij.debugger.ui.impl.watch.DebuggerTree;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.Project;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.util.dataholder.Key;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.ui.PopupHandler;
import com.intellij.util.SingleAlarm;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import consulo.internal.com.sun.jdi.VMDisconnectedException;

public abstract class DebuggerTreePanel extends UpdatableDebuggerView implements DataProvider, Disposable
{
	public static final Key<DebuggerTreePanel> DATA_KEY = Key.create("DebuggerPanel");

	private final SingleAlarm myRebuildAlarm = new SingleAlarm(new Runnable()
	{
		@Override
		public void run()
		{
			try
			{
				final DebuggerContextImpl context = getContext();
				if(context.getDebuggerSession() != null)
				{
					getTree().rebuild(context);
				}
			}
			catch(VMDisconnectedException ignored)
			{
			}

		}
	}, 100);

	protected DebuggerTree myTree;

	public DebuggerTreePanel(Project project, DebuggerStateManager stateManager)
	{
		super(project, stateManager);
		myTree = createTreeView();

		final PopupHandler popupHandler = new PopupHandler()
		{
			@Override
			public void invokePopup(Component comp, int x, int y)
			{
				ActionPopupMenu popupMenu = createPopupMenu();
				if(popupMenu != null)
				{
					myTree.myTipManager.registerPopup(popupMenu.getComponent()).show(comp, x, y);
				}
			}
		};
		myTree.addMouseListener(popupHandler);

		setFocusTraversalPolicy(new IdeFocusTraversalPolicy()
		{
			@Override
			public Component getDefaultComponentImpl(Container focusCycleRoot)
			{
				return myTree;
			}
		});

		registerDisposable(new Disposable()
		{
			@Override
			public void dispose()
			{
				myTree.removeMouseListener(popupHandler);
			}
		});

		final Shortcut[] shortcuts = KeymapManager.getInstance().getActiveKeymap().getShortcuts("ToggleBookmark");
		final CustomShortcutSet shortcutSet = shortcuts.length > 0 ? new CustomShortcutSet(shortcuts) : new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_F11, 0));
		overrideShortcut(myTree, XDebuggerActions.MARK_OBJECT, shortcutSet);
	}

	protected abstract DebuggerTree createTreeView();

	@Override
	protected void rebuild(DebuggerSession.Event event)
	{
		myRebuildAlarm.cancelAndRequest();
	}

	@Override
	public void dispose()
	{
		Disposer.dispose(myRebuildAlarm);
		try
		{
			super.dispose();
		}
		finally
		{
			final DebuggerTree tree = myTree;
			if(tree != null)
			{
				Disposer.dispose(tree);
			}
			// prevent mem leak from inside Swing
			myTree = null;
		}
	}


	protected abstract ActionPopupMenu createPopupMenu();

	public final DebuggerTree getTree()
	{
		return myTree;
	}

	public void clear()
	{
		myTree.removeAllChildren();
	}

	@Override
	public Object getData(Key dataId)
	{
		if(DATA_KEY == dataId)
		{
			return this;
		}
		return null;
	}

	@Override
	public void requestFocus()
	{
		getTree().requestFocus();
	}
}
