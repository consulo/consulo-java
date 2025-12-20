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

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.intellij.java.analysis.impl.codeInspection.ControlFlowUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

@ExtensionImpl
public class TailRecursionInspection extends BaseInspection {
  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.tailRecursionDisplayName();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.tailRecursionProblemDescriptor().get();
  }

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    PsiMethod containingMethod = (PsiMethod)infos[0];
    if (!mayBeReplacedByIterativeMethod(containingMethod)) {
      return null;
    }
    return new RemoveTailRecursionFix();
  }

  private static boolean mayBeReplacedByIterativeMethod(PsiMethod containingMethod) {
    PsiParameterList parameterList = containingMethod.getParameterList();
    PsiParameter[] parameters = parameterList.getParameters();
    for (PsiParameter parameter : parameters) {
      if (parameter.hasModifierProperty(PsiModifier.FINAL)) {
        return false;
      }
    }
    return true;
  }

  private static class RemoveTailRecursionFix extends InspectionGadgetsFix {

    @Nonnull
    public LocalizeValue getName() {
      return InspectionGadgetsLocalize.tailRecursionReplaceQuickfix();
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      PsiElement tailCallToken = descriptor.getPsiElement();
      PsiMethod method = PsiTreeUtil.getParentOfType(tailCallToken, PsiMethod.class);
      if (method == null) {
        return;
      }
      PsiCodeBlock body = method.getBody();
      if (body == null) {
        return;
      }
      @NonNls StringBuilder builder = new StringBuilder();
      builder.append('{');
      PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      String thisVariableName;
      JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(project);
      if (methodReturnsContainingClassType(method, containingClass)) {
        builder.append(containingClass.getName());
        thisVariableName = styleManager.suggestUniqueVariableName("result", method, false);
        builder.append(' ');
        builder.append(thisVariableName);
        builder.append(" = this;");
      }
      else if (methodContainsCallOnOtherInstance(method)) {
        builder.append(containingClass.getName());
        thisVariableName = styleManager.suggestUniqueVariableName("other", method, false);
        builder.append(' ');
        builder.append(thisVariableName);
        builder.append(" = this;");
      }
      else {
        thisVariableName = null;
      }
      boolean tailCallIsContainedInLoop;
      if (ControlFlowUtils.isInLoop(tailCallToken)) {
        tailCallIsContainedInLoop = true;
        builder.append(method.getName());
        builder.append(':');
      }
      else {
        tailCallIsContainedInLoop = false;
      }
      builder.append("while(true)");
      CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
      replaceTailCalls(body, method, thisVariableName, tailCallIsContainedInLoop, builder);
      builder.append('}');
      @NonNls String replacementText = builder.toString();
      JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
      PsiElementFactory elementFactory = psiFacade.getElementFactory();
      PsiCodeBlock block = elementFactory.createCodeBlockFromText(replacementText, method);
      body.replace(block);
      codeStyleManager.reformat(method);
    }

    private static boolean methodReturnsContainingClassType(
      PsiMethod method, PsiClass containingClass) {
      if (containingClass == null) {
        return false;
      }
      if (method.hasModifierProperty(PsiModifier.STATIC)) {
        return false;
      }
      PsiType returnType = method.getReturnType();
      if (!(returnType instanceof PsiClassType)) {
        return false;
      }
      PsiClassType classType = (PsiClassType)returnType;
      PsiClass aClass = classType.resolve();
      return containingClass.equals(aClass);
    }

    private static boolean methodContainsCallOnOtherInstance(
      PsiMethod method) {
      if (method.hasModifierProperty(PsiModifier.STATIC)) {
        return false;
      }
      PsiCodeBlock body = method.getBody();
      if (body == null) {
        return false;
      }
      PsiClass aClass = method.getContainingClass();
      MethodContainsCallOnOtherInstanceVisitor visitor = new MethodContainsCallOnOtherInstanceVisitor(aClass);
      body.accept(visitor);
      return visitor.containsCallOnOtherInstance();
    }

    private static class MethodContainsCallOnOtherInstanceVisitor extends JavaRecursiveElementVisitor {
      private boolean containsCallOnOtherInstance = false;
      private final PsiClass aClass;

      MethodContainsCallOnOtherInstanceVisitor(
        PsiClass aClass) {
        this.aClass = aClass;
      }

      @Override
      public void visitMethodCallExpression(
        PsiMethodCallExpression expression) {
        if (containsCallOnOtherInstance) {
          return;
        }
        super.visitMethodCallExpression(expression);
        PsiReferenceExpression methodExpression = expression.getMethodExpression();
        PsiExpression qualifier = methodExpression.getQualifierExpression();
        if (qualifier == null) {
          return;
        }
        PsiMethod method = expression.resolveMethod();
        if (method == null) {
          return;
        }
        PsiClass containingClass = method.getContainingClass();
        if (aClass.equals(containingClass)) {
          containsCallOnOtherInstance = true;
        }
      }

      public boolean containsCallOnOtherInstance() {
        return containsCallOnOtherInstance;
      }
    }

    private static void replaceTailCalls(
      PsiElement element,
      PsiMethod method,
      @Nullable String thisVariableName,
      boolean tailCallIsContainedInLoop,
      @NonNls StringBuilder out
    ) {
      String text = element.getText();
      if (isImplicitCallOnThis(element, method)) {
        if (thisVariableName != null) {
          out.append(thisVariableName);
          out.append('.');
        }
        out.append(text);
      }
      else if (element instanceof PsiThisExpression || element instanceof PsiSuperExpression) {
        if (thisVariableName == null) {
          out.append(text);
        }
        else {
          out.append(thisVariableName);
        }
      }
      else if (isTailCallReturn(element, method)) {
        PsiReturnStatement returnStatement = (PsiReturnStatement)element;
        PsiMethodCallExpression call = (PsiMethodCallExpression)returnStatement.getReturnValue();
        assert call != null;
        PsiExpressionList argumentList = call.getArgumentList();
        PsiExpression[] arguments = argumentList.getExpressions();
        PsiParameterList parameterList = method.getParameterList();
        PsiParameter[] parameters = parameterList.getParameters();
        boolean isInBlock = returnStatement.getParent() instanceof PsiCodeBlock;
        if (!isInBlock) {
          out.append('{');
        }
        for (int i = 0; i < parameters.length; i++) {
          PsiParameter parameter = parameters[i];
          PsiExpression argument = arguments[i];
          String parameterName = parameter.getName();
          if (parameterName == null) {
            continue;
          }
          String argumentText = argument.getText();
          if (parameterName.equals(argumentText)) {
            continue;
          }
          out.append(parameterName);
          out.append(" = ");
          out.append(argumentText);
          out.append(';');
        }
        if (thisVariableName != null) {
          PsiReferenceExpression methodExpression = call.getMethodExpression();
          PsiExpression qualifier = methodExpression.getQualifierExpression();
          if (qualifier != null) {
            out.append(thisVariableName);
            out.append(" = ");
            replaceTailCalls(qualifier, method, thisVariableName, tailCallIsContainedInLoop, out);
            out.append(';');
          }
        }
        PsiCodeBlock body = method.getBody();
        assert body != null;
        if (ControlFlowUtils.blockCompletesWithStatement(body, returnStatement)) {
          //don't do anything, as the continue is unnecessary
        }
        else if (tailCallIsContainedInLoop) {
          String methodName = method.getName();
          out.append("continue ");
          out.append(methodName);
          out.append(';');
        }
        else {
          out.append("continue;");
        }
        if (!isInBlock) {
          out.append('}');
        }
      }
      else {
        PsiElement[] children = element.getChildren();
        if (children.length == 0) {
          out.append(text);
        }
        else {
          for (PsiElement child : children) {
            replaceTailCalls(child, method, thisVariableName, tailCallIsContainedInLoop, out);
          }
        }
      }
    }

    private static boolean isImplicitCallOnThis(PsiElement element, PsiMethod containingMethod) {
      if (containingMethod.hasModifierProperty(PsiModifier.STATIC)) {
        return false;
      }
      if (element instanceof PsiMethodCallExpression) {
        PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)element;
        PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
        PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
        return qualifierExpression == null;
      }
      else if (element instanceof PsiReferenceExpression) {
        PsiReferenceExpression referenceExpression = (PsiReferenceExpression)element;
        PsiElement parent = referenceExpression.getParent();
        if (parent instanceof PsiMethodCallExpression) {
          return false;
        }
        PsiExpression qualifier = referenceExpression.getQualifierExpression();
        if (qualifier != null) {
          return false;
        }
        PsiElement target = referenceExpression.resolve();
        return target instanceof PsiField;
      }
      else {
        return false;
      }
    }

    private static boolean isTailCallReturn(PsiElement element, PsiMethod containingMethod) {
      if (!(element instanceof PsiReturnStatement)) {
        return false;
      }
      PsiReturnStatement returnStatement = (PsiReturnStatement)element;
      PsiExpression returnValue = returnStatement.getReturnValue();
      if (!(returnValue instanceof PsiMethodCallExpression)) {
        return false;
      }
      PsiMethodCallExpression call = (PsiMethodCallExpression)returnValue;
      PsiMethod method = call.resolveMethod();
      return containingMethod.equals(method);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new TailRecursionVisitor();
  }

  private static class TailRecursionVisitor extends BaseInspectionVisitor {
    @Override
    public void visitReturnStatement(@Nonnull PsiReturnStatement statement) {
      super.visitReturnStatement(statement);
      PsiExpression returnValue = statement.getReturnValue();
      if (!(returnValue instanceof PsiMethodCallExpression)) {
        return;
      }
      PsiMethodCallExpression returnCall = (PsiMethodCallExpression)returnValue;
      PsiMethod containingMethod = PsiTreeUtil.getParentOfType(statement, PsiMethod.class);
      if (containingMethod == null) {
        return;
      }
      PsiReferenceExpression methodExpression = returnCall.getMethodExpression();
      String name = containingMethod.getName();
      if (!name.equals(methodExpression.getReferenceName())) {
        return;
      }
      PsiMethod method = returnCall.resolveMethod();
      if (method == null) {
        return;
      }
      if (!method.equals(containingMethod)) {
        return;
      }
      registerMethodCallError(returnCall, containingMethod);
    }
  }
}