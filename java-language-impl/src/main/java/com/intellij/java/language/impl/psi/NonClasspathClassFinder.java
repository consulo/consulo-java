/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.language.impl.psi;

import com.intellij.java.language.impl.psi.impl.file.PsiPackageImpl;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiClassOwner;
import com.intellij.java.language.psi.PsiElementFinder;
import com.intellij.java.language.psi.PsiJavaPackage;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.util.LowMemoryWatcher;
import consulo.application.util.function.CommonProcessors;
import consulo.component.messagebus.MessageBusConnection;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiPackageManager;
import consulo.language.psi.scope.EverythingGlobalScope;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.module.content.PackageDirectoryCache;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.MultiMap;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.SimpleReference;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.event.BulkFileListener;
import consulo.virtualFileSystem.event.VFileEvent;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * @author peter
 */
public abstract class NonClasspathClassFinder extends PsiElementFinder {
    private static final EverythingGlobalScope ALL_SCOPE = new EverythingGlobalScope();
    protected final Project myProject;
    private volatile PackageDirectoryCache myCache;
    private final PsiManager myManager;
    private final String[] myFileExtensions;
    private PsiPackageManager myPackageManager;

    public NonClasspathClassFinder(@Nonnull Project project, @Nonnull String... fileExtensions) {
        myProject = project;
        myPackageManager = PsiPackageManager.getInstance(myProject);
        myManager = PsiManager.getInstance(myProject);
        myFileExtensions = ArrayUtil.append(fileExtensions, "class");

        MessageBusConnection connection = project.getMessageBus().connect(project);
        connect(connection);

        LowMemoryWatcher.register(this::clearCache, project);
    }

    protected void connect(MessageBusConnection connection) {
        connection.subscribe(BulkFileListener.class, new BulkFileListener() {
            @Override
            public void after(@Nonnull List<? extends VFileEvent> events) {
                clearCache();
            }
        });
    }

    @Nonnull
    protected PackageDirectoryCache getCache(@Nullable GlobalSearchScope scope) {
        PackageDirectoryCache cache = myCache;
        if (cache == null) {
            myCache = cache = createCache(calcClassRoots());
        }
        return cache;
    }

    @Nonnull
    protected static PackageDirectoryCache createCache(@Nonnull List<VirtualFile> roots) {
        MultiMap<String, VirtualFile> map = MultiMap.create();
        map.putValues("", roots);
        return new PackageDirectoryCache(map);
    }

    public void clearCache() {
        myCache = null;
    }

    protected List<VirtualFile> getClassRoots(@Nullable GlobalSearchScope scope) {
        return getCache(scope).getDirectoriesByPackageName("");
    }

    public List<VirtualFile> getClassRoots() {
        return getClassRoots(ALL_SCOPE);
    }

    @Override
    public PsiClass findClass(@Nonnull String qualifiedName, @Nonnull GlobalSearchScope scope) {
        SimpleReference<PsiClass> result = SimpleReference.create();
        processDirectories(
            StringUtil.getPackageName(qualifiedName),
            scope,
            dir -> {
                VirtualFile virtualFile = findChild(dir, StringUtil.getShortName(qualifiedName), myFileExtensions);
                PsiFile file = virtualFile == null ? null : myManager.findFile(virtualFile);
                if (file instanceof PsiClassOwner classOwner) {
                    PsiClass[] classes = classOwner.getClasses();
                    if (classes.length == 1) {
                        result.set(classes[0]);
                        return false;
                    }
                }
                return true;
            }
        );
        return result.get();
    }

    protected abstract List<VirtualFile> calcClassRoots();

