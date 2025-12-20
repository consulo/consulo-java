/*
 * Copyright 2011-2013 Bas Leijdekkers
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
package com.intellij.java.impl.ig.migration;

import com.intellij.java.analysis.impl.codeInspection.SwitchUtils;
import com.intellij.java.language.codeInsight.NullableNotNullManager;
import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.intellij.java.analysis.impl.codeInspection.ControlFlowUtils;
import com.intellij.java.analysis.impl.codeInspection.EquivalenceChecker;
import com.intellij.java.analysis.codeInspection.ParenthesesUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.ex.awt.event.DocumentAdapter;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.text.Document;
import java.awt.*;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

@ExtensionImpl
public class IfCanBeSwitchInspection extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public int minimumBranches = 3;

  @SuppressWarnings({"PublicField"})
  public boolean suggestIntSwitches = false;

  @SuppressWarnings({"PublicField"})
  public boolean suggestEnumSwitches = false;

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Nonnull
  @Override
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.ifCanBeSwitchDisplayName();
  }

  @Nonnull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.ifCanBeSwitchProblemDescriptor().get();
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new IfCanBeSwitchFix(minimumBranches);
  }

  @Override
  public JComponent createOptionsPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    JLabel label = new JLabel(InspectionGadgetsLocalize.ifCanBeSwitchMinimumBranchOption().get());
    NumberFormat formatter = NumberFormat.getIntegerInstance();
    formatter.setParseIntegerOnly(true);
    final JFormattedTextField valueField = new JFormattedTextField(formatter);
    valueField.setValue(Integer.valueOf(minimumBranches));
    valueField.setColumns(2);
    Document document = valueField.getDocument();
    document.addDocumentListener(new DocumentAdapter() {
      @Override
      public void textChanged(DocumentEvent e) {
        try {
          valueField.commitEdit();
          minimumBranches =
            ((Number)valueField.getValue()).intValue();
        }
        catch (ParseException e1) {
          // No luck this time
        }
      }
    });
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridx = 0;
    constraints.gridy = 0;
    constraints.insets.bottom = 4;
    constraints.weightx = 0.0;
    constraints.anchor = GridBagConstraints.BASELINE_LEADING;
    constraints.fill = GridBagConstraints.NONE;
    constraints.insets.right = 10;
    panel.add(label, constraints);
    constraints.gridx = 1;
    constraints.gridy = 0;
    constraints.weightx = 1.0;
    constraints.insets.right = 0;
    panel.add(valueField, constraints);
    constraints.gridx = 0;
    constraints.gridy = 1;
    constraints.gridwidth = 2;
    JCheckBox checkBox1 = new JCheckBox(InspectionGadgetsLocalize.ifCanBeSwitchIntOption().get(), suggestIntSwitches);
    final ButtonModel model1 = checkBox1.getModel();
    model1.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        suggestIntSwitches = model1.isSelected();
      }
    });
    panel.add(checkBox1, constraints);
    constraints.gridy = 2;
    constraints.weighty = 1.0;
    JCheckBox checkBox2 = new JCheckBox(InspectionGadgetsLocalize.ifCanBeSwitchEnumOption().get(), suggestEnumSwitches);
    final ButtonModel model2 = checkBox2.getModel();
    model2.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        suggestEnumSwitches = model2.isSelected();
      }
    });
    panel.add(checkBox2, constraints);
    return panel;
  }

  private static class IfCanBeSwitchFix extends InspectionGadgetsFix {
    private final int myMinimumBranches;

    public IfCanBeSwitchFix(int minimumBranches) {
      myMinimumBranches = minimumBranches;
    }

    @Nonnull
    public LocalizeValue getName() {
      return InspectionGadgetsLocalize.ifCanBeSwitchQuickfix();
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement().getParent();
      if (!(element instanceof PsiIfStatement)) {
        return;
      }
      PsiIfStatement ifStatement = (PsiIfStatement)element;
      boolean breaksNeedRelabeled = false;
      PsiStatement breakTarget = null;
      String labelString = "";
      if (ControlFlowUtils.statementContainsNakedBreak(ifStatement)) {
        breakTarget = PsiTreeUtil.getParentOfType(ifStatement, PsiLoopStatement.class, PsiSwitchStatement.class);
        if (breakTarget != null) {
          PsiElement parent = breakTarget.getParent();
          if (parent instanceof PsiLabeledStatement) {
            PsiLabeledStatement labeledStatement = (PsiLabeledStatement)parent;
            labelString = labeledStatement.getLabelIdentifier().getText();
            breakTarget = labeledStatement;
            breaksNeedRelabeled = true;
          }
          else {
            labelString = SwitchUtils.findUniqueLabelName(ifStatement, "label");
            breaksNeedRelabeled = true;
          }
        }
      }
      PsiIfStatement statementToReplace = ifStatement;
      PsiExpression switchExpression = SwitchUtils.getSwitchExpression(ifStatement, myMinimumBranches);
      if (switchExpression == null) {
        return;
      }
      List<IfStatementBranch> branches = new ArrayList<IfStatementBranch>(20);
      while (true) {
        PsiExpression condition = ifStatement.getCondition();
        PsiStatement thenBranch = ifStatement.getThenBranch();
        IfStatementBranch ifBranch = new IfStatementBranch(thenBranch, false);
        extractCaseExpressions(condition, switchExpression, ifBranch);
        if (!branches.isEmpty()) {
          extractIfComments(ifStatement, ifBranch);
        }
        extractStatementComments(thenBranch, ifBranch);
        branches.add(ifBranch);
        PsiStatement elseBranch = ifStatement.getElseBranch();
        if (elseBranch instanceof PsiIfStatement) {
          ifStatement = (PsiIfStatement)elseBranch;
        }
        else if (elseBranch == null) {
          break;
        }
        else {
          IfStatementBranch elseIfBranch = new IfStatementBranch(elseBranch, true);
          PsiKeyword elseKeyword = ifStatement.getElseElement();
          extractIfComments(elseKeyword, elseIfBranch);
          extractStatementComments(elseBranch, elseIfBranch);
          branches.add(elseIfBranch);
          break;
        }
      }

      @NonNls StringBuilder switchStatementText = new StringBuilder();
      switchStatementText.append("switch(").append(switchExpression.getText()).append("){");
      PsiType type = switchExpression.getType();
      boolean castToInt = type != null && type.equalsToText(CommonClassNames.JAVA_LANG_INTEGER);
      for (IfStatementBranch branch : branches) {
        boolean hasConflicts = false;
        for (IfStatementBranch testBranch : branches) {
          if (branch == testBranch) {
            continue;
          }
          if (branch.topLevelDeclarationsConflictWith(testBranch)) {
            hasConflicts = true;
          }
        }
        dumpBranch(branch, castToInt, hasConflicts, breaksNeedRelabeled, labelString, switchStatementText);
      }
      switchStatementText.append('}');
      JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(element.getProject());
      PsiElementFactory factory = psiFacade.getElementFactory();
      if (breaksNeedRelabeled) {
        StringBuilder out = new StringBuilder();
        if (!(breakTarget instanceof PsiLabeledStatement)) {
          out.append(labelString).append(':');
        }
        termReplace(breakTarget, statementToReplace, switchStatementText, out);
        String newStatementText = out.toString();
        PsiStatement newStatement = factory.createStatementFromText(newStatementText, element);
        breakTarget.replace(newStatement);
      }
      else {
        PsiStatement newStatement = factory.createStatementFromText(switchStatementText.toString(), element);
        statementToReplace.replace(newStatement);
      }
    }

    @Nullable
    public static <T extends PsiElement> T getPrevSiblingOfType(@Nullable PsiElement element, @Nonnull Class<T> aClass,
                                                                @Nonnull Class<? extends PsiElement>... stopAt) {
      if (element == null) {
        return null;
      }
      PsiElement sibling = element.getPrevSibling();
      while (sibling != null && !aClass.isInstance(sibling)) {
        for (Class<? extends PsiElement> stopClass : stopAt) {
          if (stopClass.isInstance(sibling)) {
            return null;
          }
        }
        sibling = sibling.getPrevSibling();
      }
      return (T)sibling;
    }

    private static void extractIfComments(PsiElement element, IfStatementBranch out) {
      PsiComment comment = getPrevSiblingOfType(element, PsiComment.class, PsiStatement.class);
      while (comment != null) {
        PsiElement sibling = comment.getPrevSibling();
        String commentText;
        if (sibling instanceof PsiWhiteSpace) {
          String whiteSpaceText = sibling.getText();
          if (whiteSpaceText.startsWith("\n")) {
            commentText = whiteSpaceText.substring(1) + comment.getText();
          }
          else {
            commentText = comment.getText();
          }
        }
        else {
          commentText = comment.getText();
        }
        out.addComment(commentText);
        comment = getPrevSiblingOfType(comment, PsiComment.class, PsiStatement.class);
      }
    }

    private static void extractStatementComments(PsiElement element, IfStatementBranch out) {
      PsiComment comment = getPrevSiblingOfType(element, PsiComment.class, PsiStatement.class, PsiKeyword.class);
      while (comment != null) {
        PsiElement sibling = comment.getPrevSibling();
        String commentText;
        if (sibling instanceof PsiWhiteSpace) {
          String whiteSpaceText = sibling.getText();
          if (whiteSpaceText.startsWith("\n")) {
            commentText = whiteSpaceText.substring(1) + comment.getText();
          }
          else {
            commentText = comment.getText();
          }
        }
        else {
          commentText = comment.getText();
        }
        out.addStatementComment(commentText);
        comment = getPrevSiblingOfType(comment, PsiComment.class, PsiStatement.class, PsiKeyword.class);
      }
    }

    private static void termReplace(PsiElement target, PsiElement replace, StringBuilder stringToReplaceWith, StringBuilder out) {
      if (target.equals(replace)) {
        out.append(stringToReplaceWith);
      }
      else if (target.getChildren().length == 0) {
        out.append(target.getText());
      }
      else {
        PsiElement[] children = target.getChildren();
        for (PsiElement child : children) {
          termReplace(child, replace, stringToReplaceWith, out);
        }
      }
    }

    private static void extractCaseExpressions(PsiExpression expression, PsiExpression switchExpression, IfStatementBranch branch) {
      if (expression instanceof PsiMethodCallExpression) {
        PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
        PsiExpressionList argumentList = methodCallExpression.getArgumentList();
        PsiExpression[] arguments = argumentList.getExpressions();
        PsiExpression argument = arguments[0];
        PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
        PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
        if (EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(switchExpression, argument)) {
          branch.addCaseExpression(qualifierExpression);
        }
        else {
          branch.addCaseExpression(argument);
        }
      }
      else if (expression instanceof PsiPolyadicExpression) {
        PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
        PsiExpression[] operands = polyadicExpression.getOperands();
        IElementType tokenType = polyadicExpression.getOperationTokenType();
        if (JavaTokenType.OROR.equals(tokenType)) {
          for (PsiExpression operand : operands) {
            extractCaseExpressions(operand, switchExpression, branch);
          }
        }
        else if (operands.length == 2) {
          PsiExpression lhs = operands[0];
          PsiExpression rhs = operands[1];
          if (EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(switchExpression, rhs)) {
            branch.addCaseExpression(lhs);
          }
          else {
            branch.addCaseExpression(rhs);
          }
        }
      }
      else if (expression instanceof PsiParenthesizedExpression) {
        PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)expression;
        PsiExpression contents = parenthesizedExpression.getExpression();
        extractCaseExpressions(contents, switchExpression, branch);
      }
    }

    private static void dumpBranch(IfStatementBranch branch, boolean castToInt, boolean wrap, boolean renameBreaks, String breakLabelName,
                                   @NonNls StringBuilder switchStatementText) {
      dumpComments(branch.getComments(), switchStatementText);
      if (branch.isElse()) {
        switchStatementText.append("default: ");
      }
      else {
        for (PsiExpression caseExpression : branch.getCaseExpressions()) {
          switchStatementText.append("case ").append(getCaseLabelText(caseExpression, castToInt)).append(": ");
        }
      }
      dumpComments(branch.getStatementComments(), switchStatementText);
      dumpBody(branch.getStatement(), wrap, renameBreaks, breakLabelName, switchStatementText);
    }

    @NonNls
    private static String getCaseLabelText(PsiExpression expression, boolean castToInt) {
      if (expression instanceof PsiReferenceExpression) {
        PsiReferenceExpression referenceExpression = (PsiReferenceExpression)expression;
        PsiElement target = referenceExpression.resolve();
        if (target instanceof PsiEnumConstant) {
          PsiEnumConstant enumConstant = (PsiEnumConstant)target;
          return enumConstant.getName();
        }
      }
      if (castToInt) {
        PsiType type = expression.getType();
        if (!PsiType.INT.equals(type)) {
          /*
         because
         Integer a = 1;
         switch (a) {
             case (byte)7:
         }
         does not compile with javac (but does with Eclipse)
          */
          return "(int)" + expression.getText();
        }
      }
      return expression.getText();
    }

    private static void dumpComments(List<String> comments, StringBuilder switchStatementText) {
      if (comments.isEmpty()) {
        return;
      }
      switchStatementText.append('\n');
      for (String comment : comments) {
        switchStatementText.append(comment).append('\n');
      }
    }

    private static void dumpBody(PsiStatement bodyStatement, boolean wrap, boolean renameBreaks, String breakLabelName,
                                 @NonNls StringBuilder switchStatementText) {
      if (wrap) {
        switchStatementText.append('{');
      }
      if (bodyStatement instanceof PsiBlockStatement) {
        PsiCodeBlock codeBlock = ((PsiBlockStatement)bodyStatement).getCodeBlock();
        PsiElement[] children = codeBlock.getChildren();
        //skip the first and last members, to unwrap the block
        for (int i = 1; i < children.length - 1; i++) {
          PsiElement child = children[i];
          appendElement(child, renameBreaks, breakLabelName, switchStatementText);
        }
      }
      else {
        appendElement(bodyStatement, renameBreaks, breakLabelName, switchStatementText);
      }
      if (ControlFlowUtils.statementMayCompleteNormally(bodyStatement)) {
        switchStatementText.append("break;");
      }
      if (wrap) {
        switchStatementText.append('}');
      }
    }

    private static void appendElement(PsiElement element, boolean renameBreakElements, String breakLabelString,
                                      @NonNls StringBuilder switchStatementText) {
      String text = element.getText();
      if (!renameBreakElements) {
        switchStatementText.append(text);
      }
      else if (element instanceof PsiBreakStatement) {
        PsiBreakStatement breakStatement = (PsiBreakStatement)element;
        PsiIdentifier identifier = breakStatement.getLabelIdentifier();
        if (identifier == null) {
          switchStatementText.append("break ").append(breakLabelString).append(';');
        }
        else {
          switchStatementText.append(text);
        }
      }
      else if (element instanceof PsiBlockStatement || element instanceof PsiCodeBlock || element instanceof PsiIfStatement) {
        PsiElement[] children = element.getChildren();
        for (PsiElement child : children) {
          appendElement(child, renameBreakElements, breakLabelString, switchStatementText);
        }
      }
      else {
        switchStatementText.append(text);
      }
      PsiElement lastChild = element.getLastChild();
      if (isEndOfLineComment(lastChild)) {
        switchStatementText.append('\n');
      }
    }

    private static boolean isEndOfLineComment(PsiElement element) {
      if (!(element instanceof PsiComment)) {
        return false;
      }
      PsiComment comment = (PsiComment)element;
      IElementType tokenType = comment.getTokenType();
      return JavaTokenType.END_OF_LINE_COMMENT.equals(tokenType);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new IfCanBeSwitchVisitor();
  }

  private class IfCanBeSwitchVisitor extends BaseInspectionVisitor {

    @Override
    public void visitIfStatement(PsiIfStatement statement) {
      super.visitIfStatement(statement);
      PsiElement parent = statement.getParent();
      if (parent instanceof PsiIfStatement) {
        return;
      }
      PsiExpression switchExpression = SwitchUtils.getSwitchExpression(statement, minimumBranches);
      if (switchExpression == null) {
        return;
      }
      PsiExpression unwrappedExpression = ParenthesesUtils.stripParentheses(switchExpression);
      if (unwrappedExpression instanceof PsiReferenceExpression) {
        PsiReferenceExpression referenceExpression = (PsiReferenceExpression)unwrappedExpression;
        PsiElement target = referenceExpression.resolve();
        if (target instanceof PsiModifierListOwner && NullableNotNullManager.isNullable((PsiModifierListOwner)target)) {
          return;
        }
      }
      PsiType type = switchExpression.getType();
      if (!suggestIntSwitches) {
        if (type instanceof PsiClassType) {
          if (type.equalsToText(CommonClassNames.JAVA_LANG_INTEGER) ||
              type.equalsToText(CommonClassNames.JAVA_LANG_SHORT) ||
              type.equalsToText(CommonClassNames.JAVA_LANG_BYTE) ||
              type.equalsToText(CommonClassNames.JAVA_LANG_CHARACTER)) {
            return;
          }
        }
        else if (PsiType.INT.equals(type) || PsiType.SHORT.equals(type) || PsiType.BYTE.equals(type) || PsiType.CHAR.equals(type)) {
          return;
        }
      }
      if (!suggestEnumSwitches && type instanceof PsiClassType) {
        PsiClassType classType = (PsiClassType)type;
        PsiClass aClass = classType.resolve();
        if (aClass == null || aClass.isEnum()) {
          return;
        }
      }
      registerStatementError(statement, switchExpression);
    }
  }
}
