/*
 * Copyright 2008-2011 Bas Leijdekkers
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
package com.intellij.java.impl.ig.migration;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;

public abstract class CollectionsFieldAccessReplaceableByMethodCallInspection extends BaseInspection {

  @Override
  @Nls
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.collectionsFieldAccessReplaceableByMethodCallDisplayName();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.collectionsFieldAccessReplaceableByMethodCallProblemDescriptor(infos[1]).get();
  }

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    PsiReferenceExpression expression = (PsiReferenceExpression) infos[0];
    return new CollectionsFieldAccessReplaceableByMethodCallFix(expression.getReferenceName());
  }

  private static class CollectionsFieldAccessReplaceableByMethodCallFix
      extends InspectionGadgetsFix {

    private final String replacementText;

    private CollectionsFieldAccessReplaceableByMethodCallFix(
        String referenceName) {
      replacementText = getCollectionsMethodCallText(referenceName);
    }

    @Nonnull
    public LocalizeValue getName() {
      return InspectionGadgetsLocalize.collectionsFieldAccessReplaceableByMethodCallQuickfix(replacementText);
    }

    @NonNls
    private static String getCollectionsMethodCallText(
        PsiReferenceExpression referenceExpression) {
      String referenceName = referenceExpression.getReferenceName();
      PsiElement parent = referenceExpression.getParent();
      if (!(parent instanceof PsiExpressionList)) {
        return getUntypedCollectionsMethodCallText(referenceName);
      }
      PsiType type = ExpectedTypeUtils.findExpectedType(
          referenceExpression, false);
      if (!(type instanceof PsiClassType)) {
        return getUntypedCollectionsMethodCallText(referenceName);
      }
      PsiClassType classType = (PsiClassType) type;
      PsiType[] parameterTypes = classType.getParameters();
      boolean useTypeParameter = false;
      String[] canonicalTexts = new String[parameterTypes.length];
      for (int i = 0, parameterTypesLength = parameterTypes.length;
           i < parameterTypesLength; i++) {
        PsiType parameterType = parameterTypes[i];
        if (parameterType instanceof PsiWildcardType) {
          PsiWildcardType wildcardType =
              (PsiWildcardType) parameterType;
          PsiType bound = wildcardType.getBound();
          if (bound != null) {
            if (!bound.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
              useTypeParameter = true;
            }
            canonicalTexts[i] = bound.getCanonicalText();
          } else {
            canonicalTexts[i] = CommonClassNames.JAVA_LANG_OBJECT;
          }
        } else {
          if (!parameterType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
            useTypeParameter = true;
          }
          canonicalTexts[i] = parameterType.getCanonicalText();
        }
      }
      if (useTypeParameter) {
        return "Collections.<" + StringUtil.join(canonicalTexts, ",") +
            '>' + getCollectionsMethodCallText(referenceName);
      } else {
        return getUntypedCollectionsMethodCallText(referenceName);
      }
    }

    @NonNls
    private static String getUntypedCollectionsMethodCallText(
        String referenceName) {
      return "Collections." + getCollectionsMethodCallText(referenceName);
    }

    @NonNls
    private static String getCollectionsMethodCallText(
        @NonNls String referenceName) {
      if ("EMPTY_LIST".equals(referenceName)) {
        return "emptyList()";
      } else if ("EMPTY_MAP".equals(referenceName)) {
        return "emptyMap()";
      } else if ("EMPTY_SET".equals(referenceName)) {
        return "emptySet()";
      } else {
        throw new AssertionError("unknown collections field name: " +
            referenceName);
      }
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor)
        throws IncorrectOperationException {
      PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiReferenceExpression)) {
        return;
      }
      PsiReferenceExpression referenceExpression =
          (PsiReferenceExpression) element;
      String newMethodCallText =
          getCollectionsMethodCallText(referenceExpression);
      replaceExpression(referenceExpression,
          "java.util." + newMethodCallText);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CollectionsFieldAccessReplaceableByMethodCallVisitor();
  }

  private static class CollectionsFieldAccessReplaceableByMethodCallVisitor
      extends BaseInspectionVisitor {

    @Override
    public void visitReferenceExpression(
        PsiReferenceExpression expression) {
      if (!PsiUtil.isLanguageLevel5OrHigher(expression)) {
        return;
      }
      super.visitReferenceExpression(expression);
      @NonNls String name = expression.getReferenceName();
      @NonNls String replacement;
      if ("EMPTY_LIST".equals(name)) {
        replacement = "emptyList()";
      } else if ("EMPTY_MAP".equals(name)) {
        replacement = "emptyMap()";
      } else if ("EMPTY_SET".equals(name)) {
        replacement = "emptySet()";
      } else {
        return;
      }
      PsiElement target = expression.resolve();
      if (!(target instanceof PsiField)) {
        return;
      }
      PsiField field = (PsiField) target;
      PsiClass containingClass = field.getContainingClass();
      if (containingClass == null) {
        return;
      }
      String qualifiedName = containingClass.getQualifiedName();
      if (!CommonClassNames.JAVA_UTIL_COLLECTIONS.equals(qualifiedName)) {
        return;
      }
      registerError(expression, expression, replacement);
    }
  }
}