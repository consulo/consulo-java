// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.codeInspection;

import consulo.localize.LocalizeValue;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.java.analysis.impl.localize.JavaInspectionsLocalize;
import consulo.annotation.component.ExtensionImpl;
import com.intellij.java.analysis.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.java.analysis.codeInspection.AbstractBaseJavaLocalInspectionTool;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.inspection.UpdateInspectionOptionFix;

import consulo.project.Project;
import consulo.util.lang.ref.Ref;
import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.java.language.psi.PsiBreakStatement;
import com.intellij.java.language.psi.PsiConditionalLoopStatement;
import com.intellij.java.language.psi.PsiContinueStatement;
import com.intellij.java.language.psi.PsiDoWhileStatement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import com.intellij.java.language.psi.PsiEmptyStatement;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiForStatement;
import com.intellij.java.language.psi.PsiIfStatement;
import com.intellij.java.language.psi.PsiLoopStatement;
import com.intellij.java.language.psi.PsiStatement;
import com.intellij.java.language.psi.PsiVariable;
import com.intellij.java.language.psi.PsiWhileStatement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.collection.ContainerUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.intellij.java.analysis.impl.codeInspection.ControlFlowUtils;
import com.intellij.java.analysis.codeInspection.ParenthesesUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import one.util.streamex.StreamEx;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import static consulo.util.lang.ObjectUtil.tryCast;

@ExtensionImpl
public final class ConditionalBreakInInfiniteLoopInspection extends AbstractBaseJavaLocalInspectionTool<ConditionalBreakInInfiniteLoopInspectionState> {
  @Override
  public String getShortName() {
    return "ConditionalBreakInInfiniteLoop";
  }

  @Override
  public LocalizeValue getDisplayName() {
    return JavaInspectionsLocalize.inspectionConditionalBreakInInfiniteLoopDescription();
  }

  @Override
  public LocalizeValue getGroupDisplayName() {
    return InspectionLocalize.groupNamesControlFlowIssues();
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.WARNING;
  }

  @Override
  public InspectionToolState<ConditionalBreakInInfiniteLoopInspectionState> createStateProvider() {
    return new ConditionalBreakInInfiniteLoopInspectionState();
  }

  @Override
  public PsiElementVisitor buildVisitorImpl(ProblemsHolder holder,
                                            boolean isOnTheFly,
                                            LocalInspectionToolSession session,
                                            ConditionalBreakInInfiniteLoopInspectionState state) {
    boolean noConversionToDoWhile = state.noConversionToDoWhile;
    boolean allowConditionFusion = state.allowConditionFusion;
    boolean suggestConversionWhenIfIsASingleStmtInLoop = state.suggestConversionWhenIfIsASingleStmtInLoop;
    return new JavaElementVisitor() {
      @Override
      public void visitForStatement(PsiForStatement statement) {
        visitLoop(statement, statement.getFirstChild());
      }

      @Override
      public void visitWhileStatement(PsiWhileStatement statement) {
        visitLoop(statement, statement.getFirstChild());
      }

      @Override
      public void visitDoWhileStatement(PsiDoWhileStatement statement) {
        visitLoop(statement, statement.getWhileKeyword());
      }

      private void visitLoop(PsiConditionalLoopStatement loopStatement, @Nullable PsiElement keyword) {
        if (keyword == null) return;
        Context context = Context.from(loopStatement, noConversionToDoWhile, suggestConversionWhenIfIsASingleStmtInLoop);
        if (context == null) return;
        // JetBrains also offers to set the "noConversionToDoWhile" option from the problem (UpdateInspectionOptionFix)
        LocalQuickFix[] fixes = new LocalQuickFix[]{new LoopTransformationFix(noConversionToDoWhile)};
        ProblemHighlightType highlightType;
        if (!allowConditionFusion && !context.isInfiniteLoop) {
          if (holder.isOnTheFly()) {
            highlightType = ProblemHighlightType.INFORMATION;
          } else {
            return;
          }
        } else {
          highlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
        }
        holder.registerProblem(keyword, JavaInspectionsLocalize.inspectionConditionalBreakInInfiniteLoopDescription().get(), highlightType, fixes);
      }
    };
  }

  public static void tryTransform(PsiWhileStatement whileStatement) {
    Context context = Context.from(whileStatement, true, true);
    if (context != null) {
      context.simplify(whileStatement);
    }
  }

