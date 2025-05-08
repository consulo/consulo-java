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
package com.intellij.java.impl.codeInsight.completion.scope;

import com.intellij.java.analysis.codeInspection.SuppressManager;
import com.intellij.java.impl.codeInspection.accessStaticViaInstance.AccessStaticViaInstanceBase;
import com.intellij.java.language.impl.codeInsight.completion.scope.JavaCompletionHints;
import com.intellij.java.language.impl.psi.impl.light.LightMethodBuilder;
import com.intellij.java.language.impl.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.java.language.impl.psi.scope.ElementClassHint;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.infos.CandidateInfo;
import com.intellij.java.language.psi.scope.JavaScopeProcessorEvent;
import com.intellij.java.language.psi.util.PsiTypesUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.psi.*;
import consulo.language.psi.filter.ElementFilter;
import consulo.language.psi.resolve.BaseScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.dataholder.Key;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * @author ik
 * @since 2003-01-20
 */
public class JavaCompletionProcessor extends BaseScopeProcessor implements ElementClassHint {
    private final boolean myInJavaDoc;
    private boolean myStatic;
    private PsiElement myDeclarationHolder;
    private final Map<CompletionElement, CompletionElement> myResults = new LinkedHashMap<>();
    private final Set<CompletionElement> mySecondRateResults = ContainerUtil.newIdentityTroveSet();
    private final Set<String> myShadowedNames = new HashSet<>();
    private final Set<String> myCurrentScopeMethodNames = new HashSet<>();
    private final Set<String> myFinishedScopesMethodNames = new HashSet<>();
    private final PsiElement myElement;
    private final PsiElement myScope;
    private final ElementFilter myFilter;
    private boolean myMembersFlag;
    private boolean myQualified;
    private PsiType myQualifierType;
    private PsiClass myQualifierClass;
    private final Predicate<String> myMatcher;
    private final Options myOptions;
    private final boolean myAllowStaticWithInstanceQualifier;

    @RequiredReadAction
    public JavaCompletionProcessor(
        @Nonnull PsiElement element,
        ElementFilter filter,
        Options options,
        @Nonnull Predicate<String> nameCondition
    ) {
        myOptions = options;
        myElement = element;
        myMatcher = nameCondition;
        myFilter = filter;
        PsiElement scope = element;
        myInJavaDoc = JavaResolveUtil.isInJavaDoc(myElement);
        if (myInJavaDoc) {
            myMembersFlag = true;
        }
        while (scope != null && !(scope instanceof PsiFile) && !(scope instanceof PsiClass)) {
            scope = scope.getContext();
        }
        myScope = scope;

        if (element.getContext() instanceof PsiReferenceExpression refExpr) {
            PsiExpression qualifier = refExpr.getQualifierExpression();
            if (qualifier instanceof PsiSuperExpression superExpr) {
                PsiJavaCodeReferenceElement qSuper = superExpr.getQualifier();
                if (qSuper == null) {
                    myQualifierClass = JavaResolveUtil.getContextClass(myElement);
                }
                else {
                    PsiElement target = qSuper.resolve();
                    myQualifierClass = target instanceof PsiClass psiClass ? psiClass : null;
                }
            }
            else if (qualifier != null) {
                myQualified = true;
                setQualifierType(qualifier.getType());
                if (myQualifierType == null && qualifier instanceof PsiJavaCodeReferenceElement javaCodeRef
                    && javaCodeRef.resolve() instanceof PsiClass psiClass) {
                    myQualifierClass = psiClass;
                }
            }
            else {
                myQualifierClass = JavaResolveUtil.getContextClass(myElement);
            }
        }
        if (myQualifierClass != null && myQualifierType == null) {
            myQualifierType = JavaPsiFacade.getElementFactory(element.getProject()).createType(myQualifierClass);
        }

        myAllowStaticWithInstanceQualifier = !options.filterStaticAfterInstance
            || SuppressManager.getInstance().isSuppressedFor(element, AccessStaticViaInstanceBase.ACCESS_STATIC_VIA_INSTANCE);
    }

