// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.core;

import com.intellij.java.language.JavaFeature;
import com.intellij.java.language.impl.psi.impl.light.LightJavaModule;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiJavaModule;
import com.intellij.java.language.psi.PsiJavaPackage;
import com.intellij.java.language.psi.PsiNameHelper;
import com.intellij.java.language.psi.PsiPackageAccessibilityStatement;
import com.intellij.java.language.psi.util.JavaMultiReleaseUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.content.bundle.Sdk;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileSystemItem;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.stub.DumbModeAccessType;
import consulo.language.psi.stub.FileBasedIndex;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.layer.orderEntry.ModuleExtensionWithSdkOrderEntry;
import consulo.module.content.layer.orderEntry.OrderEntry;
import consulo.project.Project;
import consulo.util.io.FileUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.archive.ArchiveVfsUtil;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Represents the access details between the current module and the target module.
 */
public final class JpmsModuleAccessInfo {
  public enum JpmsModuleAccessProblem {
    FROM_NAMED,
    FROM_UNNAMED,
    TO_UNNAMED,
    PACKAGE_BAD_NAME,
    BAD_NAME,
    PACKAGE_NOT_IN_GRAPH,
    NOT_IN_GRAPH,
    PACKAGE_DOES_NOT_READ,
    DOES_NOT_READ,
    JPS_DEPENDENCY_PROBLEM
  }

  /**
   * Access mode to determine whether the target is accessible
   */
  public enum JpmsModuleAccessMode {
    /**
     * Consider the target as accessible if the source actually reads the target
     */
    READ,

    /**
     * Consider the target as accessible if it's exported to the source (even if the source doesn't read it)
     */
    EXPORT
  }

  public static final String ALL_UNNAMED = "ALL-UNNAMED";
  public static final String ALL_SYSTEM = "ALL-SYSTEM";
  public static final String ALL_MODULE_PATH = "ALL-MODULE-PATH";
  public static final String ADD_EXPORTS_OPTION = "--add-exports";
  public static final String ADD_MODULES_OPTION = "--add-modules";
  public static final String ADD_READS_OPTION = "--add-reads";
  public static final String ADD_OPENS_OPTION = "--add-opens";
  public static final String PATCH_MODULE_OPTION = "--patch-module";

  private final JpmsModuleInfo.CurrentModuleInfo myCurrent;
  private final JpmsModuleInfo.TargetModuleInfo myTarget;

  public JpmsModuleAccessInfo(JpmsModuleInfo.CurrentModuleInfo current, JpmsModuleInfo.TargetModuleInfo target) {
    myCurrent = current;
    myTarget = target;
  }

  public JpmsModuleInfo.CurrentModuleInfo getCurrent() {
    return myCurrent;
  }

  public JpmsModuleInfo.TargetModuleInfo getTarget() {
    return myTarget;
  }

  public @Nullable JpmsModuleAccessProblem checkAccess(PsiFileSystemItem place, JpmsModuleAccessMode accessMode) {
    PsiJavaModule targetModule = myTarget.getModule();
    if (targetModule != null) {
      if (targetModule.equals(myCurrent.getModule())) {
        return null;
      }

      Module currentJpsModule = myCurrent.getJpsModule();
      if (myCurrent.getModule() == null) {
        PsiFile containingFile = targetModule.getContainingFile();
        VirtualFile origin = containingFile == null ? null : containingFile.getVirtualFile();
        if (origin == null || currentJpsModule == null || !isInSdk(currentJpsModule.getProject(), origin)) {
          return null;  // a target is not on the mandatory module path
        }

        if (!accessibleFromJdkModules(place, accessMode) &&
            !inAddedModules(currentJpsModule, targetModule.getName()) &&
            !hasUpgrade(currentJpsModule, targetModule.getName(), myTarget.getPackageName(), place)) {
          return JpmsModuleAccessProblem.PACKAGE_NOT_IN_GRAPH;
        }
      }

      if (!(targetModule instanceof LightJavaModule) &&
          !JavaPsiModuleUtil.exports(targetModule, myTarget.getPackageName(), myCurrent.getModule()) &&
          (currentJpsModule == null || !inAddedExports(currentJpsModule, targetModule.getName(), myTarget.getPackageName(), myCurrent.getName())) &&
          (currentJpsModule == null || !isPatchedModule(targetModule.getName(), currentJpsModule, place))) {
        return myCurrent.getModule() == null ? JpmsModuleAccessProblem.FROM_UNNAMED : JpmsModuleAccessProblem.FROM_NAMED;
      }

      if (myCurrent.getModule() != null &&
          !PsiJavaModule.JAVA_BASE.equals(targetModule.getName()) &&
          !isAccessible(accessMode) &&
          !inAddedReads(myCurrent.getModule(), targetModule)) {
        return PsiNameHelper.isValidModuleName(targetModule.getName(), myCurrent.getModule())
          ? JpmsModuleAccessProblem.PACKAGE_DOES_NOT_READ
          : JpmsModuleAccessProblem.PACKAGE_BAD_NAME;
      }
    }
    else if (myCurrent.getModule() != null) {
      JpmsModuleInfo.TargetModuleInfoByJavaModule autoModule =
        new JpmsModuleInfo.TargetModuleInfoByJavaModule(detectAutomaticModule(myTarget), myTarget.getPackageName());
      if (autoModule.getModule() == null) {
        return JpmsModuleAccessProblem.TO_UNNAMED;
      }
      else if (!new JpmsModuleAccessInfo(myCurrent, autoModule).isAccessible(accessMode) &&
               !inAddedReads(myCurrent.getModule(), null) &&
               !inSameMultiReleaseModule(myCurrent, myTarget)) {
        return JpmsModuleAccessProblem.TO_UNNAMED;
      }
    }

    return null;
  }

