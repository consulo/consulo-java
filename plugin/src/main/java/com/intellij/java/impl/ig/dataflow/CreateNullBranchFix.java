// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.ig.dataflow;

import consulo.localize.LocalizeValue;
import com.intellij.java.language.impl.psi.util.JavaPsiSwitchUtil;
import com.intellij.java.language.JavaFeature;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiCodeBlock;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiStatement;
import com.intellij.java.language.psi.PsiSwitchBlock;
import com.intellij.java.language.psi.PsiSwitchLabelStatementBase;
import com.intellij.java.language.psi.PsiType;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.intellij.java.impl.ig.fixes.BaseSwitchFix;
import com.intellij.java.impl.ig.fixes.CreateDefaultBranchFix;
import com.intellij.java.analysis.impl.codeInspection.ControlFlowUtils;
import com.intellij.java.impl.ig.psiutils.CreateSwitchBranchesUtil;
import com.intellij.java.analysis.impl.codeInspection.SwitchUtils;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CreateNullBranchFix extends BaseSwitchFix {

  private final boolean myStartTemplate;

  public CreateNullBranchFix(PsiSwitchBlock block) {
    this(block, true);
  }

  public CreateNullBranchFix(PsiSwitchBlock block, boolean startTemplate) {
    super(block);
    myStartTemplate = startTemplate;
  }

  @Override
  public LocalizeValue getName() {
    return LocalizeValue.of(InspectionGadgetsBundle.message("create.null.branch.fix.family.name"));
  }

  @Override
  public LocalizeValue getText() {
    return getName();
  }

  @Override
  protected void invoke() {
    PsiSwitchBlock switchBlock = myBlock.getElement();
    if (switchBlock == null) return;
    if (!PsiUtil.isAvailable(JavaFeature.PATTERNS_IN_SWITCH, switchBlock)) return;
    PsiCodeBlock body = switchBlock.getBody();
    if (body == null) return;
    PsiExpression selector = switchBlock.getExpression();
    if (selector == null) return;
    PsiType selectorType = selector.getType();
    if (selectorType == null) return;
    List<PsiElement> branches = JavaPsiSwitchUtil.getSwitchBranches(switchBlock);
    for (PsiElement branch : branches) {
      // just for the case if we already contain null, there is no need to apply the fix
      if (branch instanceof PsiExpression expression && TypeConversionUtil.isNullType(expression.getType())) return;
    }
    PsiElement unconditionalLabel = findUnconditionalLabel(switchBlock);
    PsiElement bodyElement = body.getFirstBodyElement();
    if (bodyElement instanceof PsiWhiteSpace && bodyElement == body.getLastBodyElement()) {
      bodyElement.delete();
    }
    PsiElement anchor = unconditionalLabel != null ? unconditionalLabel : body.getRBrace();
    if (anchor == null) return;
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(anchor.getProject());
    generateStatements(switchBlock, SwitchUtils.isRuleFormatSwitch(switchBlock), unconditionalLabel)
      .stream()
      .map(text -> factory.createStatementFromText(text, body))
      .forEach(statement -> body.addBefore(statement, anchor));
    // JetBrains starts a template on the new statement when myStartTemplate is set (ModPsiUpdater); a quick fix has no updater for it
  }

  private static @Nullable PsiElement findUnconditionalLabel(PsiSwitchBlock switchBlock) {
    PsiCodeBlock body = switchBlock.getBody();
    if (body == null) return null;
    return ContainerUtil.find(body.getStatements(),
                              stmt -> stmt instanceof PsiSwitchLabelStatementBase label && SwitchUtils.isUnconditionalLabel(label));
  }

  private static List<String> generateStatements(PsiSwitchBlock switchBlock, boolean isRuleBasedFormat,
                                                         @Nullable PsiElement defaultElement) {
    PsiStatement previousStatement;
    if (defaultElement == null) {
      previousStatement = ArrayUtil.getLastElement(Objects.requireNonNull(switchBlock.getBody()).getStatements());
    }
    else {
      previousStatement = PsiTreeUtil.getPrevSiblingOfType(defaultElement, PsiStatement.class);
    }
    List<String> result = new ArrayList<>();
    if (!isRuleBasedFormat && previousStatement != null && ControlFlowUtils.statementMayCompleteNormally(previousStatement)) {
      result.add("break;");
    }
    result.addAll(CreateSwitchBranchesUtil.generateStatements("null", switchBlock, isRuleBasedFormat, false));
    return result;
  }
}
