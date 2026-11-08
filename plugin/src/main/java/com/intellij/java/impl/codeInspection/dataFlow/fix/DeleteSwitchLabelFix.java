// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.codeInspection.dataFlow.fix;

import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import consulo.language.editor.inspection.LocalQuickFixOnPsiElement;
import com.intellij.java.language.psi.util.ExpressionUtil;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.java.language.impl.psi.util.JavaPsiSwitchUtil;
import com.intellij.java.language.psi.PsiKeyword;
import consulo.ui.ex.action.Presentation;
import consulo.project.Project;
import com.intellij.java.language.psi.PsiCaseLabelElement;
import com.intellij.java.language.psi.PsiCaseLabelElementList;
import com.intellij.java.language.psi.PsiCodeBlock;
import com.intellij.java.language.psi.PsiDeclarationStatement;
import com.intellij.java.language.psi.PsiDefaultCaseLabelElement;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiStatement;
import com.intellij.java.language.psi.PsiSwitchBlock;
import com.intellij.java.language.psi.PsiSwitchLabelStatement;
import com.intellij.java.language.psi.PsiSwitchLabelStatementBase;
import com.intellij.java.language.psi.PsiSwitchLabeledRuleStatement;
import com.intellij.java.language.psi.PsiVariable;
import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.lang.ObjectUtil;
import consulo.util.collection.ContainerUtil;
import com.intellij.java.impl.ig.fixes.CreateDefaultBranchFix;
import com.siyeh.ig.psiutils.CommentTracker;
import com.intellij.java.analysis.impl.codeInspection.ControlFlowUtils;
import one.util.streamex.StreamEx;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.intellij.java.language.impl.psi.util.JavaPatternExhaustivenessUtil.hasExhaustivenessError;

public class DeleteSwitchLabelFix extends LocalQuickFixOnPsiElement {
  private final String myName;
  private final boolean myAddDefaultIfNecessary;
  private final boolean myBranch;

  public DeleteSwitchLabelFix(PsiCaseLabelElement label, boolean addDefaultIfNecessary) {
    super(label);
    myAddDefaultIfNecessary = addDefaultIfNecessary;
    PsiSwitchLabelStatementBase labelStatement = Objects.requireNonNull(PsiImplUtil.getSwitchLabel(label));
    PsiCaseLabelElementList labelElementList = Objects.requireNonNull(labelStatement.getCaseLabelElementList());
    boolean multiple = labelElementList.getElementCount() > 1;
    myBranch = !multiple && shouldRemoveBranch(labelStatement);
    PsiExpression guardExpression = labelStatement.getGuardExpression();
    myName = myBranch && guardExpression != null ? labelStatement.getText()
      .substring(labelElementList.getStartOffsetInParent(), guardExpression.getStartOffsetInParent() +
                                                            guardExpression.getTextLength()) : label.getText();
  }

  private static boolean shouldRemoveBranch(PsiSwitchLabelStatementBase label) {
    if (label instanceof PsiSwitchLabeledRuleStatement) return true;
    PsiStatement nextStatement = PsiTreeUtil.getNextSiblingOfType(label, PsiStatement.class);
    if (nextStatement instanceof PsiSwitchLabelStatement) {
      return false;
    }
    PsiStatement prevStatement = PsiTreeUtil.getPrevSiblingOfType(label, PsiStatement.class);
    return prevStatement == null || !ControlFlowUtils.statementMayCompleteNormally(prevStatement);
  }

  @Override
  public LocalizeValue getText() {
    return LocalizeValue.of(myBranch ?
                            JavaAnalysisBundle.message("remove.switch.branch.0", myName) :
                            JavaAnalysisBundle.message("remove.switch.label.0", myName));
  }

  @Override
  public void invoke(Project project, PsiFile file, PsiElement startElement, PsiElement endElement) {
    if (!(startElement instanceof PsiCaseLabelElement labelElement)) return;
    PsiSwitchLabelStatementBase label = PsiImplUtil.getSwitchLabel(labelElement);
    if (label == null) return;
    PsiSwitchBlock block = label.getEnclosingSwitchBlock();
    if (block == null) return;
    deleteLabelElement(project, labelElement);
    if (myAddDefaultIfNecessary && shouldAddDefault(block)) {
      CreateDefaultBranchFix.addDefault(block);
    }
  }

