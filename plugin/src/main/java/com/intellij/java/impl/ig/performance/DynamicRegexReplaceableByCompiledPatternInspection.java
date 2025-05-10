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
import consulo.ide.impl.idea.codeInsight.CodeInsightUtilBase;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNameIdentifierOwner;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.Nls;
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
  @Nls
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.dynamicRegexReplaceableByCompiledPatternDisplayName().get();
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
    public String getName() {
      return InspectionGadgetsLocalize.dynamicRegexReplaceableByCompiledPatternQuickfix().get();
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor)
        throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      final PsiClass aClass = ClassUtils.getContainingStaticClass(element);
      if (aClass == null) {
        return;
      }
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiReferenceExpression methodExpression = (PsiReferenceExpression) parent;
      final PsiElement grandParent = methodExpression.getParent();
      if (!(grandParent instanceof PsiMethodCallExpression)) {
        return;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) grandParent;
      final PsiExpressionList list = methodCallExpression.getArgumentList();
      final PsiExpression[] expressions = list.getExpressions();
      @NonNls final StringBuilder fieldText =
          new StringBuilder("private static final java.util.regex.Pattern PATTERN = java.util.regex.Pattern.compile(");
      final int expressionsLength = expressions.length;
      if (expressionsLength > 0) {
        fieldText.append(expressions[0].getText());
      }
      fieldText.append(");");
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      final PsiField newField = factory.createFieldFromText(fieldText.toString(), element);
      final PsiElement field = aClass.add(newField);

      @NonNls final StringBuilder expressionText = new StringBuilder("PATTERN.");
      @NonNls final String methodName = methodExpression.getReferenceName();
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      @NonNls final String qualifierText;
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

      final PsiExpression newExpression = factory.createExpressionFromText(expressionText.toString(), element);
      PsiMethodCallExpression newMethodCallExpression = (PsiMethodCallExpression) methodCallExpression.replace(newExpression);
      newMethodCallExpression = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(newMethodCallExpression);
      final PsiReferenceExpression reference = getReference(newMethodCallExpression);
      HighlightUtils.showRenameTemplate(aClass, (PsiNameIdentifierOwner) field, reference);
    }

    private static PsiReferenceExpression getReference(PsiMethodCallExpression newMethodCallExpression) {
      final PsiReferenceExpression methodExpression = newMethodCallExpression.getMethodExpression();
      final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
      if (qualifierExpression instanceof PsiMethodCallExpression) {
        final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) qualifierExpression;
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
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final String name = methodExpression.getReferenceName();
      if (!regexMethodNames.contains(name)) {
        return false;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      for (PsiExpression argument : arguments) {
        if (!PsiUtil.isConstantExpression(argument)) {
          return false;
        }
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return false;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return false;
      }
      final String className = containingClass.getQualifiedName();
      return CommonClassNames.JAVA_LANG_STRING.equals(className);
    }
  }
}