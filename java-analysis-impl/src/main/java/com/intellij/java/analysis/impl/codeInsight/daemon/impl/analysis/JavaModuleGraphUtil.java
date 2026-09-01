// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis;

import com.intellij.java.codeserver.core.JavaPsiModuleUtil;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.PsiJavaModule;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.file.LanguageFileType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.module.Module;
import consulo.module.content.ProjectFileIndex;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.MultiMap;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileTypeRegistry;

import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

import static consulo.util.lang.ObjectUtil.tryCast;

public final class JavaModuleGraphUtil {
  private JavaModuleGraphUtil() {
  }

  @Nullable
  public static PsiJavaModule findDescriptorByElement(@Nullable PsiElement element) {
    return JavaPsiModuleUtil.findDescriptorByElement(element);
  }

  @Nullable
  public static PsiJavaModule findDescriptorByFile(@Nullable VirtualFile file, Project project) {
    return JavaPsiModuleUtil.findDescriptorByFile(file, project);
  }

  @Nullable
  public static PsiJavaModule findDescriptorByModule(@Nullable Module module, boolean inTests) {
    return JavaPsiModuleUtil.findDescriptorByModule(module, inTests);
  }

  @Nullable
  @RequiredReadAction
  public static PsiJavaModule findDescriptorInLibrary(VirtualFile file, Project project) {
    return JavaPsiModuleUtil.findDescriptorInLibrary(file, project);
  }

  public static class JavaModuleScope extends GlobalSearchScope {
    private final MultiMap<String, PsiJavaModule> myModules;
    private final boolean myIncludeLibraries;
    private final boolean myIsInTests;

    private JavaModuleScope(Project project, Set<PsiJavaModule> modules) {
      super(project);
      myModules = new MultiMap<>();
      for (PsiJavaModule module : modules) {
        myModules.putValue(module.getName(), module);
      }
      ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);
      myIncludeLibraries = ContainerUtil.or(modules, m -> {
        PsiFile containingFile = m.getContainingFile();
        if (containingFile == null) return true;
        VirtualFile moduleFile = containingFile.getVirtualFile();
        if (moduleFile == null) return true;
        return fileIndex.isInLibrary(moduleFile);
      });
      myIsInTests = !myIncludeLibraries && ContainerUtil.or(modules, m -> {
        PsiFile containingFile = m.getContainingFile();
        if (containingFile == null) return true;
        VirtualFile moduleFile = containingFile.getVirtualFile();
        if (moduleFile == null) return true;
        return fileIndex.isInTestSourceContent(moduleFile);
      });
    }

    @Override
    public boolean isSearchInModuleContent(Module aModule) {
      return contains(findDescriptorByModule(aModule, myIsInTests));
    }

    @Override
    public boolean isSearchInLibraries() {
      return myIncludeLibraries;
    }

    @Override
    public boolean contains(VirtualFile file) {
      Project project = getProject();
      if (project == null) {
        return false;
      }
      if (!isJvmLanguageFile(file)) {
        return false;
      }
      ProjectFileIndex index = ProjectFileIndex.getInstance(project);
      if (index.isInLibrary(file)) {
        return myIncludeLibraries && contains(JavaPsiModuleUtil.findDescriptorInLibrary(file, project));
      }
      Module module = index.getModuleForFile(file);
      return contains(findDescriptorByModule(module, myIsInTests));
    }

    private boolean contains(@Nullable PsiJavaModule module) {
      if (module == null || !module.isValid()) {
        return false;
      }
      Collection<PsiJavaModule> myCollectedModules = myModules.get(module.getName());
      return myCollectedModules.contains(module);
    }

    private static boolean isJvmLanguageFile(VirtualFile file) {
      FileTypeRegistry fileTypeRegistry = FileTypeRegistry.getInstance();
      LanguageFileType languageFileType = tryCast(fileTypeRegistry.getFileTypeByFileName(file.getName()), LanguageFileType.class);
      return languageFileType != null && languageFileType.getLanguage() instanceof JavaLanguage;
    }

    public static
    @Nullable
    JavaModuleScope moduleScope(PsiJavaModule module) {
      PsiFile moduleFile = module.getContainingFile();
      if (moduleFile == null) {
        return null;
      }
      VirtualFile virtualFile = moduleFile.getVirtualFile();
      if (virtualFile == null) {
        return null;
      }
      return new JavaModuleScope(module.getProject(), Set.of(module));
    }

    /**
     * Creates a JavaModuleScope that includes the given module and all transitive modules.
     *
     * @param module the base PsiJavaModule for which to create the scope, must not be null
     * @return a new JavaModuleScope including all transitive modules of the given module, or null if the moduleFile is null or no transitive modules are found
     */
    public static
    @Nullable
    JavaModuleScope moduleWithTransitiveScope(PsiJavaModule module) {
      Set<PsiJavaModule> allModules = JavaPsiModuleUtil.getAllTransitiveModulesIncludeCurrent(module);
      if (allModules.isEmpty()) {
        return null;
      }
      return new JavaModuleScope(module.getProject(), allModules);
    }
  }
}
