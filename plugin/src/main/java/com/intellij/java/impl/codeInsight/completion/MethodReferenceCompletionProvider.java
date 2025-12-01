/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.completion;

import com.intellij.java.impl.codeInsight.ExpectedTypeInfo;
import com.intellij.java.impl.codeInsight.ExpectedTypeInfoImpl;
import com.intellij.java.language.impl.psi.impl.source.resolve.graphInference.FunctionalInterfaceParameterizationUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.PsiEquivalenceUtil;
import consulo.language.editor.completion.CompletionParameters;
import consulo.language.editor.completion.CompletionProvider;
import consulo.language.editor.completion.CompletionResultSet;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.TailType;
import consulo.language.psi.PsiElement;
import consulo.language.util.ProcessingContext;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;

import jakarta.annotation.Nonnull;

import java.util.Map;
import java.util.function.Consumer;

public class MethodReferenceCompletionProvider implements CompletionProvider {
    private static final Logger LOG = Logger.getInstance(MethodReferenceCompletionProvider.class);

    @Override
    @RequiredReadAction
    public void addCompletions(
        @Nonnull CompletionParameters parameters,
        ProcessingContext context,
        @Nonnull final CompletionResultSet result
    ) {
        if (!PsiUtil.isLanguageLevel8OrHigher(parameters.getOriginalFile())) {
            return;
        }

        PsiElement rulezzRef = parameters.getPosition().getParent();
        if (rulezzRef == null || !LambdaUtil.isValidLambdaContext(rulezzRef.getParent())) {
            return;
        }

        ExpectedTypeInfo[] expectedTypes = JavaSmartCompletionContributor.getExpectedTypes(parameters);
        for (ExpectedTypeInfo expectedType : expectedTypes) {
            PsiType defaultType = expectedType.getDefaultType();
            if (LambdaUtil.isFunctionalType(defaultType)) {
                final PsiType functionalType = FunctionalInterfaceParameterizationUtil.getGroundTargetType(defaultType);
                PsiType returnType = LambdaUtil.getFunctionalInterfaceReturnType(functionalType);
                if (returnType != null) {
                    PsiElement position = parameters.getPosition();
                    final PsiElement refPlace = position.getParent();
                    ExpectedTypeInfoImpl typeInfo = new ExpectedTypeInfoImpl(
                        returnType,
                        ExpectedTypeInfo.TYPE_OR_SUBTYPE,
                        returnType,
                        TailType.UNKNOWN,
                        null,
                        ExpectedTypeInfoImpl.NULL
                    );
                    final Map<PsiElement, PsiType> map = LambdaUtil.getFunctionalTypeMap();
                    Consumer<LookupElement> noTypeCheck = new Consumer<>() {
                        @Override
                        @RequiredReadAction
                        public void accept(LookupElement lookupElement) {
                            if (lookupElement.getPsiElement() instanceof PsiMethod method) {
                                PsiMethodReferenceExpression referenceExpression = createMethodReferenceExpression(method);
                                if (referenceExpression == null) {
                                    return;
                                }

                                PsiType added = map.put(referenceExpression, functionalType);
                                try {
                                    PsiElement resolve = referenceExpression.resolve();
                                    if (resolve != null
                                        && PsiEquivalenceUtil.areElementsEquivalent(method, resolve)
                                        && PsiMethodReferenceUtil.checkMethodReferenceContext(
                                        referenceExpression,
                                        resolve,
                                        functionalType
                                    ) == LocalizeValue.empty()) {
                                        result.addElement(new JavaMethodReferenceElement(method, refPlace));
                                    }
                                }
                                finally {
                                    if (added == null) {
                                        map.remove(referenceExpression);
                                    }
                                }
                            }
                        }

                        @RequiredWriteAction
                        private PsiMethodReferenceExpression createMethodReferenceExpression(PsiMethod method) {
                            PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(method.getProject());
                            if (refPlace instanceof PsiMethodReferenceExpression methodRef) {
                                PsiMethodReferenceExpression referenceExpression = (PsiMethodReferenceExpression) methodRef.copy();
                                PsiElement referenceNameElement = referenceExpression.getReferenceNameElement();
                                LOG.assertTrue(referenceNameElement != null, referenceExpression);
                                referenceNameElement.replace(
                                    method.isConstructor()
                                        ? elementFactory.createKeyword("new")
                                        : elementFactory.createIdentifier(method.getName())
                                );
                                return referenceExpression;
                            }
                            else if (method.isStatic()) {
                                PsiClass aClass = method.getContainingClass();
                                LOG.assertTrue(aClass != null);
                                String qualifiedName = aClass.getQualifiedName();
                                return (PsiMethodReferenceExpression) elementFactory.createExpressionFromText(
                                    qualifiedName + "::" + (method.isConstructor() ? "new" : method.getName()),
                                    refPlace
                                );
                            }
                            else {
                                return null;
                            }
                        }
                    };

                    Runnable runnable = ReferenceExpressionCompletionContributor.fillCompletionVariants(
                        new JavaSmartCompletionParameters(parameters, typeInfo),
                        noTypeCheck
                    );
                    if (runnable != null) {
                        runnable.run();
                    }
                }
            }
        }
    }
}
