/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.java.impl.find.findUsages;

import com.intellij.java.analysis.impl.find.findUsages.JavaFindUsagesHelper;
import com.intellij.java.analysis.impl.psi.impl.search.ThrowSearchUtil;
import com.intellij.java.impl.ide.util.SuperMethodWarningUtil;
import com.intellij.java.impl.refactoring.util.JavaNonCodeSearchElementDescriptionProvider;
import com.intellij.java.indexing.search.searches.MethodReferencesSearch;
import com.intellij.java.indexing.search.searches.OverridingMethodsSearch;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.codeStyle.VariableKind;
import com.intellij.java.language.psi.util.PropertyUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.content.scope.SearchScope;
import consulo.dataContext.DataContext;
import consulo.find.FindUsagesHandler;
import consulo.find.FindUsagesOptions;
import consulo.find.localize.FindLocalize;
import consulo.find.ui.AbstractFindUsagesDialog;
import consulo.language.editor.refactoring.util.NonCodeSearchDescriptionLocation;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiPackage;
import consulo.language.psi.PsiReference;
import consulo.language.psi.PsiUtilCore;
import consulo.logging.Logger;
import consulo.platform.base.localize.CommonLocalize;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.usage.UsageInfo;
import consulo.util.collection.ContainerUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;
import java.util.function.Predicate;

/**
 * @author peter
 */
public class JavaFindUsagesHandler extends FindUsagesHandler {
    private static final Logger LOG = Logger.getInstance(JavaFindUsagesHandler.class);

    private final PsiElement[] myElementsToSearch;
    private final JavaFindUsagesHandlerFactory myFactory;

    public JavaFindUsagesHandler(@Nonnull PsiElement psiElement, @Nonnull JavaFindUsagesHandlerFactory factory) {
        this(psiElement, PsiElement.EMPTY_ARRAY, factory);
    }

    public JavaFindUsagesHandler(
        @Nonnull PsiElement psiElement,
        @Nonnull PsiElement[] elementsToSearch,
        @Nonnull JavaFindUsagesHandlerFactory factory
    ) {
        super(psiElement);
        myElementsToSearch = elementsToSearch;
        myFactory = factory;
    }

    @Override
    @Nonnull
    public AbstractFindUsagesDialog getFindUsagesDialog(
        boolean isSingleFile,
        boolean toShowInNewTab,
        boolean mustOpenInNewTab
    ) {
        PsiElement element = getPsiElement();
        if (element instanceof PsiPackage) {
            return new FindPackageUsagesDialog(
                element,
                getProject(),
                myFactory.getFindPackageOptions(),
                toShowInNewTab,
                mustOpenInNewTab,
                isSingleFile,
                this
            );
        }
        if (element instanceof PsiClass) {
            return new FindClassUsagesDialog(
                element,
                getProject(),
                myFactory.getFindClassOptions(),
                toShowInNewTab,
                mustOpenInNewTab,
                isSingleFile,
                this
            );
        }
        if (element instanceof PsiMethod) {
            return new FindMethodUsagesDialog(
                element,
                getProject(),
                myFactory.getFindMethodOptions(),
                toShowInNewTab,
                mustOpenInNewTab,
                isSingleFile,
                this
            );
        }
        if (element instanceof PsiVariable) {
            return new FindVariableUsagesDialog(
                element,
                getProject(),
                myFactory.getFindVariableOptions(),
                toShowInNewTab,
                mustOpenInNewTab,
                isSingleFile,
                this
            );
        }
        if (ThrowSearchUtil.isSearchable(element)) {
            return new FindThrowUsagesDialog(
                element,
                getProject(),
                myFactory.getFindThrowOptions(),
                toShowInNewTab,
                mustOpenInNewTab,
                isSingleFile,
                this
            );
        }
        return super.getFindUsagesDialog(isSingleFile, toShowInNewTab, mustOpenInNewTab);
    }

    @RequiredUIAccess
    private static boolean askWhetherShouldSearchForParameterInOverridingMethods(
        @Nonnull PsiElement psiElement,
        @Nonnull PsiParameter parameter
    ) {
        return Messages.showOkCancelDialog(
            psiElement.getProject(),
            FindLocalize.findParameterUsagesInOverridingMethodsPrompt(parameter.getName()).get(),
            FindLocalize.findParameterUsagesInOverridingMethodsTitle().get(),
            CommonLocalize.buttonYes().get(),
            CommonLocalize.buttonNo().get(),
            UIUtil.getQuestionIcon()
        ) == Messages.OK;
    }

    @Nonnull
    private static PsiElement[] getParameterElementsToSearch(
        @Nonnull PsiParameter parameter,
        @Nonnull PsiMethod method
    ) {
        PsiMethod[] overrides = OverridingMethodsSearch.search(method, true).toArray(PsiMethod.EMPTY_ARRAY);
        for (int i = 0; i < overrides.length; i++) {
            PsiElement navigationElement = overrides[i].getNavigationElement();
            if (navigationElement instanceof PsiMethod psiMethod) {
                overrides[i] = psiMethod;
            }
        }
        List<PsiElement> elementsToSearch = new ArrayList<>(overrides.length + 1);
        elementsToSearch.add(parameter);
        int idx = method.getParameterList().getParameterIndex(parameter);
        for (PsiMethod override : overrides) {
            PsiParameter[] parameters = override.getParameterList().getParameters();
            if (idx < parameters.length) {
                elementsToSearch.add(parameters[idx]);
            }
        }
        return PsiUtilCore.toPsiElementArray(elementsToSearch);
    }