    @Nonnull
    @Override
    public PsiClass[] getClasses(@Nonnull PsiJavaPackage psiPackage, @Nonnull GlobalSearchScope scope) {
        List<PsiClass> result = new ArrayList<>();
        processDirectories(
            psiPackage.getQualifiedName(),
            scope,
            dir -> {
                for (VirtualFile file : dir.getChildren()) {
                    if (!file.isDirectory() && ArrayUtil.contains(file.getExtension(), myFileExtensions)) {
                        PsiFile psi = myManager.findFile(file);
                        if (psi instanceof PsiClassOwner classOwner) {
                            ContainerUtil.addAll(result, classOwner.getClasses());
                        }
                    }
                }
                return true;
            }
        );
        return result.toArray(new PsiClass[result.size()]);
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public Set<String> getClassNames(@Nonnull PsiJavaPackage psiPackage, @Nonnull GlobalSearchScope scope) {
        Set<String> result = new HashSet<>();
        processDirectories(
            psiPackage.getQualifiedName(),
            scope,
            dir -> {
                for (VirtualFile file : dir.getChildren()) {
                    if (!file.isDirectory() && ArrayUtil.contains(file.getExtension(), myFileExtensions)) {
                        result.add(file.getNameWithoutExtension());
                    }
                }
                return true;
            }
        );
        return result;
    }

    @Override
    public PsiJavaPackage findPackage(@Nonnull String qualifiedName) {
        CommonProcessors.FindFirstProcessor<VirtualFile> processor = new CommonProcessors.FindFirstProcessor<>();
        processDirectories(qualifiedName, ALL_SCOPE, processor);
        return processor.getFoundValue() != null ? createPackage(qualifiedName) : null;
    }

    private PsiPackageImpl createPackage(String qualifiedName) {
        return new PsiPackageImpl(myManager, myPackageManager, JavaModuleExtension.class, qualifiedName);
    }

    @Override
    public boolean processPackageDirectories(
        @Nonnull PsiJavaPackage psiPackage,
        @Nonnull GlobalSearchScope scope,
        @Nonnull Predicate<PsiDirectory> consumer,
        boolean includeLibrarySources
    ) {
        return processDirectories(
            psiPackage.getQualifiedName(),
            scope,
            dir -> {
                PsiDirectory psiDirectory = psiPackage.getManager().findDirectory(dir);
                return psiDirectory == null || consumer.test(psiDirectory);
            }
        );
    }

    private boolean processDirectories(
        @Nonnull String qualifiedName,
        @Nonnull GlobalSearchScope scope,
        @Nonnull Predicate<VirtualFile> processor
    ) {
        return ContainerUtil.process(
            getCache(scope).getDirectoriesByPackageName(qualifiedName),
            file -> !scope.contains(file) || processor.test(file)
        );
    }

    @Nonnull
    @Override
    public PsiJavaPackage[] getSubPackages(@Nonnull PsiJavaPackage psiPackage, @Nonnull GlobalSearchScope scope) {
        String pkgName = psiPackage.getQualifiedName();
        Set<String> names = getCache(scope).getSubpackageNames(pkgName);
        if (names.isEmpty()) {
            return super.getSubPackages(psiPackage, scope);
        }

        List<PsiJavaPackage> result = new ArrayList<>();
        for (String name : names) {
            result.add(createPackage(pkgName.isEmpty() ? name : pkgName + "." + name));
        }
        return result.toArray(new PsiJavaPackage[result.size()]);
    }

    @Nonnull
    @Override
    public PsiClass[] findClasses(@Nonnull String qualifiedName, @Nonnull GlobalSearchScope scope) {
        PsiClass psiClass = findClass(qualifiedName, scope);
        return psiClass == null ? PsiClass.EMPTY_ARRAY : new PsiClass[]{psiClass};
    }

    @Nonnull
    public static GlobalSearchScope addNonClasspathScope(@Nonnull Project project, @Nonnull GlobalSearchScope base) {
        GlobalSearchScope scope = base;
        for (PsiElementFinder finder : project.getExtensionList(PsiElementFinder.class)) {
            if (finder instanceof NonClasspathClassFinder nonClasspathClassFinder) {
                scope = scope.uniteWith(NonClasspathDirectoriesScope.compose(nonClasspathClassFinder.getClassRoots()));
            }
        }
        return scope;
    }

    public PsiManager getPsiManager() {
        return myManager;
    }

    @Nullable
    private static VirtualFile findChild(@Nonnull VirtualFile root, @Nonnull String relPath, @Nonnull String[] extensions) {
        VirtualFile file = null;
        for (String extension : extensions) {
            file = root.findChild(relPath + '.' + extension);
            if (file != null) {
                break;
            }
        }
        return file;
    }
}