  public static void deleteLabelElement(Project project, PsiCaseLabelElement labelElement) {
    PsiSwitchLabelStatementBase label = PsiImplUtil.getSwitchLabel(labelElement);
    if (label == null) return;
    PsiCaseLabelElementList labelElementList = label.getCaseLabelElementList();
    if (labelElementList != null) {
      if (labelElementList.getElementCount() == 1) {
        deleteLabel(label);
        return;
      }
      else if (labelElementList.getElementCount() == 2) {
        PsiElement defaultElement = JavaPsiSwitchUtil.findDefaultElement(label);
        if (defaultElement != null && defaultElement != labelElement) {
          assert PsiKeyword.CASE.equals(label.getFirstChild().getText()) && defaultElement instanceof PsiDefaultCaseLabelElement;
          makeLabelDefault(label, project);
          return;
        }
      }
    }
    new CommentTracker().deleteAndRestoreComments(labelElement);
  }

  private static void makeLabelDefault(PsiSwitchLabelStatementBase label, Project project) {
    PsiElementFactory factory = PsiElementFactory.getInstance(project);
    if (label instanceof PsiSwitchLabelStatement) {
      PsiSwitchLabelStatementBase defaultLabel = (PsiSwitchLabelStatement)factory.createStatementFromText("default:", null);
      new CommentTracker().replaceAndRestoreComments(label, defaultLabel);
    }
    else if (label instanceof PsiSwitchLabeledRuleStatement rule) {
      PsiSwitchLabeledRuleStatement defaultLabel = (PsiSwitchLabeledRuleStatement)factory.createStatementFromText("default->{}", null);
      PsiStatement body = rule.getBody();
      assert body != null;
      Objects.requireNonNull(defaultLabel.getBody()).replace(body);
      new CommentTracker().replaceAndRestoreComments(label, defaultLabel);
    }
    else {
      assert false;
    }
  }

  public static void deleteLabel(PsiSwitchLabelStatementBase label) {
    if (shouldRemoveBranch(label)) {
      PsiCodeBlock scope = ObjectUtil.tryCast(label.getParent(), PsiCodeBlock.class);
      if (scope == null) return;
      PsiSwitchLabelStatementBase nextLabel = PsiTreeUtil.getNextSiblingOfType(label, PsiSwitchLabelStatementBase.class);
      PsiElement stopAt = nextLabel == null ? scope.getRBrace() : nextLabel;
      while(true) {
        PsiStatement next = PsiTreeUtil.getNextSiblingOfType(nextLabel, PsiStatement.class);
        if (!(next instanceof PsiSwitchLabelStatement)) break;
        nextLabel = (PsiSwitchLabelStatement)next;
      }
      int end = nextLabel == null ? -1 : nextLabel.getTextOffset();
      List<PsiElement> toDelete = new ArrayList<>();
      List<PsiDeclarationStatement> declarations = new ArrayList<>();
      for (PsiElement e = label.getNextSibling(); e != stopAt; e = e.getNextSibling()) {
        if (e instanceof PsiDeclarationStatement declaration && nextLabel != null) {
          PsiElement[] elements = declaration.getDeclaredElements();
          boolean declarationIsReused = ContainerUtil.or(elements, element ->
            ReferencesSearch.search(element, new LocalSearchScope(scope)).anyMatch(ref -> ref.getElement().getTextOffset() > end));
          if (declarationIsReused) {
            StreamEx.of(elements).select(PsiVariable.class).map(PsiVariable::getInitializer).nonNull().into(toDelete);
            declarations.add(declaration);
            continue;
          }
        }
        toDelete.add(e);
      }
      CommentTracker ct = new CommentTracker();
      toDelete.stream().filter(PsiElement::isValid).forEach(ct::delete);
      for (PsiDeclarationStatement declaration : declarations) {
        scope.addAfter(declaration, nextLabel);
        declaration.delete();
      }
      ct.insertCommentsBefore(label);
    }
    new CommentTracker().deleteAndRestoreComments(label);
  }

  public static boolean shouldAddDefault(PsiSwitchBlock block) {
    if (!ExpressionUtil.isEnhancedSwitch(block)) return false;
    if (JavaPsiSwitchUtil.getUnconditionalPatternLabel(block) != null) return false;
    if (JavaPsiSwitchUtil.findDefaultElement(block) != null) return false;
    return hasExhaustivenessError(block);
  }
}
