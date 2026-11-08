// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inliner;

import com.intellij.java.language.psi.util.ExpressionUtil;
import com.intellij.java.language.codeInsight.Nullability;
import com.intellij.java.language.codeInsight.NullabilityAnnotationInfo;
import com.intellij.java.language.codeInsight.NullableNotNullManager;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.NullabilityProblemKind;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.CFGBuilder;
import consulo.util.lang.ref.Ref;
import com.intellij.java.language.psi.PsiArrayAccessExpression;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiConditionalExpression;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiInstanceOfExpression;
import com.intellij.java.language.psi.PsiLambdaExpression;
import com.intellij.java.language.psi.PsiLiteralExpression;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import com.intellij.java.language.psi.PsiMethodReferenceExpression;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiParenthesizedExpression;
import com.intellij.java.language.psi.PsiPolyadicExpression;
import com.intellij.java.language.psi.PsiReferenceExpression;
import com.intellij.java.language.psi.PsiReturnStatement;
import com.intellij.java.language.psi.PsiThisExpression;
import com.intellij.java.language.psi.PsiTypeCastExpression;
import com.intellij.java.language.psi.PsiTypes;
import com.intellij.java.language.psi.PsiUnaryExpression;
import consulo.language.psi.util.PsiTreeUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.analysis.impl.codeInspection.ControlFlowUtils;

import static consulo.util.lang.ObjectUtil.tryCast;

/**
 * Inlines simple stable methods defined in the same class
 */
public class SimpleMethodInliner implements CallInliner {
  @Override
  public boolean tryInlineCall(CFGBuilder builder, PsiMethodCallExpression call) {
    if (!call.getArgumentList().isEmpty()) return false;
    if (!ExpressionUtil.isEffectivelyUnqualified(call.getMethodExpression())) return false;
    PsiMethod method = call.resolveMethod();
    if (method == null || PsiUtil.canBeOverridden(method) || (call.isPhysical() && !method.isPhysical())) return false;
    PsiClass aClass = method.getContainingClass();
    if (aClass == null || !PsiTreeUtil.isAncestor(aClass, call, true)) return false;
    if (PsiTypes.voidType().equals(method.getReturnType())) return false;
    if (PsiTreeUtil.isAncestor(method, call, true)) return false;
    PsiReturnStatement statement = tryCast(ControlFlowUtils.getOnlyStatementInBlock(method.getBody()), PsiReturnStatement.class);
    if (statement == null) return false;
    PsiExpression returnValue = PsiUtil.skipParenthesizedExprDown(statement.getReturnValue());
    if (returnValue == null) return false;
    if (returnValue instanceof PsiLiteralExpression) return false;
    if (!isSimple(returnValue)) return false;
    NullabilityAnnotationInfo info = NullableNotNullManager.getInstance(method.getProject()).findEffectiveNullabilityInfo(method);
    boolean nonNull = info != null && info.getNullability() == Nullability.NOT_NULL && !info.isInferred();
    builder.pushExpression(returnValue, nonNull ? NullabilityProblemKind.assumeNotNull : NullabilityProblemKind.noProblem)
      .boxUnbox(returnValue, call.getType())
      .resultOf(call);
    return true;
  }

  private static boolean isSimple(PsiExpression value) {
    Ref<Boolean> hasFieldRefs = Ref.create(false);
    boolean allowed = PsiTreeUtil.processElements(value, e -> {
      if (!(e instanceof PsiExpression)) return true;
      if (e instanceof PsiInstanceOfExpression || e instanceof PsiParenthesizedExpression || e instanceof PsiLiteralExpression ||
          e instanceof PsiPolyadicExpression || e instanceof PsiUnaryExpression || e instanceof PsiConditionalExpression ||
          e instanceof PsiTypeCastExpression || e instanceof PsiArrayAccessExpression || e instanceof PsiLambdaExpression ||
          e instanceof PsiMethodReferenceExpression || e instanceof PsiThisExpression) {
        return true;
      }
      if (e instanceof PsiReferenceExpression) {
        PsiElement target = ((PsiReferenceExpression)e).resolve();
        if (target instanceof PsiField && !((PsiField)target).hasModifierProperty(PsiModifier.STATIC)) {
          hasFieldRefs.set(true);
        }
        return true;
      }
      return false;
    });
    return allowed && hasFieldRefs.get();
  }
}
