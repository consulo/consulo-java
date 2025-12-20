/*
 * Copyright 2008-2012 Bas Leijdekkers
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
import com.intellij.java.language.psi.codeStyle.VariableKind;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.refactoring.rename.SuggestedNameInfo;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ExtensionImpl
public class KeySetIterationMayUseEntrySetInspection extends BaseInspection {

  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.keySetIterationMayUseEntrySetDisplayName();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.keySetIterationMayUseEntrySetProblemDescriptor().get();
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new KeySetIterationMapUseEntrySetFix();
  }

  private static class KeySetIterationMapUseEntrySetFix
      extends InspectionGadgetsFix {

    @Nonnull
    public LocalizeValue getName() {
      return InspectionGadgetsLocalize.keySetIterationMayUseEntrySetQuickfix();
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor)
        throws IncorrectOperationException {
      PsiElement element = descriptor.getPsiElement();
      PsiElement parent = element.getParent();
      if (!(parent instanceof PsiForeachStatement)) {
        return;
      }
      PsiElement map;
      if (element instanceof PsiReferenceExpression) {
        PsiReferenceExpression referenceExpression =
            (PsiReferenceExpression) element;
        PsiElement target = referenceExpression.resolve();
        if (!(target instanceof PsiVariable)) {
          return;
        }
        PsiVariable variable = (PsiVariable) target;
        PsiExpression initializer = variable.getInitializer();
        if (!(initializer instanceof PsiMethodCallExpression)) {
          return;
        }
        PsiMethodCallExpression methodCallExpression =
            (PsiMethodCallExpression) initializer;
        PsiReferenceExpression methodExpression =
            methodCallExpression.getMethodExpression();
        PsiExpression qualifier =
            methodExpression.getQualifierExpression();
        if (!(qualifier instanceof PsiReferenceExpression)) {
          return;
        }
        PsiReferenceExpression reference =
            (PsiReferenceExpression) qualifier;
        map = reference.resolve();
        String qualifierText = qualifier.getText();
        replaceExpression(referenceExpression,
            qualifierText + ".entrySet()");
      } else if (element instanceof PsiMethodCallExpression) {
        PsiMethodCallExpression methodCallExpression =
            (PsiMethodCallExpression) element;
        PsiReferenceExpression methodExpression =
            methodCallExpression.getMethodExpression();
        PsiExpression qualifier =
            methodExpression.getQualifierExpression();
        if (!(qualifier instanceof PsiReferenceExpression)) {
          return;
        }
        PsiReferenceExpression referenceExpression =
            (PsiReferenceExpression) qualifier;
        map = referenceExpression.resolve();
        String qualifierText = qualifier.getText();
        replaceExpression(methodCallExpression,
            qualifierText + ".entrySet()");
      } else {
        return;
      }
      PsiForeachStatement foreachStatement =
          (PsiForeachStatement) parent;
      PsiExpression iteratedValue =
          foreachStatement.getIteratedValue();
      if (iteratedValue == null) {
        return;
      }
      PsiType type = iteratedValue.getType();
      if (!(type instanceof PsiClassType)) {
        return;
      }
      PsiClassType classType = (PsiClassType) type;
      PsiType[] parameterTypes = classType.getParameters();
      if (parameterTypes.length != 1) {
        return;
      }
      PsiType parameterType = parameterTypes[0];
      boolean insertCast = false;
      if (parameterType == null) {
        parameterType = TypeUtils.getObjectType(foreachStatement);
        insertCast = true;
      }
      PsiParameter parameter =
          foreachStatement.getIterationParameter();
      String variableName =
          createNewVariableName(foreachStatement, parameterType);
      if (insertCast) {
        replaceParameterAccess(parameter,
            "((Map.Entry)" + variableName + ')', map,
            foreachStatement);
      } else {
        replaceParameterAccess(parameter, variableName, map,
            foreachStatement);
      }
      PsiElementFactory factory =
          JavaPsiFacade.getInstance(project).getElementFactory();
      PsiParameter newParameter = factory.createParameter(
          variableName, parameterType);
      if (parameter.hasModifierProperty(PsiModifier.FINAL)) {
        PsiModifierList modifierList =
            newParameter.getModifierList();
        if (modifierList != null) {
          modifierList.setModifierProperty(PsiModifier.FINAL, true);
        }
      }
      parameter.replace(newParameter);
    }

    private static void replaceParameterAccess(PsiParameter parameter,
                                               @NonNls String variableName,
                                               PsiElement map,
                                               PsiElement context) {
      ParameterAccessCollector collector =
          new ParameterAccessCollector(parameter, map);
      context.accept(collector);
      List<PsiExpression> accesses =
          collector.getParameterAccesses();
      for (PsiExpression access : accesses) {
        if (access instanceof PsiMethodCallExpression) {
          replaceExpression(access, variableName + ".getValue()");
        } else {
          replaceExpression(access, variableName + ".getKey()");
        }
      }
    }

    private static String createNewVariableName(
        @Nonnull PsiElement scope, @Nonnull PsiType type) {
      Project project = scope.getProject();
      JavaCodeStyleManager codeStyleManager =
          JavaCodeStyleManager.getInstance(project);
      @NonNls String baseName;
      SuggestedNameInfo suggestions =
          codeStyleManager.suggestVariableName(
              VariableKind.LOCAL_VARIABLE, null, null, type);
      String[] names = suggestions.names;
      if (names != null && names.length > 0) {
        baseName = names[0];
      } else {
        baseName = "entry";
      }
      if (baseName == null || baseName.length() == 0) {
        baseName = "entry";
      }
      return codeStyleManager.suggestUniqueVariableName(baseName, scope,
          true);
    }

    private static class ParameterAccessCollector
        extends JavaRecursiveElementVisitor {

      private final PsiParameter parameter;
      private final PsiElement map;
      private final String parameterName;

      private final List<PsiExpression> parameterAccesses = new ArrayList();

      public ParameterAccessCollector(
          PsiParameter parameter, PsiElement map) {
        this.parameter = parameter;
        parameterName = parameter.getName();
        this.map = map;
      }

      @Override
      public void visitReferenceExpression(
          PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        if (expression.getQualifierExpression() != null) {
          return;
        }
        String expressionText = expression.getText();
        if (!expressionText.equals(parameterName)) {
          return;
        }
        PsiElement target = expression.resolve();
        if (!parameter.equals(target)) {
          return;
        }
        try {
          if (!collectValueUsage(expression)) {
            parameterAccesses.add(expression);
          }
        } catch (IncorrectOperationException e) {
          throw new RuntimeException(e);
        }
      }

      private boolean collectValueUsage(PsiReferenceExpression expression) throws IncorrectOperationException {
        PsiElement parent = expression.getParent();
        if (!(parent instanceof PsiExpressionList)) {
          return false;
        }
        PsiElement grandParent = parent.getParent();
        if (!(grandParent instanceof PsiMethodCallExpression)) {
          return false;
        }
        PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) grandParent;
        PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
        @NonNls String methodName = methodExpression.getReferenceName();
        if (!"get".equals(methodName)) {
          return false;
        }
        PsiExpression qualifier = methodExpression.getQualifierExpression();
        if (!(qualifier instanceof PsiReferenceExpression)) {
          return false;
        }
        PsiReferenceExpression referenceExpression = (PsiReferenceExpression) qualifier;
        PsiElement target2 = referenceExpression.resolve();
        if (!map.equals(target2)) {
          return false;
        }
        PsiExpression qualifierExpression = referenceExpression.getQualifierExpression();
        if (qualifierExpression != null &&
            !(qualifier instanceof PsiThisExpression) || qualifierExpression instanceof PsiSuperExpression) {
          return false;
        }
        parameterAccesses.add(methodCallExpression);
        return true;
      }

      public List<PsiExpression> getParameterAccesses() {
        Collections.reverse(parameterAccesses);
        return parameterAccesses;
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new KeySetIterationMayUseEntrySetVisitor();
  }

  private static class KeySetIterationMayUseEntrySetVisitor
      extends BaseInspectionVisitor {

    @Override
    public void visitForeachStatement(PsiForeachStatement statement) {
      super.visitForeachStatement(statement);
      PsiExpression iteratedValue = statement.getIteratedValue();
      if (iteratedValue == null) {
        return;
      }
      PsiExpression iteratedExpression;
      if (iteratedValue instanceof PsiReferenceExpression) {
        PsiReferenceExpression referenceExpression =
            (PsiReferenceExpression) iteratedValue;
        PsiElement target = referenceExpression.resolve();
        if (!(target instanceof PsiLocalVariable)) {
          return;
        }
        PsiVariable variable = (PsiVariable) target;
        PsiMethod containingMethod =
            PsiTreeUtil.getParentOfType(variable, PsiMethod.class);
        if (VariableAccessUtils.variableIsAssignedAtPoint(variable,
            containingMethod, statement)) {
          return;
        }
        iteratedExpression = variable.getInitializer();
      } else {
        iteratedExpression = iteratedValue;
      }
      PsiParameter parameter = statement.getIterationParameter();
      if (!isMapKeySetIteration(iteratedExpression, parameter,
          statement.getBody())) {
        return;
      }
      registerError(iteratedValue);
    }

    private static boolean isMapKeySetIteration(PsiExpression iteratedExpression, PsiVariable key, @Nullable PsiElement context) {
      if (context == null) {
        return false;
      }
      if (!(iteratedExpression instanceof PsiMethodCallExpression)) {
        return false;
      }
      PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) iteratedExpression;
      PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      @NonNls String methodName = methodExpression.getReferenceName();
      if (!"keySet".equals(methodName)) {
        return false;
      }
      PsiExpression expression = methodExpression.getQualifierExpression();
      if (!(expression instanceof PsiReferenceExpression)) {
        return false;
      }
      PsiReferenceExpression referenceExpression = (PsiReferenceExpression) expression;
      PsiElement target = referenceExpression.resolve();
      if (!(target instanceof PsiVariable)) {
        return false;
      }
      PsiVariable targetVariable = (PsiVariable) target;
      PsiType type = targetVariable.getType();
      if (!(type instanceof PsiClassType)) {
        return false;
      }
      PsiClassType classType = (PsiClassType) type;
      PsiClass aClass = classType.resolve();
      if (aClass == null) {
        return false;
      }
      String className = aClass.getQualifiedName();
      if (!CommonClassNames.JAVA_UTIL_MAP.equals(className)) {
        return false;
      }
      GetValueFromMapChecker checker = new GetValueFromMapChecker(targetVariable, key);
      context.accept(checker);
      return checker.isGetValueFromMap();
    }
  }

  private static class GetValueFromMapChecker extends JavaRecursiveElementVisitor {

    private final PsiVariable key;
    private final PsiVariable map;
    private boolean getValueFromMap = false;
    private boolean tainted = false;

    GetValueFromMapChecker(@Nonnull PsiVariable map, @Nonnull PsiVariable key) {
      this.map = map;
      this.key = key;
    }

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      if (tainted) {
        return;
      }
      super.visitReferenceExpression(expression);
      PsiElement parent = expression.getParent();
      if (parent instanceof PsiAssignmentExpression) {
        PsiElement target = expression.resolve();
        if (key.equals(target) || map.equals(target)) {
          tainted = true;
        }
      } else if (!(parent instanceof PsiReferenceExpression)) {
        return;
      }
      PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiMethodCallExpression)) {
        return;
      }
      PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) grandParent;
      PsiReferenceExpression methodExpression = (PsiReferenceExpression) parent;
      PsiElement target = expression.resolve();
      if (!map.equals(target)) {
        return;
      }
      PsiExpression qualifierExpression = expression.getQualifierExpression();
      if (qualifierExpression != null &&
          !(qualifierExpression instanceof PsiThisExpression || qualifierExpression instanceof PsiSuperExpression)) {
        return;
      }
      @NonNls String methodName = methodExpression.getReferenceName();
      if (!"get".equals(methodName)) {
        return;
      }
      PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return;
      }
      PsiExpression argument = arguments[0];
      if (!(argument instanceof PsiReferenceExpression)) {
        return;
      }
      PsiReferenceExpression referenceExpression = (PsiReferenceExpression) argument;
      PsiElement argumentTarget = referenceExpression.resolve();
      if (!key.equals(argumentTarget)) {
        return;
      }
      getValueFromMap = true;
    }

    public boolean isGetValueFromMap() {
      return getValueFromMap && !tainted;
    }
  }
}
