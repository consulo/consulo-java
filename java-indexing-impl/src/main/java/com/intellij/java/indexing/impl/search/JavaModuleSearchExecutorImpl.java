// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.indexing.impl.search;

import com.intellij.java.indexing.impl.stubs.index.JavaAutoModuleNameIndex;
import com.intellij.java.indexing.impl.stubs.index.JavaModuleNameIndex;
import com.intellij.java.indexing.impl.stubs.index.JavaSourceModuleNameIndex;
import com.intellij.java.indexing.search.searches.JavaModuleSearch;
import com.intellij.java.indexing.search.searches.JavaModuleSearchExecutor;
import com.intellij.java.language.impl.psi.impl.light.LightJavaModule;
import com.intellij.java.language.impl.psi.util.JavaManifestUtil;
import com.intellij.java.language.psi.PsiJavaModule;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.progress.ProgressManager;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.CachedValuesManager;
import consulo.language.content.LanguageContentFolderScopes;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.module.content.ModuleRootManager;
import consulo.project.Project;
import consulo.project.content.ProjectRootModificationTracker;
import consulo.util.collection.ContainerUtil;
import consulo.virtualFileSystem.VirtualFile;

import org.jspecify.annotations.Nullable;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.jar.JarFile;

@ExtensionImpl
public class JavaModuleSearchExecutorImpl implements JavaModuleSearchExecutor {
  @Override
  public boolean execute(JavaModuleSearch.Parameters queryParameters, Predicate<? super PsiJavaModule> consumer) {
    String moduleName = queryParameters.getName();
    Project project = queryParameters.getProject();
    GlobalSearchScope scope = queryParameters.getScope();

    if (moduleName == null) {
      return processAllModules(project, consumer);
    }

    return processModuleByName(moduleName, project, scope, consumer);
  }

  private static boolean processAllModules(Project project, Predicate<? super PsiJavaModule> consumer) {
    GlobalSearchScope indexScope = GlobalSearchScope.allScope(project);

    // collect all module-name keys
    Set<String> allNames = new LinkedHashSet<>();
    allNames.addAll(JavaModuleNameIndex.getInstance().getAllKeys(project));
    allNames.addAll(JavaSourceModuleNameIndex.getAllKeys(project));
    allNames.addAll(JavaAutoModuleNameIndex.getAllKeys(project));

    Set<String> namesWithResults = new HashSet<>();
    // process real and indexed light modules only.
    for (String name : allNames) {
      ProgressManager.checkCanceled();
      if (!processModulesFromIndices(name, project, indexScope, consumer, namesWithResults)) {
        return false;
      }
    }

    return processJpsModules(project, consumer, namesWithResults, null);
  }

  private static boolean processModuleByName(String moduleName,
                                             Project project,
                                             GlobalSearchScope scope,
                                             Predicate<? super PsiJavaModule> consumer) {
    Set<String> namesWithResults = new HashSet<>();

    if (!processModulesFromIndices(moduleName, project, scope, consumer, namesWithResults)) {
      return false;
    }

    // If we already found the module, no need to fallback.
    if (namesWithResults.contains(moduleName)) {
      return true;
    }

    return processJpsModules(project, consumer, namesWithResults, moduleName);
  }

  private static boolean processJpsModules(Project project,
                                           Predicate<? super PsiJavaModule> consumer,
                                           Set<? super String> namesWithResults,
                                           @Nullable String moduleName) {
    PsiManager psiManager = PsiManager.getInstance(project);
    CachedValuesManager valuesManager = CachedValuesManager.getManager(project);
    ProjectRootModificationTracker tracker = ProjectRootModificationTracker.getInstance(project);
    Module[] modules = ModuleManager.getInstance(project).getModules();

    for (Module module : modules) {
      ProgressManager.checkCanceled();
      VirtualFile[] sourceRoots = ModuleRootManager.getInstance(module).getContentFolderFiles(LanguageContentFolderScopes.production());
      if (sourceRoots.length == 0) {
        continue;
      }

      VirtualFile root = sourceRoots[0];

      // auto module name from manifest (including virtual manifests)
      String autoModuleName = JavaManifestUtil.getManifestAttributeValue(module, PsiJavaModule.AUTO_MODULE_NAME);
      if (autoModuleName != null && !namesWithResults.contains(autoModuleName)) {
        if (moduleName != null && moduleName.equals(autoModuleName)) {
          namesWithResults.add(autoModuleName);
          if (!consumer.test(LightJavaModule.create(psiManager, root, autoModuleName))) {
            return false;
          }
          return true;
        }
        else if (moduleName == null) {
          namesWithResults.add(autoModuleName);
          if (!consumer.test(LightJavaModule.create(psiManager, root, autoModuleName))) {
            return false;
          }
          continue;
        }
      }

      // do not create auto-module if manifest exists without "Automatic-Module-Name" to avoid conflict with SourceModuleNameIndex
      if (autoModuleName == null && ContainerUtil.exists(sourceRoots, r -> r.findFileByRelativePath(JarFile.MANIFEST_NAME) != null)) {
        continue;
      }

      // default module name derived from module name
      String defaultModuleName = valuesManager.getCachedValue(module, () ->
          CachedValueProvider.Result.create(LightJavaModule.moduleName(module.getName()), tracker));
      if (!namesWithResults.contains(defaultModuleName)) {
        if (moduleName != null && moduleName.equals(defaultModuleName)) {
          namesWithResults.add(defaultModuleName);
          if (!consumer.test(LightJavaModule.create(psiManager, root, defaultModuleName))) {
            return false;
          }
          return true;
        }
        else if (moduleName == null) {
          namesWithResults.add(defaultModuleName);
          if (!consumer.test(LightJavaModule.create(psiManager, root, defaultModuleName))) {
            return false;
          }
        }
      }
    }
    return true;
  }

  private static boolean processModulesFromIndices(String moduleName,
                                                   Project project,
                                                   GlobalSearchScope scope,
                                                   Predicate<? super PsiJavaModule> consumer,
                                                   Set<? super String> namesWithResults) {
    PsiManager psiManager = PsiManager.getInstance(project);
    // Real modules from module-info.java
    for (PsiJavaModule module : JavaModuleNameIndex.getInstance().get(moduleName, project, scope)) {
      ProgressManager.checkCanceled();
      namesWithResults.add(moduleName);
      if (!consumer.test(module)) {
        return false;
      }
    }

    for (VirtualFile manifest : JavaSourceModuleNameIndex.getFilesByKey(moduleName, scope)) {
      ProgressManager.checkCanceled();
      VirtualFile root = getSourceRootFromManifest(manifest);
      if (root == null) {
        continue;
      }
      namesWithResults.add(moduleName);
      if (!consumer.test(LightJavaModule.create(psiManager, root, moduleName))) {
        return false;
      }
    }

    for (VirtualFile root : JavaAutoModuleNameIndex.getFilesByKey(moduleName, scope)) {
      ProgressManager.checkCanceled();
      namesWithResults.add(moduleName);
      if (!consumer.test(LightJavaModule.create(psiManager, root, moduleName))) {
        return false;
      }
    }
    return true;
  }

  @Nullable
  private static VirtualFile getSourceRootFromManifest(VirtualFile manifest) {
    VirtualFile parent = manifest.getParent();
    if (parent == null) {
      return null;
    }
    VirtualFile root = parent.getParent();
    if (root == null) {
      return null;
    }
    return root;
  }
}
