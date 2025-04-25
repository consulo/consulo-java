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
package com.intellij.java.debugger.impl.actions;

import com.intellij.java.debugger.impl.DebuggerInvocationUtil;
import com.intellij.java.debugger.SourcePosition;
import com.intellij.java.debugger.impl.engine.DebugProcessImpl;
import com.intellij.java.debugger.impl.engine.SourcePositionProvider;
import com.intellij.java.debugger.engine.evaluation.expression.Modifier;
import com.intellij.java.debugger.impl.engine.events.DebuggerContextCommandImpl;
import com.intellij.java.debugger.impl.DebuggerContextImpl;
import com.intellij.java.debugger.impl.DebuggerSession;
import com.intellij.java.debugger.impl.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.java.debugger.impl.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.java.debugger.impl.ui.impl.watch.WatchItemDescriptor;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.IdeActions;
import consulo.ui.ex.action.Presentation;
import consulo.application.ReadAction;
import consulo.project.Project;

public class EditSourceAction extends DebuggerAction {
    @Override
    public void actionPerformed(AnActionEvent e) {
        final Project project = e.getData(Project.KEY);

        if (project == null) {
            return;
        }

        final DebuggerTreeNodeImpl selectedNode = getSelectedNode(e.getDataContext());
        if (selectedNode != null) {
            final DebuggerContextImpl debuggerContext = getDebuggerContext(e.getDataContext());
            DebugProcessImpl process = debuggerContext.getDebugProcess();
            if (process != null) {
                process.getManagerThread().schedule(new DebuggerContextCommandImpl(debuggerContext) {
                    @Override
                    public void threadAction() {
                        final SourcePosition sourcePosition = getSourcePosition(selectedNode, debuggerContext);
                        if (sourcePosition != null) {
                            sourcePosition.navigate(true);
                        }
                    }
                });
            }
        }
    }

    private static SourcePosition getSourcePosition(DebuggerTreeNodeImpl selectedNode, DebuggerContextImpl debuggerContext) {
        final DebuggerContextImpl context = debuggerContext;

        if (selectedNode == null || context == null) {
            return null;
        }

        final Project project = selectedNode.getProject();

        final DebuggerSession debuggerSession = context.getDebuggerSession();

        if (debuggerSession == null) {
            return null;
        }

        NodeDescriptorImpl nodeDescriptor = selectedNode.getDescriptor();
        if (nodeDescriptor instanceof WatchItemDescriptor) {
            Modifier modifier = ((WatchItemDescriptor)nodeDescriptor).getModifier();
            if (modifier == null) {
                return null;
            }
            nodeDescriptor = (NodeDescriptorImpl)modifier.getInspectItem(project);
        }

        final NodeDescriptorImpl nodeDescriptor1 = nodeDescriptor;
        return ReadAction.compute(() -> SourcePositionProvider.getSourcePosition(nodeDescriptor1, project, context));
    }

    @Override
    public void update(AnActionEvent e) {
        final Project project = e.getData(Project.KEY);

        final DebuggerContextImpl debuggerContext = getDebuggerContext(e.getDataContext());
        final DebuggerTreeNodeImpl node = getSelectedNode(e.getDataContext());

        final Presentation presentation = e.getPresentation();
        if (debuggerContext.getDebugProcess() != null) {
            presentation.setEnabled(true);
            debuggerContext.getDebugProcess().getManagerThread().schedule(new DebuggerContextCommandImpl(debuggerContext) {
                @Override
                public void threadAction() {
                    final SourcePosition position = getSourcePosition(node, debuggerContext);
                    if (position == null) {
                        DebuggerInvocationUtil.swingInvokeLater(project, () -> presentation.setEnabled(false));
                    }
                }
            });
        }
        else {
            presentation.setEnabled(false);
        }
        e.getPresentation()
            .setText(ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE).getTemplatePresentation().getText());
    }
}
