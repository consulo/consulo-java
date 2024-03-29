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
package com.intellij.java.impl.ig.encapsulation;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import com.intellij.java.language.psi.*;
import consulo.project.Project;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TypeUtils;
import consulo.java.language.module.util.JavaClassNames;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;

@ExtensionImpl
public class ReturnOfDateFieldInspection extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public boolean ignorePrivateMethods = false;

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("return.date.calendar.field.display.name");
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    final String type = (String)infos[0];
    return InspectionGadgetsBundle.message("return.date.calendar.field.problem.descriptor", type);
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("return.of.null.ignore.private.option"),
                                          this, "ignorePrivateMethods");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new ReturnOfDateFieldFix((String)infos[0]);
  }

  private static class ReturnOfDateFieldFix extends InspectionGadgetsFix {

    private final String myType;

    public ReturnOfDateFieldFix(String type) {
      myType = type;
    }

    @Nonnull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("return.date.calendar.field.quickfix", myType);
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)element;
      final String type =
        TypeUtils.expressionHasTypeOrSubtype(referenceExpression, JavaClassNames.JAVA_UTIL_DATE, JavaClassNames.JAVA_UTIL_CALENDAR);
      if (type == null) {
        return;
      }
      replaceExpression(referenceExpression, '(' + type + ')' + referenceExpression.getText() + ".clone()");
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ReturnOfDateFieldVisitor();
  }

  private class ReturnOfDateFieldVisitor extends BaseInspectionVisitor {

    @Override
    public void visitReturnStatement(@Nonnull PsiReturnStatement statement) {
      super.visitReturnStatement(statement);
      final PsiExpression returnValue = statement.getReturnValue();
      if (!(returnValue instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiMethod method = PsiTreeUtil.getParentOfType(statement, PsiMethod.class, true, PsiClass.class);
      if (method == null || (ignorePrivateMethods && method.hasModifierProperty(PsiModifier.PRIVATE))) {
        return;
      }
      final PsiReferenceExpression fieldReference = (PsiReferenceExpression)returnValue;
      final PsiElement element = fieldReference.resolve();
      if (!(element instanceof PsiField)) {
        return;
      }
      final String type = TypeUtils.expressionHasTypeOrSubtype(
        returnValue, JavaClassNames.JAVA_UTIL_DATE, JavaClassNames.JAVA_UTIL_CALENDAR);
      if (type == null) {
        return;
      }
      registerError(returnValue, type);
    }
  }
}