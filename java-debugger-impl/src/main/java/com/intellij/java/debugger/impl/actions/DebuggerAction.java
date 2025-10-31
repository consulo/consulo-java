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
package com.intellij.java.debugger.impl.actions;

import com.intellij.java.debugger.impl.DebuggerContextImpl;
import com.intellij.java.debugger.impl.DebuggerManagerEx;
import com.intellij.java.debugger.impl.DebuggerStateManager;
import com.intellij.java.debugger.impl.engine.JavaDebugProcess;
import com.intellij.java.debugger.impl.ui.impl.DebuggerTreePanel;
import com.intellij.java.debugger.impl.ui.impl.watch.DebuggerTree;
import com.intellij.java.debugger.impl.ui.impl.watch.DebuggerTreeNodeImpl;
import consulo.dataContext.DataContext;
import consulo.dataContext.DataManager;
import consulo.disposer.Disposable;
import consulo.execution.debug.XDebugSession;
import consulo.execution.debug.frame.XValueNode;
import consulo.execution.debug.ui.XValueTree;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.CommonShortcuts;
import consulo.ui.ex.awt.event.DoubleClickListener;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Class DebuggerAction
 *
 * @author Jeka
 */
public abstract class DebuggerAction extends AnAction {
    private static final DebuggerTreeNodeImpl[] EMPTY_TREE_NODE_ARRAY = new DebuggerTreeNodeImpl[0];

    protected DebuggerAction(@Nonnull LocalizeValue text) {
        super(text);
    }

    @Nullable
    public static DebuggerTree getTree(DataContext dataContext) {
        return dataContext.getData(DebuggerTree.DATA_KEY);
    }

    @Nullable
    public static DebuggerTreePanel getPanel(DataContext dataContext) {
        return dataContext.getData(DebuggerTreePanel.DATA_KEY);
    }

    @Nullable
    public static DebuggerTreeNodeImpl getSelectedNode(DataContext dataContext) {
        DebuggerTree tree = getTree(dataContext);
        if (tree == null) {
            return null;
        }

        if (tree.getSelectionCount() != 1) {
            return null;
        }
        TreePath path = tree.getSelectionPath();
        if (path == null) {
            return null;
        }
        Object component = path.getLastPathComponent();
        return component instanceof DebuggerTreeNodeImpl debuggerTreeNode ? debuggerTreeNode : null;
    }

    @Nullable
    public static DebuggerTreeNodeImpl[] getSelectedNodes(DataContext dataContext) {
        DebuggerTree tree = getTree(dataContext);
        if (tree == null) {
            return null;
        }
        TreePath[] paths = tree.getSelectionPaths();
        if (paths == null || paths.length == 0) {
            return EMPTY_TREE_NODE_ARRAY;
        }
        List<DebuggerTreeNodeImpl> nodes = new ArrayList<>(paths.length);
        for (TreePath path : paths) {
            Object component = path.getLastPathComponent();
            if (component instanceof DebuggerTreeNodeImpl debuggerTreeNode) {
                nodes.add(debuggerTreeNode);
            }
        }
        return nodes.toArray(new DebuggerTreeNodeImpl[nodes.size()]);
    }

    public static DebuggerContextImpl getDebuggerContext(DataContext dataContext) {
        DebuggerTreePanel panel = getPanel(dataContext);
        if (panel != null) {
            return panel.getContext();
        }
        else {
            Project project = dataContext.getData(Project.KEY);
            return project != null ? (DebuggerManagerEx.getInstanceEx(project)).getContext() : DebuggerContextImpl.EMPTY_CONTEXT;
        }
    }

    @Nullable
    public static DebuggerStateManager getContextManager(DataContext dataContext) {
        DebuggerTreePanel panel = getPanel(dataContext);
        return panel == null ? null : panel.getContextManager();
    }

    public static Disposable installEditAction(JTree tree, String actionName) {
        DoubleClickListener listener = new DoubleClickListener() {
            @Override
            protected boolean onDoubleClick(MouseEvent e) {
                if (tree.getPathForLocation(e.getX(), e.getY()) == null) {
                    return false;
                }
                DataContext dataContext = DataManager.getInstance().getDataContext(tree);
                GotoFrameSourceAction.doAction(dataContext);
                return true;
            }
        };
        listener.installOn(tree);

        AnAction action = ActionManager.getInstance().getAction(actionName);
        action.registerCustomShortcutSet(CommonShortcuts.getEditSource(), tree);

        return () -> {
            listener.uninstall(tree);
            action.unregisterCustomShortcutSet(tree);
        };
    }

    public static boolean isFirstStart(AnActionEvent event) {
        //noinspection HardCodedStringLiteral
        String key = "initialized";
        if (event.getPresentation().getClientProperty(key) != null) {
            return false;
        }

        event.getPresentation().putClientProperty(key, key);
        return true;
    }

    public static void enableAction(AnActionEvent event, boolean enable) {
        SwingUtilities.invokeLater(() -> {
            event.getPresentation().setEnabled(enable);
            event.getPresentation().setVisible(true);
        });
    }

    public static void refreshViews(@Nonnull XValueNode node) {
        XValueTree tree = node.getTree();
        if (tree != null) {
            refreshViews(tree.getSession());
        }
    }

    public static void refreshViews(@Nullable XDebugSession session) {
        if (session != null) {
            if (session.getDebugProcess() instanceof JavaDebugProcess debugProcess) {
                debugProcess.saveNodeHistory();
            }
            session.rebuildViews();
        }
    }
}