  private boolean isAccessible(JpmsModuleAccessMode accessMode) {
    return switch (accessMode) {
      case READ -> isAccessible();
      case EXPORT -> isExported();
    };
  }

  /**
   * @param place place where the target module is accessed
   * @return access problem, or null if the target module is accessible without any problem
   */
  public @Nullable JpmsModuleAccessProblem checkModuleAccess(PsiElement place) {
    PsiJavaModule targetModule = myTarget.getModule();
    if (targetModule != null) {
      if (targetModule.equals(myCurrent.getModule())) {
        return null;
      }

      Module currentJpsModule = myCurrent.getJpsModule();
      if (myCurrent.getModule() == null) {
        PsiFile containingFile = targetModule.getContainingFile();
        VirtualFile origin = containingFile == null ? null : containingFile.getVirtualFile();
        if (origin == null && targetModule instanceof LightJavaModule light) origin = light.getRootVirtualFile();
        if (origin == null || currentJpsModule == null) return null;

        if (!isInSdk(currentJpsModule.getProject(), origin)) {
          GlobalSearchScope searchScope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(currentJpsModule);
          if (searchScope.contains(origin)) return null;
          return JpmsModuleAccessProblem.JPS_DEPENDENCY_PROBLEM;
        }

        if (!accessibleFromJdkModules(place, JpmsModuleAccessMode.READ) &&
            !inAddedModules(currentJpsModule, targetModule.getName())) {
          return JpmsModuleAccessProblem.NOT_IN_GRAPH;
        }
      }

      if (myCurrent.getModule() != null &&
          !PsiJavaModule.JAVA_BASE.equals(targetModule.getName()) &&
          !isAccessible() &&
          !inAddedReads(myCurrent.getModule(), targetModule)) {
        return PsiNameHelper.isValidModuleName(targetModule.getName(), myCurrent.getModule())
          ? JpmsModuleAccessProblem.DOES_NOT_READ
          : JpmsModuleAccessProblem.BAD_NAME;
      }
    }
    else if (myCurrent.getModule() != null) {
      JpmsModuleInfo.TargetModuleInfoByJavaModule autoModule =
        new JpmsModuleInfo.TargetModuleInfoByJavaModule(detectAutomaticModule(myTarget), myTarget.getPackageName());
      if (autoModule.getModule() != null &&
          !new JpmsModuleAccessInfo(myCurrent, autoModule).isAccessible() &&
          !inAddedReads(myCurrent.getModule(), null) &&
          !inSameMultiReleaseModule(myCurrent, myTarget)) {
        return JpmsModuleAccessProblem.TO_UNNAMED;
      }
    }

    return null;
  }

  public boolean isExported() {
    PsiJavaModule targetModule = myTarget.getModule();
    if (targetModule == null) return false;
    if (!targetModule.isPhysical() || JavaPsiModuleUtil.exports(targetModule, myTarget.getPackageName(), myCurrent.getModule())) return true;
    Module currentJpsModule = myCurrent.getJpsModule();
    if (currentJpsModule == null) return false;
    return inAddedExports(currentJpsModule, targetModule.getName(), myTarget.getPackageName(), myCurrent.getName());
  }

