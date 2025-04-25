/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.java.debugger.impl.engine.events.DebuggerCommandImpl;
import com.intellij.java.debugger.impl.jdi.ThreadReferenceProxyImpl;
import com.intellij.java.debugger.impl.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.java.debugger.impl.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.java.debugger.impl.ui.impl.watch.ThreadDescriptorImpl;
import com.intellij.java.debugger.localize.JavaDebuggerLocalize;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.Presentation;

import java.util.ArrayList;
import java.util.List;

/**
 * @author lex
 * @since 2003-09-26
 */
public class InterruptThreadAction extends DebuggerAction {
    @Override
    @RequiredUIAccess
    public void actionPerformed(AnActionEvent e) {
        DebuggerTreeNodeImpl[] nodes = getSelectedNodes(e.getDataContext());
        if (nodes == null) {
            return;
        }

        //noinspection ConstantConditions
        List<ThreadReferenceProxyImpl> threadsToInterrupt = new ArrayList<>();
        for (DebuggerTreeNodeImpl debuggerTreeNode : nodes) {
            if (debuggerTreeNode.getDescriptor() instanceof ThreadDescriptorImpl threadDescriptor) {
                threadsToInterrupt.add(threadDescriptor.getThreadReference());
            }
        }

        if (!threadsToInterrupt.isEmpty()) {
            DebuggerContextImpl debuggerContext = getDebuggerContext(e.getDataContext());
            debuggerContext.getDebugProcess().getManagerThread().schedule(new DebuggerCommandImpl() {
                @Override
                protected void action() throws Exception {
                    for (ThreadReferenceProxyImpl thread : threadsToInterrupt) {
                        thread.getThreadReference().interrupt();
                    }
                }
            });
        }
    }

    @Override
    @RequiredUIAccess
    public void update(AnActionEvent e) {
        DebuggerTreeNodeImpl[] selectedNodes = getSelectedNodes(e.getDataContext());

        boolean visible = false;
        boolean enabled = false;

        if (selectedNodes != null && selectedNodes.length > 0) {
            visible = true;
            enabled = true;
            for (DebuggerTreeNodeImpl selectedNode : selectedNodes) {
                if (!(selectedNode.getDescriptor() instanceof ThreadDescriptorImpl)) {
                    visible = false;
                    break;
                }
            }

            if (visible) {
                for (DebuggerTreeNodeImpl selectedNode : selectedNodes) {
                    ThreadDescriptorImpl threadDescriptor = (ThreadDescriptorImpl)selectedNode.getDescriptor();
                    if (threadDescriptor.isFrozen()) {
                        enabled = false;
                        break;
                    }
                }
            }
        }
        Presentation presentation = e.getPresentation();
        presentation.setTextValue(JavaDebuggerLocalize.actionInterruptThreadText());
        presentation.setVisible(visible);
        presentation.setEnabled(enabled);
    }
}
