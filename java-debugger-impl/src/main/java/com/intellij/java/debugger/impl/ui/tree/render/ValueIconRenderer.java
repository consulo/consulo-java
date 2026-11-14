// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.debugger.impl.ui.tree.render;

import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.engine.evaluation.EvaluationContext;
import com.intellij.java.debugger.impl.ui.tree.ValueDescriptor;
import consulo.ui.image.Image;
import org.jspecify.annotations.Nullable;

public interface ValueIconRenderer {
    @Nullable
    Image calcValueIcon(ValueDescriptor descriptor, EvaluationContext evaluationContext, DescriptorLabelListener listener)
        throws EvaluateException;
}
