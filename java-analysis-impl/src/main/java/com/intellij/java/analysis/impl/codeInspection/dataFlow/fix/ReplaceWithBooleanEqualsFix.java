// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.fix;

import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiPrefixExpression;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.localize.CommonQuickFixLocalize;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.util.lang.ObjectUtil;

public class ReplaceWithBooleanEqualsFix implements LocalQuickFix {
  private final String myOldExprText;
  private final boolean myFalseIsAcceptable;

  public ReplaceWithBooleanEqualsFix(PsiExpression qualifier) {
    myOldExprText = qualifier.getText();
    PsiPrefixExpression parent = ObjectUtil.tryCast(PsiUtil.skipParenthesizedExprUp(qualifier.getParent()), PsiPrefixExpression.class);
    myFalseIsAcceptable = parent != null && parent.getOperationTokenType() == JavaTokenType.EXCL;
  }

  @Override
  public LocalizeValue getName() {
    return CommonQuickFixLocalize.fixReplaceWithX(createNewExprText());
  }

  @Override
  public void applyFix(Project project, ProblemDescriptor descriptor) {
    PsiElement qualifier = ObjectUtil.tryCast(descriptor.getPsiElement(), PsiExpression.class);
    if (qualifier == null) return;
    if (myFalseIsAcceptable) {
      qualifier = qualifier.getParent();
    }
    PsiExpression oldExpr = ObjectUtil.tryCast(PsiUtil.skipParenthesizedExprUp(qualifier), PsiExpression.class);
    if (oldExpr == null) return;
    PsiReplacementUtil.replaceExpression(oldExpr, createNewExprText(), new CommentTracker());
  }

  private String createNewExprText() {
    return "Boolean." + (myFalseIsAcceptable ? "FALSE" : "TRUE") + ".equals(" + myOldExprText + ")";
  }
}
