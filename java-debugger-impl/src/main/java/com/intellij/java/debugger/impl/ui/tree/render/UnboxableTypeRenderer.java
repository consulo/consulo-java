// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.debugger.impl.ui.tree.render;

import com.intellij.java.debugger.engine.DebuggerUtils;
import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.engine.evaluation.EvaluationContext;
import com.intellij.java.debugger.impl.engine.evaluation.EvaluationContextImpl;
import com.intellij.java.debugger.impl.engine.evaluation.expression.UnBoxingEvaluator;
import com.intellij.java.debugger.impl.ui.tree.ValueDescriptor;
import consulo.annotation.component.ExtensionImpl;
import consulo.internal.com.sun.jdi.ReferenceType;
import consulo.internal.com.sun.jdi.Type;
import consulo.internal.com.sun.jdi.Value;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Shows the boxed primitive types (Integer, Long, ...) by their primitive value instead of the quoted toString().
 * <p>
 * Upstream registers one provider per boxed type, here one provider handles all of them.
 */
@ExtensionImpl
public class UnboxableTypeRenderer extends CompoundRendererProvider {
    @Override
    protected String getName() {
        return "Boxed primitive";
    }

    @Override
    protected ValueLabelRenderer getValueLabelRenderer() {
        return new LabelRenderer() {
            @Override
            public String calcLabel(ValueDescriptor descriptor, EvaluationContext evaluationContext, DescriptorLabelListener labelListener)
                throws EvaluateException {
                Value value = descriptor.getValue();
                if (value == null) {
                    return "null";
                }
                Object primitive = UnBoxingEvaluator.unbox(value, (EvaluationContextImpl) evaluationContext);
                return DebuggerUtils.getValueAsString(evaluationContext, (Value) primitive);
            }

            @Override
            public boolean isOnDemand(EvaluationContext evaluationContext, ValueDescriptor valueDescriptor) {
                return false;
            }
        };
    }

    @Override
    protected Function<Type, CompletableFuture<Boolean>> getIsApplicableChecker() {
        return type -> CompletableFuture.completedFuture(type instanceof ReferenceType && UnBoxingEvaluator.isTypeUnboxable(type.name()));
    }

    @Override
    protected boolean isEnabled() {
        return true;
    }
}
