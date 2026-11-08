// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.fix;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import consulo.language.editor.inspection.LocalQuickFixOnPsiElement;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;

public class RedundantInstanceofFix extends LocalQuickFixOnPsiElement {
  public RedundantInstanceofFix(PsiElement element) {
    super(element);
  }

  @Override
  public LocalizeValue getText() {
    return InspectionLocalize.inspectionDataFlowRedundantInstanceofQuickfix();
  }

  @Override
  public void invoke(Project project, PsiFile file, PsiElement element, PsiElement endElement) {
    PsiElement psiElement = element;
    CommentTracker ct = new CommentTracker();
    JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(project);
    if (psiElement instanceof PsiMethodReferenceExpression) {
      String replacement = CommonClassNames.JAVA_UTIL_OBJECTS + "::nonNull";
      javaCodeStyleManager.shortenClassReferences(ct.replaceAndRestoreComments(psiElement, replacement));
      return;
    }
    String nonNullExpression = null;
    if (psiElement instanceof PsiInstanceOfExpression instanceOf) {
      nonNullExpression = ct.text(instanceOf.getOperand());
    }
    else if (psiElement instanceof PsiMethodCallExpression call) {
      PsiExpression arg = ArrayUtil.getFirstElement(call.getArgumentList().getExpressions());
      if (arg == null) return;
      nonNullExpression = ct.text(arg);
    }
    if (nonNullExpression == null) return;
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(psiElement.getParent());
    String replacement;
    if (parent instanceof PsiExpression expression && BoolUtils.isNegation(expression)) {
      replacement = nonNullExpression + "==null";
      psiElement = parent;
    } else {
      replacement = nonNullExpression + "!=null";
    }
    javaCodeStyleManager.shortenClassReferences(ct.replaceAndRestoreComments(psiElement, replacement));
  }
}
