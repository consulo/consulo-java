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
import jakarta.annotation.Nonnull;

import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;

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
        @Nonnull Predicate<? super PsiReference> processor,
        @Nonnull PsiMethod constructor,
        @Nonnull PsiClass containingClass,
        @Nonnull SearchScope searchScope,
        @Nonnull Project project,
        boolean ignoreAccessScope,
        boolean isStrictSignatureSearch,
        @Nonnull SearchRequestCollector collector
    ) {
        boolean[] constructorCanBeCalledImplicitly = new boolean[1];
        boolean[] isEnum = new boolean[1];
        boolean[] isUnder18 = new boolean[1];

        MethodUsagesSearcher.resolveInReadAction(
            project,
            (Supplier<Void>)() -> {
                constructorCanBeCalledImplicitly[0] = constructor.getParameterList().getParametersCount() == 0;
                isEnum[0] = containingClass.isEnum();
                isUnder18[0] = PsiUtil.getLanguageLevel(containingClass).isAtLeast(LanguageLevel.JDK_1_8);
                return null;
            }
        );

        if (isEnum[0]) {
            if (!processEnumReferences(processor, constructor, project, containingClass)) {
                return false;
            }
        }

        // search usages like "new XXX(..)"
        BiPredicate<PsiReference, SearchRequestCollector> processor1 = (reference, collector1) -> {
            PsiElement parent = reference.getElement().getParent();
            if (parent instanceof PsiAnonymousClass) {
                parent = parent.getParent();
            }
            if (parent instanceof PsiNewExpression newExpr) {
                PsiMethod constructor1 = newExpr.resolveConstructor();
                if (constructor1 != null) {
                    if (isStrictSignatureSearch) {
                        if (myManager.areElementsEquivalent(constructor, constructor1)) {
                            return processor.test(reference);
                        }
                    }
                    else {
                        if (myManager.areElementsEquivalent(containingClass, constructor1.getContainingClass())) {
                            return processor.test(reference);
                        }
                    }
                }
            }
            return true;
        };

        ReferencesSearch.searchOptimized(containingClass, searchScope, ignoreAccessScope, collector, true, processor1);
        if (isUnder18[0]) {
            if (!process18MethodPointers(processor, constructor, project, containingClass, searchScope)) {
                return false;
            }
        }

        // search usages like "this(..)"
        if (!MethodUsagesSearcher.resolveInReadAction(
            project,
            () -> processSuperOrThis(
                containingClass,
                constructor,
                constructorCanBeCalledImplicitly[0],
                searchScope,
                project,
                isStrictSignatureSearch,
                PsiKeyword.THIS,
                processor
            )
        )) {
            return false;
        }

        // search usages like "super(..)"
        Predicate<PsiClass> processor2 = inheritor -> {
            PsiElement navigationElement = inheritor.getNavigationElement();
            return !(navigationElement instanceof PsiClass psiClass) || processSuperOrThis(
                psiClass,
                constructor,
                constructorCanBeCalledImplicitly[0],
                searchScope,
                project,
                isStrictSignatureSearch,
                PsiKeyword.SUPER,
                processor
            );
        };

        return ClassInheritorsSearch.search(containingClass, searchScope, false).forEach(processor2);
    }

    private static boolean processEnumReferences(
        @Nonnull Predicate<? super PsiReference> processor,
        @Nonnull PsiMethod constructor,
        @Nonnull Project project,
        @Nonnull PsiClass aClass
    ) {
        return MethodUsagesSearcher.resolveInReadAction(
            project,
            () -> {
                for (PsiField field : aClass.getFields()) {
                    if (field instanceof PsiEnumConstant) {
                        PsiReference reference = field.getReference();
                        if (reference != null && reference.isReferenceTo(constructor) && !processor.test(reference)) {
                            return false;
                        }
                    }
                }
                return true;
            }
        );
    }

    private static boolean process18MethodPointers(
        @Nonnull Predicate<? super PsiReference> processor,
        @Nonnull PsiMethod constructor,
        @Nonnull Project project,
        @Nonnull PsiClass aClass,
        SearchScope searchScope
    ) {
        return ReferencesSearch.search(aClass, searchScope).forEach(reference -> {
            PsiElement element = reference.getElement();
            if (element != null) {
                return MethodUsagesSearcher.resolveInReadAction(
                    project,
                    () -> !(element.getParent() instanceof PsiMethodReferenceExpression methodReferenceExpression
                        && methodReferenceExpression.getReferenceNameElement() instanceof PsiKeyword
                        && methodReferenceExpression.isReferenceTo(constructor)
                        && !processor.test(methodReferenceExpression))
                );
            }
            return true;
        });
    }

    private boolean processSuperOrThis(
        @Nonnull PsiClass inheritor,
        @Nonnull PsiMethod constructor,
        boolean constructorCanBeCalledImplicitly,
        @Nonnull SearchScope searchScope,
        @Nonnull Project project,
        boolean isStrictSignatureSearch,
        @Nonnull String superOrThisKeyword,
        @Nonnull Predicate<? super PsiReference> processor
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
                if (statement instanceof PsiExpressionStatement exprStmt
                    && exprStmt.getExpression() instanceof PsiMethodCallExpression methodCall) {
                    PsiReferenceExpression refExpr = methodCall.getMethodExpression();
                    if (PsiSearchScopeUtil.isInScope(searchScope, refExpr) && refExpr.textMatches(superOrThisKeyword)) {
                        if (refExpr.resolve() instanceof PsiMethod constructor1) {
                            boolean match = isStrictSignatureSearch
                                ? myManager.areElementsEquivalent(constructor1, constructor)
                                : myManager.areElementsEquivalent(
                                constructor.getContainingClass(),
                                constructor1.getContainingClass()
                            );
                            if (match && !processor.test(refExpr)) {
                                return false;
                            }
                        }
                        //as long as we've encountered super/this keyword, no implicit ctr calls are possible here
                        continue;
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
        @Nonnull PsiMember usage,
        @Nonnull Predicate<? super PsiReference> processor,
        @Nonnull PsiMethod constructor,
        @Nonnull Project project,
        @Nonnull PsiClass containingClass
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

        //noinspection SimplifiableIfStatement
        if (!resolvesToThisConstructor) {
            return true;
        }

        return processor.test(new LightMemberReference(myManager, usage, PsiSubstitutor.EMPTY) {
            @Nonnull
            @Override
            public PsiElement getElement() {
                return usage;
            }

            @Nonnull
            @Override
            @RequiredReadAction
            public TextRange getRangeInElement() {
                if (usage instanceof PsiNameIdentifierOwner nameIdentifierOwner) {
                    PsiElement identifier = nameIdentifierOwner.getNameIdentifier();
                    if (identifier != null) {
                        int startOffsetInParent = identifier.getStartOffsetInParent();
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
