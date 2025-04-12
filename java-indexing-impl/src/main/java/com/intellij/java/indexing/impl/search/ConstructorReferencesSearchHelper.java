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
package com.intellij.java.indexing.impl.search;

import com.intellij.java.indexing.search.searches.ClassInheritorsSearch;
import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.impl.psi.impl.light.LightMemberReference;
import com.intellij.java.language.impl.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.util.function.Computable;
import consulo.application.util.function.Processor;
import consulo.content.scope.SearchScope;
import consulo.document.util.TextRange;
import consulo.document.util.UnfairTextRange;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiNameIdentifierOwner;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.PsiSearchScopeUtil;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.search.SearchRequestCollector;
import consulo.project.DumbService;
import consulo.project.Project;
import consulo.util.lang.function.PairProcessor;

import jakarta.annotation.Nonnull;

/**
 * @author max
 */
public class ConstructorReferencesSearchHelper {
    private final PsiManager myManager;

    public ConstructorReferencesSearchHelper(@Nonnull PsiManager manager) {
        myManager = manager;
    }

    /*
     * Project is passed around explicitly to avoid invoking PsiElement.getProject each time we need it. There are two reasons:
     * 1. Performance. getProject traverses AST upwards
     * 2. Exception avoidance. Project is needed outside of read action (to run it via DumbService in the first place),
     *    and so getProject would fail with an assertion that read action is required but not present.
     */
    public boolean processConstructorReferences(
        @Nonnull final Processor<? super PsiReference> processor,
        @Nonnull final PsiMethod constructor,
        @Nonnull final PsiClass containingClass,
        @Nonnull final SearchScope searchScope,
        @Nonnull final Project project,
        boolean ignoreAccessScope,
        final boolean isStrictSignatureSearch,
        @Nonnull SearchRequestCollector collector
    ) {
        final boolean[] constructorCanBeCalledImplicitly = new boolean[1];
        final boolean[] isEnum = new boolean[1];
        final boolean[] isUnder18 = new boolean[1];

        MethodUsagesSearcher.resolveInReadAction(project, new Computable<Void>() {
            @Override
            public Void compute() {
                constructorCanBeCalledImplicitly[0] = constructor.getParameterList().getParametersCount() == 0;
                isEnum[0] = containingClass.isEnum();
                isUnder18[0] = PsiUtil.getLanguageLevel(containingClass).isAtLeast(LanguageLevel.JDK_1_8);
                return null;
            }
        });

        if (isEnum[0]) {
            if (!processEnumReferences(processor, constructor, project, containingClass)) {
                return false;
            }
        }

        // search usages like "new XXX(..)"
        PairProcessor<PsiReference, SearchRequestCollector> processor1 = new PairProcessor<PsiReference, SearchRequestCollector>() {
            @Override
            public boolean process(PsiReference reference, SearchRequestCollector collector) {
                PsiElement parent = reference.getElement().getParent();
                if (parent instanceof PsiAnonymousClass) {
                    parent = parent.getParent();
                }
                if (parent instanceof PsiNewExpression) {
                    PsiMethod constructor1 = ((PsiNewExpression)parent).resolveConstructor();
                    if (constructor1 != null) {
                        if (isStrictSignatureSearch) {
                            if (myManager.areElementsEquivalent(constructor, constructor1)) {
                                return processor.process(reference);
                            }
                        }
                        else {
                            if (myManager.areElementsEquivalent(containingClass, constructor1.getContainingClass())) {
                                return processor.process(reference);
                            }
                        }
                    }
                }
                return true;
            }
        };

        ReferencesSearch.searchOptimized(containingClass, searchScope, ignoreAccessScope, collector, true, processor1);
        if (isUnder18[0]) {
            if (!process18MethodPointers(processor, constructor, project, containingClass, searchScope)) {
                return false;
            }
        }

        // search usages like "this(..)"
        if (!MethodUsagesSearcher.resolveInReadAction(project, new Computable<Boolean>() {
            @Override
            public Boolean compute() {
                return processSuperOrThis(
                    containingClass,
                    constructor,
                    constructorCanBeCalledImplicitly[0],
                    searchScope,
                    project,
                    isStrictSignatureSearch,
                    PsiKeyword.THIS,
                    processor
                );
            }
        })) {
            return false;
        }

        // search usages like "super(..)"
        Processor<PsiClass> processor2 = new Processor<PsiClass>() {
            @Override
            public boolean process(PsiClass inheritor) {
                final PsiElement navigationElement = inheritor.getNavigationElement();
                if (navigationElement instanceof PsiClass) {
                    return processSuperOrThis(
                        (PsiClass)navigationElement,
                        constructor,
                        constructorCanBeCalledImplicitly[0],
                        searchScope,
                        project,
                        isStrictSignatureSearch,
                        PsiKeyword.SUPER,
                        processor
                    );
                }
                return true;
            }
        };

        return ClassInheritorsSearch.search(containingClass, searchScope, false).forEach(processor2);
    }

    private static boolean processEnumReferences(
        @Nonnull final Processor<? super PsiReference> processor,
        @Nonnull final PsiMethod constructor,
        @Nonnull final Project project,
        @Nonnull final PsiClass aClass
    ) {
        return MethodUsagesSearcher.resolveInReadAction(project, new Computable<Boolean>() {
            @Override
            public Boolean compute() {
                for (PsiField field : aClass.getFields()) {
                    if (field instanceof PsiEnumConstant) {
                        PsiReference reference = field.getReference();
                        if (reference != null && reference.isReferenceTo(constructor)) {
                            if (!processor.process(reference)) {
                                return false;
                            }
                        }
                    }
                }
                return true;
            }
        });
    }

