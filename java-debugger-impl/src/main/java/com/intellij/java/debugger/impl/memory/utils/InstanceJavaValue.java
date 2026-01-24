// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.debugger.impl.memory.utils;

import com.intellij.java.debugger.impl.engine.JavaValue;
import com.intellij.java.debugger.impl.engine.evaluation.EvaluationContextImpl;
import com.intellij.java.debugger.impl.ui.impl.watch.NodeManagerImpl;
import com.intellij.java.debugger.impl.ui.impl.watch.ValueDescriptorImpl;
import jakarta.annotation.Nonnull;

public class InstanceJavaValue extends JavaValue {
    public InstanceJavaValue(@Nonnull ValueDescriptorImpl valueDescriptor,
                             @Nonnull EvaluationContextImpl evaluationContext,
                             NodeManagerImpl nodeManager) {
        super(null, valueDescriptor, evaluationContext, nodeManager, false);
    }

    @Override
    public boolean canNavigateToSource() {
        return false;
    }
}
