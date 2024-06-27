/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInspection;

import com.intellij.java.analysis.impl.codeInspection.JavaSuppressionUtil;
import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;


/**
 * @author ven
 */
public class AddAssertStatementFix implements LocalQuickFix {
  private static final Logger LOG = Logger.getInstance(AddAssertStatementFix.class);
  private final String myText;

  public AddAssertStatementFix(@Nonnull String text) {
    myText = text;
  }

  @Override
  @Nonnull
  public String getName() {
    return InspectionLocalize.inspectionAssertQuickfix(myText).get();
  }

  @Override
  @RequiredReadAction
  public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    PsiElement anchorElement = RefactoringUtil.getParentStatement(element, false);
    LOG.assertTrue(anchorElement != null);
    final PsiElement tempParent = anchorElement.getParent();
    if (tempParent instanceof PsiForStatement forStatement && !PsiTreeUtil.isAncestor(forStatement.getBody(), anchorElement, false)) {
      anchorElement = tempParent;
    }
    PsiElement prev = PsiTreeUtil.skipWhitespacesBackward(anchorElement);
    if (prev instanceof PsiComment && JavaSuppressionUtil.getSuppressedInspectionIdsIn(prev) != null) {
      anchorElement = prev;
    }

    try {
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(element.getProject());
      @NonNls String text = "assert " + myText + ";";
      PsiAssertStatement assertStatement = (PsiAssertStatement) factory.createStatementFromText(text, null);

      final PsiElement parent = anchorElement.getParent();
      if (parent instanceof PsiCodeBlock) {
        parent.addBefore(assertStatement, anchorElement);
      } else {
        RefactoringUtil.putStatementInLoopBody(assertStatement, parent, anchorElement);
      }
    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @Override
  @Nonnull
  public String getFamilyName() {
    return InspectionLocalize.inspectionQuickfixAssertFamily().get();
  }
}