    @Override
    public void handleEvent(@Nonnull Event event, Object associated) {
        if (event == JavaScopeProcessorEvent.START_STATIC) {
            myStatic = true;
        }
        if (event == JavaScopeProcessorEvent.CHANGE_LEVEL) {
            myMembersFlag = true;
            myFinishedScopesMethodNames.addAll(myCurrentScopeMethodNames);
            myCurrentScopeMethodNames.clear();
        }
        if (event == JavaScopeProcessorEvent.SET_CURRENT_FILE_CONTEXT) {
            myDeclarationHolder = (PsiElement)associated;
        }
    }

    @Override
    @RequiredReadAction
    public boolean execute(@Nonnull PsiElement element, @Nonnull ResolveState state) {
        if (element instanceof PsiPackage psiPackage && !isQualifiedContext()) {
            if (myScope instanceof PsiClass) {
                return true;
            }
            if (psiPackage.getQualifiedName().contains(".")
                && PsiTreeUtil.getParentOfType(myElement, PsiImportStatementBase.class) != null) {
                return true;
            }
        }

        if (element instanceof PsiMethod method && PsiTypesUtil.isGetClass(method) && PsiUtil.isLanguageLevel5OrHigher(myElement)) {
            PsiType patchedType = PsiTypesUtil.createJavaLangClassType(myElement, myQualifierType, false);
            if (patchedType != null) {
                element = new LightMethodBuilder(element.getManager(), method.getName()).
                    addModifier(PsiModifier.PUBLIC).
                    setMethodReturnType(patchedType).
                    setContainingClass(method.getContainingClass());
            }
        }

        if (element instanceof PsiVariable variable) {
            String name = variable.getName();
            if (myShadowedNames.contains(name)) {
                return true;
            }
            if (element instanceof PsiLocalVariable || element instanceof PsiParameter) {
                myShadowedNames.add(name);
            }
        }

        if (element instanceof PsiMethod method) {
            myCurrentScopeMethodNames.add(method.getName());
        }

        if (!satisfies(element, state) || !isAccessible(element)) {
            return true;
        }

        StaticProblem sp = myElement.getParent() instanceof PsiMethodReferenceExpression ? StaticProblem.none : getStaticProblem(element);
        if (sp == StaticProblem.instanceAfterStatic) {
            return true;
        }

        CompletionElement completion = new CompletionElement(element, state.get(PsiSubstitutor.KEY), getCallQualifierText(element));
        CompletionElement prev = myResults.get(completion);
        if (prev == null || completion.isMoreSpecificThan(prev)) {
            myResults.put(completion, completion);
            if (sp == StaticProblem.staticAfterInstance) {
                mySecondRateResults.add(completion);
            }
        }

        return true;
    }

    @Nonnull
    @RequiredReadAction
    private String getCallQualifierText(@Nonnull PsiElement element) {
        if (element instanceof PsiMethod method) {
            if (myFinishedScopesMethodNames.contains(method.getName())) {
                String className = myDeclarationHolder instanceof PsiClass psiClass ? psiClass.getName() : null;
                if (className != null) {
                    return className + (method.isStatic() ? "." : ".this.");
                }
            }
        }
        return "";
    }

    private boolean isQualifiedContext() {
        PsiElement elementParent = myElement.getParent();
        return elementParent instanceof PsiQualifiedReference qualifiedRef && qualifiedRef.getQualifier() != null;
    }

    private StaticProblem getStaticProblem(PsiElement element) {
        if (myOptions.showInstanceInStaticContext && !isQualifiedContext()) {
            return StaticProblem.none;
        }
        if (element instanceof PsiModifierListOwner modifierListOwner) {
            if (myStatic) {
                if (!(element instanceof PsiClass) && !modifierListOwner.hasModifierProperty(PsiModifier.STATIC)) {
                    // we don't need non static method in static context.
                    return StaticProblem.instanceAfterStatic;
                }
            }
            else if (!myAllowStaticWithInstanceQualifier && modifierListOwner.hasModifierProperty(PsiModifier.STATIC) && !myMembersFlag) {
                // according settings we don't need to process such fields/methods
                return StaticProblem.staticAfterInstance;
            }
        }
        return StaticProblem.none;
    }

