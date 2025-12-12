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
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.util.RedundantCastUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.util.TextRange;
import consulo.language.editor.inspection.*;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;

import jakarta.annotation.Nonnull;

import java.util.Collection;
import java.util.Collections;

/**
 * User: anna
 */
@ExtensionImpl
public class AnonymousCanBeMethodReferenceInspection extends BaseJavaBatchLocalInspectionTool<AnonymousCanBeMethodReferenceInspectionState> {
    private static final Logger LOG = Logger.getInstance(AnonymousCanBeMethodReferenceInspection.class);


    @Nonnull
    @Override
    public HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.WARNING;
    }

    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return InspectionLocalize.groupNamesLanguageLevelSpecificIssuesAndMigrationAids();
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return LocalizeValue.localizeTODO("Anonymous type can be replaced with method reference");
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Nonnull
    @Override
    public String getShortName() {
        return "Anonymous2MethodRef";
    }

    @Nonnull
    @Override
    public InspectionToolState<? extends AnonymousCanBeMethodReferenceInspectionState> createStateProvider() {
        return new AnonymousCanBeMethodReferenceInspectionState();
    }

    @Nonnull
    @Override
    public PsiElementVisitor buildVisitorImpl(
        @Nonnull final ProblemsHolder holder,
        boolean isOnTheFly,
        LocalInspectionToolSession session,
        AnonymousCanBeMethodReferenceInspectionState state
    ) {
        return new JavaElementVisitor() {
            @Override
            @RequiredReadAction
            public void visitAnonymousClass(@Nonnull PsiAnonymousClass aClass) {
                super.visitAnonymousClass(aClass);
                if (AnonymousCanBeLambdaInspection.canBeConvertedToLambda(
                    aClass,
                    true,
                    state.reportNotAnnotatedInterfaces,
                    Collections.emptySet()
                )) {
                    PsiMethod method = aClass.getMethods()[0];
                    PsiCodeBlock body = method.getBody();
                    PsiExpression lambdaBodyCandidate =
                        LambdaCanBeMethodReferenceInspection.extractMethodReferenceCandidateExpression(body, false);
                    PsiExpression methodRefCandidate = LambdaCanBeMethodReferenceInspection.canBeMethodReferenceProblem(
                        method.getParameterList().getParameters(),
                        aClass.getBaseClassType(),
                        aClass.getParent(),
                        lambdaBodyCandidate
                    );
                    if (methodRefCandidate instanceof PsiCallExpression call) {
                        PsiMethod resolveMethod = call.resolveMethod();
                        if (resolveMethod != method
                            && !AnonymousCanBeLambdaInspection.functionalInterfaceMethodReferenced(resolveMethod, aClass, call)
                            && aClass.getParent() instanceof PsiNewExpression newExpr) {
                            PsiJavaCodeReferenceElement classReference = newExpr.getClassOrAnonymousClassReference();
                            if (classReference != null) {
                                PsiElement lBrace = aClass.getLBrace();
                                LOG.assertTrue(lBrace != null);
                                TextRange rangeInElement =
                                    new TextRange(0, aClass.getStartOffsetInParent() + lBrace.getStartOffsetInParent());
                                ProblemHighlightType highlightType =
                                    LambdaCanBeMethodReferenceInspection.checkQualifier(lambdaBodyCandidate)
                                        ? ProblemHighlightType.LIKE_UNUSED_SYMBOL
                                        : ProblemHighlightType.INFORMATION;
                                holder.newProblem(LocalizeValue.localizeTODO("Anonymous #ref #loc can be replaced with method reference"))
                                    .range(newExpr, rangeInElement)
                                    .highlightType(highlightType)
                                    .withFix(new ReplaceWithMethodRefFix())
                                    .create();
                            }
                        }
                    }
                }
            }
        };
    }

    private static class ReplaceWithMethodRefFix implements LocalQuickFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return LocalizeValue.localizeTODO("Replace with method reference");
        }

        @Override
        @RequiredWriteAction
        public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
            if (descriptor.getPsiElement() instanceof PsiNewExpression newExpression) {
                PsiAnonymousClass anonymousClass = newExpression.getAnonymousClass();
                if (anonymousClass == null) {
                    return;
                }
                PsiMethod[] methods = anonymousClass.getMethods();
                if (methods.length != 1) {
                    return;
                }

                PsiParameter[] parameters = methods[0].getParameterList().getParameters();
                PsiType functionalInterfaceType = anonymousClass.getBaseClassType();
                PsiExpression methodRefCandidate =
                    LambdaCanBeMethodReferenceInspection.extractMethodReferenceCandidateExpression(methods[0].getBody(), false);
                PsiExpression candidate = LambdaCanBeMethodReferenceInspection.canBeMethodReferenceProblem(
                    parameters,
                    functionalInterfaceType,
                    anonymousClass.getParent(),
                    methodRefCandidate
                );

                String methodRefText =
                    LambdaCanBeMethodReferenceInspection.createMethodReferenceText(candidate, functionalInterfaceType, parameters);

                replaceWithMethodReference(project, methodRefText, anonymousClass.getBaseClassType(), anonymousClass.getParent());
            }
        }
    }

    @RequiredWriteAction
    static void replaceWithMethodReference(@Nonnull Project project, String methodRefText, PsiType castType, PsiElement replacementTarget) {
        Collection<PsiComment> comments = ContainerUtil.map(
            PsiTreeUtil.findChildrenOfType(replacementTarget, PsiComment.class),
            comment -> (PsiComment) comment.copy()
        );

        if (methodRefText != null) {
            String canonicalText = castType.getCanonicalText();
            PsiExpression psiExpression = JavaPsiFacade.getElementFactory(project)
                .createExpressionFromText("(" + canonicalText + ")" + methodRefText, replacementTarget);

            PsiElement castExpr = replacementTarget.replace(psiExpression);
            if (RedundantCastUtil.isCastRedundant((PsiTypeCastExpression) castExpr)) {
                PsiExpression operand = ((PsiTypeCastExpression) castExpr).getOperand();
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
