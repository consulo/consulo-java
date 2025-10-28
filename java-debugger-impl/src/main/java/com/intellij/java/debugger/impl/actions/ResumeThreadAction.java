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
import com.intellij.java.debugger.impl.engine.DebugProcessImpl;
import com.intellij.java.debugger.impl.engine.events.SuspendContextCommandImpl;
import com.intellij.java.debugger.impl.jdi.ThreadReferenceProxyImpl;
import com.intellij.java.debugger.impl.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.java.debugger.impl.ui.impl.watch.ThreadDescriptorImpl;
import com.intellij.java.debugger.localize.JavaDebuggerLocalize;
import consulo.annotation.component.ActionImpl;
import consulo.execution.debug.localize.XDebuggerLocalize;
import consulo.internal.com.sun.jdi.request.EventRequest;
import consulo.localize.LocalizeValue;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.Presentation;

/**
 * @author lex
 * @since 2003-09-26
 */
@ActionImpl(id = "Debugger.ResumeThread")
public class ResumeThreadAction extends DebuggerAction {
    public ResumeThreadAction() {
        super(XDebuggerLocalize.actionResumeThreadText());
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(AnActionEvent e) {
        DebuggerTreeNodeImpl[] selectedNode = getSelectedNodes(e.getDataContext());
        DebuggerContextImpl debuggerContext = getDebuggerContext(e.getDataContext());
        DebugProcessImpl debugProcess = debuggerContext.getDebugProcess();

        //noinspection ConstantConditions
        for (DebuggerTreeNodeImpl debuggerTreeNode : selectedNode) {
            ThreadDescriptorImpl threadDescriptor = ((ThreadDescriptorImpl)debuggerTreeNode.getDescriptor());

            if (threadDescriptor.isSuspended()) {
                ThreadReferenceProxyImpl thread = threadDescriptor.getThreadReference();
                debugProcess.getManagerThread().schedule(new SuspendContextCommandImpl(debuggerContext.getSuspendContext()) {
                    @Override
                    public void contextAction() throws Exception {
                        debugProcess.createResumeThreadCommand(getSuspendContext(), thread).run();
                        debuggerTreeNode.calcValue();
                    }
                });
            }
        }
    }

    @Override
    @RequiredUIAccess
    public void update(AnActionEvent e) {
        DebuggerTreeNodeImpl[] selectedNodes = getSelectedNodes(e.getDataContext());

        boolean visible = false;
        boolean enabled = false;
        LocalizeValue text = JavaDebuggerLocalize.actionResumeThreadTextResume();

        if (selectedNodes != null && selectedNodes.length > 0) {
            visible = true;
            enabled = true;
            for (DebuggerTreeNodeImpl selectedNode : selectedNodes) {
                if (!(selectedNode.getDescriptor() instanceof ThreadDescriptorImpl threadDescr && threadDescr.isSuspended())) {
                    visible = false;
                    break;
                }
            }
            if (visible) {
                for (DebuggerTreeNodeImpl selectedNode : selectedNodes) {
                    ThreadDescriptorImpl threadDescriptor = (ThreadDescriptorImpl)selectedNode.getDescriptor();
                    if (threadDescriptor.getSuspendContext().getSuspendPolicy() == EventRequest.SUSPEND_ALL
                        && !threadDescriptor.isFrozen()) {
                        enabled = false;
                        break;
                    }
                    else if (threadDescriptor.isFrozen()) {
                        text = JavaDebuggerLocalize.actionResumeThreadTextUnfreeze();
                    }
                }
            }
        }
        Presentation presentation = e.getPresentation();
        presentation.setTextValue(text);
        presentation.setVisible(visible);
        presentation.setEnabled(enabled);
    }
}
