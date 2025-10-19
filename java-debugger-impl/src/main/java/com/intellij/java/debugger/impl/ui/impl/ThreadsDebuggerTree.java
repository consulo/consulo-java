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

import com.intellij.java.debugger.impl.DebuggerContextImpl;
import com.intellij.java.debugger.impl.DebuggerInvocationUtil;
import com.intellij.java.debugger.impl.DebuggerSession;
import com.intellij.java.debugger.impl.engine.DebugProcessImpl;
import com.intellij.java.debugger.impl.engine.SuspendContextImpl;
import com.intellij.java.debugger.impl.engine.evaluation.EvaluationContextImpl;
import com.intellij.java.debugger.impl.engine.events.DebuggerCommandImpl;
import com.intellij.java.debugger.impl.jdi.StackFrameProxyImpl;
import com.intellij.java.debugger.impl.jdi.ThreadGroupReferenceProxyImpl;
import com.intellij.java.debugger.impl.jdi.ThreadReferenceProxyImpl;
import com.intellij.java.debugger.impl.jdi.VirtualMachineProxyImpl;
import com.intellij.java.debugger.impl.settings.ThreadsViewSettings;
import com.intellij.java.debugger.impl.ui.impl.watch.*;
import com.intellij.java.debugger.localize.JavaDebuggerLocalize;
import consulo.application.Application;
import consulo.execution.debug.localize.XDebuggerLocalize;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.ex.awt.tree.TreeModelAdapter;

import javax.swing.*;
import javax.swing.event.TreeModelEvent;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * @author lex
 * @since 2003-09-26
 */
public class ThreadsDebuggerTree extends DebuggerTree {
    private static final Logger LOG = Logger.getInstance(ThreadsDebuggerTree.class);

    public ThreadsDebuggerTree(Project project) {
        super(project);
        getEmptyText().setText(XDebuggerLocalize.debuggerThreadsNotAvailable().get());
    }

    @Override
    protected NodeManagerImpl createNodeManager(Project project) {
        return new NodeManagerImpl(project, this) {
            @Override
            public String getContextKey(StackFrameProxyImpl frame) {
                return "ThreadsView";
            }
        };
    }

    @Override
    protected boolean isExpandable(DebuggerTreeNodeImpl node) {
        NodeDescriptorImpl descriptor = node.getDescriptor();
        return !(descriptor instanceof StackFrameDescriptorImpl) && descriptor.isExpandable();
    }

    @Override
    protected void build(DebuggerContextImpl context) {
        DebuggerSession session = context.getDebuggerSession();
        final RefreshThreadsTreeCommand command = new RefreshThreadsTreeCommand(session);

        final DebuggerSession.State state = session != null ? session.getState() : DebuggerSession.State.DISPOSED;
        if (Application.get().isUnitTestMode()
            || state == DebuggerSession.State.PAUSED
            || state == DebuggerSession.State.RUNNING) {
            showMessage(MessageDescriptor.EVALUATING);
            context.getDebugProcess().getManagerThread().schedule(command);
        }
        else {
            showMessage(session != null ? session.getStateDescription() : JavaDebuggerLocalize.statusDebugStopped());
        }
    }

    private class RefreshThreadsTreeCommand extends DebuggerCommandImpl {
        private final DebuggerSession mySession;

        public RefreshThreadsTreeCommand(DebuggerSession session) {
            mySession = session;
        }

