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

import consulo.annotation.access.RequiredReadAction;
import consulo.language.editor.inspection.InspectionsBundle;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import com.intellij.java.analysis.impl.codeInspection.JavaSuppressionUtil;
import com.intellij.java.impl.codeInsight.generation.surroundWith.JavaWithIfSurrounder;
import com.intellij.java.impl.ipp.trivialif.MergeIfAndIntention;
import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.language.psi.*;
import consulo.document.Document;
import consulo.codeEditor.Editor;
import consulo.codeEditor.ScrollType;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.project.Project;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.editor.util.PsiUtilBase;
import consulo.language.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import consulo.logging.Logger;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;

/**
 * @author ven
 */
public class SurroundWithIfFix implements LocalQuickFix {
  private static final Logger LOG = Logger.getInstance(SurroundWithIfFix.class);
  private final String myText;
  private final String mySuffix;

  @Override
  @Nonnull
  public String getName() {
    return InspectionsBundle.message("inspection.surround.if.quickfix", myText, mySuffix);
  }

  public SurroundWithIfFix(@Nonnull PsiExpression expressionToAssert, String suffix) {
    myText = ParenthesesUtils.getText(expressionToAssert, ParenthesesUtils.BINARY_AND_PRECEDENCE);
    mySuffix = suffix;
  }

  @Override
  @RequiredReadAction
  public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    PsiElement anchorStatement = RefactoringUtil.getParentStatement(element, false);
    LOG.assertTrue(anchorStatement != null);
    if (anchorStatement.getParent() instanceof PsiLambdaExpression lambdaExpression) {
      final PsiCodeBlock body = RefactoringUtil.expandExpressionLambdaToCodeBlock(lambdaExpression);
      anchorStatement = body.getStatements()[0];
    }
    Editor editor = PsiUtilBase.findEditor(anchorStatement);
    if (editor == null) {
      return;
    }
    PsiFile file = anchorStatement.getContainingFile();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    Document document = documentManager.getDocument(file);
    if (document == null) {
      return;
    }
    PsiElement[] elements = {anchorStatement};
    PsiElement prev = PsiTreeUtil.skipWhitespacesBackward(anchorStatement);
    if (prev instanceof PsiComment && JavaSuppressionUtil.getSuppressedInspectionIdsIn(prev) != null) {
      elements = new PsiElement[]{
          prev,
          anchorStatement
      };
    }
    try {
      TextRange textRange = new JavaWithIfSurrounder().surroundElements(project, editor, elements);
      if (textRange == null) {
        return;
      }

      @NonNls String newText = myText + mySuffix;
      document.replaceString(textRange.getStartOffset(), textRange.getEndOffset(), newText);

      editor.getCaretModel().moveToOffset(textRange.getEndOffset() + newText.length());

      PsiDocumentManager.getInstance(project).commitAllDocuments();

      new MergeIfAndIntention().invoke(project, editor, file);

      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @Override
  @Nonnull
  public String getFamilyName() {
    return InspectionLocalize.inspectionSurroundIfFamily().get();
  }

  @RequiredReadAction
  public static boolean isAvailable(PsiExpression qualifier) {
    if (!qualifier.isValid() || qualifier.getText() == null) {
      return false;
    }
    PsiStatement statement = PsiTreeUtil.getParentOfType(qualifier, PsiStatement.class);
    if (statement == null) {
      return false;
    }
    PsiElement parent = statement.getParent();
    return !(parent instanceof PsiForStatement);
  }
}
