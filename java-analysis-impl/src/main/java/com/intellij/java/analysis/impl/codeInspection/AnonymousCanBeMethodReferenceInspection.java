/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.java.analysis.codeInspection.GroupNames;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.util.RedundantCastUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.util.TextRange;
import consulo.language.editor.inspection.*;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import org.jetbrains.annotations.Nls;

import jakarta.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;

/**
 * User: anna
 */
@ExtensionImpl
public class AnonymousCanBeMethodReferenceInspection extends BaseJavaBatchLocalInspectionTool<AnonymousCanBeMethodReferenceInspectionState> {
  private static final Logger LOG = Logger.getInstance(AnonymousCanBeMethodReferenceInspection.class);


  @jakarta.annotation.Nonnull
  @Override
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.WARNING;
  }

  @Nls
  @Nonnull
  @Override
  public String getGroupDisplayName() {
    return GroupNames.LANGUAGE_LEVEL_SPECIFIC_GROUP_NAME;
  }

  @Nls
  @jakarta.annotation.Nonnull
  @Override
  public String getDisplayName() {
    return "Anonymous type can be replaced with method reference";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @jakarta.annotation.Nonnull
  @Override
  public String getShortName() {
    return "Anonymous2MethodRef";
  }

  @jakarta.annotation.Nonnull
  @Override
  public InspectionToolState<? extends AnonymousCanBeMethodReferenceInspectionState> createStateProvider() {
    return new AnonymousCanBeMethodReferenceInspectionState();
  }

  @Nonnull
  @Override
  public PsiElementVisitor buildVisitorImpl(@jakarta.annotation.Nonnull final ProblemsHolder holder,
                                            boolean isOnTheFly,
                                            LocalInspectionToolSession session,
                                            AnonymousCanBeMethodReferenceInspectionState state) {
    return new JavaElementVisitor() {
      @Override
      public void visitAnonymousClass(PsiAnonymousClass aClass) {
        super.visitAnonymousClass(aClass);
        if (AnonymousCanBeLambdaInspection.canBeConvertedToLambda(aClass, true, state.reportNotAnnotatedInterfaces, Collections.emptySet())) {
          final PsiMethod method = aClass.getMethods()[0];
          final PsiCodeBlock body = method.getBody();
          PsiExpression lambdaBodyCandidate = LambdaCanBeMethodReferenceInspection.extractMethodReferenceCandidateExpression(body, false);
          final PsiExpression methodRefCandidate = LambdaCanBeMethodReferenceInspection.canBeMethodReferenceProblem(method.getParameterList().getParameters(), aClass.getBaseClassType(),
              aClass.getParent(), lambdaBodyCandidate);
          if (methodRefCandidate instanceof PsiCallExpression) {
            final PsiCallExpression callExpression = (PsiCallExpression) methodRefCandidate;
            final PsiMethod resolveMethod = callExpression.resolveMethod();
            if (resolveMethod != method && !AnonymousCanBeLambdaInspection.functionalInterfaceMethodReferenced(resolveMethod, aClass, callExpression)) {
              final PsiElement parent = aClass.getParent();
              if (parent instanceof PsiNewExpression) {
                final PsiJavaCodeReferenceElement classReference = ((PsiNewExpression) parent).getClassOrAnonymousClassReference();
                if (classReference != null) {
                  final PsiElement lBrace = aClass.getLBrace();
                  LOG.assertTrue(lBrace != null);
                  final TextRange rangeInElement = new TextRange(0, aClass.getStartOffsetInParent() + lBrace.getStartOffsetInParent());
                  ProblemHighlightType highlightType = LambdaCanBeMethodReferenceInspection.checkQualifier(lambdaBodyCandidate) ? ProblemHighlightType.LIKE_UNUSED_SYMBOL :
                      ProblemHighlightType.INFORMATION;
                  holder.registerProblem(parent, "Anonymous #ref #loc can be replaced with method reference", highlightType, rangeInElement, new ReplaceWithMethodRefFix());
                }
              }
            }
          }
        }
      }
    };
  }

  private static class ReplaceWithMethodRefFix implements LocalQuickFix {
    @jakarta.annotation.Nonnull
    @Override
    public String getFamilyName() {
      return "Replace with method reference";
    }

    @Override
    public void applyFix(@jakarta.annotation.Nonnull Project project, @jakarta.annotation.Nonnull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (element instanceof PsiNewExpression) {
        final PsiAnonymousClass anonymousClass = ((PsiNewExpression) element).getAnonymousClass();
        if (anonymousClass == null) {
          return;
        }
        final PsiMethod[] methods = anonymousClass.getMethods();
        if (methods.length != 1) {
          return;
        }

        final PsiParameter[] parameters = methods[0].getParameterList().getParameters();
        final PsiType functionalInterfaceType = anonymousClass.getBaseClassType();
        PsiExpression methodRefCandidate = LambdaCanBeMethodReferenceInspection.extractMethodReferenceCandidateExpression(methods[0].getBody(), false);
        final PsiExpression candidate = LambdaCanBeMethodReferenceInspection.canBeMethodReferenceProblem(parameters, functionalInterfaceType, anonymousClass.getParent(), methodRefCandidate);

        final String methodRefText = LambdaCanBeMethodReferenceInspection.createMethodReferenceText(candidate, functionalInterfaceType, parameters);

        replaceWithMethodReference(project, methodRefText, anonymousClass.getBaseClassType(), anonymousClass.getParent());
      }
    }
  }

  static void replaceWithMethodReference(@jakarta.annotation.Nonnull Project project, String methodRefText, PsiType castType, PsiElement replacementTarget) {
    final Collection<PsiComment> comments = ContainerUtil.map(PsiTreeUtil.findChildrenOfType(replacementTarget, PsiComment.class), comment -> (PsiComment) comment.copy());

    if (methodRefText != null) {
      final String canonicalText = castType.getCanonicalText();
      final PsiExpression psiExpression = JavaPsiFacade.getElementFactory(project).createExpressionFromText("(" + canonicalText + ")" + methodRefText, replacementTarget);

      PsiElement castExpr = replacementTarget.replace(psiExpression);
      if (RedundantCastUtil.isCastRedundant((PsiTypeCastExpression) castExpr)) {
        final PsiExpression operand = ((PsiTypeCastExpression) castExpr).getOperand();
        LOG.assertTrue(operand != null);
        castExpr = castExpr.replace(operand);
      }

      PsiElement anchor = PsiTreeUtil.getParentOfType(castExpr, PsiStatement.class);
      if (anchor == null) {
        anchor = castExpr;
      }
      for (PsiComment comment : comments) {
        anchor.getParent().addBefore(comment, anchor);
      }
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(castExpr);
    }
  }
}
