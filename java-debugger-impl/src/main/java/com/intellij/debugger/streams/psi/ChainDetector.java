// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.psi;

import com.intellij.java.language.psi.PsiMethodCallExpression;

/**
 * @author Vitaliy.Bibaev
 */
public interface ChainDetector {
    boolean isTerminationCall(PsiMethodCallExpression callExpression);

    boolean isIntermediateCall(PsiMethodCallExpression callExpression);

    default boolean isStreamCall(PsiMethodCallExpression callExpression) {
        return isIntermediateCall(callExpression) || isTerminationCall(callExpression);
    }
}
