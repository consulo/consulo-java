package com.intellij.java.language.impl.psi.impl;

import com.intellij.java.language.impl.psi.impl.file.impl.JavaFileManager;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.application.util.ReadActionProcessor;
import consulo.language.content.FileIndexFacade;
import consulo.language.psi.*;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.stub.StubTreeLoader;
import consulo.module.content.DirectoryIndex;
import consulo.project.Project;
import consulo.util.collection.Lists;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

import java.util.*;
import java.util.function.Predicate;

/**
 * @author VISTALL
 * @since 09/12/2022
 */
@ExtensionImpl(id = "default", order = "first")
public class DefaultPsiElementFinderImpl extends PsiElementFinder implements DumbAware {
    private final Project myProject;
    private final JavaFileManager myJavaFileManager;

    @Inject
    public DefaultPsiElementFinderImpl(Project project, JavaFileManager javaFileManager) {
        myProject = project;
        myJavaFileManager = javaFileManager;
    }

    @Override
    public PsiClass findClass(@Nonnull String qualifiedName, @Nonnull GlobalSearchScope scope) {
        return myJavaFileManager.findClass(qualifiedName, scope);
    }

    @Override
    @Nonnull
    public PsiClass[] findClasses(@Nonnull String qualifiedName, @Nonnull GlobalSearchScope scope) {
        return myJavaFileManager.findClasses(qualifiedName, scope);
    }

    @Override
    public PsiJavaPackage findPackage(@Nonnull String qualifiedName) {
        return myJavaFileManager.findPackage(qualifiedName);
    }

    @Override
    @Nonnull
    public PsiJavaPackage[] getSubPackages(@Nonnull PsiJavaPackage psiPackage, @Nonnull GlobalSearchScope scope) {
        Map<String, PsiJavaPackage> packagesMap = new HashMap<>();
        String qualifiedName = psiPackage.getQualifiedName();
        for (PsiDirectory dir : psiPackage.getDirectories(scope)) {
            PsiDirectory[] subDirs = dir.getSubdirectories();
            for (PsiDirectory subDir : subDirs) {
                PsiJavaPackage aPackage = JavaDirectoryService.getInstance().getPackage(subDir);
                if (aPackage != null) {
                    String subQualifiedName = aPackage.getQualifiedName();
                    if (subQualifiedName.startsWith(qualifiedName) && !packagesMap.containsKey(subQualifiedName)) {
                        packagesMap.put(aPackage.getQualifiedName(), aPackage);
                    }
                }
            }
        }

        packagesMap.remove(qualifiedName);    // avoid SOE caused by returning a package as a subpackage of itself
        return packagesMap.values().toArray(new PsiJavaPackage[packagesMap.size()]);
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public PsiClass[] getClasses(@Nonnull PsiJavaPackage psiPackage, @Nonnull GlobalSearchScope scope) {
        return getClasses(null, psiPackage, scope);
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public PsiClass[] getClasses(@Nullable String shortName, @Nonnull PsiJavaPackage psiPackage, @Nonnull GlobalSearchScope scope) {
        List<PsiClass> list = null;
        String packageName = psiPackage.getQualifiedName();
        for (PsiDirectory dir : psiPackage.getDirectories(scope)) {
            PsiClass[] classes = JavaDirectoryService.getInstance().getClasses(dir);
            if (classes.length == 0) {
                continue;
            }
            if (list == null) {
                list = new ArrayList<>();
            }
            for (PsiClass aClass : classes) {
                // class file can be located in wrong place inside file system
                String qualifiedName = aClass.getQualifiedName();
                if (qualifiedName != null) {
                    qualifiedName = StringUtil.getPackageName(qualifiedName);
                }
                if (Comparing.strEqual(qualifiedName, packageName)) {
                    if (shortName == null || shortName.equals(aClass.getName())) {
                        list.add(aClass);
                    }
                }
            }
        }
        if (list == null) {
            return PsiClass.EMPTY_ARRAY;
        }

        if (list.size() > 1) {
            Lists.quickSort(list, (o1, o2) -> {
                VirtualFile file1 = PsiUtilCore.getVirtualFile(o1);
                VirtualFile file2 = PsiUtilCore.getVirtualFile(o2);
                return file1 == null ? file2 == null ? 0 : -1 : file2 == null ? 1 : scope.compare(file2, file1);
            });
        }

        return list.toArray(new PsiClass[list.size()]);
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public Set<String> getClassNames(@Nonnull PsiJavaPackage psiPackage, @Nonnull GlobalSearchScope scope) {
        Set<String> names = null;
        FileIndexFacade facade = FileIndexFacade.getInstance(myProject);
        for (PsiDirectory dir : psiPackage.getDirectories(scope)) {
            for (PsiFile file : dir.getFiles()) {
                if (file instanceof PsiClassOwner && file.getViewProvider().getLanguages().size() == 1) {
                    VirtualFile vFile = file.getVirtualFile();
                    if (vFile != null
                        && !(file instanceof PsiCompiledElement)
                        && !facade.isInSourceContent(vFile)
                        && (!scope.isForceSearchingInLibrarySources() || !StubTreeLoader.getInstance().canHaveStub(vFile))) {
                        continue;
                    }

                    Set<String> inFile = file instanceof PsiClassOwnerEx classOwnerEx
                        ? classOwnerEx.getClassNames()
                        : getClassNames(((PsiClassOwner)file).getClasses());

                    if (inFile.isEmpty()) {
                        continue;
                    }
                    if (names == null) {
                        names = new HashSet<>();
                    }
                    names.addAll(inFile);
                }
            }

        }
        return names == null ? Collections.<String>emptySet() : names;
    }


    @Override
    public boolean processPackageDirectories(
        @Nonnull PsiJavaPackage psiPackage,
        @Nonnull GlobalSearchScope scope,
        @Nonnull Predicate<PsiDirectory> consumer
    ) {
        PsiManager psiManager = PsiManager.getInstance(myProject);
        return DirectoryIndex.getInstance(myProject)
            .getDirectoriesByPackageName(psiPackage.getQualifiedName(), false)
            .forEach(new ReadActionProcessor<>() {
                @RequiredReadAction
                @Override
                public boolean processInReadAction(VirtualFile dir) {
                    if (!scope.contains(dir)) {
                        return true;
                    }
                    PsiDirectory psiDir = psiManager.findDirectory(dir);
                    return psiDir == null || consumer.test(psiDir);
                }
            });
    }
}