  private record Context(PsiLoopStatement loopStatement,
                         PsiStatement loopBody,
                         PsiExpression condition,
                         PsiIfStatement statement,
                         boolean conditionInTheBeginning,
                         boolean conditionInThen,
                         boolean isInfiniteLoop) {
    private static @Nullable Context from(PsiConditionalLoopStatement loopStatement,
                                          boolean noConversionToDoWhile,
                                          boolean suggestConversionWhenIfIsASingleStmtInLoop) {
      boolean isEndlessLoop = ControlFlowUtils.isEndlessLoop(loopStatement);
      if (!isEndlessLoop) {
        if (loopStatement instanceof PsiForStatement forStatement) {
          if ((forStatement.getInitialization() != null && !(forStatement.getInitialization() instanceof PsiEmptyStatement))
              || (forStatement.getUpdate() != null && !(forStatement.getUpdate() instanceof PsiEmptyStatement))) {
            return null;
          }
        }
      }
      PsiStatement body = loopStatement.getBody();
      if (body == null) return null;
      PsiStatement[] statements = ControlFlowUtils.unwrapBlock(body);
      if (statements.length < 1) return null;
      if (statements.length == 1 && !suggestConversionWhenIfIsASingleStmtInLoop) return null;
      if (StreamEx.ofTree((PsiElement)body, el -> StreamEx.of(el.getChildren()))
            .select(PsiBreakStatement.class)
            .filter(stmt -> ControlFlowUtils.statementBreaksLoop(stmt, loopStatement))
            .count() != 1) {
        return null;
      }
      PsiIfStatement first = tryCast(statements[0], PsiIfStatement.class);
      Ref<Boolean> isBreakInThen = new Ref<>(false);
      PsiExpression firstBreakCondition = extractBreakCondition(first, loopStatement, isBreakInThen);
      PsiExpression loopCondition = loopStatement.getCondition();
      boolean isLoopConditionAtStart = !(loopStatement instanceof PsiDoWhileStatement);
      if (first != null
          && firstBreakCondition != null
          && (isEndlessLoop ||
           (isLoopConditionAtStart &&
            (BoolUtils.getLogicalOperandCount(loopCondition) + BoolUtils.getLogicalOperandCount(firstBreakCondition)) < 4))) {
        return new Context(loopStatement, body, firstBreakCondition, first, true, isBreakInThen.get(), isEndlessLoop);
      }
      if (noConversionToDoWhile) return null;
      PsiIfStatement last = tryCast(statements[statements.length - 1], PsiIfStatement.class);
      if (last != null && last.getElseBranch() != null) return null;
      PsiExpression lastBreakCondition = extractBreakCondition(last, loopStatement, isBreakInThen);
      if (lastBreakCondition == null || !isBreakInThen.get()) return null;
      if (!isEndlessLoop &&
          (isLoopConditionAtStart ||
           (3 < BoolUtils.getLogicalOperandCount(loopCondition) + BoolUtils.getLogicalOperandCount(lastBreakCondition)))) {
        return null;
      }
      if (StreamEx.of(statements)
        .flatMap(statement -> StreamEx.ofTree((PsiElement)statement, el -> StreamEx.of(el.getChildren())))
        .anyMatch(e -> e instanceof PsiContinueStatement &&
                       ((PsiContinueStatement)e).findContinuedStatement() == loopStatement)) {
        return null;
      }
      boolean variablesInLoop =
        ContainerUtil.exists(VariableAccessUtils.collectUsedVariables(lastBreakCondition),
                             variable -> PsiTreeUtil.isAncestor(loopStatement, variable, false));
      if (variablesInLoop) return null;
      return new Context(loopStatement, body, lastBreakCondition, last, false, isBreakInThen.get(), isEndlessLoop);
    }

    private static @Nullable PsiExpression extractBreakCondition(@Nullable PsiIfStatement ifStatement,
                                                                 PsiLoopStatement loopStatement,
                                                                 Ref<? super Boolean> isBreakInThen) {
      if (ifStatement == null) return null;
      if (ControlFlowUtils.statementBreaksLoop(ControlFlowUtils.stripBraces(ifStatement.getThenBranch()), loopStatement)) {
        if (hasVariableNameConflict(loopStatement, ifStatement, ifStatement.getElseBranch())) return null;
        isBreakInThen.set(true);
        return ifStatement.getCondition();
      }
      if (ifStatement.getElseBranch() != null &&
          ControlFlowUtils.statementBreaksLoop(ControlFlowUtils.stripBraces(ifStatement.getElseBranch()), loopStatement)) {
        if (hasVariableNameConflict(loopStatement, ifStatement, ifStatement.getThenBranch())) return null;
        isBreakInThen.set(false);
        return ifStatement.getCondition();
      }
      return null;
    }

    private static boolean hasVariableNameConflict(PsiLoopStatement loopStatement,
                                                   PsiIfStatement ifStatement,
                                                   @Nullable PsiStatement branch) {
      if (branch == null) return false;
      Set<String> variablesCreatedInBranch = VariableAccessUtils.findDeclaredVariables(branch).stream()
        .map(PsiVariable::getName)
        .collect(Collectors.toSet());
      Set<String> otherVariablesUsedInLoop = VariableAccessUtils.collectUsedVariables(loopStatement.getBody()).stream()
        .filter(variable -> !PsiTreeUtil.isAncestor(ifStatement, variable, false))
        .map(PsiVariable::getName)
        .collect(Collectors.toSet());
      return !Collections.disjoint(variablesCreatedInBranch, otherVariablesUsedInLoop);
    }

    private void simplify(PsiConditionalLoopStatement loop) {
      CommentTracker ct = new CommentTracker();
      String loopText;
      if (ControlFlowUtils.isEndlessLoop(loop)) {
        String conditionForWhile = this.conditionInThen ? BoolUtils.getNegatedExpressionText(this.condition, ct) : ct.text(
          this.condition);
        LoopTransformationFix.pullDownStatements(this.statement,
                                                 this.conditionInThen ? this.statement.getElseBranch() : this.statement.getThenBranch());
        ct.delete(this.statement);
        loopText = this.conditionInTheBeginning
                   ? "while(" + conditionForWhile + ")" + ct.text(this.loopBody)
                   : "do" + ct.text(this.loopBody) + "while(" + conditionForWhile + ");";
      }
      else {
        String conditionForWhile = this.conditionInThen
                                   ? BoolUtils.getNegatedExpressionText(this.condition, ParenthesesUtils.AND_PRECEDENCE, ct) : ct.text(
          this.condition, ParenthesesUtils.AND_PRECEDENCE);
        ct.delete(this.statement);
        PsiExpression loopCondition = loop.getCondition();
        assert loopCondition != null;
        loopText = this.conditionInTheBeginning
                   ? "while(" + ct.text(loopCondition, ParenthesesUtils.AND_PRECEDENCE) + " && " + conditionForWhile + ")" + ct.text(
          this.loopBody)
                   : "do" + ct.text(this.loopBody) + "while(" + conditionForWhile + " && " + ct.text(loopCondition, ParenthesesUtils.AND_PRECEDENCE) + ");";
      }
      ct.replaceAndRestoreComments(this.loopStatement, loopText);
    }
  }

  private static class LoopTransformationFix implements LocalQuickFix {
    private final boolean noConversionToDoWhile;

    private LoopTransformationFix(boolean noConversionToDoWhile) {
      this.noConversionToDoWhile = noConversionToDoWhile;
    }

    @Override
    public LocalizeValue getName() {
      return JavaInspectionsLocalize.inspectionConditionalBreakInInfiniteLoop();
    }

    @Override
    public void applyFix(Project project, ProblemDescriptor descriptor) {
      PsiConditionalLoopStatement loop = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiConditionalLoopStatement.class);
      if (loop == null) return;
      Context context = Context.from(loop, noConversionToDoWhile, false);
      if (context == null) return;
      context.simplify(loop);
    }

    private static void pullDownStatements(PsiIfStatement conditionStatement, @Nullable PsiStatement branch) {
      if (branch != null) {
        PsiElement parent = conditionStatement.getParent();
        PsiStatement[] branchStatements = ControlFlowUtils.unwrapBlock(branch);
        for (int statementIndex = branchStatements.length - 1; 0 <= statementIndex; statementIndex--) {
          PsiStatement statement = branchStatements[statementIndex];
          parent.addAfter(statement, conditionStatement);
        }
      }
    }
  }
}
