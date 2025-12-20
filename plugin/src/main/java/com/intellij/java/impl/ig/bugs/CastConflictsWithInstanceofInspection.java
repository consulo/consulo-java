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
package com.intellij.java.impl.ig.bugs;

import com.intellij.java.impl.ig.psiutils.InstanceOfUtils;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class CastConflictsWithInstanceofInspection extends BaseInspection {

  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.castConflictsWithInstanceofDisplayName();
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.castConflictsWithInstanceofProblemDescriptor().get();
  }

  @Nonnull
  @Override
  protected InspectionGadgetsFix[] buildFixes(Object... infos) {
    String castExpressionType = ((PsiTypeElement)infos[1]).getText();
    String instanceofType = ((PsiTypeElement)infos[2]).getText();
    return new InspectionGadgetsFix[]{
      new ReplaceCastFix(instanceofType, castExpressionType),
      new ReplaceInstanceofFix(instanceofType, castExpressionType)
    };
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CastConflictsWithInstanceofVisitor();
  }

  private static class CastConflictsWithInstanceofVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitTypeCastExpression(@Nonnull PsiTypeCastExpression expression) {
      super.visitTypeCastExpression(expression);
      PsiTypeElement castType = expression.getCastType();
      if (castType == null) {
        return;
      }
      PsiType type = castType.getType();
      PsiExpression operand = expression.getOperand();
      if (!(operand instanceof PsiReferenceExpression)) {
        return;
      }
      PsiReferenceExpression referenceExpression = (PsiReferenceExpression)operand;
      PsiInstanceOfExpression conflictingInstanceof =
        InstanceOfUtils.getConflictingInstanceof(type, referenceExpression, expression);
      if (conflictingInstanceof == null) {
        return;
      }
      PsiTypeElement instanceofTypeElement = conflictingInstanceof.getCheckType();
      if (instanceofTypeElement == null) {
        return;
      }
      registerError(expression, referenceExpression, castType, instanceofTypeElement);
    }

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      PsiReferenceExpression methodExpression = expression.getMethodExpression();
      String methodName = methodExpression.getReferenceName();
      if (!"cast".equals(methodName)) {
        return;
      }
      PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      String qualifiedName = containingClass.getQualifiedName();
      if (!"java.lang.Class".equals(qualifiedName)) {
        return;
      }
      PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (!(qualifier instanceof PsiClassObjectAccessExpression)) {
        return;
      }
      PsiClassObjectAccessExpression classObjectAccessExpression = (PsiClassObjectAccessExpression)qualifier;
      PsiTypeElement operand = classObjectAccessExpression.getOperand();
      PsiType castType = operand.getType();
      if (!(castType instanceof PsiClassType)) {
        return;
      }
      PsiExpressionList argumentList = expression.getArgumentList();
      PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return;
      }
      PsiExpression argument = arguments[0];
      if (!(argument instanceof PsiReferenceExpression)) {
        return;
      }
      PsiReferenceExpression referenceExpression = (PsiReferenceExpression)argument;
      PsiInstanceOfExpression conflictingInstanceof =
        InstanceOfUtils.getConflictingInstanceof(castType, referenceExpression, expression);
      if (conflictingInstanceof == null) {
        return;
      }
      PsiTypeElement instanceofTypeElement = conflictingInstanceof.getCheckType();
      registerError(expression, referenceExpression, operand, instanceofTypeElement);
    }
  }

  private static abstract class ReplaceFix extends InspectionGadgetsFix {

    protected ReplaceFix() {
    }

    @Override
    protected final void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
        PsiElement element = descriptor.getPsiElement();
      PsiTypeElement castTypeElement;
      PsiReferenceExpression reference;
      if (element instanceof PsiTypeCastExpression) {
        PsiTypeCastExpression typeCastExpression = (PsiTypeCastExpression)element;
        castTypeElement = typeCastExpression.getCastType();
        PsiExpression operand = typeCastExpression.getOperand();
        if (!(operand instanceof PsiReferenceExpression)) {
          return;
        }
        reference = (PsiReferenceExpression)operand;
      } else if (element instanceof PsiMethodCallExpression) {
        PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)element;
        PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
        PsiExpression qualifier = methodExpression.getQualifierExpression();
        if (!(qualifier instanceof PsiClassObjectAccessExpression)) {
          return;
        }
        PsiClassObjectAccessExpression classObjectAccessExpression = (PsiClassObjectAccessExpression)qualifier;
        castTypeElement = classObjectAccessExpression.getOperand();
        PsiExpressionList argumentList = methodCallExpression.getArgumentList();
        PsiExpression[] arguments = argumentList.getExpressions();
        if (arguments.length != 1) {
          return;
        }
        PsiExpression argument = arguments[0];
        if (!(argument instanceof PsiReferenceExpression)) {
          return;
        }
        reference = (PsiReferenceExpression)argument;
      } else {
        return;
      }
      if (castTypeElement == null) {
        return;
      }
      PsiInstanceOfExpression conflictingInstanceof =
        InstanceOfUtils.getConflictingInstanceof(castTypeElement.getType(), reference, element);
      if (conflictingInstanceof == null) {
        return;
      }
      PsiTypeElement instanceofTypeElement = conflictingInstanceof.getCheckType();
      if (instanceofTypeElement == null) {
        return;
      }
      PsiElement newElement = replace(castTypeElement, instanceofTypeElement);
      JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
      codeStyleManager.shortenClassReferences(newElement);
    }

    protected abstract PsiElement replace(PsiTypeElement castTypeElement, PsiTypeElement instanceofTypeElement);
  }

  private static class ReplaceCastFix extends ReplaceFix {

    private String myInstanceofType;
    private String myCastType;

    public ReplaceCastFix(String instanceofType, String castType) {
      myInstanceofType = instanceofType;
      myCastType = castType;
    }

    @Nonnull
    public LocalizeValue getName() {
      return InspectionGadgetsLocalize.castConflictsWithInstanceofQuickfix1(myCastType, myInstanceofType);
    }

    @Override
    protected PsiElement replace(PsiTypeElement castTypeElement, PsiTypeElement instanceofTypeElement) {
      return castTypeElement.replace(instanceofTypeElement);
    }
  }

  private static class ReplaceInstanceofFix extends ReplaceFix {

    private final String myInstanceofType;
    private final String myCastType;

    public ReplaceInstanceofFix(String instanceofType, String castType) {
      myInstanceofType = instanceofType;
      myCastType = castType;
    }

    @Nonnull
    public LocalizeValue getName() {
      return InspectionGadgetsLocalize.castConflictsWithInstanceofQuickfix2(myInstanceofType, myCastType);
    }

    @Override
    protected PsiElement replace(PsiTypeElement castTypeElement, PsiTypeElement instanceofTypeElement) {
      return instanceofTypeElement.replace(castTypeElement);
    }
  }
}