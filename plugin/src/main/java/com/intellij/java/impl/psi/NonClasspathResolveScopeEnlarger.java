package com.intellij.java.impl.psi;

import com.intellij.java.language.impl.JavaClassFileType;
import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.language.impl.psi.NonClasspathClassFinder;
import com.intellij.java.language.psi.PsiElementFinder;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.ResolveScopeEnlarger;
import com.intellij.psi.search.NonClasspathDirectoriesScope;
import com.intellij.psi.search.SearchScope;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * @author peter
 */
public class NonClasspathResolveScopeEnlarger extends ResolveScopeEnlarger {
  @Override
  public SearchScope getAdditionalResolveScope(@Nonnull VirtualFile file, Project project) {
    ProjectFileIndex index = ProjectFileIndex.getInstance(project);
    if (index.isInLibraryClasses(file) || index.isInContent(file)) {
      return null;
    }

    FileType fileType = file.getFileType();
    if (fileType == JavaFileType.INSTANCE || fileType == JavaClassFileType.INSTANCE) {
      for (PsiElementFinder finder : Extensions.getExtensions(PsiElementFinder.EP_NAME, project)) {
        if (finder instanceof NonClasspathClassFinder) {
          final List<VirtualFile> roots = ((NonClasspathClassFinder) finder).getClassRoots();
          for (VirtualFile root : roots) {
            if (VfsUtilCore.isAncestor(root, file, true)) {
              return NonClasspathDirectoriesScope.compose(roots);
            }
          }
        }
      }
    }
    return null;
  }
}
