/*
 * Copyright 2013-2017 consulo.io
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

package com.intellij.java.analysis.impl.codeInspection.dataFlow.inference;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.JavaMethodContractUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * from kotlin
 */
class PurityInferenceResult {
    private List<ExpressionRange> mutableRefs;
    @Nullable
    private ExpressionRange singleCall;

    PurityInferenceResult(List<ExpressionRange> mutableRefs, @Nullable ExpressionRange singleCall) {
        this.mutableRefs = mutableRefs;
        this.singleCall = singleCall;
    }

    @RequiredReadAction
    public boolean isPure(PsiMethod method, Supplier<PsiCodeBlock> body) {
        return !mutatesNonLocals(method, body) && callsOnlyPureMethods(method, body);
    }

    public List<ExpressionRange> getMutableRefs() {
        return mutableRefs;
    }

    @Nullable
    public ExpressionRange getSingleCall() {
        return singleCall;
    }

    @RequiredReadAction
    private boolean mutatesNonLocals(PsiMethod method, Supplier<PsiCodeBlock> body) {
        for (ExpressionRange range : mutableRefs) {
            if (!isLocalVarReference(range.restoreExpression(body.get()), method)) {
                return true;
            }
        }
        return false;
    }

    @RequiredReadAction
    private boolean callsOnlyPureMethods(PsiMethod currentMethod, Supplier<PsiCodeBlock> body) {
        if (singleCall == null) {
            return true;
        }
        PsiCall psiCall = (PsiCall)singleCall.restoreExpression(body.get());
        assert psiCall != null;
        PsiMethod method = psiCall.resolveMethod();
        if (method != null) {
            return method.equals(currentMethod) || JavaMethodContractUtil.isPure(method);
        }
        else if (psiCall instanceof PsiNewExpression newExpr && psiCall.getArgumentList() != null
            && psiCall.getArgumentList().getExpressionCount() == 0) {

            PsiJavaCodeReferenceElement classOrAnonymousClassReference = newExpr.getClassOrAnonymousClassReference();
            if (classOrAnonymousClassReference != null
                && classOrAnonymousClassReference.resolve() instanceof PsiClass psiClass) {
                PsiClass superClass = psiClass.getSuperClass();
                return superClass == null || CommonClassNames.JAVA_LANG_OBJECT.equals(superClass.getQualifiedName());
            }
        }

        return false;
    }

    @RequiredReadAction
    private boolean isLocalVarReference(PsiExpression expression, PsiMethod scope) {
        if (expression instanceof PsiReferenceExpression refExpr) {
            PsiElement resolve = refExpr.resolve();
            return resolve instanceof PsiLocalVariable || resolve instanceof PsiParameter;
        }
        else if (expression instanceof PsiArrayAccessExpression arrayAccess) {
            if (arrayAccess.getArrayExpression() instanceof PsiReferenceExpression arrayRefExpr) {
                return arrayRefExpr.resolve() instanceof PsiLocalVariable localVar && isLocallyCreatedArray(scope, localVar);
            }
        }
        return false;
    }

    private boolean isLocallyCreatedArray(PsiMethod scope, PsiLocalVariable target) {
        PsiExpression initializer = target.getInitializer();
        if (initializer != null & !(initializer instanceof PsiNewExpression)) {
            return false;
        }

        for (PsiReference ref : ReferencesSearch.search(target, new LocalSearchScope(scope)).findAll()) {
            if (ref instanceof PsiReferenceExpression refExpr && PsiUtil.isAccessedForWriting(refExpr)) {
                PsiAssignmentExpression assign = PsiTreeUtil.getParentOfType(refExpr, PsiAssignmentExpression.class);
                if (assign == null || !(assign.getRExpression() instanceof PsiNewExpression)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        PurityInferenceResult that = (PurityInferenceResult)o;

        return Objects.equals(mutableRefs, that.mutableRefs)
            && Objects.equals(singleCall, that.singleCall);
    }

    @Override
    public int hashCode() {
        int result = mutableRefs != null ? mutableRefs.hashCode() : 0;
        return 31 * result + (singleCall != null ? singleCall.hashCode() : 0);
    }
}