  public boolean isAccessible() {
    PsiJavaModule currentModule = myCurrent.getModule();
    if (currentModule == null) return false;
    PsiJavaModule targetModule = myTarget.getModule();
    if (targetModule == null) return false;
    return JavaPsiModuleUtil.reads(currentModule, targetModule);
  }

  private boolean accessibleFromJdkModules(PsiElement place, JpmsModuleAccessMode accessMode) {
    Module jpsModule = myCurrent.getJpsModule();
    if (jpsModule == null) return false;
    PsiJavaModule targetModule = myTarget.getModule();
    if (targetModule == null) return false;
    if (PsiJavaModule.JAVA_BASE.equals(targetModule.getName())) return true;

    if (!isJdkModule(jpsModule, targetModule)) return false;
    // https://bugs.openjdk.org/browse/JDK-8197532
    Predicate<PsiJavaModule> jdkModulePred;
    if (PsiUtil.isAvailable(JavaFeature.AUTO_ROOT_MODULES, place)) {
      jdkModulePred = JpmsModuleAccessInfo::hasUnqualifiedExport;
    }
    else {
      PsiJavaModule javaSE = FileBasedIndex.getInstance().ignoreDumbMode(
        DumbModeAccessType.RELIABLE_DATA_ONLY,
        () -> JavaPsiFacade.getInstance(place.getProject()).findModule("java.se", GlobalSearchScope.moduleWithLibrariesScope(jpsModule))
      );

      if (javaSE != null) {
        jdkModulePred = module ->
          (!module.getName().startsWith("java.") && hasUnqualifiedExport(module)) ||
          new JpmsModuleAccessInfo(new JpmsModuleInfo.CurrentModuleInfo(javaSE, myCurrent.getName(), () -> jpsModule), myTarget).isAccessible(accessMode);
      }
      else {
        jdkModulePred = module -> true;
      }
    }
    Predicate<PsiJavaModule> noIncubatorPred = module -> !module.doNotResolveByDefault();
    return jdkModulePred.test(targetModule) && noIncubatorPred.test(targetModule);
  }

  private static boolean hasUnqualifiedExport(PsiJavaModule module) {
    for (PsiPackageAccessibilityStatement export : module.getExports()) {
      if (export.getModuleNames().isEmpty()) {
        return true;
      }
    }
    return false;
  }

  private static boolean isJdkModule(Module jpsModule, PsiJavaModule psiModule) {
    Sdk sdk = ModuleUtilCore.getSdk(jpsModule, JavaModuleExtension.class);
    VirtualFile sdkHomePath = toLocalVirtualFile(sdk == null ? null : sdk.getHomeDirectory());
    PsiFile containingFile = psiModule.getContainingFile();
    VirtualFile moduleFilePath = toLocalVirtualFile(containingFile == null ? null : containingFile.getVirtualFile());

    if (sdkHomePath != null && moduleFilePath != null) {
      return VirtualFileUtil.isAncestor(sdkHomePath, moduleFilePath, false);
    }
    else {
      return psiModule.getName().startsWith("java.") ||
             psiModule.getName().startsWith("jdk.");
    }
  }

  private static @Nullable VirtualFile toLocalVirtualFile(@Nullable VirtualFile file) {
    if (file == null) return null;
    VirtualFile jar = ArchiveVfsUtil.getVirtualFileForJar(file);
    return jar != null ? jar : file;
  }

  private static boolean inSameMultiReleaseModule(JpmsModuleInfo current, JpmsModuleInfo target) {
    Module placeModule = current.getJpsModule();
    if (placeModule == null) return false;
    Module targetModule = target.getJpsModule();
    if (targetModule == null) return false;
    return JavaMultiReleaseUtil.areMainAndAdditionalMultiReleaseModules(targetModule, placeModule);
  }

  private static @Nullable PsiJavaModule detectAutomaticModule(JpmsModuleInfo current) {
    Module module = current.getJpsModule();
    if (module == null) return null;
    return JavaPsiFacade.getInstance(module.getProject())
      .findModule(LightJavaModule.moduleName(module.getName()), GlobalSearchScope.moduleScope(module));
  }