    @Nonnull
    @Override
    @RequiredUIAccess
    public PsiElement[] getPrimaryElements() {
        PsiElement element = getPsiElement();
        if (element instanceof PsiParameter parameter
            && parameter.getDeclarationScope() instanceof PsiMethod method
            && PsiUtil.canBeOverriden(method)) {
            PsiClass aClass = method.getContainingClass();
            LOG.assertTrue(aClass != null); //Otherwise can not be overriden

            boolean hasOverridden = OverridingMethodsSearch.search(method).findFirst() != null;
            if (hasOverridden && askWhetherShouldSearchForParameterInOverridingMethods(parameter, parameter)) {
                return getParameterElementsToSearch(parameter, method);
            }
        }
        return myElementsToSearch.length == 0 ? new PsiElement[]{element} : myElementsToSearch;
    }

    @Override
    @Nonnull
    @RequiredUIAccess
    public PsiElement[] getSecondaryElements() {
        PsiElement element = getPsiElement();
        if (element.getApplication().isUnitTestMode()) {
            return PsiElement.EMPTY_ARRAY;
        }
        if (element instanceof PsiField field) {
            PsiClass containingClass = field.getContainingClass();
            if (containingClass != null) {
                String fieldName = field.getName();
                String propertyName = JavaCodeStyleManager.getInstance(getProject())
                    .variableNameToPropertyName(fieldName, VariableKind.FIELD);
                Set<PsiMethod> accessors = new HashSet<>();
                boolean isStatic = field.isStatic();
                PsiMethod getter = PropertyUtil.findPropertyGetterWithType(
                    propertyName,
                    isStatic,
                    field.getType(),
                    List.of(containingClass.getMethods()).iterator()
                );
                if (getter != null) {
                    accessors.add(getter);
                }
                PsiMethod setter = PropertyUtil.findPropertySetterWithType(
                    propertyName,
                    isStatic,
                    field.getType(),
                    List.of(containingClass.getMethods()).iterator()
                );
                if (setter != null) {
                    accessors.add(setter);
                }
                accessors.addAll(PropertyUtil.getAccessors(containingClass, fieldName));
                if (!accessors.isEmpty()) {
                    boolean containsPhysical = ContainerUtil.find(accessors, PsiElement::isPhysical) != null;
                    boolean doSearch = !containsPhysical || Messages.showOkCancelDialog(
                        FindLocalize.findFieldAccessorsPrompt(fieldName).get(),
                        FindLocalize.findFieldAccessorsTitle().get(),
                        CommonLocalize.buttonYes().get(),
                        CommonLocalize.buttonNo().get(),
                        UIUtil.getQuestionIcon()
                    ) == Messages.OK;
                    if (doSearch) {
                        Set<PsiElement> elements = new HashSet<>();
                        for (PsiMethod accessor : accessors) {
                            ContainerUtil.addAll(
                                elements,
                                SuperMethodWarningUtil.checkSuperMethods(accessor, FindLocalize.findSuperMethodWarningActionVerb())
                            );
                        }
                        return PsiUtilCore.toPsiElementArray(elements);
                    }
                }
            }
        }
        return super.getSecondaryElements();
    }

    @Override
    @Nonnull
    public FindUsagesOptions getFindUsagesOptions(@Nullable DataContext dataContext) {
        PsiElement element = getPsiElement();
        if (element instanceof PsiPackage) {
            return myFactory.getFindPackageOptions();
        }
        if (element instanceof PsiClass) {
            return myFactory.getFindClassOptions();
        }
        if (element instanceof PsiMethod) {
            return myFactory.getFindMethodOptions();
        }
        if (element instanceof PsiVariable) {
            return myFactory.getFindVariableOptions();
        }
        if (ThrowSearchUtil.isSearchable(element)) {
            return myFactory.getFindThrowOptions();
        }
        return super.getFindUsagesOptions(dataContext);
    }

    @Override
    protected Set<String> getStringsToSearch(@Nonnull PsiElement element) {
        return JavaFindUsagesHelper.getElementNames(element);
    }

    @Override
    public boolean processElementUsages(
        @Nonnull PsiElement element,
        @Nonnull Predicate<UsageInfo> processor,
        @Nonnull FindUsagesOptions options
    ) {
        return JavaFindUsagesHelper.processElementUsages(element, options, processor);
    }

    @Override
    protected boolean isSearchForTextOccurencesAvailable(@Nonnull PsiElement psiElement, boolean isSingleFile) {
        return !isSingleFile && new JavaNonCodeSearchElementDescriptionProvider()
            .getElementDescription(psiElement, NonCodeSearchDescriptionLocation.NON_JAVA) != null;
    }

    @Nonnull
    @Override
    public Collection<PsiReference> findReferencesToHighlight(@Nonnull PsiElement target, @Nonnull SearchScope searchScope) {
        if (target instanceof PsiMethod method) {
            PsiMethod[] superMethods = method.findDeepestSuperMethods();
            if (superMethods.length == 0) {
                return MethodReferencesSearch.search(method, searchScope, true).findAll();
            }
            Collection<PsiReference> result = new ArrayList<>();
            for (PsiMethod superMethod : superMethods) {
                result.addAll(MethodReferencesSearch.search(superMethod, searchScope, true).findAll());
            }
            return result;
        }
        return super.findReferencesToHighlight(target, searchScope);
    }
}
