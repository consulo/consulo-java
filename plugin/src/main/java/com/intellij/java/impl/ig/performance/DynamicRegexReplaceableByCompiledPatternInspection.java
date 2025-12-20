/*
 * Copyright 2009-2012 Bas Leijdekkers
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

import com.intellij.java.impl.ig.psiutils.HighlightUtils;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.CodeInsightUtilCore;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNameIdentifierOwner;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

import java.util.Collection;
import java.util.HashSet;

@ExtensionImpl
public class DynamicRegexReplaceableByCompiledPatternInspection extends BaseInspection {

  @NonNls
  private static final Collection<String> regexMethodNames = new HashSet(4);

  static {
    regexMethodNames.add("matches");
    regexMethodNames.add("replaceFirst");
    regexMethodNames.add("replaceAll");
    regexMethodNames.add("split");
  }

  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.dynamicRegexReplaceableByCompiledPatternDisplayName();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.dynamicRegexReplaceableByCompiledPatternProblemDescriptor().get();
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new DynamicRegexReplaceableByCompiledPatternFix();
  }

  private static class DynamicRegexReplaceableByCompiledPatternFix extends InspectionGadgetsFix {

    @Nonnull
    public LocalizeValue getName() {
      return InspectionGadgetsLocalize.dynamicRegexReplaceableByCompiledPatternQuickfix();
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor)
        throws IncorrectOperationException {
      PsiElement element = descriptor.getPsiElement();
      PsiClass aClass = ClassUtils.getContainingStaticClass(element);
      if (aClass == null) {
        return;
      }
      PsiElement parent = element.getParent();
      if (!(parent instanceof PsiReferenceExpression)) {
        return;
      }
      PsiReferenceExpression methodExpression = (PsiReferenceExpression) parent;
      PsiElement grandParent = methodExpression.getParent();
      if (!(grandParent instanceof PsiMethodCallExpression)) {
        return;
      }
      PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) grandParent;
      PsiExpressionList list = methodCallExpression.getArgumentList();
      PsiExpression[] expressions = list.getExpressions();
      @NonNls StringBuilder fieldText =
          new StringBuilder("private static final java.util.regex.Pattern PATTERN = java.util.regex.Pattern.compile(");
      int expressionsLength = expressions.length;
      if (expressionsLength > 0) {
        fieldText.append(expressions[0].getText());
      }
      fieldText.append(");");
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      PsiField newField = factory.createFieldFromText(fieldText.toString(), element);
      PsiElement field = aClass.add(newField);

      @NonNls StringBuilder expressionText = new StringBuilder("PATTERN.");
      @NonNls String methodName = methodExpression.getReferenceName();
      PsiExpression qualifier = methodExpression.getQualifierExpression();
      @NonNls String qualifierText;
      if (qualifier == null) {
        qualifierText = "this";
      } else {
        qualifierText = qualifier.getText();
      }
      if ("split".equals(methodName)) {
        expressionText.append(methodName);
        expressionText.append('(');
        expressionText.append(qualifierText);
        for (int i = 1; i < expressionsLength; i++) {
          expressionText.append(',');
          expressionText.append(expressions[i].getText());
        }
        expressionText.append(')');
      } else {
        expressionText.append("matcher(");
        expressionText.append(qualifierText);
        expressionText.append(").");
        expressionText.append(methodName);
        expressionText.append('(');
        if (expressionsLength > 1) {
          expressionText.append(expressions[1].getText());
          for (int i = 2; i < expressionsLength; i++) {
            expressionText.append(',');
            expressionText.append(expressions[i].getText());
          }
        }
        expressionText.append(')');
      }

      PsiExpression newExpression = factory.createExpressionFromText(expressionText.toString(), element);
      PsiMethodCallExpression newMethodCallExpression = (PsiMethodCallExpression) methodCallExpression.replace(newExpression);
      newMethodCallExpression = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(newMethodCallExpression);
      PsiReferenceExpression reference = getReference(newMethodCallExpression);
      HighlightUtils.showRenameTemplate(aClass, (PsiNameIdentifierOwner) field, reference);
    }

    private static PsiReferenceExpression getReference(PsiMethodCallExpression newMethodCallExpression) {
      PsiReferenceExpression methodExpression = newMethodCallExpression.getMethodExpression();
      PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
      if (qualifierExpression instanceof PsiMethodCallExpression) {
        PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) qualifierExpression;
        return getReference(methodCallExpression);
      }
      if (!(qualifierExpression instanceof PsiReferenceExpression)) {
        return null;
      }
      return (PsiReferenceExpression) qualifierExpression;
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new DynamicRegexReplaceableByCompiledPatternVisitor();
  }

  private static class DynamicRegexReplaceableByCompiledPatternVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!isCallToRegexMethod(expression)) {
        return;
      }
      registerMethodCallError(expression);
    }


    private static boolean isCallToRegexMethod(PsiMethodCallExpression expression) {
      PsiReferenceExpression methodExpression = expression.getMethodExpression();
      String name = methodExpression.getReferenceName();
      if (!regexMethodNames.contains(name)) {
        return false;
      }
      PsiExpressionList argumentList = expression.getArgumentList();
      PsiExpression[] arguments = argumentList.getExpressions();
      for (PsiExpression argument : arguments) {
        if (!PsiUtil.isConstantExpression(argument)) {
          return false;
        }
      }
      PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return false;
      }
      PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return false;
      }
      String className = containingClass.getQualifiedName();
      return CommonClassNames.JAVA_LANG_STRING.equals(className);
    }
  }
}