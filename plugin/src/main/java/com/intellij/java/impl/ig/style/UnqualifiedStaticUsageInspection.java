/*
 * Copyright 2003-2008 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.style;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.deadCodeNotWorking.impl.MultipleCheckboxOptionsPanel;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

import javax.swing.*;

public abstract class UnqualifiedStaticUsageInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreStaticFieldAccesses = false;
  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreStaticMethodCalls = false;
  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreStaticAccessFromStaticContext = false;

  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.unqualifiedStaticUsageDisplayName();
  }

  @Nonnull
  public String buildErrorString(Object... infos) {
    return infos[0] instanceof PsiMethodCallExpression
      ? InspectionGadgetsLocalize.unqualifiedStaticUsageProblemDescriptor().get()
      : InspectionGadgetsLocalize.unqualifiedStaticUsageProblemDescriptor1().get();
  }

  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel optionsPanel =
      new MultipleCheckboxOptionsPanel(this);
    optionsPanel.addCheckbox(
      InspectionGadgetsLocalize.unqualifiedStaticUsageIgnoreFieldOption().get(),
      "m_ignoreStaticFieldAccesses"
    );
    optionsPanel.addCheckbox(
      InspectionGadgetsLocalize.unqualifiedStaticUsageIgnoreMethodOption().get(),
      "m_ignoreStaticMethodCalls"
    );
    optionsPanel.addCheckbox(
      InspectionGadgetsLocalize.unqualifiedStaticUsageOnlyReportStaticUsagesOption().get(),
      "m_ignoreStaticAccessFromStaticContext"
    );
    return optionsPanel;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new UnqualifiedStaticCallVisitor();
  }

  public InspectionGadgetsFix buildFix(Object... infos) {
    if (infos[0] instanceof PsiMethodCallExpression) {
      return new UnqualifiedStaticAccessFix(false);
    }
    else {
      return new UnqualifiedStaticAccessFix(true);
    }
  }

  private static class UnqualifiedStaticAccessFix
    extends InspectionGadgetsFix {

    private final boolean m_fixField;

    UnqualifiedStaticAccessFix(boolean fixField) {
      m_fixField = fixField;
    }

    @Nonnull
    public LocalizeValue getName() {
      return m_fixField
        ? InspectionGadgetsLocalize.unqualifiedStaticUsageQualifyFieldQuickfix()
        : InspectionGadgetsLocalize.unqualifiedStaticUsageQualifyMethodQuickfix();
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiReferenceExpression expression = (PsiReferenceExpression)descriptor.getPsiElement();
      final PsiMember member = (PsiMember)expression.resolve();
      assert member != null;
      final PsiClass containingClass = member.getContainingClass();
      assert containingClass != null;
      final String className = containingClass.getName();
      final String text = expression.getText();
      replaceExpression(expression, className + '.' + text);
    }
  }

  private class UnqualifiedStaticCallVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      @Nonnull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (m_ignoreStaticMethodCalls) {
        return;
      }
      final PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      if (!isUnqualifiedStaticAccess(methodExpression)) {
        return;
      }
      registerError(methodExpression, expression);
    }

    @Override
    public void visitReferenceExpression(
      @Nonnull PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      if (m_ignoreStaticFieldAccesses) {
        return;
      }
      final PsiElement element = expression.resolve();
      if (!(element instanceof PsiField)) {
        return;
      }
      final PsiField field = (PsiField)element;
      if (field.hasModifierProperty(PsiModifier.FINAL) &&
          PsiUtil.isOnAssignmentLeftHand(expression)) {
        return;
      }
      if (!isUnqualifiedStaticAccess(expression)) {
        return;
      }
      registerError(expression, expression);
    }

    private boolean isUnqualifiedStaticAccess(
      PsiReferenceExpression expression) {
      if (m_ignoreStaticAccessFromStaticContext) {
        final PsiMember member =
          PsiTreeUtil.getParentOfType(expression,
                                      PsiMember.class);
        if (member != null &&
            member.hasModifierProperty(PsiModifier.STATIC)) {
          return false;
        }
      }
      final PsiExpression qualifierExpression =
        expression.getQualifierExpression();
      if (qualifierExpression != null) {
        return false;
      }
      final JavaResolveResult resolveResult =
        expression.advancedResolve(false);
      final PsiElement currentFileResolveScope =
        resolveResult.getCurrentFileResolveScope();
      if (currentFileResolveScope instanceof PsiImportStaticStatement) {
        return false;
      }
      final PsiElement element = resolveResult.getElement();
      if (!(element instanceof PsiField) &&
          !(element instanceof PsiMethod)) {
        return false;
      }
      final PsiMember member = (PsiMember)element;
      if (member instanceof PsiEnumConstant &&
          expression.getParent() instanceof PsiSwitchLabelStatement) {
        return false;
      }
      return member.hasModifierProperty(PsiModifier.STATIC);
    }
  }
}
