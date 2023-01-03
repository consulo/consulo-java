// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.ig.fixes;

import com.intellij.java.language.impl.codeInsight.template.JavaTemplateUtil;
import com.intellij.java.impl.ig.psiutils.CreateSwitchBranchesUtil;
import com.intellij.java.impl.ig.psiutils.SwitchUtils;
import com.intellij.java.language.psi.*;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import consulo.codeEditor.Editor;
import consulo.fileTemplate.FileTemplate;
import consulo.fileTemplate.FileTemplateManager;
import consulo.language.editor.template.ConstantNode;
import consulo.language.editor.template.TemplateBuilder;
import consulo.language.editor.template.TemplateBuilderFactory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import org.jetbrains.annotations.Nls;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.*;

public class CreateDefaultBranchFix extends BaseSwitchFix {
  private static final String PLACEHOLDER_NAME = "$EXPRESSION$";
  private final String myMessage;

  public CreateDefaultBranchFix(@Nonnull PsiSwitchBlock block, String message) {
    super(block);
    myMessage = message;
  }

  @Nonnull
  @Override
  public String getText() {
    return myMessage == null ? getName() : myMessage;
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @Nonnull
  @Override
  public String getFamilyName() {
    return "Insert 'default' branch";
  }

  @Override
  protected void invoke() {
    PsiSwitchBlock switchBlock = myBlock.getElement();
    if (switchBlock == null) {
      return;
    }
    PsiCodeBlock body = switchBlock.getBody();
    if (body == null) {
      return;
    }
    if (SwitchUtils.calculateBranchCount(switchBlock) < 0) {
      // Default already present for some reason
      return;
    }
    PsiExpression switchExpression = switchBlock.getExpression();
    if (switchExpression == null) {
      return;
    }
    boolean isRuleBasedFormat = SwitchUtils.isRuleFormatSwitch(switchBlock);
    PsiElement anchor = body.getRBrace();
    if (anchor == null) {
      return;
    }
    PsiElement parent = anchor.getParent();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(anchor.getProject());
    generateStatements(switchBlock, isRuleBasedFormat).stream()
        .map(text -> factory.createStatementFromText(text, parent))
        .forEach(statement -> parent.addBefore(statement, anchor));
    adjustEditor(switchBlock);
  }

  private static void adjustEditor(@Nonnull PsiSwitchBlock block) {
    PsiCodeBlock body = block.getBody();
    if (body == null) {
      return;
    }
    Editor editor = CreateSwitchBranchesUtil.prepareForTemplateAndObtainEditor(block);
    if (editor == null) {
      return;
    }
    PsiStatement lastStatement = ArrayUtil.getLastElement(body.getStatements());
    if (lastStatement instanceof PsiSwitchLabeledRuleStatement) {
      lastStatement = ((PsiSwitchLabeledRuleStatement) lastStatement).getBody();
    }
    if (lastStatement != null) {
      TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(block);
      builder.replaceElement(lastStatement, new ConstantNode(lastStatement.getText()));
      builder.run(editor, true);
    }
  }

  private static final String ATTRIBUTE_EXPRESSION = "EXPRESSION";
  private static final String ATTRIBUTE_EXPRESSION_TYPE = "EXPRESSION_TYPE";

  private static List<String> generateStatements(PsiSwitchBlock switchBlock, boolean isRuleBasedFormat) {
    Project project = switchBlock.getProject();
    FileTemplate branchTemplate = FileTemplateManager.getInstance(project).getCodeTemplate(JavaTemplateUtil.TEMPLATE_SWITCH_DEFAULT_BRANCH);
    Properties props = FileTemplateManager.getInstance(project).getDefaultProperties();
    PsiExpression expression = switchBlock.getExpression();
    props.setProperty(ATTRIBUTE_EXPRESSION, PLACEHOLDER_NAME);
    PsiType expressionType = expression == null ? null : expression.getType();
    props.setProperty(ATTRIBUTE_EXPRESSION_TYPE, expressionType == null ? "" : expressionType.getCanonicalText());
    PsiStatement statement;
    try {
      String text = branchTemplate.getText(props);
      if (text.trim().isEmpty()) {
        if (switchBlock instanceof PsiSwitchExpression) {
          String value = TypeUtils.getDefaultValue(((PsiSwitchExpression) switchBlock).getType());
          text = isRuleBasedFormat ? value + ";" : "break " + value + ";";
        }
      }
      statement = JavaPsiFacade.getElementFactory(project).createStatementFromText("{" + text + "}", switchBlock);
      if (expression != null) {
        PsiElement[] refs = PsiTreeUtil.collectElements(
            statement, e -> e instanceof PsiReferenceExpression && e.textMatches(PLACEHOLDER_NAME));
        for (PsiElement ref : refs) {
          // This would add parentheses when necessary
          ref.replace(expression);
        }
      }
    } catch (IOException | IncorrectOperationException e) {
      throw new IncorrectOperationException("Incorrect file template", (Throwable) e);
    }
    PsiStatement stripped = ControlFlowUtils.stripBraces(statement);
    if (!isRuleBasedFormat || stripped instanceof PsiThrowStatement || stripped instanceof PsiExpressionStatement) {
      statement = stripped;
    }
    if (isRuleBasedFormat) {
      return Collections.singletonList("default -> " + statement.getText());
    } else {
      PsiStatement lastStatement = ArrayUtil.getLastElement(Objects.requireNonNull(switchBlock.getBody()).getStatements());
      if (lastStatement != null && ControlFlowUtils.statementMayCompleteNormally(lastStatement)) {
        return Arrays.asList("break;", "default:", statement.getText());
      }
      return Arrays.asList("default:", statement.getText());
    }
  }
}
