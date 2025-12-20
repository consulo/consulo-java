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
package com.intellij.java.impl.ig.logging;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;

@ExtensionImpl
public class LoggingConditionDisagreesWithLogStatementInspection extends BaseInspection {

  private static final Set<String> loggingLevels = new HashSet(Arrays.asList(
    "debug", "error", "fatal", "info", "trace", "warn", "severe", "warning", "info", "config", "fine", "finer", "finest"));

  private static final Map<String, LoggingProblemChecker> problemCheckers = new HashMap();

  static {
    register(new Log4jProblemChecker());
    register(new CommonsLoggingProblemChecker());
    register(new JavaUtilLoggingProblemChecker());
    register(new Slf4jProblemChecker());
  }

  private static void register(LoggingProblemChecker problemChecker) {
    for (String name : problemChecker.getClassNames()) {
      problemCheckers.put(name, problemChecker);
    }
  }

  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.loggingConditionDisagreesWithLogStatementDisplayName();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.loggingConditionDisagreesWithLogStatementProblemDescriptor(infos[0]).get();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new LoggingConditionDisagreesWithLogStatementVisitor();
  }

  private static class LoggingConditionDisagreesWithLogStatementVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      PsiReferenceExpression methodExpression = expression.getMethodExpression();
      String referenceName = methodExpression.getReferenceName();
      if (referenceName == null) {
        return;
      }
      String loggingLevel;
      if (!loggingLevels.contains(referenceName)) {
        if (!"log".equals(referenceName)) {
          return;
        }
        PsiExpressionList argumentList = expression.getArgumentList();
        PsiExpression[] arguments = argumentList.getExpressions();
        if (arguments.length < 2) {
          return;
        }
        PsiExpression argument = arguments[0];
        loggingLevel = JavaUtilLoggingProblemChecker.getLoggingLevelFromArgument(argument);
        if (loggingLevel == null) {
          return;
        }
      }
      else {
        loggingLevel = referenceName;
      }
      PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      PsiElement parent = expression.getParent();
      if (!(parent instanceof PsiExpressionStatement)) {
        return;
      }
      PsiElement grandParent = parent.getParent();
      PsiIfStatement ifStatement;
      if (grandParent instanceof PsiCodeBlock) {
        PsiElement greatGrandParent = grandParent.getParent();
        if (!(greatGrandParent instanceof PsiBlockStatement)) {
          return;
        }
        PsiElement greatGreatGrandParent = greatGrandParent.getParent();
        if (!(greatGreatGrandParent instanceof PsiIfStatement)) {
          return;
        }
        ifStatement = (PsiIfStatement)greatGreatGrandParent;
      }
      else if (grandParent instanceof PsiIfStatement) {
        ifStatement = (PsiIfStatement)grandParent;
      }
      else {
        return;
      }
      PsiExpression condition = ifStatement.getCondition();
      if (condition instanceof PsiMethodCallExpression) {
        PsiStatement thenBranch = ifStatement.getThenBranch();
        if (!PsiTreeUtil.isAncestor(thenBranch, expression, false)) {
          return;
        }
      }
      else if (condition instanceof PsiPrefixExpression) {
        PsiPrefixExpression prefixExpression = (PsiPrefixExpression)condition;
        if (!JavaTokenType.EXCL.equals(prefixExpression.getOperationTokenType())) {
          return;
        }
        PsiStatement elseBranch = ifStatement.getElseBranch();
        if (!PsiTreeUtil.isAncestor(elseBranch, expression, false)) {
          return;
        }
        condition = prefixExpression.getOperand();
        if (!(condition instanceof PsiMethodCallExpression)) {
          return;
        }
      }
      else {
        return;
      }
      PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (!(qualifier instanceof PsiReferenceExpression)) {
        return;
      }
      PsiReferenceExpression referenceExpression = (PsiReferenceExpression)qualifier;
      PsiElement target = referenceExpression.resolve();
      if (target == null) {
        return;
      }
      PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)condition;
      PsiReferenceExpression conditionMethodExpression = methodCallExpression.getMethodExpression();
      PsiExpression conditionQualifier = conditionMethodExpression.getQualifierExpression();
      if (!(conditionQualifier instanceof PsiReferenceExpression)) {
        return;
      }
      PsiReferenceExpression conditionReferenceExpression = (PsiReferenceExpression)conditionQualifier;
      PsiElement conditionTarget = conditionReferenceExpression.resolve();
      if (!target.equals(conditionTarget)) {
        return;
      }
      String qualifiedName = containingClass.getQualifiedName();
      LoggingProblemChecker problemChecker = problemCheckers.get(qualifiedName);
      if (problemChecker == null || !problemChecker.hasLoggingProblem(loggingLevel, methodCallExpression)) {
        return;
      }
      registerMethodCallError(methodCallExpression, loggingLevel);
    }
  }

  interface LoggingProblemChecker {

    String[] getClassNames();

    boolean hasLoggingProblem(String priority, PsiMethodCallExpression methodCallExpression);
  }

  private static class JavaUtilLoggingProblemChecker implements LoggingProblemChecker {

    @Override
    public String[] getClassNames() {
      return new String[]{"java.util.logging.Logger"};
    }

    @Override
    public boolean hasLoggingProblem(String priority, PsiMethodCallExpression methodCallExpression) {
      PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      String methodName = methodExpression.getReferenceName();
      if (!"isLoggable".equals(methodName)) {
        return false;
      }
      PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return false;
      }
      PsiExpression argument = arguments[0];
      String loggingLevel = getLoggingLevelFromArgument(argument);
      if (loggingLevel == null) {
        return false;
      }
      return !loggingLevel.equals(priority);
    }

    @Nullable
    public static String getLoggingLevelFromArgument(PsiExpression argument) {
      if (!(argument instanceof PsiReferenceExpression)) {
        return null;
      }
      PsiReferenceExpression argumentReference = (PsiReferenceExpression)argument;
      PsiType type = argument.getType();
      if (!(type instanceof PsiClassType)) {
        return null;
      }
      PsiClassType classType = (PsiClassType)type;
      PsiClass aClass = classType.resolve();
      if (aClass == null) {
        return null;
      }
      String qName = aClass.getQualifiedName();
      if (!"java.util.logging.Level".equals(qName)) {
        return null;
      }
      PsiElement argumentTarget = argumentReference.resolve();
      if (!(argumentTarget instanceof PsiField)) {
        return null;
      }
      PsiField field = (PsiField)argumentTarget;
      return field.getName().toLowerCase();
    }
  }

  private static class CommonsLoggingProblemChecker implements LoggingProblemChecker {

    @Override
    public String[] getClassNames() {
      return new String[]{"org.apache.commons.logging.Log"};
    }

    @Override
    public boolean hasLoggingProblem(String priority, PsiMethodCallExpression methodCallExpression) {
      PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      String methodName = methodExpression.getReferenceName();
      if ("isTraceEnabled".equals(methodName)) {
        return !priority.equals("trace");
      }
      else if ("isDebugEnabled".equals(methodName)) {
        return !priority.equals("debug");
      }
      else if ("isInfoEnabled".equals(methodName)) {
        return !priority.equals("info");
      }
      else if ("isWarnEnabled".equals(methodName)) {
        return !priority.equals("warn");
      }
      else if ("isErrorEnabled".equals(methodName)) {
        return !priority.equals("error");
      }
      else if ("isFatalEnabled".equals(methodName)) {
        return !priority.equals("fatal");
      }
      return false;
    }
  }

  private static class Slf4jProblemChecker implements LoggingProblemChecker {
    @Override
    public String[] getClassNames() {
      return new String[]{"org.slf4j.Logger"};
    }

    @Override
    public boolean hasLoggingProblem(String priority, PsiMethodCallExpression methodCallExpression) {
      PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      String methodName = methodExpression.getReferenceName();
      if ("isTraceEnabled".equals(methodName)) {
        return !"trace".equals(priority);
      }
      else if ("isDebugEnabled".equals(methodName)) {
        return !"debug".equals(priority);
      }
      else if ("isInfoEnabled".equals(methodName)) {
        return !"info".equals(priority);
      }
      else if ("isWarnEnabled".equals(methodName)) {
        return !"warn".equals(priority);
      }
      else if ("isErrorEnabled".equals(methodName)) {
        return !"error".equals(priority);
      }
      return false;
    }
  }

  private static class Log4jProblemChecker implements LoggingProblemChecker {

    @Override
    public String[] getClassNames() {
      return new String[]{"org.apache.log4j.Logger", "org.apache.log4j.Category"};
    }

    @Override
    public boolean hasLoggingProblem(String priority, PsiMethodCallExpression methodCallExpression) {
      PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      String methodName = methodExpression.getReferenceName();
      String enabledFor = null;
      if ("isDebugEnabled".equals(methodName)) {
        enabledFor = "debug";
      }
      else if ("isInfoEnabled".equals(methodName)) {
        enabledFor = "info";
      }
      else if ("isTraceEnabled".equals(methodName)) {
        enabledFor = "trace";
      }
      else if ("isEnabledFor".equals(methodName)) {
        PsiExpressionList argumentList = methodCallExpression.getArgumentList();
        PsiExpression[] arguments = argumentList.getExpressions();
        for (PsiExpression argument : arguments) {
          if (!(argument instanceof PsiReferenceExpression)) {
            continue;
          }
          PsiReferenceExpression argumentReference = (PsiReferenceExpression)argument;
          PsiType type = argument.getType();
          if (!(type instanceof PsiClassType)) {
            continue;
          }
          PsiClassType classType = (PsiClassType)type;
          PsiClass aClass = classType.resolve();
          if (!InheritanceUtil.isInheritor(aClass, "org.apache.log4j.Priority")) {
            continue;
          }
          PsiElement argumentTarget = argumentReference.resolve();
          if (!(argumentTarget instanceof PsiField)) {
            continue;
          }
          PsiField field = (PsiField)argumentTarget;
          enabledFor = field.getName().toLowerCase();
        }
        if (enabledFor == null) {
          return false;
        }
      }
      return !priority.equals(enabledFor);
    }
  }
}
