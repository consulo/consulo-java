/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.bugs;

import javax.annotation.Nonnull;

import com.intellij.java.language.psi.*;
import org.jetbrains.annotations.NonNls;
import com.intellij.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import consulo.java.language.module.util.JavaClassNames;

public class CollectionAddedToSelfInspection extends BaseInspection {

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "collection.added.to.self.display.name");
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "collection.added.to.self.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CollectionAddedToSelfVisitor();
  }

  private static class CollectionAddedToSelfVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      @Nonnull PsiMethodCallExpression call) {
      super.visitMethodCallExpression(call);
      final PsiReferenceExpression methodExpression =
        call.getMethodExpression();
      @NonNls final String methodName =
        methodExpression.getReferenceName();
      if (!"put".equals(methodName) && !"set".equals(methodName) &&
          !"add".equals(methodName)) {
        return;
      }
      final PsiExpression qualifier =
        methodExpression.getQualifierExpression();
      if (qualifier == null) {
        return;
      }
      if (!(qualifier instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiElement referent = ((PsiReference)qualifier).resolve();
      if (!(referent instanceof PsiVariable)) {
        return;
      }
      final PsiExpressionList argumentList = call.getArgumentList();
      boolean hasMatchingArg = false;
      final PsiExpression[] args = argumentList.getExpressions();
      for (PsiExpression arg : args) {
        if (EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(qualifier, arg)) {
          hasMatchingArg = true;
        }
      }
      if (!hasMatchingArg) {
        return;
      }
      final PsiType qualifierType = qualifier.getType();
      if (!(qualifierType instanceof PsiClassType)) {
        return;
      }
      final PsiClassType classType = (PsiClassType)qualifierType;
      final PsiClass qualifierClass = classType.resolve();
      if (qualifierClass == null) {
        return;
      }
      if (!InheritanceUtil.isInheritor(qualifierClass,
                                       JavaClassNames.JAVA_UTIL_COLLECTION) &&
          !InheritanceUtil.isInheritor(qualifierClass,
                                       JavaClassNames.JAVA_UTIL_MAP)) {
        return;
      }
      registerError(qualifier);
    }
  }
}