    private static boolean process18MethodPointers(
        @Nonnull final Processor<? super PsiReference> processor,
        @Nonnull final PsiMethod constructor,
        @Nonnull final Project project,
        @Nonnull PsiClass aClass,
        SearchScope searchScope
    ) {
        return ReferencesSearch.search(aClass, searchScope).forEach(new Processor<PsiReference>() {
            @Override
            public boolean process(PsiReference reference) {
                final PsiElement element = reference.getElement();
                if (element != null) {
                    return MethodUsagesSearcher.resolveInReadAction(project, new Computable<Boolean>() {
                        @Override
                        public Boolean compute() {
                            final PsiElement parent = element.getParent();
                            if (parent instanceof PsiMethodReferenceExpression && ((PsiMethodReferenceExpression)parent).getReferenceNameElement() instanceof PsiKeyword) {
                                if (((PsiMethodReferenceExpression)parent).isReferenceTo(constructor)) {
                                    if (!processor.process((PsiReference)parent)) {
                                        return false;
                                    }
                                }
                            }
                            return true;
                        }
                    });
                }
                return true;
            }
        });
    }

    private boolean processSuperOrThis(
        @Nonnull PsiClass inheritor,
        @Nonnull PsiMethod constructor,
        final boolean constructorCanBeCalledImplicitly,
        @Nonnull SearchScope searchScope,
        @Nonnull Project project,
        final boolean isStrictSignatureSearch,
        @Nonnull String superOrThisKeyword,
        @Nonnull Processor<? super PsiReference> processor
    ) {
        PsiMethod[] constructors = inheritor.getConstructors();
        if (constructors.length == 0 && constructorCanBeCalledImplicitly) {
            if (!processImplicitConstructorCall(inheritor, processor, constructor, project, inheritor)) {
                return false;
            }
        }
        for (PsiMethod method : constructors) {
            PsiCodeBlock body = method.getBody();
            if (body == null || method == constructor && isStrictSignatureSearch) {
                continue;
            }
            PsiStatement[] statements = body.getStatements();
            if (statements.length != 0) {
                PsiStatement statement = statements[0];
                if (statement instanceof PsiExpressionStatement) {
                    PsiExpression expr = ((PsiExpressionStatement)statement).getExpression();
                    if (expr instanceof PsiMethodCallExpression) {
                        PsiReferenceExpression refExpr = ((PsiMethodCallExpression)expr).getMethodExpression();
                        if (PsiSearchScopeUtil.isInScope(searchScope, refExpr)) {
                            if (refExpr.textMatches(superOrThisKeyword)) {
                                PsiElement referencedElement = refExpr.resolve();
                                if (referencedElement instanceof PsiMethod) {
                                    PsiMethod constructor1 = (PsiMethod)referencedElement;
                                    boolean match = isStrictSignatureSearch
                                        ? myManager.areElementsEquivalent(constructor1, constructor)
                                        : myManager.areElementsEquivalent(
                                            constructor.getContainingClass(),
                                            constructor1.getContainingClass()
                                        );
                                    if (match && !processor.process(refExpr)) {
                                        return false;
                                    }
                                }
                                //as long as we've encountered super/this keyword, no implicit ctr calls are possible here
                                continue;
                            }
                        }
                    }
                }
            }
            if (constructorCanBeCalledImplicitly) {
                if (!processImplicitConstructorCall(method, processor, constructor, project, inheritor)) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean processImplicitConstructorCall(
        @Nonnull final PsiMember usage,
        @Nonnull final Processor<? super PsiReference> processor,
        @Nonnull final PsiMethod constructor,
        @Nonnull final Project project,
        @Nonnull final PsiClass containingClass
    ) {
        if (containingClass instanceof PsiAnonymousClass) {
            return true;
        }

        PsiClass ctrClass = constructor.getContainingClass();
        if (ctrClass == null) {
            return true;
        }

        boolean isImplicitSuper = DumbService.getInstance(project).runReadActionInSmartMode(
            () -> myManager.areElementsEquivalent(ctrClass, containingClass.getSuperClass()));
        if (!isImplicitSuper) {
            return true;
        }

        PsiElement resolved = JavaResolveUtil.resolveImaginarySuperCallInThisPlace(usage, project, ctrClass);

        boolean resolvesToThisConstructor = DumbService.getInstance(project).runReadActionInSmartMode(
            () -> myManager.areElementsEquivalent(constructor, resolved));

        if (!resolvesToThisConstructor) {
            return true;
        }

        return processor.process(new LightMemberReference(myManager, usage, PsiSubstitutor.EMPTY) {
            @Nonnull
            @Override
            public PsiElement getElement() {
                return usage;
            }

            @Nonnull
            @Override
            @RequiredReadAction
            public TextRange getRangeInElement() {
                if (usage instanceof PsiNameIdentifierOwner) {
                    PsiElement identifier = ((PsiNameIdentifierOwner)usage).getNameIdentifier();
                    if (identifier != null) {
                        final int startOffsetInParent = identifier.getStartOffsetInParent();
                        if (startOffsetInParent >= 0) { // -1 for light elements generated e.g. by lombok
                            return TextRange.from(startOffsetInParent, identifier.getTextLength());
                        }
                        else {
                            return new UnfairTextRange(-1, -1);
                        }
                    }
                }
                return super.getRangeInElement();
            }
        });
    }
}
