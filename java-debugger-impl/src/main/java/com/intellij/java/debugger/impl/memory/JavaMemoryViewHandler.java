/*
 * Copyright 2013-2026 consulo.io
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
package com.intellij.java.debugger.impl.memory;

import com.intellij.java.debugger.impl.engine.DebugProcessImpl;
import com.intellij.java.debugger.impl.engine.JavaValue;
import com.intellij.java.debugger.impl.engine.SuspendContextImpl;
import com.intellij.java.debugger.impl.engine.evaluation.EvaluationContextImpl;
import com.intellij.java.debugger.impl.engine.events.SuspendContextCommandImpl;
import com.intellij.java.debugger.impl.memory.utils.InstanceJavaValue;
import com.intellij.java.debugger.impl.memory.utils.InstanceValueDescriptor;
import com.intellij.java.debugger.impl.ui.impl.watch.NodeManagerImpl;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import consulo.application.ReadAction;
import consulo.execution.debug.frame.XSuspendContext;
import consulo.execution.debug.frame.XValue;
import consulo.execution.debug.frame.XValueChildrenList;
import consulo.execution.debug.memory.TypeInfo;
import consulo.execution.debug.memory.XMemoryClasses;
import consulo.execution.debug.memory.XMemoryViewHandler;
import consulo.internal.com.sun.jdi.ObjectReference;
import consulo.internal.com.sun.jdi.ReferenceType;
import consulo.internal.com.sun.jdi.VirtualMachine;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.navigation.Navigatable;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

/**
 * The classes of the debugged VM with their instance counts.
 *
 * @author VISTALL
 * @since 2026-11-17
 */
public class JavaMemoryViewHandler implements XMemoryViewHandler {
    private final DebugProcessImpl myDebugProcess;
    private final NodeManagerImpl myNodeManager;

    public JavaMemoryViewHandler(DebugProcessImpl debugProcess, NodeManagerImpl nodeManager) {
        myDebugProcess = debugProcess;
        myNodeManager = nodeManager;
    }

    @Override
    public CompletableFuture<XMemoryClasses> computeClasses(XSuspendContext context) {
        CompletableFuture<XMemoryClasses> result = new CompletableFuture<>();
        schedule(context, result, suspendContext -> {
            List<ReferenceType> classes = myDebugProcess.getVirtualMachineProxy().allClasses();
            List<TypeInfo> types = new ArrayList<>(classes.size());
            for (ReferenceType type : classes) {
                types.add(new JavaTypeInfo(type));
            }
            if (classes.isEmpty() || !classes.getFirst().virtualMachine().canGetInstanceInfo()) {
                return new XMemoryClasses(types, null);
            }
            return new XMemoryClasses(types, getInstancesCounts(classes, classes.getFirst().virtualMachine()));
        });
        return result;
    }

    private static Map<TypeInfo, Long> getInstancesCounts(List<ReferenceType> classes, VirtualMachine vm) {
        long[] counts = vm.instanceCounts(classes);
        Map<TypeInfo, Long> result = new LinkedHashMap<>();
        for (int i = 0; i < classes.size(); i++) {
            result.put(new JavaTypeInfo(classes.get(i)), counts[i]);
        }
        return result;
    }

    @Override
    public CompletableFuture<XValueChildrenList> computeInstances(TypeInfo type, int limit, XSuspendContext context) {
        CompletableFuture<XValueChildrenList> result = new CompletableFuture<>();
        if (!(type instanceof JavaTypeInfo javaType)) {
            result.complete(XValueChildrenList.EMPTY);
            return result;
        }
        schedule(context, result, suspendContext -> {
            Project project = myDebugProcess.getProject();
            EvaluationContextImpl evaluationContext = myDebugProcess.getDebuggerContext().createEvaluationContext();
            XValueChildrenList children = new XValueChildrenList();
            for (ObjectReference instance : javaType.getReferenceType().instances(limit)) {
                children.add(new InstanceJavaValue(new InstanceValueDescriptor(project, instance), evaluationContext, myNodeManager));
            }
            return children;
        });
        return result;
    }

    @Override
    public @Nullable TypeInfo getTypeInfo(XValue value) {
        if (value instanceof JavaValue javaValue && javaValue.getDescriptor().getValue() instanceof ObjectReference reference) {
            return new JavaTypeInfo(reference.referenceType());
        }
        return null;
    }

    @Override
    public @Nullable Navigatable getTypeSource(TypeInfo type) {
        Project project = myDebugProcess.getProject();
        PsiClass psiClass = ReadAction.compute(() -> JavaPsiFacade.getInstance(project)
            .findClass(type.name().replace('$', '.'), GlobalSearchScope.allScope(project)));
        return psiClass;
    }

    private interface SuspendedComputation<T> {
        T compute(SuspendContextImpl suspendContext) throws Exception;
    }

    private <T> void schedule(XSuspendContext context, CompletableFuture<T> result, SuspendedComputation<T> computation) {
        if (!(context instanceof SuspendContextImpl suspendContext)) {
            result.completeExceptionally(new IllegalArgumentException("Not a java suspend context: " + context));
            return;
        }
        myDebugProcess.getManagerThread().schedule(new SuspendContextCommandImpl(suspendContext) {
            @Override
            public void contextAction(SuspendContextImpl suspendContext) throws Exception {
                try {
                    result.complete(computation.compute(suspendContext));
                }
                catch (Exception e) {
                    result.completeExceptionally(e);
                }
            }

            @Override
            protected void commandCancelled() {
                result.completeExceptionally(new CancellationException());
            }
        });
    }
}
