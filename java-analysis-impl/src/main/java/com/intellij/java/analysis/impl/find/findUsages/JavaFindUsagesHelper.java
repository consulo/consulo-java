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
package com.intellij.java.analysis.impl.find.findUsages;

import com.intellij.java.analysis.impl.psi.impl.search.ThrowSearchUtil;
import com.intellij.java.indexing.search.searches.ClassInheritorsSearch;
import com.intellij.java.indexing.search.searches.FunctionalExpressionSearch;
import com.intellij.java.indexing.search.searches.MethodReferencesSearch;
import com.intellij.java.indexing.search.searches.OverridingMethodsSearch;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.targets.AliasingPsiTarget;
import com.intellij.java.language.psi.targets.AliasingPsiTargetMapper;
import com.intellij.java.language.psi.util.MethodSignature;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.AccessRule;
import consulo.application.Application;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.util.ReadActionProcessor;
import consulo.content.scope.SearchScope;
import consulo.document.util.TextRange;
import consulo.find.FindUsagesHelper;
import consulo.find.FindUsagesOptions;
import consulo.find.localize.FindLocalize;
import consulo.language.inject.InjectedLanguageManager;
import consulo.language.pom.PomService;
import consulo.language.pom.PomTarget;
import consulo.language.psi.*;
import consulo.language.psi.meta.PsiMetaData;
import consulo.language.psi.meta.PsiMetaOwner;
import consulo.language.psi.resolve.PsiElementProcessorAdapter;
import consulo.language.psi.resolve.PsiReferenceProcessorAdapter;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.logging.Logger;
import consulo.usage.UsageInfo;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Comparing;
import consulo.util.lang.function.ThrowableSupplier;
import consulo.xml.psi.xml.XmlAttributeValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class JavaFindUsagesHelper {
    private static final Logger LOG = Logger.getInstance(JavaFindUsagesHelper.class);

    @Nonnull
    public static Set<String> getElementNames(@Nonnull PsiElement element) {
        if (element instanceof PsiDirectory directory) {  // normalize a directory to a corresponding package
            PsiPackage aPackage = AccessRule.read(() -> JavaDirectoryService.getInstance().getPackage(directory));
            return aPackage == null ? Collections.emptySet() : getElementNames(aPackage);
        }

        Set<String> result = new HashSet<>();

        Application.get().runReadAction(() -> {
            if (element instanceof PsiPackage psiPackage) {
                ContainerUtil.addIfNotNull(result, psiPackage.getQualifiedName());
            }
            else if (element instanceof PsiClass psiClass) {
                String qname = psiClass.getQualifiedName();
                if (qname != null) {
                    result.add(qname);
                    PsiClass topLevelClass = PsiUtil.getTopLevelClass(element);
                    if (topLevelClass != null && !(topLevelClass instanceof PsiSyntheticClass)) {
                        String topName = topLevelClass.getQualifiedName();
                        assert topName != null : "topLevelClass : " + topLevelClass + ";" +
                            " element: " + element + " (" + qname + ")" +
                            " top level file: " + InjectedLanguageManager.getInstance(element.getProject()).getTopLevelFile(element);
                        if (qname.length() > topName.length()) {
                            result.add(topName + qname.substring(topName.length()).replace('.', '$'));
                        }
                    }
                }
            }
            else if (element instanceof PsiMethod method) {
                ContainerUtil.addIfNotNull(result, method.getName());
            }
            else if (element instanceof PsiVariable variable) {
                ContainerUtil.addIfNotNull(result, variable.getName());
            }
            else if (element instanceof PsiMetaOwner metaOwner) {
                PsiMetaData metaData = metaOwner.getMetaData();
                if (metaData != null) {
                    ContainerUtil.addIfNotNull(result, metaData.getName());
                }
            }
            else if (element instanceof PsiNamedElement namedElement) {
                ContainerUtil.addIfNotNull(result, namedElement.getName());
            }
            else if (element instanceof XmlAttributeValue xmlAttrValue) {
                ContainerUtil.addIfNotNull(result, xmlAttrValue.getValue());
            }
            else {
                LOG.error("Unknown element type: " + element);
            }
        });

        return result;
    }

    public static boolean processElementUsages(
        @Nonnull PsiElement element,
        @Nonnull FindUsagesOptions options,
        @Nonnull Predicate<UsageInfo> processor
    ) {
        if (options instanceof JavaVariableFindUsagesOptions varOptions) {
            if (varOptions.isReadAccess || varOptions.isWriteAccess) {
                if (varOptions.isReadAccess && varOptions.isWriteAccess) {
                    if (!addElementUsages(element, options, processor)) {
                        return false;
                    }
                }
                else {
                    if (!addElementUsages(
                        element,
                        varOptions,
                        info -> {
                            boolean isWrite = info.getElement() instanceof PsiExpression expression
                                && PsiUtil.isAccessedForWriting(expression);
                            return isWrite != varOptions.isWriteAccess || processor.test(info);
                        }
                    )) {
                        return false;
                    }
                }
            }
        }
        else if (options.isUsages) {
            if (!addElementUsages(element, options, processor)) {
                return false;
            }
        }

        boolean success = AccessRule.read(() -> {
            if (ThrowSearchUtil.isSearchable(element)
                && options instanceof JavaThrowFindUsagesOptions throwOptions
                && options.isUsages) {
                ThrowSearchUtil.Root root = throwOptions.getRoot();
                if (root == null) {
                    ThrowSearchUtil.Root[] roots = ThrowSearchUtil.getSearchRoots(element);
                    if (roots != null && roots.length > 0) {
                        root = roots[0];
                    }
                }
                if (root != null) {
                    return ThrowSearchUtil.addThrowUsages(processor, root, options);
                }
            }
            return true;
        });
        if (!success) {
            return false;
        }

        if (options instanceof JavaPackageFindUsagesOptions packageOptions && packageOptions.isClassesUsages
            && !addClassesUsages((PsiPackage)element, packageOptions, processor)) {
            return false;
        }

        if (options instanceof JavaClassFindUsagesOptions classOptions) {
            PsiClass psiClass = (PsiClass)element;
            PsiManager manager = AccessRule.read(psiClass::getManager);
            if (classOptions.isMethodsUsages && !addMethodsUsages(psiClass, manager, classOptions, processor)) {
                return false;
            }
            if (classOptions.isFieldsUsages && !addFieldsUsages(psiClass, manager, classOptions, processor)) {
                return false;
            }
            if (AccessRule.read(psiClass::isInterface)) {
                if (classOptions.isDerivedInterfaces) {
                    if (classOptions.isImplementingClasses) {
                        if (!addInheritors(psiClass, classOptions, processor)) {
                            return false;
                        }
                    }
                    else if (!addDerivedInterfaces(psiClass, classOptions, processor)) {
                        return false;
                    }
                }
                else if (classOptions.isImplementingClasses && !addImplementingClasses(psiClass, classOptions, processor)) {
                    return false;
                }

                if (classOptions.isImplementingClasses) {
                    FunctionalExpressionSearch.search(psiClass, classOptions.searchScope)
                        .forEach(new PsiElementProcessorAdapter<>(expression -> addResult(expression, options, processor)));
                }
            }
            else if (classOptions.isDerivedClasses && !addInheritors(psiClass, classOptions, processor)) {
                return false;
            }
        }

        if (options instanceof JavaMethodFindUsagesOptions methodOptions) {
            PsiMethod psiMethod = (PsiMethod)element;
            boolean isAbstract = AccessRule.read(psiMethod::isAbstract);
            if (isAbstract && methodOptions.isImplementingMethods || methodOptions.isOverridingMethods) {
                if (!processOverridingMethods(psiMethod, processor, methodOptions)) {
                    return false;
                }
                FunctionalExpressionSearch.search(psiMethod, methodOptions.searchScope)
                    .forEach(new PsiElementProcessorAdapter<>(expression -> addResult(expression, options, processor)));
            }
        }

        if (element instanceof PomTarget pomTarget && !addAliasingUsages(pomTarget, options, processor)) {
            return false;
        }
        Boolean isSearchable = AccessRule.read(() -> ThrowSearchUtil.isSearchable(element));
        if (!isSearchable && options.isSearchForTextOccurrences && options.searchScope instanceof GlobalSearchScope globalSearchScope) {
            Collection<String> stringsToSearch =
                Application.get().runReadAction((Supplier<Collection<String>>)() -> getElementNames(element));
            // todo add to fastTrack
            if (!FindUsagesHelper.processUsagesInText(element, stringsToSearch, globalSearchScope, processor)) {
                return false;
            }
        }
        return true;
    }

    private static boolean addAliasingUsages(
        @Nonnull PomTarget pomTarget,
        @Nonnull FindUsagesOptions options,
        @Nonnull Predicate<UsageInfo> processor
    ) {
        for (AliasingPsiTargetMapper aliasingPsiTargetMapper : AliasingPsiTargetMapper.EP_NAME.getExtensionList()) {
            for (AliasingPsiTarget psiTarget : aliasingPsiTargetMapper.getTargets(pomTarget)) {
                boolean success = ReferencesSearch.search(new ReferencesSearch.SearchParameters(
                        AccessRule.read(() -> PomService.convertToPsi(psiTarget)),
                        options.searchScope,
                        false,
                        options.fastTrack
                    ))
                    .forEach(new ReadActionProcessor<>() {
                        @Override
                        @RequiredReadAction
                        public boolean processInReadAction(PsiReference reference) {
                            return addResult(reference, options, processor);
                        }
                    });
                if (!success) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean processOverridingMethods(
        @Nonnull PsiMethod psiMethod,
        @Nonnull Predicate<UsageInfo> processor,
        @Nonnull JavaMethodFindUsagesOptions options
    ) {
        return OverridingMethodsSearch.search(psiMethod, options.searchScope, options.isCheckDeepInheritance)
            .forEach(new PsiElementProcessorAdapter<>(
                element -> addResult(element.getNavigationElement(), options, processor)
            ));
    }

    private static boolean addClassesUsages(
        @Nonnull PsiPackage aPackage,
        @Nonnull JavaPackageFindUsagesOptions options,
        @Nonnull Predicate<UsageInfo> processor
    ) {
        ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
        if (progress != null) {
            progress.pushState();
        }

        try {
            List<PsiClass> classes = new ArrayList<>();
            addClassesInPackage(aPackage, options.isIncludeSubpackages, classes);
            for (PsiClass aClass : classes) {
                if (progress != null) {
                    String name = AccessRule.read(aClass::getName);
                    progress.setTextValue(FindLocalize.findSearchingForReferencesToClassProgress(name));
                }
                ProgressManager.checkCanceled();
                boolean success = ReferencesSearch.search(
                        new ReferencesSearch.SearchParameters(aClass, options.searchScope, false, options.fastTrack)
                    )
                    .forEach(new ReadActionProcessor<>() {
                        @Override
                        @RequiredReadAction
                        public boolean processInReadAction(PsiReference psiReference) {
                            return addResult(psiReference, options, processor);
                        }
                    });
                if (!success) {
                    return false;
                }
            }
        }
        finally {
            if (progress != null) {
                progress.popState();
            }
        }

        return true;
    }

    private static void addClassesInPackage(@Nonnull PsiPackage aPackage, boolean includeSubpackages, @Nonnull List<PsiClass> array) {
        PsiDirectory[] dirs = AccessRule.read((ThrowableSupplier<PsiDirectory[], RuntimeException>)aPackage::getDirectories);
        for (PsiDirectory dir : dirs) {
            addClassesInDirectory(dir, includeSubpackages, array);
        }
    }

    private static void addClassesInDirectory(
        @Nonnull PsiDirectory dir,
        boolean includeSubdirs,
        @Nonnull List<PsiClass> array
    ) {
        Application.get().runReadAction(() -> {
            PsiClass[] classes = JavaDirectoryService.getInstance().getClasses(dir);
            ContainerUtil.addAll(array, classes);
            if (includeSubdirs) {
                PsiDirectory[] dirs = dir.getSubdirectories();
                for (PsiDirectory directory : dirs) {
                    addClassesInDirectory(directory, true, array);
                }
            }
        });
    }

    private static boolean addMethodsUsages(
        @Nonnull PsiClass aClass,
        @Nonnull PsiManager manager,
        @Nonnull JavaClassFindUsagesOptions options,
        @Nonnull Predicate<UsageInfo> processor
    ) {
        if (options.isIncludeInherited) {
            PsiMethod[] methods = AccessRule.read(aClass::getAllMethods);
            for (int i = 0; i < methods.length; i++) {
                PsiMethod method = methods[i];
                // filter overridden methods
                int finalI = i;
                PsiClass methodClass = AccessRule.read(() -> {
                    MethodSignature methodSignature = method.getSignature(PsiSubstitutor.EMPTY);
                    for (int j = 0; j < finalI; j++) {
                        if (methodSignature.equals(methods[j].getSignature(PsiSubstitutor.EMPTY))) {
                            return null;
                        }
                    }
                    return method.getContainingClass();
                });
                if (methodClass == null) {
                    continue;
                }
                boolean equivalent = AccessRule.read(() -> manager.areElementsEquivalent(methodClass, aClass));
                if (equivalent) {
                    if (!addElementUsages(method, options, processor)) {
                        return false;
                    }
                }
                else {
                    MethodReferencesSearch.SearchParameters parameters =
                        new MethodReferencesSearch.SearchParameters(method, options.searchScope, true, options.fastTrack);
                    boolean success = MethodReferencesSearch.search(parameters).forEach(new PsiReferenceProcessorAdapter(reference -> {
                        addResultFromReference(reference, methodClass, manager, aClass, options, processor);
                        return true;
                    }));
                    if (!success) {
                        return false;
                    }
                }
            }
        }
        else {
            PsiMethod[] methods = AccessRule.read(aClass::getMethods);
            for (PsiMethod method : methods) {
                if (!addElementUsages(method, options, processor)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean addFieldsUsages(
        @Nonnull PsiClass aClass,
        @Nonnull PsiManager manager,
        @Nonnull JavaClassFindUsagesOptions options,
        @Nonnull Predicate<UsageInfo> processor
    ) {
        if (options.isIncludeInherited) {
            PsiField[] fields = AccessRule.read(aClass::getAllFields);
            for (int i = 0; i < fields.length; i++) {
                PsiField field = fields[i];
                // filter hidden fields
                int finalI = i;
                ThrowableSupplier<PsiClass, RuntimeException> action = () -> {
                    for (int j = 0; j < finalI; j++) {
                        if (Comparing.strEqual(field.getName(), fields[j].getName())) {
                            return null;
                        }
                    }
                    return field.getContainingClass();
                };
                PsiClass fieldClass = AccessRule.read(action);
                if (fieldClass == null) {
                    continue;
                }
                boolean equivalent = AccessRule.read(() -> manager.areElementsEquivalent(fieldClass, aClass));
                if (equivalent) {
                    if (!addElementUsages(fields[i], options, processor)) {
                        return false;
                    }
                }
                else {
                    boolean success = ReferencesSearch.search(
                            new ReferencesSearch.SearchParameters(field, options.searchScope, false, options.fastTrack)
                        )
                        .forEach(new ReadActionProcessor<>() {
                            @Override
                            @RequiredReadAction
                            public boolean processInReadAction(PsiReference reference) {
                                return addResultFromReference(reference, fieldClass, manager, aClass, options, processor);
                            }
                        });
                    if (!success) {
                        return false;
                    }
                }
            }
        }
        else {
            for (PsiField field : AccessRule.read(aClass::getFields)) {
                if (!addElementUsages(field, options, processor)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Nullable
    @RequiredReadAction
    private static PsiClass getFieldOrMethodAccessedClass(@Nonnull PsiReferenceExpression ref, @Nonnull PsiClass fieldOrMethodClass) {
        PsiElement[] children = ref.getChildren();
        if (children.length > 1 && children[0] instanceof PsiExpression expr) {
            PsiType type = expr.getType();
            if (type != null) {
                return type instanceof PsiClassType ? PsiUtil.resolveClassInType(type) : null;
            }
            else {
                if (expr instanceof PsiReferenceExpression refExpr && refExpr.resolve() instanceof PsiClass psiClass) {
                    return psiClass;
                }
                return null;
            }
        }
        PsiManager manager = ref.getManager();
        for (PsiElement parent = ref; parent != null; parent = parent.getParent()) {
            if (parent instanceof PsiClass psiClass && (manager.areElementsEquivalent(parent, fieldOrMethodClass)
                || psiClass.isInheritor(fieldOrMethodClass, true))) {
                return psiClass;
            }
        }
        return null;
    }

    private static boolean addInheritors(
        @Nonnull PsiClass aClass,
        @Nonnull JavaClassFindUsagesOptions options,
        @Nonnull Predicate<UsageInfo> processor
    ) {
        return ClassInheritorsSearch.search(aClass, options.searchScope, options.isCheckDeepInheritance)
            .forEach(new PsiElementProcessorAdapter<>(element -> addResult(element, options, processor)));
    }

    private static boolean addDerivedInterfaces(
        @Nonnull PsiClass anInterface,
        @Nonnull JavaClassFindUsagesOptions options,
        @Nonnull Predicate<UsageInfo> processor
    ) {
        return ClassInheritorsSearch.search(anInterface, options.searchScope, options.isCheckDeepInheritance).forEach(
            new PsiElementProcessorAdapter<>(inheritor -> !inheritor.isInterface() || addResult(inheritor, options, processor))
        );
    }

    private static boolean addImplementingClasses(
        @Nonnull PsiClass anInterface,
        @Nonnull JavaClassFindUsagesOptions options,
        @Nonnull Predicate<UsageInfo> processor
    ) {
        return ClassInheritorsSearch.search(anInterface, options.searchScope, options.isCheckDeepInheritance).forEach(
            new PsiElementProcessorAdapter<>(inheritor -> inheritor.isInterface() || addResult(inheritor, options, processor))
        );
    }

    @RequiredReadAction
    private static boolean addResultFromReference(
        @Nonnull PsiReference reference,
        @Nonnull PsiClass methodClass,
        @Nonnull PsiManager manager,
        @Nonnull PsiClass aClass,
        @Nonnull FindUsagesOptions options,
        @Nonnull Predicate<UsageInfo> processor
    ) {
        PsiElement refElement = reference.getElement();
        if (refElement instanceof PsiReferenceExpression refExpr) {
            PsiClass usedClass = getFieldOrMethodAccessedClass(refExpr, methodClass);
            if (usedClass != null && (manager.areElementsEquivalent(usedClass, aClass) || usedClass.isInheritor(aClass, true))) {
                if (!addResult(refElement, options, processor)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean addElementUsages(
        @Nonnull PsiElement element,
        @Nonnull FindUsagesOptions options,
        @Nonnull Predicate<UsageInfo> processor
    ) {
        SearchScope searchScope = options.searchScope;
        PsiClass[] parentClass = new PsiClass[1];
        if (element instanceof PsiMethod method && AccessRule.read(() -> {
            parentClass[0] = method.getContainingClass();
            return method.isConstructor();
        })) {
            if (parentClass[0] != null) {
                boolean strictSignatureSearch =
                    !(options instanceof JavaMethodFindUsagesOptions methodOptions) || !methodOptions.isIncludeOverloadUsages;
                return MethodReferencesSearch.search(new MethodReferencesSearch.SearchParameters(
                    method,
                    searchScope,
                    strictSignatureSearch,
                    options.fastTrack
                )).forEach(new ReadActionProcessor<>() {
                    @Override
                    @RequiredReadAction
                    public boolean processInReadAction(PsiReference ref) {
                        return addResult(ref, options, processor);
                    }
                });
            }
            return true;
        }

        ReadActionProcessor<PsiReference> consumer = new ReadActionProcessor<>() {
            @Override
            @RequiredReadAction
            public boolean processInReadAction(PsiReference ref) {
                return addResult(ref, options, processor);
            }
        };

        if (element instanceof PsiMethod method) {
            boolean strictSignatureSearch = !(options instanceof JavaMethodFindUsagesOptions methodOptions) || // field with getter
                !methodOptions.isIncludeOverloadUsages;
            return MethodReferencesSearch.search(new MethodReferencesSearch.SearchParameters(
                method,
                searchScope,
                strictSignatureSearch,
                options.fastTrack
            )).forEach(consumer);
        }
        return ReferencesSearch.search(new ReferencesSearch.SearchParameters(element, searchScope, false, options.fastTrack))
            .forEach(consumer);
    }

    @RequiredReadAction
    private static boolean addResult(
        @Nonnull PsiElement element,
        @Nonnull FindUsagesOptions options,
        @Nonnull Predicate<UsageInfo> processor
    ) {
        return !filterUsage(element, options) || processor.test(new UsageInfo(element));
    }

    @RequiredReadAction
    private static boolean addResult(
        @Nonnull PsiReference ref,
        @Nonnull FindUsagesOptions options,
        @Nonnull Predicate<UsageInfo> processor
    ) {
        if (filterUsage(ref.getElement(), options)) {
            TextRange rangeInElement = ref.getRangeInElement();
            return processor.test(new UsageInfo(
                ref.getElement(),
                rangeInElement.getStartOffset(),
                rangeInElement.getEndOffset(),
                false
            ));
        }
        return true;
    }

    @RequiredReadAction
    private static boolean filterUsage(PsiElement usage, @Nonnull FindUsagesOptions options) {
        if (!(usage instanceof PsiJavaCodeReferenceElement)) {
            return true;
        }
        if (options instanceof JavaPackageFindUsagesOptions packageOptions
            && !packageOptions.isIncludeSubpackages
            && ((PsiReference)usage).resolve() instanceof PsiPackage
            && usage.getParent() instanceof PsiJavaCodeReferenceElement codeRef
            && codeRef.resolve() instanceof PsiPackage) {
            return false;
        }

        if (!(usage instanceof PsiReferenceExpression)) {
            if (options instanceof JavaFindUsagesOptions javaOptions && javaOptions.isSkipImportStatements) {
                PsiElement parent = usage.getParent();
                while (parent instanceof PsiJavaCodeReferenceElement) {
                    parent = parent.getParent();
                }
                if (parent instanceof PsiImportStatement) {
                    return false;
                }
            }

            if (options instanceof JavaPackageFindUsagesOptions packageOptions && packageOptions.isSkipPackageStatements) {
                PsiElement parent = usage.getParent();
                while (parent instanceof PsiJavaCodeReferenceElement) {
                    parent = parent.getParent();
                }
                if (parent instanceof PsiPackageStatement) {
                    return false;
                }
            }
        }
        return true;
    }
}
