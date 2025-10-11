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
package com.intellij.java.impl.ig.style;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

import javax.swing.*;

@ExtensionImpl
public class ImplicitCallToSuperInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean m_ignoreForObjectSubclasses = false;

  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.implicitCallToSuperDisplayName();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.implicitCallToSuperProblemDescriptor().get();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new AddExplicitSuperCall();
  }

  @Override
  public JComponent createOptionsPanel() {
    LocalizeValue message = InspectionGadgetsLocalize.implicitCallToSuperIgnoreOption();
    return new SingleCheckboxOptionsPanel(message.get(), this, "m_ignoreForObjectSubclasses");
  }

  private static class AddExplicitSuperCall extends InspectionGadgetsFix {

    @Nonnull
    public LocalizeValue getName() {
      return InspectionGadgetsLocalize.implicitCallToSuperMakeExplicitQuickfix();
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement methodName = descriptor.getPsiElement();
      final PsiMethod method = (PsiMethod)methodName.getParent();
      if (method == null) {
        return;
      }
      final PsiCodeBlock body = method.getBody();
      final PsiElementFactory factory =
        JavaPsiFacade.getElementFactory(project);
      final PsiStatement newStatement =
        factory.createStatementFromText("super();", null);
      final CodeStyleManager styleManager =
        CodeStyleManager.getInstance(project);
      if (body == null) {
        return;
      }
      final PsiJavaToken brace = body.getLBrace();
      body.addAfter(newStatement, brace);
      styleManager.reformat(body);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ImplicitCallToSuperVisitor();
  }

  private class ImplicitCallToSuperVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@Nonnull PsiMethod method) {
      super.visitMethod(method);
      if (!method.isConstructor()) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      if (containingClass.isEnum()) {
        return;
      }
      if (m_ignoreForObjectSubclasses) {
        final PsiClass superClass = containingClass.getSuperClass();
        if (superClass != null) {
          final String superClassName = superClass.getQualifiedName();
          if (CommonClassNames.JAVA_LANG_OBJECT.equals(superClassName)) {
            return;
          }
        }
      }
      final PsiCodeBlock body = method.getBody();
      if (body == null) {
        return;
      }
      final PsiStatement[] statements = body.getStatements();
      if (statements.length == 0) {
        registerMethodError(method);
        return;
      }
      final PsiStatement firstStatement = statements[0];
      if (isConstructorCall(firstStatement)) {
        return;
      }
      registerMethodError(method);
    }

    private boolean isConstructorCall(PsiStatement statement) {
      if (!(statement instanceof PsiExpressionStatement)) {
        return false;
      }
      final PsiExpressionStatement expressionStatement =
        (PsiExpressionStatement)statement;
      final PsiExpression expression =
        expressionStatement.getExpression();
      return ExpressionUtils.isConstructorInvocation(expression);
    }
  }
}
