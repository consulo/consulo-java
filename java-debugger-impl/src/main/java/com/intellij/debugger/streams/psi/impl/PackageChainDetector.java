// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.psi.impl;

import com.intellij.debugger.streams.psi.ChainDetector;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import com.intellij.java.language.psi.util.PsiUtil;

/**
 * @author Vitaliy.Bibaev
 */
public class PackageChainDetector implements ChainDetector {
    private final ChainDetector delegate;
    private final String packageName;

    public PackageChainDetector(ChainDetector delegate, String packageName) {
        this.delegate = delegate;
        this.packageName = packageName;
    }

    public static PackageChainDetector forJavaStreams(String packageName) {
        return new PackageChainDetector(new JavaStreamChainDetector(), packageName);
    }

    @Override
    public boolean isTerminationCall(PsiMethodCallExpression callExpression) {
        return delegate.isTerminationCall(callExpression) && isPackageSupported(callExpression);
    }

    @Override
    public boolean isIntermediateCall(PsiMethodCallExpression callExpression) {
        return delegate.isIntermediateCall(callExpression) && isPackageSupported(callExpression);
    }

    @Override
    public boolean isStreamCall(PsiMethodCallExpression callExpression) {
        return delegate.isStreamCall(callExpression) && isPackageSupported(callExpression);
    }

    private boolean isPackageSupported(String name) {
        return name.startsWith(packageName);
    }

    private boolean isPackageSupported(PsiMethodCallExpression callExpression) {
        return isPackageSupported(extractPackage(callExpression));
    }

    private String extractPackage(PsiMethodCallExpression callExpression) {
        PsiMethod psiMethod = callExpression.resolveMethod();
        if (psiMethod != null) {
            PsiClass topClass = PsiUtil.getTopLevelClass(psiMethod);
            if (topClass != null) {
                String pkgName = PsiUtil.getPackageName(topClass);
                if (pkgName != null) {
                    return pkgName;
                }
            }
        }
        return "";
    }
}
