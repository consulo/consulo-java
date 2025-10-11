/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.performance;

import com.intellij.java.impl.ig.psiutils.StringUtils;
import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class InstantiatingObjectToGetClassObjectInspection extends BaseInspection {
  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.instantiatingObjectToGetClassObjectDisplayName();
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.instantiatingObjectToGetClassObjectProblemDescriptor().get();
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new InstantiatingObjectToGetClassObjectFix();
  }

  private static class InstantiatingObjectToGetClassObjectFix
    extends InspectionGadgetsFix {

    @Nonnull
    public LocalizeValue getName() {
      return InspectionGadgetsLocalize.instantiatingObjectToGetClassObjectReplaceQuickfix();
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiMethodCallExpression expression = (PsiMethodCallExpression)descriptor.getPsiElement();
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier == null) {
        return;
      }
      final PsiType type = qualifier.getType();
      if (type == null) {
        return;
      }
      replaceExpression(expression, getTypeText(type, new StringBuilder()) + ".class");
    }

    private static StringBuilder getTypeText(PsiType type, StringBuilder text) {
      if (type instanceof PsiArrayType) {
        text.append("[]");
        final PsiArrayType arrayType = (PsiArrayType)type;
        getTypeText(arrayType.getComponentType(), text);
      }
      else if (type instanceof PsiClassType) {
        final String canonicalText = type.getCanonicalText();
        final String typeText = StringUtils.stripAngleBrackets(canonicalText);
        text.insert(0, typeText);
      }
      else {
        text.insert(0, type.getCanonicalText());
      }
      return text;
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new InstantiatingObjectToGetClassObjectVisitor();
  }

  private static class InstantiatingObjectToGetClassObjectVisitor extends BaseInspectionVisitor {
    @Override
    public void visitMethodCallExpression(@Nonnull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      @NonNls final String methodName = methodExpression.getReferenceName();
      if (!"getClass".equals(methodName)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] args = argumentList.getExpressions();
      if (args.length != 0) {
        return;
      }
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (!(qualifier instanceof PsiNewExpression)) {
        return;
      }
      final PsiNewExpression newExpression = (PsiNewExpression)qualifier;
      if (newExpression.getAnonymousClass() != null) {
        return;
      }
      registerError(expression);
    }
  }
}