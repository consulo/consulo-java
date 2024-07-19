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

import javax.swing.*;

@ExtensionImpl
public class CallToSimpleGetterInClassInspection extends BaseInspection {

  @SuppressWarnings("UnusedDeclaration")
  public boolean ignoreGetterCallsOnOtherObjects = false;

  @SuppressWarnings("UnusedDeclaration")
  public boolean onlyReportPrivateGetter = false;

  @Override
  @Nonnull
  public String getID() {
    return "CallToSimpleGetterFromWithinClass";
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.callToSimpleGetterInClassDisplayName().get();
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.callToSimpleGetterInClassProblemDescriptor().get();
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel optionsPanel = new MultipleCheckboxOptionsPanel(this);
    optionsPanel.addCheckbox(
      InspectionGadgetsLocalize.callToSimpleGetterInClassIgnoreOption().get(),
      "ignoreGetterCallsOnOtherObjects"
    );
    optionsPanel.addCheckbox(InspectionGadgetsLocalize.callToPrivateSimpleGetterInClassOption().get(), "onlyReportPrivateGetter");
    return optionsPanel;
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new InlineCallFix();
  }

  private static class InlineCallFix extends InspectionGadgetsFix {
    @Nonnull
    public String getName() {
      return InspectionGadgetsLocalize.callToSimpleGetterInClassInlineQuickfix().get();
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiElement methodIdentifier = descriptor.getPsiElement();
      final PsiReferenceExpression methodExpression = (PsiReferenceExpression)methodIdentifier.getParent();
      if (methodExpression == null) {
        return;
      }
      final PsiMethodCallExpression call = (PsiMethodCallExpression)methodExpression.getParent();
      if (call == null) {
        return;
      }
      final PsiMethod method = call.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiCodeBlock body = method.getBody();
      if (body == null) {
        return;
      }
      final PsiStatement[] statements = body.getStatements();
      final PsiReturnStatement returnStatement = (PsiReturnStatement)statements[0];
      final PsiExpression returnValue = returnStatement.getReturnValue();
      if (!(returnValue instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)returnValue;
      final PsiField field = (PsiField)referenceExpression.resolve();
      if (field == null) {
        return;
      }
      final String fieldName = field.getName();
      if (fieldName == null) {
        return;
      }
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier == null) {
        final JavaPsiFacade facade = JavaPsiFacade.getInstance(call.getProject());
        final PsiResolveHelper resolveHelper = facade.getResolveHelper();
        final PsiVariable variable = resolveHelper.resolveReferencedVariable(fieldName, call);
        if (variable == null) {
          return;
        }
        if (variable.equals(field)) {
          replaceExpression(call, fieldName);
        }
        else {
          replaceExpression(call, "this." + fieldName);
        }
      }
      else {
        replaceExpression(call, qualifier.getText() + '.' + fieldName);
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CallToSimpleGetterInClassVisitor();
  }

  private class CallToSimpleGetterInClassVisitor extends BaseInspectionVisitor {
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
        if (ignoreGetterCallsOnOtherObjects) {
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
      if (!PropertyUtil.isSimpleGetter(method)) {
        return;
      }
      if (onlyReportPrivateGetter && !method.hasModifierProperty(PsiModifier.PRIVATE)) {
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