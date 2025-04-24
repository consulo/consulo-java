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
package com.intellij.java.language.impl.psi.impl;

import com.intellij.java.language.impl.psi.impl.file.impl.JavaFileManager;
import com.intellij.java.language.impl.psi.impl.source.JavaDummyHolder;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ServiceImpl;
import consulo.application.progress.ProgressIndicatorProvider;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.language.psi.*;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.project.DumbService;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.SmartList;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.*;
import java.util.function.Predicate;

/**
 * @author max
 */
@Singleton
@ServiceImpl
public class JavaPsiFacadeImpl extends JavaPsiFacadeEx {
    private final PsiConstantEvaluationHelper myConstantEvaluationHelper;

    private final Project myProject;
    private final PsiPackageManager myPackageManager;
    private final PsiResolveHelper myPsiResolveHelper;
    private final PsiElementFactory myPsiElementFactory;
    private final PsiNameHelper myPsiNameHelper;

    private final Map<GlobalSearchScope, Map<String, Collection<PsiJavaModule>>> myModulesByScopeCache =
        ContainerUtil.createConcurrentSoftKeySoftValueMap();

    private final Map<VirtualFile, PsiJavaModule> myModuleScopeByFile = ContainerUtil.createConcurrentWeakKeyWeakValueMap();

    @Inject
    public JavaPsiFacadeImpl(
        Project project,
        PsiResolveHelper psiResolveHelper,
        PsiPackageManager psiManager,
        PsiElementFactory psiElementFactory,
        PsiNameHelper psiNameHelper
    ) {
        myProject = project;
        myPackageManager = psiManager;
        myPsiResolveHelper = psiResolveHelper;
        myPsiElementFactory = psiElementFactory;
        myPsiNameHelper = psiNameHelper;
        myConstantEvaluationHelper = new PsiConstantEvaluationHelperImpl();

        project.getMessageBus().connect().subscribe(PsiModificationTrackerListener.class, () -> {
            myModulesByScopeCache.clear();
            myModuleScopeByFile.clear();
        });

        JavaElementType.ANNOTATION.getIndex(); // Initialize stubs.
    }

    @Override
    @RequiredReadAction
    public PsiClass findClass(@Nonnull String qualifiedName, @Nonnull GlobalSearchScope scope) {
        ProgressIndicatorProvider.checkCanceled(); // We hope this method is being called often enough to cancel daemon processes smoothly

        if (DumbService.getInstance(getProject()).isDumb()) {
            PsiClass[] classes = findClassesInDumbMode(qualifiedName, scope);
            if (classes.length != 0) {
                return classes[0];
            }
            return null;
        }

        for (PsiElementFinder finder : finders()) {
            PsiClass aClass = finder.findClass(qualifiedName, scope);
            if (aClass != null) {
                return aClass;
            }
        }

        return null;
    }