  private static boolean hasUpgrade(Module module, String targetName, String packageName, PsiFileSystemItem place) {
    if (PsiJavaModule.UPGRADEABLE.contains(targetName)) {
      PsiJavaPackage target = JavaPsiFacade.getInstance(module.getProject()).findPackage(packageName);
      if (target != null) {
        VirtualFile useVFile = place.getVirtualFile();
        if (useVFile != null) {
          boolean test = ModuleRootManager.getInstance(module).getFileIndex().isInTestSourceContent(useVFile);
          PsiDirectory[] dirs = target.getDirectories(GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, test));
          Project project = module.getProject();
          for (PsiDirectory dir : dirs) {
            if (!isInSdk(project, dir.getVirtualFile())) {
              return true;
            }
          }
        }
      }
    }

    return false;
  }

  private static boolean isPatchedModule(String targetModuleName, Module module, PsiFileSystemItem place) {
    VirtualFile virtualFile = place.getVirtualFile();
    if (virtualFile == null) return false;
    VirtualFile rootForFile = ProjectFileIndex.getInstance(place.getProject()).getSourceRootForFile(virtualFile);
    if (rootForFile == null) return false;
    String prefix = targetModuleName + "=";
    for (String option : optionValues(getAdditionalOptions(module), PATCH_MODULE_OPTION)) {
      if (!option.startsWith(prefix)) continue;
      for (String patchingPath : option.substring(prefix.length()).split(File.pathSeparator)) {
        if (FileUtil.pathsEqual(rootForFile.getPath(), FileUtil.toCanonicalPath(FileUtil.toSystemIndependentName(patchingPath)))) {
          return true;
        }
      }
    }
    return false;
  }

  private static List<String> getAdditionalOptions(Module module) {
    JavaModuleExtension<?> extension = ModuleUtilCore.getExtension(module, JavaModuleExtension.class);
    return extension == null ? List.of() : extension.getCompilerArguments();
  }

  private static List<String> optionValues(List<String> options, String name) {
    if (options.isEmpty()) {
      return List.of();
    }
    boolean useValue = false;
    List<String> result = new ArrayList<>();
    for (String option : options) {
      if (option.equals(name)) {
        useValue = true;
        continue;
      }
      else if (useValue) {
        useValue = false;
      }
      else if (option.startsWith(name) && option.length() > name.length() + 1 && option.charAt(name.length()) == '=') {
        option = option.substring(name.length() + 1);
      }
      else {
        continue;
      }
      if (!option.isEmpty()) {
        result.add(option);
      }
    }
    return result;
  }

  private static boolean inAddedExports(Module module, String targetName, String packageName, String useName) {
    List<String> options = getAdditionalOptions(module);
    if (options.isEmpty()) return false;
    String prefix = targetName + "/" + packageName + "=";
    for (String value : optionValues(options, ADD_EXPORTS_OPTION)) {
      if (!value.startsWith(prefix)) continue;
      for (String name : value.substring(prefix.length()).split(",")) {
        if (name.equals(useName)) return true;
      }
    }
    return false;
  }

  private static boolean inAddedModules(Module module, String moduleName) {
    List<String> options = getAdditionalOptions(module);
    for (String value : optionValues(options, ADD_MODULES_OPTION)) {
      for (String name : value.split(",")) {
        if (name.equals(moduleName) || name.equals(ALL_SYSTEM) || name.equals(ALL_MODULE_PATH)) return true;
      }
    }
    return false;
  }

  private static boolean inAddedReads(PsiJavaModule fromJavaModule, @Nullable PsiJavaModule toJavaModule) {
    Module fromModule = ModuleUtilCore.findModuleForPsiElement(fromJavaModule);
    if (fromModule == null) return false;
    List<String> options = getAdditionalOptions(fromModule);
    for (String value : optionValues(options, ADD_READS_OPTION)) {
      for (String entry : value.split(",")) {
        String[] parts = entry.split("=");
        String optFromModuleName = parts[0];
        String optToModuleName = parts[parts.length - 1];
        if (fromJavaModule.getName().equals(optFromModuleName) &&
            ((toJavaModule != null && toJavaModule.getName().equals(optToModuleName)) ||
             (ALL_UNNAMED.equals(optToModuleName) && isUnnamedModule(toJavaModule)))) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean isUnnamedModule(@Nullable PsiJavaModule module) {
    return module == null || module instanceof LightJavaModule;
  }

  private static boolean isInSdk(Project project, VirtualFile file) {
    for (OrderEntry entry : ProjectFileIndex.getInstance(project).getOrderEntriesForFile(file)) {
      if (entry instanceof ModuleExtensionWithSdkOrderEntry) {
        return true;
      }
    }
    return false;
  }
}