        @Override
        protected void action() throws Exception {
            final DebuggerTreeNodeImpl root = getNodeFactory().getDefaultNode();

            final DebugProcessImpl debugProcess = mySession.getProcess();
            if (!debugProcess.isAttached()) {
                return;
            }
            final DebuggerContextImpl context = mySession.getContextManager().getContext();
            final SuspendContextImpl suspendContext = context.getSuspendContext();
            final ThreadReferenceProxyImpl suspendContextThread = suspendContext != null ? suspendContext.getThread() : null;

            final boolean showGroups = ThreadsViewSettings.getInstance().SHOW_THREAD_GROUPS;
            try {
                final ThreadReferenceProxyImpl currentThread =
                    ThreadsViewSettings.getInstance().SHOW_CURRENT_THREAD ? suspendContextThread : null;
                final VirtualMachineProxyImpl vm = debugProcess.getVirtualMachineProxy();

                final EvaluationContextImpl evaluationContext =
                    suspendContext != null ? getDebuggerContext().createEvaluationContext() : null;
                final NodeManagerImpl nodeManager = getNodeFactory();

                if (showGroups) {
                    ThreadGroupReferenceProxyImpl topCurrentGroup = null;

                    if (currentThread != null) {
                        topCurrentGroup = currentThread.threadGroupProxy();
                        if (topCurrentGroup != null) {
                            for (ThreadGroupReferenceProxyImpl parentGroup = topCurrentGroup.parent(); parentGroup != null;
                                 parentGroup = parentGroup.parent()) {
                                topCurrentGroup = parentGroup;
                            }
                        }

                        if (topCurrentGroup != null) {
                            root.add(nodeManager.createNode(
                                nodeManager.getThreadGroupDescriptor(null, topCurrentGroup),
                                evaluationContext
                            ));
                        }
                        else {
                            root.add(nodeManager.createNode(nodeManager.getThreadDescriptor(null, currentThread), evaluationContext));
                        }
                    }

                    for (ThreadGroupReferenceProxyImpl group : vm.topLevelThreadGroups()) {
                        if (group != topCurrentGroup) {
                            DebuggerTreeNodeImpl threadGroup =
                                nodeManager.createNode(nodeManager.getThreadGroupDescriptor(null, group), evaluationContext);
                            root.add(threadGroup);
                        }
                    }
                }
                else {
                    // do not show thread groups
                    if (currentThread != null) {
                        root.insert(nodeManager.createNode(nodeManager.getThreadDescriptor(null, currentThread), evaluationContext), 0);
                    }
                    List<ThreadReferenceProxyImpl> allThreads = new ArrayList<>(vm.allThreads());
                    Collections.sort(allThreads, ThreadReferenceProxyImpl.ourComparator);

                    for (ThreadReferenceProxyImpl threadProxy : allThreads) {
                        if (threadProxy.equals(currentThread)) {
                            continue;
                        }
                        root.add(nodeManager.createNode(nodeManager.getThreadDescriptor(null, threadProxy), evaluationContext));
                    }
                }
            }
            catch (Exception ex) {
                root.add(MessageDescriptor.DEBUG_INFO_UNAVAILABLE);
                if (LOG.isDebugEnabled()) {
                    LOG.debug(ex);
                }
            }

            final boolean hasThreadToSelect = suspendContextThread != null; // thread can be null if pause was pressed
            final List<ThreadGroupReferenceProxyImpl> groups;
            if (hasThreadToSelect && showGroups) {
                groups = new ArrayList<>();
                for (ThreadGroupReferenceProxyImpl group = suspendContextThread.threadGroupProxy(); group != null; group = group.parent()) {
                    groups.add(group);
                }
                Collections.reverse(groups);
            }
            else {
                groups = Collections.emptyList();
            }

            DebuggerInvocationUtil.swingInvokeLater(
                getProject(),
                () -> {
                    getMutableModel().setRoot(root);
                    treeChanged();
                    if (hasThreadToSelect) {
                        selectThread(groups, suspendContextThread, true);
                    }
                }
            );
        }

        private void selectThread(
            final List<ThreadGroupReferenceProxyImpl> pathToThread,
            final ThreadReferenceProxyImpl thread,
            final boolean expand
        ) {
            LOG.assertTrue(SwingUtilities.isEventDispatchThread());
            class MyTreeModelAdapter extends TreeModelAdapter {
                private void structureChanged(DebuggerTreeNodeImpl node) {
                    for (Enumeration enumeration = node.children(); enumeration.hasMoreElements(); ) {
                        DebuggerTreeNodeImpl child = (DebuggerTreeNodeImpl)enumeration.nextElement();
                        nodeChanged(child);
                    }
                }

                private void nodeChanged(DebuggerTreeNodeImpl debuggerTreeNode) {
                    if (pathToThread.size() == 0) {
                        if (debuggerTreeNode.getDescriptor() instanceof ThreadDescriptorImpl && ((ThreadDescriptorImpl)debuggerTreeNode.getDescriptor()).getThreadReference() == thread) {
                            removeListener();
                            final TreePath treePath = new TreePath(debuggerTreeNode.getPath());
                            setSelectionPath(treePath);
                            if (expand && !isExpanded(treePath)) {
                                expandPath(treePath);
                            }
                        }
                    }
                    else {
                        if (debuggerTreeNode.getDescriptor() instanceof ThreadGroupDescriptorImpl && ((ThreadGroupDescriptorImpl)debuggerTreeNode.getDescriptor()).getThreadGroupReference() ==
                            pathToThread.get(0)) {
                            pathToThread.remove(0);
                            expandPath(new TreePath(debuggerTreeNode.getPath()));
                        }
                    }
                }

                private void removeListener() {
                    final TreeModelAdapter listener = this;
                    SwingUtilities.invokeLater(() -> getModel().removeTreeModelListener(listener));
                }

                @Override
                public void treeStructureChanged(TreeModelEvent event) {
                    if (event.getPath().length <= 1) {
                        removeListener();
                        return;
                    }
                    structureChanged((DebuggerTreeNodeImpl)event.getTreePath().getLastPathComponent());
                }
            }

            MyTreeModelAdapter listener = new MyTreeModelAdapter();
            listener.structureChanged((DebuggerTreeNodeImpl)getModel().getRoot());
            getModel().addTreeModelListener(listener);
        }
    }
}
