/*
 * Copyright 2008-2013 Bas Leijdekkers
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

import com.intellij.java.impl.ig.ui.UiUtils;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.CheckBox;
import consulo.deadCodeNotWorking.impl.TextField;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.ex.awt.FormBuilder;
import consulo.ui.ex.awt.table.ListTable;
import consulo.ui.ex.awt.table.ListWrappingTableModel;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.WriteExternalException;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@ExtensionImpl
public class LogStatementGuardedByLogConditionInspection extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public String loggerClassName = "java.util.logging.Logger";
  @SuppressWarnings({"PublicField"})
  @NonNls
  public String loggerMethodAndconditionMethodNames =
    "fine,isLoggable(java.util.logging.Level.FINE)," +
    "finer,isLoggable(java.util.logging.Level.FINER)," +
    "finest,isLoggable(java.util.logging.Level.FINEST)";
  final List<String> logMethodNameList = new ArrayList();
  final List<String> logConditionMethodNameList = new ArrayList();

  @SuppressWarnings("PublicField")
  public boolean flagAllUnguarded = false;

  public LogStatementGuardedByLogConditionInspection() {
    parseString(loggerMethodAndconditionMethodNames, logMethodNameList, logConditionMethodNameList);
  }

  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.logStatementGuardedByLogConditionDisplayName();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.logStatementGuardedByLogConditionProblemDescriptor().get();
  }

  @Override
  public JComponent createOptionsPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    JLabel classNameLabel = new JLabel(InspectionGadgetsLocalize.loggerNameOption().get());
    classNameLabel.setHorizontalAlignment(SwingConstants.TRAILING);
    TextField loggerClassNameField = new TextField(this, "loggerClassName");
    ListTable table = new ListTable(new ListWrappingTableModel(
      Arrays.asList(logMethodNameList, logConditionMethodNameList),
      InspectionGadgetsLocalize.logMethodName().get(),
      InspectionGadgetsLocalize.logConditionText().get()
    ));
    panel.add(UiUtils.createAddRemovePanel(table), BorderLayout.CENTER);
    panel.add(FormBuilder.createFormBuilder().addLabeledComponent(classNameLabel, loggerClassNameField).getPanel(), BorderLayout.NORTH);
    panel.add(
      new CheckBox(
        InspectionGadgetsLocalize.logStatementGuardedByLogConditionFlagAllUnguardedOption().get(),
        this,
        "flagAllUnguarded"
      ),
      BorderLayout.SOUTH
    );
    return panel;
  }

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new LogStatementGuardedByLogConditionFix();
  }

  private class LogStatementGuardedByLogConditionFix extends InspectionGadgetsFix {

    @Nonnull
    public LocalizeValue getName() {
      return InspectionGadgetsLocalize.logStatementGuardedByLogConditionQuickfix();
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)element.getParent().getParent();
      PsiStatement statement = PsiTreeUtil.getParentOfType(
        methodCallExpression, PsiStatement.class);
      if (statement == null) {
        return;
      }
      List<PsiStatement> logStatements = new ArrayList();
      logStatements.add(statement);
      PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      String referenceName = methodExpression.getReferenceName();
      if (referenceName == null) {
        return;
      }
      PsiStatement previousStatement = PsiTreeUtil.getPrevSiblingOfType(statement, PsiStatement.class);
      while (previousStatement != null && isSameLogMethodCall(previousStatement, referenceName)) {
        logStatements.add(0, previousStatement);
        previousStatement = PsiTreeUtil.getPrevSiblingOfType(previousStatement, PsiStatement.class);
      }
      PsiStatement nextStatement = PsiTreeUtil.getNextSiblingOfType(statement, PsiStatement.class);
      while (nextStatement != null && isSameLogMethodCall(nextStatement, referenceName)) {
        logStatements.add(nextStatement);
        nextStatement = PsiTreeUtil.getNextSiblingOfType(nextStatement, PsiStatement.class);
      }
      PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
      PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier == null) {
        return;
      }
      @NonNls StringBuilder ifStatementText = new StringBuilder("if (");
      ifStatementText.append(qualifier.getText());
      ifStatementText.append('.');
      int index = logMethodNameList.indexOf(referenceName);
      String conditionMethodText = logConditionMethodNameList.get(index);
      ifStatementText.append(conditionMethodText);
      ifStatementText.append(") {}");
      PsiIfStatement ifStatement = (PsiIfStatement)factory.createStatementFromText(
          ifStatementText.toString(), statement);
      PsiBlockStatement blockStatement = (PsiBlockStatement)ifStatement.getThenBranch();
      if (blockStatement == null) {
        return;
      }
      PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
      for (PsiStatement logStatement : logStatements) {
        codeBlock.add(logStatement);
      }
      PsiStatement firstStatement = logStatements.get(0);
      PsiElement parent = firstStatement.getParent();
      PsiElement result = parent.addBefore(ifStatement, firstStatement);
      JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
      codeStyleManager.shortenClassReferences(result);
      for (PsiStatement logStatement : logStatements) {
        logStatement.delete();
      }
    }

    private boolean isSameLogMethodCall(PsiStatement statement, @Nonnull String methodName) {
      if (statement == null) {
        return false;
      }
      if (!(statement instanceof PsiExpressionStatement)) {
        return false;
      }
      PsiExpressionStatement expressionStatement = (PsiExpressionStatement)statement;
      PsiExpression expression = expressionStatement.getExpression();
      if (!(expression instanceof PsiMethodCallExpression)) {
        return false;
      }
      PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
      PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      String referenceName = methodExpression.getReferenceName();
      if (!methodName.equals(referenceName)) {
        return false;
      }
      PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier == null) {
        return false;
      }
      return TypeUtils.expressionHasTypeOrSubtype(qualifier, loggerClassName);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new LogStatementGuardedByLogConditionVisitor();
  }

  private class LogStatementGuardedByLogConditionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      PsiReferenceExpression methodExpression = expression.getMethodExpression();
      String referenceName = methodExpression.getReferenceName();
      if (!logMethodNameList.contains(referenceName)) {
        return;
      }
      PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier == null) {
        return;
      }
      if (!TypeUtils.expressionHasTypeOrSubtype(qualifier, loggerClassName)) {
        return;
      }
      if (isSurroundedByLogGuard(expression, referenceName)) {
        return;
      }
      PsiExpressionList argumentList = expression.getArgumentList();
      PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length == 0) {
        return;
      }
      if (!flagAllUnguarded) {
        boolean constant = true;
        for (PsiExpression argument : arguments) {
          if (!PsiUtil.isConstantExpression(argument)) {
            constant = false;
            break;
          }
        }
        if (constant) {
          return;
        }
      }
      registerMethodCallError(expression);
    }

    private boolean isSurroundedByLogGuard(PsiElement element, String logMethodName) {
      while (true) {
        PsiIfStatement ifStatement = PsiTreeUtil.getParentOfType(element, PsiIfStatement.class);
        if (ifStatement == null) {
          return false;
        }
        PsiExpression condition = ifStatement.getCondition();
        if (isLogGuardCheck(condition, logMethodName)) {
          return true;
        }
        element = ifStatement;
      }
    }

    private boolean isLogGuardCheck(@Nullable PsiExpression expression, String logMethodName) {
      if (expression instanceof PsiMethodCallExpression) {
        PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
        PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
        PsiExpression qualifier = methodExpression.getQualifierExpression();
        if (qualifier == null) {
          return false;
        }
        if (!TypeUtils.expressionHasTypeOrSubtype(qualifier, loggerClassName)) {
          return false;
        }
        String referenceName = methodExpression.getReferenceName();
        int index = logMethodNameList.indexOf(logMethodName);
        String conditionName = logConditionMethodNameList.get(index);
        return conditionName.startsWith(referenceName);
      }
      else if (expression instanceof PsiPolyadicExpression) {
        PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
        PsiExpression[] operands = polyadicExpression.getOperands();
        for (PsiExpression operand : operands) {
          if (isLogGuardCheck(operand, logMethodName)) {
            return true;
          }
        }
      }
      return false;
    }
  }

  @Override
  public void readSettings(@Nonnull Element element) throws InvalidDataException {
    super.readSettings(element);
    parseString(loggerMethodAndconditionMethodNames, logMethodNameList, logConditionMethodNameList);
  }

  @Override
  public void writeSettings(@Nonnull Element element) throws WriteExternalException {
    loggerMethodAndconditionMethodNames = formatString(logMethodNameList, logConditionMethodNameList);
    super.writeSettings(element);
  }
}
