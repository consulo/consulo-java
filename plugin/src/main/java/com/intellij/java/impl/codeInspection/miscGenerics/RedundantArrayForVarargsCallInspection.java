/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInspection.miscGenerics;

import com.intellij.java.analysis.impl.codeInspection.miscGenerics.GenericsInspectionToolBase;
import com.intellij.java.impl.codeInsight.ExpectedTypeInfo;
import com.intellij.java.impl.codeInsight.ExpectedTypesProvider;
import com.intellij.java.impl.refactoring.util.InlineUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */
@ExtensionImpl
public class RedundantArrayForVarargsCallInspection extends GenericsInspectionToolBase {
    private static final Logger LOG = Logger.getInstance(RedundantArrayForVarargsCallInspection.class);
    private final LocalQuickFix myQuickFixAction = new MyQuickFix();

    private static class MyQuickFix implements LocalQuickFix {
        @Override
        @RequiredUIAccess
        public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
            PsiNewExpression arrayCreation = (PsiNewExpression) descriptor.getPsiElement();
            if (arrayCreation == null || !arrayCreation.isValid()) {
                return;
            }
            if (!FileModificationService.getInstance().prepareFileForWrite(arrayCreation.getContainingFile())) {
                return;
            }
            InlineUtil.inlineArrayCreationForVarargs(arrayCreation);
        }

        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionLocalize.inspectionRedundantArrayCreationQuickfix();
        }
    }

    @Override
    @RequiredReadAction
    public ProblemDescriptor[] getDescriptions(PsiElement place, final InspectionManager manager, final boolean isOnTheFly, Object state) {
        if (!PsiUtil.isLanguageLevel5OrHigher(place)) {
            return null;
        }
        final List<ProblemDescriptor> problems = new ArrayList<>();
        place.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            @RequiredReadAction
            public void visitCallExpression(@Nonnull PsiCallExpression expression) {
                super.visitCallExpression(expression);
                checkCall(expression);
            }

            @Override
            @RequiredReadAction
            public void visitEnumConstant(@Nonnull PsiEnumConstant enumConstant) {
                super.visitEnumConstant(enumConstant);
                checkCall(enumConstant);
            }

            @Override
            public void visitClass(@Nonnull PsiClass aClass) {
                //do not go inside to prevent multiple signals of the same problem
            }

            @RequiredReadAction
            private void checkCall(PsiCall expression) {
                JavaResolveResult resolveResult = expression.resolveMethodGenerics();
                PsiSubstitutor substitutor = resolveResult.getSubstitutor();
                if (resolveResult.getElement() instanceof PsiMethod method && method.isVarArgs()) {
                    PsiParameter[] parameters = method.getParameterList().getParameters();
                    PsiExpressionList argumentList = expression.getArgumentList();
                    if (argumentList != null) {
                        PsiExpression[] args = argumentList.getExpressions();
                        if (parameters.length == args.length) {
                            PsiExpression lastArg = args[args.length - 1];
                            PsiParameter lastParameter = parameters[args.length - 1];
                            PsiType lastParamType = lastParameter.getType();
                            LOG.assertTrue(lastParamType instanceof PsiEllipsisType);
                            if (lastArg instanceof PsiNewExpression newExpression &&
                                substitutor.substitute(((PsiEllipsisType) lastParamType).toArrayType()).equals(lastArg.getType())) {
                                PsiExpression[] initializers = getInitializers(newExpression);
                                if (initializers != null && isSafeToFlatten(expression, method, initializers)) {
                                    ProblemDescriptor descriptor = manager.newProblemDescriptor(
                                            InspectionLocalize.inspectionRedundantArrayCreationForVarargsCallDescriptor()
                                        )
                                        .range(lastArg)
                                        .onTheFly(isOnTheFly)
                                        .withFix(myQuickFixAction)
                                        .create();

                                    problems.add(descriptor);
                                }
                            }
                        }
                    }
                }
            }

            private boolean isSafeToFlatten(PsiCall callExpression, PsiMethod oldRefMethod, PsiExpression[] arrayElements) {
                if (arrayElements.length == 1) {
                    PsiType type = arrayElements[0].getType();
                    // change foo(new Object[]{array}) to foo(array) is not safe
                    if (PsiType.NULL.equals(type) || type instanceof PsiArrayType) {
                        return false;
                    }
                }
                PsiCall copy = (PsiCall) callExpression.copy();
                PsiExpressionList copyArgumentList = copy.getArgumentList();
                LOG.assertTrue(copyArgumentList != null);
                PsiExpression[] args = copyArgumentList.getExpressions();
                try {
                    args[args.length - 1].delete();
                    if (arrayElements.length > 0) {
                        copyArgumentList.addRange(arrayElements[0], arrayElements[arrayElements.length - 1]);
                    }
                    Project project = callExpression.getProject();
                    JavaResolveResult resolveResult;
                    if (callExpression instanceof PsiEnumConstant enumConstant) {
                        PsiClass containingClass = enumConstant.getContainingClass();
                        JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
                        PsiClassType classType = facade.getElementFactory().createType(containingClass);
                        resolveResult = facade.getResolveHelper().resolveConstructor(classType, copyArgumentList, enumConstant);
                        return resolveResult.isValidResult() && resolveResult.getElement() == oldRefMethod;
                    }
                    else {
                        resolveResult = copy.resolveMethodGenerics();
                        if (!resolveResult.isValidResult() || resolveResult.getElement() != oldRefMethod) {
                            return false;
                        }
                        ExpectedTypeInfo[] expectedTypes =
                            ExpectedTypesProvider.getExpectedTypes((PsiCallExpression) callExpression, false);
                        PsiType expressionType = ((PsiCallExpression) copy).getType();
                        for (ExpectedTypeInfo expectedType : expectedTypes) {
                            if (!expectedType.getType().isAssignableFrom(expressionType)) {
                                return false;
                            }
                        }
                        return true;
                    }
                }
                catch (IncorrectOperationException e) {
                    return false;
                }
            }
        });
        if (problems.isEmpty()) {
            return null;
        }
        return problems.toArray(new ProblemDescriptor[problems.size()]);
    }

    @Nullable
    private static PsiExpression[] getInitializers(PsiNewExpression newExpression) {
        PsiArrayInitializerExpression initializer = newExpression.getArrayInitializer();
        if (initializer != null) {
            return initializer.getInitializers();
        }
        PsiExpression[] dims = newExpression.getArrayDimensions();
        if (dims.length > 0) {
            PsiExpression firstDimension = dims[0];
            Object value =
                JavaPsiFacade.getInstance(newExpression.getProject())
                    .getConstantEvaluationHelper()
                    .computeConstantExpression(firstDimension);
            if (value instanceof Integer intValue && intValue == 0) {
                return PsiExpression.EMPTY_ARRAY;
            }
        }

        return null;
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return InspectionLocalize.groupNamesVerboseOrRedundantCodeConstructs();
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionLocalize.inspectionRedundantArrayCreationDisplayName();
    }

    @Nonnull
    @Override
    public String getShortName() {
        return "RedundantArrayCreation";
    }
}
