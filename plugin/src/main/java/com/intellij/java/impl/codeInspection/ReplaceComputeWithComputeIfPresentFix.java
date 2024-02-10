// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.codeInspection;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpressionUtils;
import consulo.java.analysis.impl.codeInsight.JavaInspectionsBundle;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.intention.HighPriorityAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import org.jetbrains.annotations.Contract;

import jakarta.annotation.Nonnull;

import static consulo.util.lang.ObjectUtil.tryCast;

public class ReplaceComputeWithComputeIfPresentFix implements LocalQuickFix, HighPriorityAction {
  private static final CallMatcher MAP_COMPUTE = CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_MAP, "compute").
      parameterTypes("K", CommonClassNames.JAVA_UTIL_FUNCTION_BI_FUNCTION);

  @Override
  @Nonnull
  public String getFamilyName() {
    return JavaInspectionsBundle.message("inspection.data.flow.use.computeifpresent.quickfix");
  }

  @Override
  public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
    PsiLambdaExpression lambda = PsiTreeUtil.getParentOfType(descriptor.getStartElement(), PsiLambdaExpression.class);
    if (lambda == null) {
      return;
    }
    PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(lambda, PsiMethodCallExpression.class);
    if (call == null || !"compute".equals(call.getMethodExpression().getReferenceName())) {
      return;
    }
    ExpressionUtils.bindCallTo(call, "computeIfPresent");
  }

  @Contract("null -> null")
  public static ReplaceComputeWithComputeIfPresentFix makeFix(PsiElement reference) {
    if (!(reference instanceof PsiReferenceExpression)) {
      return null;
    }
    PsiParameter parameter = tryCast(((PsiReferenceExpression) reference).resolve(), PsiParameter.class);
    if (parameter == null) {
      return null;
    }
    PsiParameterList parameterList = tryCast(parameter.getParent(), PsiParameterList.class);
    if (parameterList == null || parameterList.getParametersCount() != 2 || parameterList.getParameterIndex(parameter) != 1) {
      return null;
    }
    PsiLambdaExpression lambda = tryCast(parameterList.getParent(), PsiLambdaExpression.class);
    if (lambda == null) {
      return null;
    }
    PsiExpressionList arguments = tryCast(lambda.getParent(), PsiExpressionList.class);
    if (arguments == null || arguments.getExpressionCount() != 2 || arguments.getExpressions()[1] != lambda) {
      return null;
    }
    PsiMethodCallExpression call = tryCast(arguments.getParent(), PsiMethodCallExpression.class);
    if (!MAP_COMPUTE.test(call)) {
      return null;
    }
    return new ReplaceComputeWithComputeIfPresentFix();
  }
}
