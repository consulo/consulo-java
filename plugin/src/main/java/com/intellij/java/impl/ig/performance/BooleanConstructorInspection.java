/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class BooleanConstructorInspection extends BaseInspection {

  @Override
  @Nonnull
  public String getID() {
    return "BooleanConstructorCall";
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.booleanConstructorDisplayName().get();
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.booleanConstructorProblemDescriptor().get();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BooleanConstructorVisitor();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new BooleanConstructorFix();
  }

  private static class BooleanConstructorFix extends InspectionGadgetsFix {

    private static final String TRUE = '\"' + PsiKeyword.TRUE + '\"';
    private static final String FALSE = '\"' + PsiKeyword.FALSE + '\"';

    @Override
    @Nonnull
    public String getName() {
      return InspectionGadgetsLocalize.booleanConstructorSimplifyQuickfix().get();
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiNewExpression expression = (PsiNewExpression)descriptor.getPsiElement();
      final PsiExpressionList argumentList = expression.getArgumentList();
      if (argumentList == null) {
        return;
      }
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return;
      }
      final PsiExpression argument = arguments[0];
      final String text = argument.getText();
      final LanguageLevel languageLevel = PsiUtil.getLanguageLevel(expression);
      @NonNls final String newExpression;
      if (PsiKeyword.TRUE.equals(text) || TRUE.equalsIgnoreCase(text)) {
        newExpression = "java.lang.Boolean.TRUE";
      }
      else if (PsiKeyword.FALSE.equals(text) || FALSE.equalsIgnoreCase(text)) {
        newExpression = "java.lang.Boolean.FALSE";
      }
      else if (languageLevel.equals(LanguageLevel.JDK_1_3)) {
        newExpression = buildText(argument, false);
      }
      else {
        final PsiClass booleanClass = ClassUtils.findClass(CommonClassNames.JAVA_LANG_BOOLEAN, argument);
        boolean methodFound = false;
        if (booleanClass != null) {
          final PsiMethod[] methods = booleanClass.findMethodsByName("valueOf", false);
          for (PsiMethod method : methods) {
            final PsiParameterList parameterList = method.getParameterList();
            final PsiParameter[] parameters = parameterList.getParameters();
            if (parameters.length == 0) {
              continue;
            }
            final PsiParameter parameter = parameters[0];
            final PsiType type = parameter.getType();
            if (PsiType.BOOLEAN.equals(type)) {
              methodFound = true;
              break;
            }
          }
        }
        newExpression = buildText(argument, methodFound);
      }
      replaceExpression(expression, newExpression);
    }

    @NonNls
    private static String buildText(PsiExpression argument, boolean useValueOf) {
      final String text = argument.getText();
      final PsiType argumentType = argument.getType();
      if (!useValueOf && PsiType.BOOLEAN.equals(argumentType)) {
        if (ParenthesesUtils.getPrecedence(argument) > ParenthesesUtils.CONDITIONAL_PRECEDENCE) {
          return text + "?java.lang.fBoolean.TRUE:java.lang.Boolean.FALSE";
        }
        else {
          return '(' + text + ")?java.lang.Boolean.TRUE:java.lang.Boolean.FALSE";
        }
      }
      else {
        return "java.lang.Boolean.valueOf(" + text + ')';
      }
    }
  }

  private static class BooleanConstructorVisitor extends BaseInspectionVisitor {

    @Override
    public void visitNewExpression(@Nonnull PsiNewExpression expression) {
      super.visitNewExpression(expression);
      final PsiType type = expression.getType();
      if (type == null || !type.equalsToText(CommonClassNames.JAVA_LANG_BOOLEAN)) {
        return;
      }
      final PsiClass aClass = ClassUtils.getContainingClass(expression);
      if (aClass != null) {
        final String qualifiedName = aClass.getQualifiedName();
        if (CommonClassNames.JAVA_LANG_BOOLEAN.equals(qualifiedName)) {
          return;
        }
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      if (argumentList == null) {
        return;
      }
      final PsiExpression[] expressions = argumentList.getExpressions();
      if (expressions.length != 1) {
        return;
      }
      registerError(expression);
    }
  }
}