/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.internationalization;

import com.intellij.java.analysis.impl.codeInsight.intention.AddAnnotationFix;
import com.intellij.java.impl.ig.DelegatingFix;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.impl.refactoring.util.RefactoringChangeUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.ig.psiutils.TestUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.MultipleCheckboxOptionsPanel;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;

@ExtensionImpl
public class StringConcatenationInspection extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public boolean ignoreAsserts = false;

  @SuppressWarnings({"PublicField"})
  public boolean ignoreSystemOuts = false;

  @SuppressWarnings({"PublicField"})
  public boolean ignoreSystemErrs = false;

  @SuppressWarnings({"PublicField"})
  public boolean ignoreThrowableArguments = false;

  @SuppressWarnings({"PublicField"})
  public boolean ignoreConstantInitializers = false;

  @SuppressWarnings({"PublicField"})
  public boolean ignoreInTestCode = false;

  @SuppressWarnings("PublicField")
  public boolean ignoreInToString = false;

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.stringConcatenationDisplayName().get();
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.stringConcatenationProblemDescriptor().get();
  }

  @Override
  @Nonnull
  protected InspectionGadgetsFix[] buildFixes(Object... infos) {
    final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)infos[0];
    final Collection<InspectionGadgetsFix> result = new ArrayList();
    final PsiElement parent = polyadicExpression.getParent();
    if (parent instanceof PsiVariable) {
      final PsiVariable variable = (PsiVariable)parent;
      final InspectionGadgetsFix fix = new DelegatingFix(new AddAnnotationFix(AnnotationUtil.NON_NLS, variable));
      result.add(fix);
    }
    else if (parent instanceof PsiAssignmentExpression) {
      final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)parent;
      final PsiExpression lhs = assignmentExpression.getLExpression();
      if (lhs instanceof PsiReferenceExpression) {
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)lhs;
        final PsiElement target = referenceExpression.resolve();
        if (target instanceof PsiModifierListOwner) {
          final PsiModifierListOwner modifierListOwner = (PsiModifierListOwner)target;
          final InspectionGadgetsFix fix = new DelegatingFix(new AddAnnotationFix(AnnotationUtil.NON_NLS, modifierListOwner));
          result.add(fix);
        }
      }
    }
    final PsiExpression[] operands = polyadicExpression.getOperands();
    for (PsiExpression operand : operands) {
      final PsiModifierListOwner element1 = getAnnotatableElement(operand);
      if (element1 != null) {
        final InspectionGadgetsFix fix = new DelegatingFix(new AddAnnotationFix(AnnotationUtil.NON_NLS, element1));
        result.add(fix);
      }
    }
    final PsiElement expressionParent = PsiTreeUtil.getParentOfType(polyadicExpression, PsiReturnStatement.class, PsiExpressionList.class);
    if (!(expressionParent instanceof PsiExpressionList) && expressionParent != null) {
      final PsiMethod method = PsiTreeUtil.getParentOfType(expressionParent, PsiMethod.class);
      if (method != null) {
        final InspectionGadgetsFix fix = new DelegatingFix(new AddAnnotationFix(AnnotationUtil.NON_NLS, method));
        result.add(fix);
      }
    }
    return result.toArray(new InspectionGadgetsFix[result.size()]);
  }

  @Nullable
  public static PsiModifierListOwner getAnnotatableElement(PsiExpression expression) {
    if (!(expression instanceof PsiReferenceExpression)) {
      return null;
    }
    final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)expression;
    final PsiElement element = referenceExpression.resolve();
    if (!(element instanceof PsiModifierListOwner)) {
      return null;
    }
    return (PsiModifierListOwner)element;
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel optionsPanel = new MultipleCheckboxOptionsPanel(this);
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("string.concatenation.ignore.assert.option"), "ignoreAsserts");
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("string.concatenation.ignore.system.out.option"), "ignoreSystemOuts");
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("string.concatenation.ignore.system.err.option"), "ignoreSystemErrs");
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("string.concatenation.ignore.exceptions.option"), "ignoreThrowableArguments");
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("string.concatenation.ignore.constant.initializers.option"),
                             "ignoreConstantInitializers");
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("ignore.in.test.code"), "ignoreInTestCode");
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("ignore.in.tostring"), "ignoreInToString");
    return optionsPanel;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StringConcatenationVisitor();
  }

  private class StringConcatenationVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPolyadicExpression(@Nonnull PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      final IElementType tokenType = expression.getOperationTokenType();
      if (!JavaTokenType.PLUS.equals(tokenType)) {
        return;
      }
      final PsiType type = expression.getType();
      if (!TypeUtils.isJavaLangString(type)) {
        return;
      }
      final PsiExpression[] operands = expression.getOperands();
      for (PsiExpression operand : operands) {
        if (NonNlsUtils.isNonNlsAnnotated(operand)) {
          return;
        }
      }
      if (AnnotationUtil.isInsideAnnotation(expression)) {
        return;
      }
      if (ignoreInTestCode && TestUtils.isInTestCode(expression)) {
        return;
      }
      if (ignoreAsserts) {
        final PsiAssertStatement assertStatement =
          PsiTreeUtil.getParentOfType(expression, PsiAssertStatement.class, true, PsiCodeBlock.class, PsiClass.class);
        if (assertStatement != null) {
          return;
        }
      }
      if (ignoreSystemErrs || ignoreSystemOuts) {
        final PsiMethodCallExpression methodCallExpression =
          PsiTreeUtil.getParentOfType(expression, PsiMethodCallExpression.class, true, PsiCodeBlock.class, PsiClass.class);
        if (methodCallExpression != null) {
          final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
          @NonNls
          final String canonicalText = methodExpression.getCanonicalText();
          if (ignoreSystemOuts && "System.out.println".equals(canonicalText) || "System.out.print".equals(canonicalText)) {
            return;
          }
          if (ignoreSystemErrs && "System.err.println".equals(canonicalText) || "System.err.print".equals(canonicalText)) {
            return;
          }
        }
      }
      if (ignoreThrowableArguments) {
        final PsiNewExpression newExpression =
          PsiTreeUtil.getParentOfType(expression, PsiNewExpression.class, true, PsiCodeBlock.class, PsiClass.class);
        if (newExpression != null) {
          final PsiType newExpressionType = newExpression.getType();
          if (InheritanceUtil.isInheritor(newExpressionType, "java.lang.Throwable")) {
            return;
          }
        } else {
          final PsiMethodCallExpression methodCallExpression =
            PsiTreeUtil.getParentOfType(expression, PsiMethodCallExpression.class, true, PsiCodeBlock.class, PsiClass.class);
          if (RefactoringChangeUtil.isSuperOrThisMethodCall(methodCallExpression)) {
            return;
          }
        }
      }
      if (ignoreConstantInitializers) {
        PsiElement parent = expression.getParent();
        while (parent instanceof PsiBinaryExpression) {
          parent = parent.getParent();
        }
        if (parent instanceof PsiField) {
          final PsiField field = (PsiField)parent;
          if (field.hasModifierProperty(PsiModifier.STATIC) && field.hasModifierProperty(PsiModifier.FINAL)) {
            return;
          }
          final PsiClass containingClass = field.getContainingClass();
          if (containingClass != null && containingClass.isInterface()) {
            return;
          }
        }
      }
      if (ignoreInToString) {
        final PsiMethod method = PsiTreeUtil.getParentOfType(expression, PsiMethod.class, true, PsiClass.class);
        if (MethodUtils.isToString(method)) {
          return;
        }
      }
      if (NonNlsUtils.isNonNlsAnnotatedUse(expression)) {
        return;
      }
      for (int i = 1; i < operands.length; i++) {
        final PsiExpression operand = operands[i];
        if (!ExpressionUtils.isStringConcatenationOperand(operand)) {
          continue;
        }
        final PsiJavaToken token = expression.getTokenBeforeOperand(operand);
        if (token == null) {
          continue;
        }
        registerError(token, expression);
      }
    }
  }
}