    public boolean satisfies(@Nonnull PsiElement element, @Nonnull ResolveState state) {
        String name = PsiUtilCore.getName(element);
        return name != null
            && StringUtil.isNotEmpty(name)
            && myMatcher.test(name)
            && myFilter.isClassAcceptable(element.getClass())
            && myFilter.isAcceptable(new CandidateInfo(element, state.get(PsiSubstitutor.KEY)), myElement);
    }

    public void setQualifierType(@Nullable PsiType qualifierType) {
        myQualifierType = qualifierType;
        myQualifierClass = PsiUtil.resolveClassInClassTypeOnly(qualifierType);
    }

    @Nullable
    public PsiType getQualifierType() {
        return myQualifierType;
    }

    public boolean isAccessible(@Nullable PsiElement element) {
        // if checkAccess is false, we only show inaccessible source elements because their access modifiers can be changed later by the user.
        // compiled element can't be changed so we don't pollute the completion with them. In Javadoc, everything is allowed.
        if (!myOptions.checkAccess && myInJavaDoc) {
            return true;
        }
        if (!(element instanceof PsiMember member)) {
            return true;
        }

        PsiClass accessObjectClass = myQualified ? myQualifierClass : null;
        //noinspection SimplifiableIfStatement
        if (JavaPsiFacade.getInstance(element.getProject()).getResolveHelper()
            .isAccessible(member, member.getModifierList(), myElement, accessObjectClass, myDeclarationHolder)) {
            return true;
        }
        return !myOptions.checkAccess && !(element instanceof PsiCompiledElement);
    }

    public void setCompletionElements(@Nonnull Object[] elements) {
        for (Object element : elements) {
            CompletionElement completion = new CompletionElement(element, PsiSubstitutor.EMPTY);
            myResults.put(completion, completion);
        }
    }

    public Iterable<CompletionElement> getResults() {
        if (mySecondRateResults.size() == myResults.size()) {
            return mySecondRateResults;
        }
        return ContainerUtil.filter(myResults.values(), element -> !mySecondRateResults.contains(element));
    }

    public void clear() {
        myResults.clear();
        mySecondRateResults.clear();
    }

    @Override
    public boolean shouldProcess(DeclarationKind kind) {
        switch (kind) {
            case CLASS:
                return myFilter.isClassAcceptable(PsiClass.class);

            case FIELD:
                return myFilter.isClassAcceptable(PsiField.class);

            case METHOD:
                return myFilter.isClassAcceptable(PsiMethod.class);

            case PACKAGE:
                return myFilter.isClassAcceptable(PsiPackage.class);

            case VARIABLE:
                return myFilter.isClassAcceptable(PsiVariable.class);

            case ENUM_CONST:
                return myFilter.isClassAcceptable(PsiEnumConstant.class);
        }

        return false;
    }

    @Override
    public <T> T getHint(@Nonnull Key<T> hintKey) {
        if (hintKey == ElementClassHint.KEY) {
            //noinspection unchecked
            return (T)this;
        }
        if (hintKey == JavaCompletionHints.NAME_FILTER) {
            //noinspection unchecked
            return (T)myMatcher;
        }

        return super.getHint(hintKey);
    }

    public static class Options {
        public static final Options DEFAULT_OPTIONS = new Options(true, true, false);
        public static final Options CHECK_NOTHING = new Options(false, false, false);
        final boolean checkAccess;
        final boolean filterStaticAfterInstance;
        final boolean showInstanceInStaticContext;

        private Options(boolean checkAccess, boolean filterStaticAfterInstance, boolean showInstanceInStaticContext) {
            this.checkAccess = checkAccess;
            this.filterStaticAfterInstance = filterStaticAfterInstance;
            this.showInstanceInStaticContext = showInstanceInStaticContext;
        }

        public Options withCheckAccess(boolean checkAccess) {
            return new Options(checkAccess, filterStaticAfterInstance, showInstanceInStaticContext);
        }

        public Options withFilterStaticAfterInstance(boolean filterStaticAfterInstance) {
            return new Options(checkAccess, filterStaticAfterInstance, showInstanceInStaticContext);
        }

        public Options withShowInstanceInStaticContext(boolean showInstanceInStaticContext) {
            return new Options(checkAccess, filterStaticAfterInstance, showInstanceInStaticContext);
        }
    }

    private enum StaticProblem {
        none,
        staticAfterInstance,
        instanceAfterStatic
    }
}
