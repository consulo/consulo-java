// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.ui.impl;

import com.intellij.debugger.streams.trace.impl.JavaEvaluationContext;
import com.intellij.debugger.streams.trace.impl.JvmValue;
import com.intellij.java.debugger.engine.evaluation.EvaluationContext;
import com.intellij.java.debugger.impl.JavaDebuggerEditorsProvider;
import com.intellij.java.debugger.impl.engine.JavaValue;
import com.intellij.java.debugger.impl.memory.utils.InstanceJavaValue;
import com.intellij.java.debugger.impl.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.java.debugger.impl.ui.impl.watch.MessageDescriptor;
import com.intellij.java.debugger.impl.ui.impl.watch.NodeManagerImpl;
import com.intellij.java.debugger.ui.tree.NodeDescriptor;
import consulo.execution.debug.evaluation.XDebuggerEditorsProvider;
import consulo.execution.debug.frame.XNamedValue;
import consulo.execution.debug.frame.XValueContainer;
import consulo.execution.debug.stream.trace.CollectionTreeBuilder;
import consulo.execution.debug.stream.trace.GenericEvaluationContext;
import consulo.execution.debug.stream.trace.TraceElement;
import consulo.execution.debug.stream.trace.Value;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public class JavaCollectionTreeBuilder implements CollectionTreeBuilder {
    private final Project project;
    private final MyNodeManager nodeManager;

    public JavaCollectionTreeBuilder(@Nonnull Project project) {
        this.project = project;
        this.nodeManager = new MyNodeManager(project);
    }

    private static class MyNodeManager extends NodeManagerImpl {
        MyNodeManager(@Nullable Project project) {
            super(project, null);
        }

        @Override
        public DebuggerTreeNodeImpl createNode(NodeDescriptor descriptor, EvaluationContext evaluationContext) {
            return new DebuggerTreeNodeImpl(null, descriptor);
        }

        @Override
        public DebuggerTreeNodeImpl createMessageNode(MessageDescriptor descriptor) {
            return new DebuggerTreeNodeImpl(null, descriptor);
        }

        @Override
        public DebuggerTreeNodeImpl createMessageNode(LocalizeValue message) {
            return new DebuggerTreeNodeImpl(null, new MessageDescriptor(message));
        }
    }

    @Nonnull
    @Override
    public XNamedValue createXNamedValue(@Nullable Value value, @Nonnull GenericEvaluationContext evaluationContext) {
        consulo.internal.com.sun.jdi.Value jvmValue = value instanceof JvmValue ? ((JvmValue) value).getValue() : null;
        PrimitiveValueDescriptor valueDescriptor = new PrimitiveValueDescriptor(project, jvmValue);
        return new InstanceJavaValue(valueDescriptor, ((JavaEvaluationContext) evaluationContext).getContext(), nodeManager);
    }

    @Nonnull
    @Override
    public Object getKey(@Nonnull XValueContainer container, @Nonnull Object nullMarker) {
        consulo.internal.com.sun.jdi.Value jvmValue = ((JavaValue) container).getDescriptor().getValue();
        return jvmValue != null ? jvmValue : nullMarker;
    }

    @Nonnull
    @Override
    public Object getKey(@Nonnull TraceElement traceElement, @Nonnull Object nullMarker) {
        Value value = traceElement.getValue();
        consulo.internal.com.sun.jdi.Value jvmValue = value instanceof JvmValue ? ((JvmValue) value).getValue() : null;
        return jvmValue != null ? jvmValue : nullMarker;
    }

    @Nonnull
    @Override
    public XDebuggerEditorsProvider getEditorsProvider() {
        return new JavaDebuggerEditorsProvider();
    }

    @Override
    public boolean isSupported(@Nonnull XValueContainer container) {
        return container instanceof JavaValue;
    }
}
