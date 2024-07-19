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
package com.intellij.java.impl.ig.performance;

import com.intellij.java.indexing.search.searches.OverridingMethodsSearch;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PropertyUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.query.Query;
import consulo.deadCodeNotWorking.impl.MultipleCheckboxOptionsPanel;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

@ExtensionImpl
public class CallToSimpleSetterInClassInspection extends BaseInspection {

  @SuppressWarnings("UnusedDeclaration")
  public boolean ignoreSetterCallsOnOtherObjects = false;

  @SuppressWarnings("UnusedDeclaration")
  public boolean onlyReportPrivateSetter = false;

  @Nonnull
  public String getID() {
    return "CallToSimpleSetterFromWithinClass";
  }

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.callToSimpleSetterInClassDisplayName().get();
  }

  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.callToSimpleSetterInClassProblemDescriptor().get();
  }

  @Nullable
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel optionsPanel = new MultipleCheckboxOptionsPanel(this);
    optionsPanel.addCheckbox(
      InspectionGadgetsLocalize.callToSimpleSetterInClassIgnoreOption().get(),
      "ignoreSetterCallsOnOtherObjects"
    );
    optionsPanel.addCheckbox(
      InspectionGadgetsLocalize.callToPrivateSetterInClassOption().get(),
      "onlyReportPrivateSetter"
    );
    return optionsPanel;
  }

  public InspectionGadgetsFix buildFix(Object... infos) {
    return new InlineCallFix();
  }

  private static class InlineCallFix extends InspectionGadgetsFix {

    @Nonnull
    public String getName() {
      return InspectionGadgetsLocalize.callToSimpleSetterInClassInlineQuickfix().get();
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement methodIdentifier = descriptor.getPsiElement();
      final PsiReferenceExpression methodExpression = (PsiReferenceExpression)methodIdentifier.getParent();
      if (methodExpression == null) {
        return;
      }
      final PsiMethodCallExpression call = (PsiMethodCallExpression)methodExpression.getParent();
      if (call == null) {
        return;
      }
      final PsiExpressionList argumentList = call.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      final PsiExpression argument = arguments[0];
      final PsiMethod method = call.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiCodeBlock body = method.getBody();
      if (body == null) {
        return;
      }
      final PsiStatement[] statements = body.getStatements();
      final PsiExpressionStatement assignmentStatement = (PsiExpressionStatement)statements[0];
      final PsiAssignmentExpression assignment = (PsiAssignmentExpression)assignmentStatement.getExpression();
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      final PsiReferenceExpression lhs = (PsiReferenceExpression)assignment.getLExpression();
      final PsiField field = (PsiField)lhs.resolve();
      if (field == null) {
        return;
      }
      final String fieldName = field.getName();
      if (qualifier == null) {
        final JavaPsiFacade manager = JavaPsiFacade.getInstance(call.getProject());
        final PsiResolveHelper resolveHelper = manager.getResolveHelper();
        final PsiVariable variable = resolveHelper.resolveReferencedVariable(fieldName, call);
        if (variable == null) {
          return;
        }
        @NonNls final String newExpression;
        if (variable.equals(field)) {
          newExpression = fieldName + " = " + argument.getText();
        }
        else {
          newExpression = "this." + fieldName + " = " + argument.getText();
        }
        replaceExpression(call, newExpression);
      }
      else {
        final String newExpression = qualifier.getText() + '.' + fieldName + " = " + argument.getText();
        replaceExpression(call, newExpression);
      }
    }
  }

  public BaseInspectionVisitor buildVisitor() {
    return new CallToSimpleSetterInClassVisitor();
  }

  private class CallToSimpleSetterInClassVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@Nonnull PsiMethodCallExpression call) {
      super.visitMethodCallExpression(call);
      final PsiClass containingClass = ClassUtils.getContainingClass(call);
      if (containingClass == null) {
        return;
      }
      final PsiMethod method = call.resolveMethod();
      if (method == null) {
        return;
      }
      if (!containingClass.equals(method.getContainingClass())) {
        return;
      }
      final PsiReferenceExpression methodExpression = call.getMethodExpression();
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier != null && !(qualifier instanceof PsiThisExpression)) {
        if (ignoreSetterCallsOnOtherObjects) {
          return;
        }
        final PsiType type = qualifier.getType();
        if (!(type instanceof PsiClassType)) {
          return;
        }
        final PsiClassType classType = (PsiClassType)type;
        final PsiClass qualifierClass = classType.resolve();
        if (!containingClass.equals(qualifierClass)) {
          return;
        }
      }
      if (!PropertyUtil.isSimpleSetter(method)) {
        return;
      }
      if (onlyReportPrivateSetter && !method.hasModifierProperty(PsiModifier.PRIVATE)) {
        return;
      }
      final Query<PsiMethod> query = OverridingMethodsSearch.search(method, true);
      final PsiMethod overridingMethod = query.findFirst();
      if (overridingMethod != null) {
        return;
      }
      registerMethodCallError(call);
    }
  }
}