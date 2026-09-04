// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.core;

import com.intellij.java.language.JavaFeature;
import com.intellij.java.language.impl.psi.impl.light.LightJavaModule;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiJavaModule;
import com.intellij.java.language.psi.PsiJavaPackage;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileSystemItem;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.module.content.ProjectFileIndex;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Represents a JPMS module and the corresponding module in Consulo project model
 */
public interface JpmsModuleInfo {
  @Nullable PsiJavaModule getModule();

  @Nullable Module getJpsModule();

  /**
   * Represents the details of a current module.
   * <p>
   * Note: "name" is not always possible to get from "module".
   * For example, "module" can be "java.se", but the name is from the original module.
   */
  final class CurrentModuleInfo implements JpmsModuleInfo {
    private final @Nullable PsiJavaModule myModule;
    private final String myName;
    private final Supplier<@Nullable Module> myJps;
    private volatile boolean myJpsComputed;
    private volatile @Nullable Module myJpsModule;

    public CurrentModuleInfo(@Nullable PsiJavaModule module, String name, Supplier<@Nullable Module> jps) {
      myModule = module;
      myName = name;
      myJps = jps;
    }

    public CurrentModuleInfo(@Nullable PsiJavaModule use, PsiElement element) {
      this(use, use != null ? use.getName() : JpmsModuleAccessInfo.ALL_UNNAMED, () -> ModuleUtilCore.findModuleForPsiElement(element));
    }

    @Override
    public @Nullable PsiJavaModule getModule() {
      return myModule;
    }

    /**
     * @return original module name
     */
    public String getName() {
      return myName;
    }

    @Override
    public @Nullable Module getJpsModule() {
      if (!myJpsComputed) {
        myJpsModule = myJps.get();
        myJpsComputed = true;
      }
      return myJpsModule;
    }
  }

  /**
   * Represents the details of a target module
   */
  interface TargetModuleInfo extends JpmsModuleInfo {
    String getPackageName();

    /**
     * @return access information when the specified target module is accessed at a given place
     */
    default JpmsModuleAccessInfo accessAt(PsiFileSystemItem place) {
      PsiJavaModule found = JavaPsiModuleUtil.findDescriptorByElement(place);
      PsiJavaModule useModule = found instanceof LightJavaModule ? null : found;
      CurrentModuleInfo current = new CurrentModuleInfo(useModule, place);
      return new JpmsModuleAccessInfo(current, this);
    }
  }

  final class TargetModuleInfoByJavaModule implements TargetModuleInfo {
    private final @Nullable PsiJavaModule myModule;
    private final String myPackageName;
    private volatile boolean myJpsComputed;
    private volatile @Nullable Module myJpsModule;

    public TargetModuleInfoByJavaModule(@Nullable PsiJavaModule module, String packageName) {
      myModule = module;
      myPackageName = packageName;
    }

    @Override
    public @Nullable PsiJavaModule getModule() {
      return myModule;
    }

    @Override
    public String getPackageName() {
      return myPackageName;
    }

    @Override
    public @Nullable Module getJpsModule() {
      if (!myJpsComputed) {
        myJpsModule = myModule == null ? null : ModuleUtilCore.findModuleForPsiElement(myModule);
        myJpsComputed = true;
      }
      return myJpsModule;
    }
  }

  final class TargetModuleInfoByFile implements TargetModuleInfo {
    private final VirtualFile myVirtualFile;
    private final Project myProject;
    private final String myPackageName;
    private volatile boolean myJpsComputed;
    private volatile @Nullable Module myJpsModule;
    private volatile boolean myModuleComputed;
    private volatile @Nullable PsiJavaModule myModule;

    public TargetModuleInfoByFile(VirtualFile virtualFile, Project project, String packageName) {
      myVirtualFile = virtualFile;
      myProject = project;
      myPackageName = packageName;
    }

    @Override
    public String getPackageName() {
      return myPackageName;
    }

    @Override
    public @Nullable Module getJpsModule() {
      if (!myJpsComputed) {
        myJpsModule = ModuleUtilCore.findModuleForFile(myVirtualFile, myProject);
        myJpsComputed = true;
      }
      return myJpsModule;
    }

    @Override
    public @Nullable PsiJavaModule getModule() {
      if (!myModuleComputed) {
        myModule = JavaPsiModuleUtil.findDescriptorByFile(myVirtualFile, myProject);
        myModuleComputed = true;
      }
      return myModule;
    }
  }

  /**
   * Find module info structures when accessing a given location.
   *
   * @param targetPackageName package name which about to be accessed
   * @param targetFile        concrete target file which is about to be accessed; null if not known (in this case,
   *                          multiple results could be returned, as multiple source roots may define a given package)
   * @param place             source place from where the access is requested
   * @return list of TargetModuleInfo structures that describe the possible target; empty list if the target package is empty
   * (which is generally an error), null if not applicable (e.g., modules are not supported at place;
   * target does not belong to any module; etc.). In this case, no access problem should be reported.
   */
  static @Nullable List<TargetModuleInfo> findTargetModuleInfos(String targetPackageName, @Nullable PsiFile targetFile, PsiFile place) {
    if (!PsiUtil.isAvailable(JavaFeature.MODULES, place)) return null;

    VirtualFile useVFile = place.getVirtualFile();
    Project project = place.getProject();
    ProjectFileIndex index = ProjectFileIndex.getInstance(project);
    if (useVFile != null && index.isInLibrarySource(useVFile)) return null;
    VirtualFile targetVirtualFile = targetFile == null ? null : targetFile.getVirtualFile();
    if (targetVirtualFile != null && isInProject(index, targetVirtualFile)) {
      return List.of(new TargetModuleInfoByFile(targetVirtualFile, project, targetPackageName));
    }
    if (useVFile == null) return null;

    PsiJavaPackage target = JavaPsiFacade.getInstance(project).findPackage(targetPackageName);
    if (target == null) return null;
    Module module = index.getModuleForFile(useVFile);
    if (module == null) return null;
    boolean test = index.isInTestSourceContent(useVFile);
    GlobalSearchScope moduleScope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, test);
    PsiDirectory[] dirs = target.getDirectories(moduleScope);
    String packageName = target.getQualifiedName();
    if (dirs.length == 0) {
      return target.getFiles(moduleScope).length == 0 ? List.of() : null;
    }

    List<TargetModuleInfo> result = new ArrayList<>(dirs.length);
    for (PsiDirectory dir : dirs) {
      result.add(new TargetModuleInfoByFile(dir.getVirtualFile(), project, packageName));
    }
    return result;
  }

  private static boolean isInProject(ProjectFileIndex index, VirtualFile file) {
    return index.isInContent(file) || index.isInLibraryClasses(file) || index.isInLibrarySource(file);
  }
}
