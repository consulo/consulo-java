/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInspection.accessStaticViaInstance;

import com.intellij.java.analysis.codeInspection.BaseJavaBatchLocalInspectionTool;
import com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis.HighlightMessageUtil;
import com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.java.impl.codeInsight.daemon.impl.quickfix.RemoveUnusedVariableUtil;
import com.intellij.java.language.impl.codeInsight.daemon.JavaErrorBundle;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiPackage;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;

public abstract class AccessStaticViaInstanceBase extends BaseJavaBatchLocalInspectionTool {
  @NonNls public static final String ACCESS_STATIC_VIA_INSTANCE = "AccessStaticViaInstance";

  @Override
  @Nonnull
  public String getGroupDisplayName() {
    return "";
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionLocalize.accessStaticViaInstance().get();
  }

  @Override
  @Nonnull
  @NonNls
  public String getShortName() {
    return ACCESS_STATIC_VIA_INSTANCE;
  }

  @Override
  public String getAlternativeID() {
    return "static-access";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @Nonnull
  public PsiElementVisitor buildVisitorImpl(
    @Nonnull final ProblemsHolder holder,
    final boolean isOnTheFly,
    LocalInspectionToolSession session,
    Object state
  ) {
    return new JavaElementVisitor() {
      @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
        checkAccessStaticMemberViaInstanceReference(expression, holder, isOnTheFly);
      }
    };
  }

  @RequiredReadAction
  private void checkAccessStaticMemberViaInstanceReference(PsiReferenceExpression expr, ProblemsHolder holder, boolean onTheFly) {
    JavaResolveResult result = expr.advancedResolve(false);
    PsiElement resolved = result.getElement();

    if (!(resolved instanceof PsiMember)) return;
    PsiExpression qualifierExpression = expr.getQualifierExpression();
    if (qualifierExpression == null) return;

    if (qualifierExpression instanceof PsiReferenceExpression referenceExpression) {
      final PsiElement qualifierResolved = referenceExpression.resolve();
      if (qualifierResolved instanceof PsiClass || qualifierResolved instanceof PsiPackage) {
        return;
      }
    }
    if (!((PsiMember)resolved).hasModifierProperty(PsiModifier.STATIC)) return;

    String description = JavaErrorBundle.message("static.member.accessed.via.instance.reference",
                                                   JavaHighlightUtil.formatType(qualifierExpression.getType()),
                                                   HighlightMessageUtil.getSymbolName(resolved, result.getSubstitutor()));
    if (!onTheFly) {
      if (RemoveUnusedVariableUtil.checkSideEffects(qualifierExpression, null, new ArrayList<>())) {
        holder.registerProblem(expr, description);
        return;
      }
    }
    holder.registerProblem(expr, description, createAccessStaticViaInstanceFix(expr, onTheFly, result));
  }

  protected LocalQuickFix createAccessStaticViaInstanceFix(
    PsiReferenceExpression expr,
    boolean onTheFly,
    JavaResolveResult result
  ) {
    return null;
  }
}
