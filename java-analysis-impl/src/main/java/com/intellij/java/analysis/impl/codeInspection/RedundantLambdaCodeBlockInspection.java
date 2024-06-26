/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.java.analysis.impl.codeInspection;

import com.intellij.java.analysis.codeInspection.BaseJavaBatchLocalInspectionTool;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.*;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.editor.intention.HighPriorityAction;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.logging.Logger;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.Nls;

import java.util.Collection;

/**
 * User: anna
 */
@ExtensionImpl
public class RedundantLambdaCodeBlockInspection extends BaseJavaBatchLocalInspectionTool {
  public static final Logger LOG = Logger.getInstance(RedundantLambdaCodeBlockInspection.class);

  @Nls
  @Nonnull
  @Override
  public String getGroupDisplayName() {
    return InspectionLocalize.groupNamesLanguageLevelSpecificIssuesAndMigrationAids().get();
  }

  @Nonnull
  @Override
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.WARNING;
  }

  @Nls
  @Nonnull
  @Override
  public String getDisplayName() {
    return "Statement lambda can be replaced with expression lambda";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Nonnull
  @Override
  public String getShortName() {
    return "CodeBlock2Expr";
  }

  @Nonnull
  @Override
  public PsiElementVisitor buildVisitorImpl(@Nonnull final ProblemsHolder holder,
                                            boolean isOnTheFly,
                                            LocalInspectionToolSession session,
                                            Object state) {
    return new JavaElementVisitor() {
      @Override
      public void visitLambdaExpression(PsiLambdaExpression expression) {
        super.visitLambdaExpression(expression);
        if (PsiUtil.isLanguageLevel8OrHigher(expression)) {
          final PsiElement body = expression.getBody();
          final PsiExpression psiExpression = isCodeBlockRedundant(body);
          if (psiExpression != null) {
            final PsiElement errorElement;
            final PsiElement parent = psiExpression.getParent();
            if (parent instanceof PsiReturnStatement) {
              errorElement = parent.getFirstChild();
            } else {
              errorElement = body.getFirstChild();
            }
            holder.registerProblem(errorElement, "Statement lambda can be replaced with expression lambda", ProblemHighlightType.LIKE_UNUSED_SYMBOL, new ReplaceWithExprFix());
          }
        }
      }
    };
  }

  public static PsiExpression isCodeBlockRedundant(PsiElement body) {
    if (body instanceof PsiCodeBlock) {
      PsiExpression psiExpression = LambdaUtil.extractSingleExpressionFromBody(body);
      if (psiExpression != null && !findCommentsOutsideExpression(body, psiExpression)) {
        if (LambdaUtil.isExpressionStatementExpression(psiExpression)) {
          final PsiCall call = LambdaUtil.treeWalkUp(body);
          if (call != null && call.resolveMethod() != null) {
            final int offsetInTopCall = body.getTextRange().getStartOffset() - call.getTextRange().getStartOffset();
            PsiCall copyCall = LambdaUtil.copyTopLevelCall(call);
            if (copyCall == null) {
              return null;
            }
            final PsiCodeBlock codeBlock = PsiTreeUtil.getParentOfType(copyCall.findElementAt(offsetInTopCall), PsiCodeBlock.class);
            if (codeBlock != null) {
              final PsiElement parent = codeBlock.getParent();
              if (parent instanceof PsiLambdaExpression) {
                codeBlock.replace(psiExpression);
                if (copyCall.resolveMethod() == null || ((PsiLambdaExpression) parent).getFunctionalInterfaceType() == null) {
                  return null;
                }
              }
            }
          }
        }
        return psiExpression;
      }
    }
    return null;
  }

  private static boolean findCommentsOutsideExpression(PsiElement body, PsiExpression psiExpression) {
    final Collection<PsiComment> comments = PsiTreeUtil.findChildrenOfType(body, PsiComment.class);
    for (PsiComment comment : comments) {
      if (!PsiTreeUtil.isAncestor(psiExpression, comment, true)) {
        return true;
      }
    }
    return false;
  }

  private static class ReplaceWithExprFix implements LocalQuickFix, HighPriorityAction {
    @Nonnull
    @Override
    public String getFamilyName() {
      return "Replace with expression lambda";
    }

    @Override
    public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (element != null) {
        final PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(element, PsiLambdaExpression.class);
        if (lambdaExpression != null) {
          final PsiElement body = lambdaExpression.getBody();
          if (body != null) {
            PsiExpression expression = LambdaUtil.extractSingleExpressionFromBody(body);
            if (expression != null) {
              body.replace(expression);
            }
          }
        }
      }
    }
  }
}
