/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Aug 22, 2002
 * Time: 2:55:23 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.java.impl.codeInsight.intention.impl;

import com.intellij.java.impl.codeInsight.CodeInsightServicesUtil;
import com.intellij.java.language.impl.psi.controlFlow.*;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.ide.impl.idea.util.ArrayUtilRt;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.editor.intention.PsiElementBaseIntentionAction;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

import java.util.List;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.InvertIfConditionAction", categories = {"Java", "Control Flow"}, fileExtensions = "java")
public class InvertIfConditionAction extends PsiElementBaseIntentionAction {
  private static final Logger LOG = Logger.getInstance(InvertIfConditionAction.class);

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, @Nonnull PsiElement element) {

    int offset = editor.getCaretModel().getOffset();
    final PsiIfStatement ifStatement = PsiTreeUtil.getParentOfType(element, PsiIfStatement.class);
    if (ifStatement == null) return false;
    final PsiExpression condition = ifStatement.getCondition();
    if (condition == null) return false;
    if (ifStatement.getThenBranch() == null) return false;
    if (element instanceof PsiKeyword keyword) {
      if ((keyword.getTokenType() == JavaTokenType.IF_KEYWORD || keyword.getTokenType() == JavaTokenType.ELSE_KEYWORD)
          && keyword.getParent() == ifStatement) {
        return true;
      }
    }
    final TextRange condTextRange = condition.getTextRange();
    if (condTextRange == null) return false;
    if (!condTextRange.contains(offset)) return false;
    PsiElement block = findCodeBlock(ifStatement);
    return block != null;
  }

  @Override
  @Nonnull
  public String getText() {
    return CodeInsightLocalize.intentionInvertIfCondition().get();
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, @Nonnull PsiElement element) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return;

    PsiIfStatement ifStatement = PsiTreeUtil.getParentOfType(element, PsiIfStatement.class);

    LOG.assertTrue(ifStatement != null);
    PsiElement block = findCodeBlock(ifStatement);

    ControlFlow controlFlow = buildControlFlow(block);

    PsiExpression condition = (PsiExpression) ifStatement.getCondition().copy();

    setupBranches(ifStatement, controlFlow);
    if (condition != null) {
      ifStatement.getCondition().replace(CodeInsightServicesUtil.invertCondition(condition));
    }

    formatIf(ifStatement);
  }

  private static void formatIf(PsiIfStatement ifStatement) throws IncorrectOperationException {
    final Project project = ifStatement.getProject();
    PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();

    PsiElement thenBranch = ifStatement.getThenBranch().copy();
    PsiElement elseBranch = ifStatement.getElseBranch() != null ? ifStatement.getElseBranch().copy() : null;
    PsiElement condition = ifStatement.getCondition().copy();

    final CodeStyleManager codeStyle = CodeStyleManager.getInstance(project);

    PsiBlockStatement codeBlock = (PsiBlockStatement) factory.createStatementFromText("{}", null);
    codeBlock = (PsiBlockStatement) codeStyle.reformat(codeBlock);

    ifStatement.getThenBranch().replace(codeBlock);
    if (elseBranch != null) {
      ifStatement.getElseBranch().replace(codeBlock);
    }
    ifStatement.getCondition().replace(factory.createExpressionFromText("true", null));
    ifStatement = (PsiIfStatement) codeStyle.reformat(ifStatement);

    if (!(thenBranch instanceof PsiBlockStatement)) {
      PsiBlockStatement codeBlock1 = (PsiBlockStatement) ifStatement.getThenBranch().replace(codeBlock);
      codeBlock1 = (PsiBlockStatement) codeStyle.reformat(codeBlock1);
      codeBlock1.getCodeBlock().add(thenBranch);
    } else {
      ifStatement.getThenBranch().replace(thenBranch);
    }

    if (elseBranch != null) {
      if (!(elseBranch instanceof PsiBlockStatement)) {
        PsiBlockStatement codeBlock1 = (PsiBlockStatement) ifStatement.getElseBranch().replace(codeBlock);
        codeBlock1 = (PsiBlockStatement) codeStyle.reformat(codeBlock1);
        codeBlock1.getCodeBlock().add(elseBranch);
      } else {
        elseBranch = ifStatement.getElseBranch().replace(elseBranch);

        if (emptyBlock(((PsiBlockStatement) elseBranch).getCodeBlock())) {
          ifStatement.getElseBranch().delete();
        }
      }
    }

    ifStatement.getCondition().replace(condition);
  }

  private static boolean emptyBlock(PsiCodeBlock block) {
    PsiElement[] children = block.getChildren();
    for (PsiElement child : children) {
      if (child instanceof PsiComment) return false;
      if (!(child instanceof PsiWhiteSpace) && !(child instanceof PsiJavaToken)) return false;
    }
    return true;
  }

  private static PsiElement findCodeBlock(PsiIfStatement ifStatement) {
    PsiElement e = PsiTreeUtil.getParentOfType(ifStatement, PsiMethod.class, PsiClassInitializer.class);
    return e instanceof PsiMethod method ? method.getBody()
      : e instanceof PsiClassInitializer classInitializer ? classInitializer.getBody() : null;
  }

  private static PsiElement findNearestCodeBlock(PsiIfStatement ifStatement) {
    return PsiTreeUtil.getParentOfType(ifStatement, PsiCodeBlock.class);
  }

  private static ControlFlow buildControlFlow(PsiElement element) {
    try {
      //return new ControlFlowAnalyzer(element, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance(), false, false).buildControlFlow();
      return ControlFlowFactory.getInstance(element.getProject()).getControlFlow(element, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance(), false);
    } catch (AnalysisCanceledException e) {
      return ControlFlow.EMPTY;
    }
  }

  @RequiredReadAction
  private static void setupBranches(PsiIfStatement ifStatement, ControlFlow flow) throws IncorrectOperationException {
    PsiElementFactory factory = JavaPsiFacade.getInstance(ifStatement.getProject()).getElementFactory();
    Project project = ifStatement.getProject();

    PsiStatement thenBranch = ifStatement.getThenBranch();
    PsiStatement elseBranch = ifStatement.getElseBranch();

    if (elseBranch != null) {
      elseBranch = (PsiStatement) elseBranch.copy();
      setElseBranch(ifStatement, thenBranch, flow);
      ifStatement.getThenBranch().replace(elseBranch);
      return;
    }

    final CodeStyleManager codeStyle = CodeStyleManager.getInstance(project);
    if (flow.getSize() == 0) {
      ifStatement.setElseBranch(thenBranch);
      PsiStatement statement = factory.createStatementFromText("{}", null);
      statement = (PsiStatement) codeStyle.reformat(statement);
      statement = (PsiStatement) ifStatement.getThenBranch().replace(statement);
      codeStyle.reformat(statement);
      return;
    }

    int endOffset = calcEndOffset(flow, ifStatement);

    LOG.assertTrue(endOffset >= 0);

    if (endOffset >= flow.getSize()) {
      PsiStatement statement = factory.createStatementFromText("return;", null);
      statement = (PsiStatement) codeStyle.reformat(statement);
      if (thenBranch instanceof PsiBlockStatement blockStatement) {
        PsiStatement[] statements = blockStatement.getCodeBlock().getStatements();
        int len = statements.length;
        if (len > 0) {
          //if (statements[len - 1] instanceof PsiReturnStatement) len--;
          if (len > 0) {
            PsiElement firstElement = statements[0];
            while (firstElement.getPrevSibling() instanceof PsiWhiteSpace || firstElement.getPrevSibling() instanceof PsiComment) {
              firstElement = firstElement.getPrevSibling();
            }
            ifStatement.getParent().addRangeAfter(firstElement, statements[len - 1], ifStatement);
          }
        }
      } else {
        if (!(thenBranch instanceof PsiReturnStatement)) {
          addAfter(ifStatement, thenBranch);
        }
      }
      ifStatement.getThenBranch().replace(statement);
      return;
    }
    PsiElement element = flow.getElement(endOffset);
    while (element != null && !(element instanceof PsiStatement)) element = element.getParent();

    if (element != null && element.getParent() instanceof PsiForStatement forStatement) {
      if (forStatement.getUpdate() == element) {
        PsiStatement statement = factory.createStatementFromText("continue;", null);
        statement = (PsiStatement) codeStyle.reformat(statement);
        addAfter(ifStatement, thenBranch);
        ifStatement.getThenBranch().replace(statement);
        return;
      }
    }
    if (element instanceof PsiWhileStatement && flow.getStartOffset(element) == endOffset ||
        element instanceof PsiForeachStatement && flow.getStartOffset(element) + 1 == endOffset // Foreach doesn't loop on it's first instruction
      // but rather on second. It only accesses collection initially.
    ) {
      PsiStatement statement = factory.createStatementFromText("continue;", null);
      statement = (PsiStatement) codeStyle.reformat(statement);
      addAfter(ifStatement, thenBranch);
      ifStatement.getThenBranch().replace(statement);
      return;
    }

    if (element instanceof PsiReturnStatement returnStatement) {
      addAfter(ifStatement, thenBranch);
      ifStatement.getThenBranch().replace(returnStatement.copy());

      ControlFlow flow2 = buildControlFlow(findCodeBlock(ifStatement));
      if (!ControlFlowUtil.isInstructionReachable(flow2, flow2.getStartOffset(returnStatement), 0))
        returnStatement.delete();
      return;
    }

    boolean nextUnreachable = flow.getEndOffset(ifStatement) == flow.getSize();
    if (!nextUnreachable) {
      PsiElement nearestCodeBlock = findNearestCodeBlock(ifStatement);
      if (nearestCodeBlock != null) {
        ControlFlow flow2 = buildControlFlow(nearestCodeBlock);
        nextUnreachable = !ControlFlowUtil.isInstructionReachable(flow2, flow2.getEndOffset(ifStatement), getThenOffset(flow2, ifStatement));
      }
    }
    if (nextUnreachable) {
      setElseBranch(ifStatement, thenBranch, flow);

      PsiElement first = ifStatement.getNextSibling();
//      while (first instanceof PsiWhiteSpace) first = first.getNextSibling();
      if (first != null) {
        PsiElement last = first;
        PsiElement next = last.getNextSibling();
        while (next != null && !(next instanceof PsiSwitchLabelStatement)) {
          last = next;
          next = next.getNextSibling();
        }
        while (first != last && (last instanceof PsiWhiteSpace
          || last instanceof PsiJavaToken javaToken && javaToken.getTokenType() == JavaTokenType.RBRACE)) {
          last = last.getPrevSibling();
        }

        PsiBlockStatement codeBlock = (PsiBlockStatement) factory.createStatementFromText("{}", null);
        codeBlock.getCodeBlock().addRange(first, last);
        first.getParent().deleteChildRange(first, last);
        ifStatement.getThenBranch().replace(codeBlock);
      }
      codeStyle.reformat(ifStatement);
      return;
    }

    setElseBranch(ifStatement, thenBranch, flow);
    PsiStatement statement = factory.createStatementFromText("{}", null);
    statement = (PsiStatement) codeStyle.reformat(statement);
    statement = (PsiStatement) ifStatement.getThenBranch().replace(statement);
    codeStyle.reformat(statement);
  }

  private static void setElseBranch(PsiIfStatement ifStatement, PsiStatement thenBranch, ControlFlow flow)
      throws IncorrectOperationException {
    if (flow.getEndOffset(ifStatement) == flow.getEndOffset(thenBranch)) {
      final PsiLoopStatement loopStmt = PsiTreeUtil.getParentOfType(ifStatement, PsiLoopStatement.class);
      if (loopStmt != null) {
        final PsiStatement body = loopStmt.getBody();
        if (body instanceof PsiBlockStatement blockStatement) {
          final PsiStatement[] statements = blockStatement.getCodeBlock().getStatements();
          if (statements.length > 0 && !PsiTreeUtil.isAncestor(statements[statements.length - 1], ifStatement, false) &&
              ArrayUtilRt.find(statements, ifStatement) < 0) {
            ifStatement.setElseBranch(thenBranch);
            return;
          }
        }
      }
      if (thenBranch instanceof PsiContinueStatement) {
        PsiStatement elseBranch = ifStatement.getElseBranch();
        if (elseBranch != null) {
          elseBranch.delete();
        }
        return;
      } else if (thenBranch instanceof PsiBlockStatement blockStatement) {
        PsiStatement[] statements = blockStatement.getCodeBlock().getStatements();
        if (statements.length > 0 && statements[statements.length - 1] instanceof PsiContinueStatement) {
          statements[statements.length - 1].delete();
        }
      }
    }
    ifStatement.setElseBranch(thenBranch);
  }

  private static void addAfter(PsiIfStatement ifStatement, PsiStatement thenBranch) throws IncorrectOperationException {
    if (thenBranch instanceof PsiBlockStatement blockStatement) {
      PsiStatement[] statements = blockStatement.getCodeBlock().getStatements();
      if (statements.length > 0) {
        ifStatement.getParent().addRangeAfter(statements[0], statements[statements.length - 1], ifStatement);
      }
    } else {
      ifStatement.getParent().addAfter(thenBranch, ifStatement);
    }
  }

  private static int getThenOffset(ControlFlow controlFlow, PsiIfStatement ifStatement) {
    PsiStatement thenBranch = ifStatement.getThenBranch();

    for (int i = 0; i < controlFlow.getSize(); i++) {
      if (PsiTreeUtil.isAncestor(thenBranch, controlFlow.getElement(i), false)) return i;
    }
    return -1;
  }

  private static int calcEndOffset(ControlFlow controlFlow, PsiIfStatement ifStatement) {
    int endOffset = -1;

    List<Instruction> instructions = controlFlow.getInstructions();
    for (int i = 0; i < instructions.size(); i++) {
      Instruction instruction = instructions.get(i);
      if (controlFlow.getElement(i) != ifStatement) continue;

      if (instruction instanceof GoToInstruction goToInstruction) {
        if (goToInstruction.role != BranchingInstruction.Role.END) continue;

        endOffset = goToInstruction.offset;
        break;
      } else if (instruction instanceof ConditionalGoToInstruction goToInstruction) {
        if (goToInstruction.role != BranchingInstruction.Role.END) continue;

        endOffset = goToInstruction.offset;
        break;
      }
    }
    if (endOffset == -1) {
      endOffset = controlFlow.getSize();
    }
    while (endOffset < instructions.size() && instructions.get(endOffset) instanceof GoToInstruction goTo && !goTo.isReturn) {
      endOffset = goTo.offset;
    }

    return endOffset;
  }
}
