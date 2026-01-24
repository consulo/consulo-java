// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.psi.impl;

import com.intellij.debugger.streams.psi.ChainDetector;
import com.intellij.debugger.streams.psi.StreamApiUtil;
import com.intellij.java.language.psi.PsiMethodCallExpression;

/**
 * @author Vitaliy.Bibaev
 */
public class JavaStreamChainDetector implements ChainDetector {
    @Override
    public boolean isTerminationCall(PsiMethodCallExpression callExpression) {
        return StreamApiUtil.isTerminationStreamCall(callExpression);
    }

    @Override
    public boolean isIntermediateCall(PsiMethodCallExpression callExpression) {
        return StreamApiUtil.isIntermediateStreamCall(callExpression);
    }
}