    @Nonnull
    @RequiredReadAction
    private PsiClass[] findClassesInDumbMode(@Nonnull String qualifiedName, @Nonnull GlobalSearchScope scope) {
        String packageName = StringUtil.getPackageName(qualifiedName);
        PsiJavaPackage pkg = findPackage(packageName);
        String className = StringUtil.getShortName(qualifiedName);
        if (pkg == null && packageName.length() < qualifiedName.length()) {
            PsiClass[] containingClasses = findClassesInDumbMode(packageName, scope);
            if (containingClasses.length == 1) {
                return PsiElementFinder.filterByName(className, containingClasses[0].getInnerClasses());
            }

            return PsiClass.EMPTY_ARRAY;
        }

        if (pkg == null || !pkg.containsClassNamed(className)) {
            return PsiClass.EMPTY_ARRAY;
        }

        return pkg.findClassByShortName(className, scope);
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public PsiClass[] findClasses(@Nonnull String qualifiedName, @Nonnull GlobalSearchScope scope) {
        if (DumbService.getInstance(getProject()).isDumb()) {
            return findClassesInDumbMode(qualifiedName, scope);
        }

        List<PsiClass> classes = new SmartList<>();
        for (PsiElementFinder finder : finders()) {
            PsiClass[] finderClasses = finder.findClasses(qualifiedName, scope);
            ContainerUtil.addAll(classes, finderClasses);
        }

        return classes.toArray(new PsiClass[classes.size()]);
    }

    @Nonnull
    private List<PsiElementFinder> finders() {
        return myProject.getExtensionList(PsiElementFinder.class);
    }

    @Nonnull
    @Override
    public PsiConstantEvaluationHelper getConstantEvaluationHelper() {
        return myConstantEvaluationHelper;
    }

    @Override
    @RequiredReadAction
    public PsiJavaPackage findPackage(@Nonnull String qualifiedName) {
        for (PsiElementFinder elementFinder : filteredFinders()) {
            PsiJavaPackage aPackage = elementFinder.findPackage(qualifiedName);
            if (aPackage != null) {
                return aPackage;
            }
        }
        return (PsiJavaPackage)myPackageManager.findPackage(qualifiedName, JavaModuleExtension.class);
    }

    @Nullable
    @Override
    public PsiJavaModule findModule(@Nonnull VirtualFile file) {
        PsiJavaModule psiJavaModule = myModuleScopeByFile.get(file);
        if (psiJavaModule != null) {
            return psiJavaModule;
        }

        for (PsiElementFinder finder : filteredFinders()) {
            PsiJavaModule module = finder.findModule(file);
            if (module != null) {
                myModuleScopeByFile.put(file, module);
                return module;
            }
        }
        return null;
    }

    @Override
    @Nonnull
    public PsiJavaPackage[] getSubPackages(@Nonnull PsiJavaPackage psiPackage, @Nonnull GlobalSearchScope scope) {
        LinkedHashSet<PsiJavaPackage> result = new LinkedHashSet<>();
        for (PsiElementFinder finder : filteredFinders()) {
            PsiJavaPackage[] packages = finder.getSubPackages(psiPackage, scope);
            ContainerUtil.addAll(result, packages);
        }

        return result.toArray(new PsiJavaPackage[result.size()]);
    }

    @Nonnull
    private List<PsiElementFinder> filteredFinders() {
        DumbService dumbService = DumbService.getInstance(getProject());
        List<PsiElementFinder> finders = finders();
        if (dumbService.isDumb()) {
            return dumbService.filterByDumbAwareness(finders);
        }
        return finders;
    }

    @Override
    @Nonnull
    public PsiJavaParserFacade getParserFacade() {
        return getElementFactory();
    }

    @Override
    @Nonnull
    public PsiResolveHelper getResolveHelper() {
        return myPsiResolveHelper;
    }

    @Override
    @Nonnull
    public PsiNameHelper getNameHelper() {
        return myPsiNameHelper;
    }

    @Nonnull
    @RequiredReadAction
    public Set<String> getClassNames(@Nonnull PsiJavaPackage psiPackage, @Nonnull GlobalSearchScope scope) {
        Set<String> result = new HashSet<>();
        for (PsiElementFinder finder : filteredFinders()) {
            result.addAll(finder.getClassNames(psiPackage, scope));
        }
        return result;
    }

    @Nonnull
    public PsiClass[] getClasses(@Nonnull PsiJavaPackage psiPackage, @Nonnull GlobalSearchScope scope) {
        List<PsiClass> result = null;
        for (PsiElementFinder finder : filteredFinders()) {
            PsiClass[] classes = finder.getClasses(psiPackage, scope);
            if (classes.length == 0) {
                continue;
            }
            if (result == null) {
                result = new ArrayList<>();
            }
            ContainerUtil.addAll(result, classes);
        }

        return result == null ? PsiClass.EMPTY_ARRAY : result.toArray(new PsiClass[result.size()]);
    }

    @Nonnull
    @RequiredReadAction
    public PsiFile[] getPackageFiles(@Nonnull PsiJavaPackage psiPackage, @Nonnull GlobalSearchScope scope) {
        Predicate<PsiFile> filter = null;

        for (PsiElementFinder finder : filteredFinders()) {
            Predicate<PsiFile> finderFilter = finder.getPackageFilesFilter(psiPackage, scope);
            if (finderFilter != null) {
                if (filter == null) {
                    filter = finderFilter;
                }
                else {
                    Predicate<PsiFile> oldFilter = filter;
                    filter = psiFile -> oldFilter.test(psiFile) && finderFilter.test(psiFile);
                }
            }
        }

        Set<PsiFile> result = new LinkedHashSet<>();
        PsiDirectory[] directories = psiPackage.getDirectories(scope);
        for (PsiDirectory directory : directories) {
            for (PsiFile file : directory.getFiles()) {
                if (filter == null || filter.test(file)) {
                    result.add(file);
                }
            }
        }

        for (PsiElementFinder finder : filteredFinders()) {
            Collections.addAll(result, finder.getPackageFiles(psiPackage, scope));
        }
        return result.toArray(PsiFile.EMPTY_ARRAY);
    }

    public boolean processPackageDirectories(
        @Nonnull PsiJavaPackage psiPackage,
        @Nonnull GlobalSearchScope scope,
        @Nonnull Predicate<PsiDirectory> consumer
    ) {
        for (PsiElementFinder finder : filteredFinders()) {
            if (!finder.processPackageDirectories(psiPackage, scope, consumer)) {
                return false;
            }
        }
        return true;
    }

    @RequiredReadAction
    public PsiClass[] findClassByShortName(String name, PsiJavaPackage psiPackage, GlobalSearchScope scope) {
        List<PsiClass> result = null;
        for (PsiElementFinder finder : filteredFinders()) {
            PsiClass[] classes = finder.getClasses(name, psiPackage, scope);
            if (classes.length == 0) {
                continue;
            }
            if (result == null) {
                result = new ArrayList<>();
            }
            ContainerUtil.addAll(result, classes);
        }

        return result == null ? PsiClass.EMPTY_ARRAY : result.toArray(new PsiClass[result.size()]);
    }


    @Override
    public boolean isPartOfPackagePrefix(@Nonnull String packageName) {
        Collection<String> packagePrefixes = JavaFileManager.getInstance(myProject).getNonTrivialPackagePrefixes();
        for (String subpackageName : packagePrefixes) {
            if (isSubpackageOf(subpackageName, packageName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSubpackageOf(@Nonnull String subpackageName, @Nonnull String packageName) {
        return subpackageName.equals(packageName) || subpackageName.startsWith(packageName) && subpackageName.charAt(packageName.length()) == '.';
    }

    @Override
    public boolean isInPackage(@Nonnull PsiElement element, @Nonnull PsiJavaPackage aPackage) {
        PsiFile file = FileContextUtil.getContextFile(element);
        if (file instanceof JavaDummyHolder javaDummyHolder) {
            return javaDummyHolder.isInPackage(aPackage);
        }
        if (file instanceof PsiJavaFile javaFile) {
            String packageName = javaFile.getPackageName();
            return packageName.equals(aPackage.getQualifiedName());
        }
        return false;
    }

    @Override
    public boolean arePackagesTheSame(@Nonnull PsiElement element1, @Nonnull PsiElement element2) {
        PsiFile file1 = FileContextUtil.getContextFile(element1);
        PsiFile file2 = FileContextUtil.getContextFile(element2);
        if (Comparing.equal(file1, file2)) {
            return true;
        }
        if (file1 instanceof JavaDummyHolder && file2 instanceof JavaDummyHolder) {
            return true;
        }
        if (file1 instanceof JavaDummyHolder || file2 instanceof JavaDummyHolder) {
            JavaDummyHolder dummyHolder = (JavaDummyHolder)(file1 instanceof JavaDummyHolder ? file1 : file2);
            PsiElement other = file1 instanceof JavaDummyHolder ? file2 : file1;
            return dummyHolder.isSamePackage(other);
        }
        return file1 instanceof PsiClassOwner pco1 && file2 instanceof PsiClassOwner pco2
            && Objects.equals(pco1.getPackageName(), pco2.getPackageName());
    }

    @Override
    @Nonnull
    public Project getProject() {
        return myProject;
    }

    @Override
    @Nonnull
    public PsiElementFactory getElementFactory() {
        return myPsiElementFactory;
    }

    @Override
    public PsiJavaModule findModule(@Nonnull String moduleName, @Nonnull GlobalSearchScope scope) {
        Collection<PsiJavaModule> modules = findModules(moduleName, scope);
        return modules.size() == 1 ? modules.iterator().next() : null;
    }

    @Nonnull
    @Override
    public Collection<PsiJavaModule> findModules(@Nonnull String moduleName, @Nonnull GlobalSearchScope scope) {
        return myModulesByScopeCache
            .computeIfAbsent(scope, k -> ContainerUtil.createConcurrentWeakValueMap())
            .computeIfAbsent(moduleName, k -> JavaFileManager.getInstance(myProject).findModules(k, scope));
    }
}
