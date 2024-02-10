package com.intellij.java.analysis.impl.psi.impl.search;

import com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.java.indexing.impl.stubs.index.JavaModuleNameIndex;
import com.intellij.java.language.impl.psi.impl.light.AutomaticJavaModule;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiElementFinder;
import com.intellij.java.language.psi.PsiJavaFile;
import com.intellij.java.language.psi.PsiJavaModule;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.module.content.ProjectFileIndex;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.archive.ArchiveFileSystem;
import jakarta.inject.Inject;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author VISTALL
 * @since 25/04/2023
 */
@ExtensionImpl(order = "after default")
public class JavaModuleFinderImpl extends PsiElementFinder {
  private final Project myProject;
  private final ProjectFileIndex myProjectFileIndex;

  @Inject
  public JavaModuleFinderImpl(Project project, ProjectFileIndex projectFileIndex) {
    myProject = project;
    myProjectFileIndex = projectFileIndex;
  }

  @Nullable
  @Override
  @RequiredReadAction
  public PsiJavaModule findModule(@Nonnull VirtualFile file) {
    return myProjectFileIndex.isInLibrary(file)
      ? findDescriptorInLibrary(myProject, myProjectFileIndex, file)
      : JavaModuleGraphUtil.findDescriptorByModule(myProjectFileIndex.getModuleForFile(file),
                                                   myProjectFileIndex.isInTestSourceContent(file));
  }

  @RequiredReadAction
  public static PsiJavaModule findDescriptorInLibrary(Project project, ProjectFileIndex index, VirtualFile file) {
    VirtualFile root = index.getClassRootForFile(file);
    if (root != null) {
      VirtualFile descriptorFile = JavaModuleNameIndex.descriptorFile(root);
      if (descriptorFile != null) {
        PsiFile psiFile = PsiManager.getInstance(project).findFile(descriptorFile);
        if (psiFile instanceof PsiJavaFile) {
          return ((PsiJavaFile)psiFile).getModuleDeclaration();
        }
      }
      else if (root.getFileSystem() instanceof ArchiveFileSystem && "jar".equalsIgnoreCase(root.getExtension())) {
        return AutomaticJavaModule.findModule(PsiManager.getInstance(project), root);
      }
    }
    return null;
  }

  @Nullable
  @Override
  public PsiClass findClass(@Nonnull String qualifiedName, @Nonnull GlobalSearchScope scope) {
    return null;
  }

  @Nonnull
  @Override
  public PsiClass[] findClasses(@Nonnull String qualifiedName, @Nonnull GlobalSearchScope scope) {
    return PsiClass.EMPTY_ARRAY;
  }
}
