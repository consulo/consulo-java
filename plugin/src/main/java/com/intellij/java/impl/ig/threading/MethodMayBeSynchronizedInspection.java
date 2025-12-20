/*
 * Copyright 2006-2007 Bas Leijdekkers
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
package com.intellij.java.impl.ig.threading;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

@ExtensionImpl
public class MethodMayBeSynchronizedInspection extends BaseInspection {

  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.methodMayBeSynchronizedDisplayName();
  }

  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.methodMayBeSynchronizedProblemDescriptor().get();
  }

  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new MethodMayBeSynchronizedQuickFix();
  }

  public BaseInspectionVisitor buildVisitor() {
    return new MethodMayBeSynchronizedVisitor();
  }

  private static class MethodMayBeSynchronizedQuickFix
    extends InspectionGadgetsFix {

    @Nonnull
    public LocalizeValue getName() {
      return InspectionGadgetsLocalize.methodMayBeSynchronizedQuickfix();
    }

    protected void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      PsiElement identifier = descriptor.getPsiElement();
      PsiMethod method = (PsiMethod)identifier.getParent();
      PsiCodeBlock methodBody = method.getBody();
      if (methodBody == null) {
        return;
      }
      PsiStatement[] methodStatements = methodBody.getStatements();
      if (methodStatements.length != 1) {
        return;
      }
      PsiStatement statement = methodStatements[0];
      if (!(statement instanceof PsiSynchronizedStatement)) {
        return;
      }
      PsiSynchronizedStatement synchronizedStatement =
        (PsiSynchronizedStatement)statement;
      PsiCodeBlock body = synchronizedStatement.getBody();
      if (body == null) {
        return;
      }
      PsiStatement[] statements = body.getStatements();
      if (statements.length > 0) {
        PsiElement added =
          methodBody.addRangeBefore(
            statements[0],
            statements[statements.length - 1],
            synchronizedStatement);
        CodeStyleManager codeStyleManager =
          CodeStyleManager.getInstance(project);
        codeStyleManager.reformat(added);
      }
      synchronizedStatement.delete();
      PsiModifierList modifierList = method.getModifierList();
      modifierList.setModifierProperty(PsiModifier.SYNCHRONIZED, true);
    }
  }

  private static class MethodMayBeSynchronizedVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitSynchronizedStatement(
      PsiSynchronizedStatement statement) {
      super.visitSynchronizedStatement(statement);
      PsiElement parent = statement.getParent();
      if (!(parent instanceof PsiCodeBlock)) {
        return;
      }
      PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiMethod)) {
        return;
      }
      PsiMethod method = (PsiMethod)grandParent;
      PsiCodeBlock body = method.getBody();
      if (body == null) {
        return;
      }
      PsiStatement[] statements = body.getStatements();
      if (statements.length != 1) {
        return;
      }
      PsiExpression lockExpression = statement.getLockExpression();
      if (method.hasModifierProperty(PsiModifier.STATIC)) {
        if (!(lockExpression
                instanceof PsiClassObjectAccessExpression)) {
          return;
        }
        PsiClassObjectAccessExpression classExpression =
          (PsiClassObjectAccessExpression)lockExpression;
        PsiTypeElement typeElement =
          classExpression.getOperand();
        PsiType type = typeElement.getType();
        if (!(type instanceof PsiClassType)) {
          return;
        }
        PsiClassType classType = (PsiClassType)type;
        PsiClass aClass = classType.resolve();
        PsiClass containingClass = method.getContainingClass();
        if (aClass != containingClass) {
          return;
        }
        registerMethodError(method);
      }
      else {
        if (!(lockExpression instanceof PsiThisExpression)) {
          return;
        }
        PsiThisExpression thisExpression =
          (PsiThisExpression)lockExpression;
        PsiJavaCodeReferenceElement qualifier =
          thisExpression.getQualifier();
        if (qualifier != null) {
          PsiElement target = qualifier.resolve();
          PsiClass containingClass = method.getContainingClass();
          if (!containingClass.equals(target)) {
            return;
          }
        }
        registerMethodError(method);
      }
    }
  }
}