// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.codeInspection.miscGenerics;

import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.java.analysis.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.CommonDataflow;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.TypeConstraint;
import com.intellij.java.analysis.impl.codeInspection.miscGenerics.SuspiciousMethodCallUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.MethodSignature;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.psi.PsiElementVisitor;
import consulo.util.lang.ObjectUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */
@ExtensionImpl
public class SuspiciousCollectionsMethodCallsInspection extends AbstractBaseJavaLocalInspectionTool {
    public boolean REPORT_CONVERTIBLE_METHOD_CALLS = true;

    @Nonnull
    @Override
    public String getDisplayName() {
        return InspectionLocalize.inspectionSuspiciousCollectionsMethodCallsDisplayName().get();
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    @Nullable
    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel(
            JavaAnalysisBundle.message("report.suspicious.but.possibly.correct.method.calls"),
            this,
            "REPORT_CONVERTIBLE_METHOD_CALLS"
        );
    }

    @Override
    @Nonnull
    public PsiElementVisitor buildVisitorImpl(
        @Nonnull final ProblemsHolder holder,
        final boolean isOnTheFly,
        LocalInspectionToolSession session,
        Object state
    ) {
        final List<SuspiciousMethodCallUtil.PatternMethod> patternMethods = new ArrayList<>();
        return new JavaElementVisitor() {
            @Override
            public void visitMethodCallExpression(PsiMethodCallExpression methodCall) {
                final PsiExpression[] args = methodCall.getArgumentList().getExpressions();
                if (args.length < 1) {
                    return;
                }
                for (int idx = 0; idx < Math.min(2, args.length); idx++) {
                    String message =
                        getSuspiciousMethodCallMessage(methodCall, REPORT_CONVERTIBLE_METHOD_CALLS, patternMethods, args[idx], idx);
                    if (message != null) {
                        holder.registerProblem(methodCall.getArgumentList().getExpressions()[idx], message);
                    }
                }
            }

            @Override
            public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
                final PsiType functionalInterfaceType = expression.getFunctionalInterfaceType();
                final PsiClassType.ClassResolveResult functionalInterfaceResolveResult =
                    PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
                final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(functionalInterfaceType);
                if (interfaceMethod != null && interfaceMethod.getParameterList().getParametersCount() == 1) {
                    final PsiSubstitutor psiSubstitutor = LambdaUtil.getSubstitutor(interfaceMethod, functionalInterfaceResolveResult);
                    final MethodSignature signature = interfaceMethod.getSignature(psiSubstitutor);
                    String message = SuspiciousMethodCallUtil.getSuspiciousMethodCallMessage(
                        expression,
                        signature.getParameterTypes()[0],
                        REPORT_CONVERTIBLE_METHOD_CALLS,
                        patternMethods,
                        0
                    );
                    if (message != null) {
                        holder.registerProblem(ObjectUtil.notNull(expression.getReferenceNameElement(), expression), message);
                    }
                }
            }
        };
    }

    @Override
    @Nonnull
    public String getGroupDisplayName() {
        return InspectionLocalize.groupNamesProbableBugs().get();
    }

    @Override
    @Nonnull
    public String getShortName() {
        return "SuspiciousMethodCalls";
    }

    private static String getSuspiciousMethodCallMessage(
        PsiMethodCallExpression methodCall,
        boolean reportConvertibleMethodCalls,
        List<SuspiciousMethodCallUtil.PatternMethod> patternMethods,
        PsiExpression arg,
        int i
    ) {
        PsiType argType = arg.getType();
        boolean exactType = arg instanceof PsiNewExpression;
        final String plainMessage = SuspiciousMethodCallUtil
            .getSuspiciousMethodCallMessage(methodCall, arg, argType, exactType || reportConvertibleMethodCalls, patternMethods, i);
        if (plainMessage != null && !exactType) {
            String methodName = methodCall.getMethodExpression().getReferenceName();
            if (SuspiciousMethodCallUtil.isCollectionAcceptingMethod(methodName)) {
                // DFA works on raw types, so anyway we cannot narrow the argument type
                return plainMessage;
            }
            TypeConstraint constraint = TypeConstraint.fromDfType(CommonDataflow.getDfType(arg));
            PsiType type = constraint.getPsiType(methodCall.getProject());
            if (type != null && SuspiciousMethodCallUtil.getSuspiciousMethodCallMessage(
                methodCall,
                arg,
                type,
                reportConvertibleMethodCalls,
                patternMethods,
                i
            ) == null) {
                return null;
            }
        }

        return plainMessage;
    }
}
