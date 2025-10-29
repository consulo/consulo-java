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
import com.intellij.java.debugger.impl.engine.events.DebuggerContextCommandImpl;
import com.intellij.java.debugger.impl.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.java.debugger.impl.ui.impl.watch.ValueDescriptorImpl;
import consulo.annotation.component.ActionImpl;
import consulo.execution.debug.localize.XDebuggerLocalize;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;

@ActionImpl(id = "Debugger.AutoRenderer")
public class AutoRendererAction extends AnAction {
    public AutoRendererAction() {
        super(XDebuggerLocalize.actionAutoRendererText());
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(AnActionEvent e) {
        DebuggerContextImpl debuggerContext = DebuggerAction.getDebuggerContext(e.getDataContext());
        DebuggerTreeNodeImpl[] selectedNodes = DebuggerAction.getSelectedNodes(e.getDataContext());

        if (debuggerContext != null && debuggerContext.getDebugProcess() != null) {
            debuggerContext.getDebugProcess().getManagerThread().schedule(new DebuggerContextCommandImpl(debuggerContext) {
                @Override
                public void threadAction() {
                    for (DebuggerTreeNodeImpl selectedNode : selectedNodes) {
                        if (selectedNode.getDescriptor() instanceof ValueDescriptorImpl valueDescriptor) {
                            valueDescriptor.setRenderer(null);
                            selectedNode.calcRepresentation();
                        }
                    }
                }
            });
        }
    }
}
