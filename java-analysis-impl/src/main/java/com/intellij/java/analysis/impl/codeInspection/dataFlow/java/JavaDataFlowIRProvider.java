// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.java;

import com.intellij.java.language.JavaLanguage;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.dataFlow.ir.ControlFlow;
import consulo.language.dataFlow.ir.DataFlowIRProvider;
import consulo.language.dataFlow.value.DfaValueFactory;
import consulo.document.util.TextRange;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiCatchSection;
import com.intellij.java.language.psi.PsiClassInitializer;
import com.intellij.java.language.psi.PsiCodeBlock;
import com.intellij.java.language.psi.PsiConditionalExpression;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiIfStatement;
import com.intellij.java.language.psi.PsiJavaToken;
import com.intellij.java.language.psi.PsiKeyword;
import com.intellij.java.language.psi.PsiLoopStatement;
import com.intellij.java.language.psi.PsiPolyadicExpression;
import com.intellij.java.language.psi.PsiStatement;
import com.intellij.java.language.psi.PsiSwitchBlock;
import com.intellij.java.language.psi.PsiSwitchLabelStatement;
import com.intellij.java.language.psi.PsiSwitchLabeledRuleStatement;
import com.intellij.java.language.psi.PsiTryStatement;
import consulo.language.ast.IElementType;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.lang.ObjectUtil;
import consulo.util.collection.ContainerUtil;
import com.intellij.java.analysis.impl.codeInspection.ControlFlowUtils;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@ExtensionImpl
public final class JavaDataFlowIRProvider implements DataFlowIRProvider {
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }

  @Override
  public @Nullable ControlFlow createControlFlow(DfaValueFactory factory, PsiElement psiBlock) {
    return new ControlFlowAnalyzer(factory, psiBlock, true).buildControlFlow();
  }

  @Override
  public Collection<TextRange> unreachableSegments(PsiElement startAnchor, Set<PsiElement> unreachable) {
    Set<TextRange> result = new HashSet<>();
    for (PsiElement element : unreachable) {
      ContainerUtil.addIfNotNull(result, createRange(startAnchor, element, unreachable));
    }
    return result;
  }

  private static @Nullable TextRange createRange(PsiElement startAnchor,
                                                 PsiElement unreachable,
                                                 Set<PsiElement> allUnreachable) {
    PsiElement parent = unreachable.getParent();
    if (unreachable instanceof PsiExpression expression) {
      if (parent instanceof PsiConditionalExpression && !allUnreachable.contains(parent)) {
        return expression.getTextRange();
      }
      if (parent instanceof PsiPolyadicExpression polyadicExpression) {
        IElementType tokenType = polyadicExpression.getOperationTokenType();
        if (tokenType == JavaTokenType.ANDAND || tokenType == JavaTokenType.OROR) {
          PsiExpression prev = PsiTreeUtil.getPrevSiblingOfType(expression, PsiExpression.class);
          if (prev != null && !allUnreachable.contains(prev)) {
            PsiJavaToken token = polyadicExpression.getTokenBeforeOperand(expression);
            if (token != null) {
              return TextRange.create(token.getTextRange().getStartOffset(), parent.getTextRange().getEndOffset());
            }
          }
        }
      }
    }
    if (unreachable instanceof PsiCodeBlock) {
      if (unreachable.getParent() instanceof PsiCatchSection catchSection && 
          catchSection.getParent() instanceof PsiTryStatement tryStatement &&
          !allUnreachable.contains(tryStatement)) {
        return unreachable.getTextRange();
      }
      if (unreachable.getParent() instanceof PsiClassInitializer initializer &&
          !allUnreachable.contains(initializer)) {
        return unreachable.getTextRange();
      }
    }
    if (unreachable instanceof PsiStatement statement) {
      if (ControlFlowUtils.isEmpty(unreachable, false, true)) return null;
      PsiElement statementParent = statement.getParent();
      if (unreachable instanceof PsiSwitchLabelStatement) return null;
      if (allUnreachable.contains(statementParent)) return null;
      if (parent instanceof PsiStatement) {
        if (parent instanceof PsiIfStatement ifStatement && ifStatement.getElseBranch() == unreachable) {
          PsiKeyword elseKeyword = ifStatement.getElseElement();
          if (elseKeyword != null) {
            return TextRange.create(elseKeyword.getTextRange().getStartOffset(), unreachable.getTextRange().getEndOffset());
          }
        }
        return statement.getTextRange();
      }
      if (parent instanceof PsiCodeBlock) {
        if (statement instanceof PsiSwitchLabeledRuleStatement ruleStatement) {
          PsiSwitchBlock block = ruleStatement.getEnclosingSwitchBlock();
          if (!allUnreachable.contains(block)) {
            PsiStatement body = ruleStatement.getBody();
            if (body != null) {
              return body.getTextRange();
            }
          }
          return null;
        }
        PsiStatement prevStatement = ObjectUtil.tryCast(PsiTreeUtil.skipWhitespacesAndCommentsBackward(statement), PsiStatement.class);
        if (prevStatement instanceof PsiSwitchLabelStatement labelStatement) {
          PsiSwitchBlock block = labelStatement.getEnclosingSwitchBlock();
          if (block != null && !allUnreachable.contains(block)) {
            PsiElement last = ((PsiCodeBlock)statementParent).getRBrace();
            PsiSwitchLabelStatement nextLabel = PsiTreeUtil.getNextSiblingOfType(statement, PsiSwitchLabelStatement.class);
            if (nextLabel != null) {
              last = nextLabel;
            }
            PsiElement lastStatement = PsiTreeUtil.skipWhitespacesAndCommentsBackward(last);
            if (lastStatement != null) {
              return TextRange.create(statement.getTextRange().getStartOffset(), lastStatement.getTextRange().getEndOffset());
            }
          }
          return null;
        }
        if (allUnreachable.contains(prevStatement)) return null;
        PsiElement lastStatement = getLastStatement(statement);
        if (prevStatement != null) {
          if (prevStatement instanceof PsiLoopStatement && PsiTreeUtil.isAncestor(prevStatement, startAnchor, false)) {
            return null;
          }
          return TextRange.create(statement.getTextRange().getStartOffset(), lastStatement.getTextRange().getEndOffset());
        }
      }
    }
    return null;
  }

  private static PsiElement getLastStatement(PsiStatement statement) {
    PsiElement lastStatement = statement;
    while (true) {
      PsiElement nextStatement = PsiTreeUtil.skipWhitespacesAndCommentsForward(lastStatement);
      if (!(nextStatement instanceof PsiStatement) || nextStatement instanceof PsiSwitchLabelStatement) break;
      lastStatement = nextStatement;
    }
    return lastStatement;
  }
}
