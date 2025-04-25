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

import java.util.List;
import java.util.function.Supplier;

import consulo.application.Application;
import consulo.ui.annotation.RequiredUIAccess;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import com.intellij.java.debugger.SourcePosition;
import com.intellij.java.debugger.impl.engine.DebugProcessImpl;
import com.intellij.java.debugger.impl.engine.JVMNameUtil;
import com.intellij.java.debugger.impl.engine.evaluation.EvaluationContextImpl;
import com.intellij.java.debugger.impl.engine.events.SuspendContextCommandImpl;
import com.intellij.java.debugger.impl.DebuggerContextImpl;
import com.intellij.java.debugger.impl.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.java.debugger.impl.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.java.debugger.impl.ui.tree.ValueDescriptor;
import consulo.ui.ex.action.AnActionEvent;
import consulo.application.ApplicationManager;
import consulo.logging.Logger;
import consulo.application.util.function.Computable;
import com.intellij.java.language.psi.PsiClass;
import consulo.internal.com.sun.jdi.AbsentInformationException;
import consulo.internal.com.sun.jdi.ArrayType;
import consulo.internal.com.sun.jdi.ClassNotLoadedException;
import consulo.internal.com.sun.jdi.ClassNotPreparedException;
import consulo.internal.com.sun.jdi.ClassType;
import consulo.internal.com.sun.jdi.Location;
import consulo.internal.com.sun.jdi.ReferenceType;
import consulo.internal.com.sun.jdi.Type;
import consulo.internal.com.sun.jdi.Value;

public class JumpToObjectAction extends DebuggerAction {
    private static final Logger LOG = Logger.getInstance(JumpToObjectAction.class);

    @Override
    @RequiredUIAccess
    public void actionPerformed(AnActionEvent e) {
        DebuggerTreeNodeImpl selectedNode = getSelectedNode(e.getDataContext());
        if (selectedNode == null) {
            return;
        }

        NodeDescriptorImpl descriptor = selectedNode.getDescriptor();
        if (!(descriptor instanceof ValueDescriptor valueDescriptor)) {
            return;
        }

        DebuggerContextImpl debuggerContext = getDebuggerContext(e.getDataContext());
        DebugProcessImpl debugProcess = debuggerContext.getDebugProcess();
        if (debugProcess == null) {
            return;
        }

        debugProcess.getManagerThread().schedule(new NavigateCommand(debuggerContext, valueDescriptor, debugProcess, e));
    }

    @Override
    @RequiredUIAccess
    public void update(@Nonnull AnActionEvent e) {
        if (!isFirstStart(e)) {
            return;
        }

        DebuggerContextImpl debuggerContext = getDebuggerContext(e.getDataContext());
        DebugProcessImpl debugProcess = debuggerContext.getDebugProcess();
        if (debugProcess == null) {
            e.getPresentation().setVisible(false);
            return;
        }

        DebuggerTreeNodeImpl selectedNode = getSelectedNode(e.getDataContext());
        if (selectedNode == null) {
            e.getPresentation().setVisible(false);
            return;
        }

        NodeDescriptorImpl descriptor = selectedNode.getDescriptor();
        if (descriptor instanceof ValueDescriptor valueDescriptor) {
            debugProcess.getManagerThread().schedule(new EnableCommand(debuggerContext, valueDescriptor, debugProcess, e));
        }
        else {
            e.getPresentation().setVisible(false);
        }
    }

    private static SourcePosition calcPosition(ValueDescriptor descriptor, DebugProcessImpl debugProcess)
        throws ClassNotLoadedException {
        Value value = descriptor.getValue();
        if (value == null) {
            return null;
        }

        Type type = value.type();
        if (type == null) {
            return null;
        }

        try {
            if (type instanceof ArrayType arrayType) {
                type = arrayType.componentType();
            }
            if (type instanceof ClassType clsType) {
                List<Location> locations = clsType.allLineLocations();
                if (locations.size() > 0) {
                    Location location = locations.get(0);
                    return Application.get().runReadAction((Supplier<SourcePosition>)() -> {
                        SourcePosition position = debugProcess.getPositionManager().getSourcePosition(location);
                        // adjust position for non-anonymous classes
                        if (clsType.name().indexOf('$') < 0) {
                            PsiClass classAt = JVMNameUtil.getClassAt(position);
                            if (classAt != null) {
                                SourcePosition classPosition = SourcePosition.createFromElement(classAt);
                                if (classPosition != null) {
                                    position = classPosition;
                                }
                            }
                        }
                        return position;
                    });
                }
            }
        }
        catch (ClassNotPreparedException | AbsentInformationException e) {
            LOG.debug(e);
        }
        return null;
    }

    public static class NavigateCommand extends SourcePositionCommand {
        public NavigateCommand(
            DebuggerContextImpl debuggerContext,
            ValueDescriptor descriptor,
            DebugProcessImpl debugProcess,
            AnActionEvent e
        ) {
            super(debuggerContext, descriptor, debugProcess, e);
        }

        @Override
        protected NavigateCommand createRetryCommand() {
            return new NavigateCommand(myDebuggerContext, myDescriptor, myDebugProcess, myActionEvent);
        }

        @Override
        protected void doAction(SourcePosition sourcePosition) {
            if (sourcePosition != null) {
                sourcePosition.navigate(true);
            }
        }
    }

    private static class EnableCommand extends SourcePositionCommand {
        public EnableCommand(
            DebuggerContextImpl debuggerContext,
            ValueDescriptor descriptor,
            DebugProcessImpl debugProcess,
            AnActionEvent e
        ) {
            super(debuggerContext, descriptor, debugProcess, e);
        }

        @Override
        protected EnableCommand createRetryCommand() {
            return new EnableCommand(myDebuggerContext, myDescriptor, myDebugProcess, myActionEvent);
        }

        @Override
        protected void doAction(SourcePosition sourcePosition) {
            enableAction(myActionEvent, sourcePosition != null);
        }
    }

    public abstract static class SourcePositionCommand extends SuspendContextCommandImpl {
        protected final DebuggerContextImpl myDebuggerContext;
        protected final ValueDescriptor myDescriptor;
        protected final DebugProcessImpl myDebugProcess;
        protected final AnActionEvent myActionEvent;

        public SourcePositionCommand(
            DebuggerContextImpl debuggerContext,
            ValueDescriptor descriptor,
            DebugProcessImpl debugProcess,
            AnActionEvent actionEvent
        ) {
            super(debuggerContext.getSuspendContext());
            myDebuggerContext = debuggerContext;
            myDescriptor = descriptor;
            myDebugProcess = debugProcess;
            myActionEvent = actionEvent;
        }

        @Override
        public final void contextAction() throws Exception {
            try {
                doAction(calcPosition(myDescriptor, myDebugProcess));
            }
            catch (ClassNotLoadedException ex) {
                String className = ex.className();
                if (loadClass(className) != null) {
                    myDebugProcess.getManagerThread().schedule(createRetryCommand());
                }
            }
        }

        protected abstract SourcePositionCommand createRetryCommand();

        protected abstract void doAction(@Nullable SourcePosition sourcePosition);

        private ReferenceType loadClass(String className) {
            EvaluationContextImpl eContext = myDebuggerContext.createEvaluationContext();
            try {
                return myDebugProcess.loadClass(eContext, className, eContext.getClassLoader());
            }
            catch (Throwable ignored) {
            }
            return null;
        }
    }
}
