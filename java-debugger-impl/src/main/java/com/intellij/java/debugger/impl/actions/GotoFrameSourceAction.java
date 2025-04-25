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

import com.intellij.java.debugger.impl.DebuggerContextUtil;
import com.intellij.java.debugger.impl.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.java.debugger.impl.ui.impl.watch.StackFrameDescriptorImpl;
import consulo.ui.ex.action.AnActionEvent;
import consulo.dataContext.DataContext;
import consulo.project.Project;

/**
 * @author lex
 */
public abstract class GotoFrameSourceAction extends DebuggerAction {
    public void actionPerformed(AnActionEvent e) {
        DataContext dataContext = e.getDataContext();
        doAction(dataContext);
    }

    protected static void doAction(DataContext dataContext) {
        final Project project = dataContext.getData(Project.KEY);
        if (project == null) {
            return;
        }
        StackFrameDescriptorImpl stackFrameDescriptor = getStackFrameDescriptor(dataContext);
        if (stackFrameDescriptor != null) {
            DebuggerContextUtil.setStackFrame(getContextManager(dataContext), stackFrameDescriptor.getFrameProxy());
        }
    }

    public void update(AnActionEvent e) {
        e.getPresentation().setVisible(getStackFrameDescriptor(e.getDataContext()) != null);
    }

    private static StackFrameDescriptorImpl getStackFrameDescriptor(DataContext dataContext) {
        DebuggerTreeNodeImpl selectedNode = getSelectedNode(dataContext);
        if (selectedNode == null) {
            return null;
        }
        if (selectedNode.getDescriptor() == null || !(selectedNode.getDescriptor() instanceof StackFrameDescriptorImpl)) {
            return null;
        }
        return (StackFrameDescriptorImpl)selectedNode.getDescriptor();
    }
}
