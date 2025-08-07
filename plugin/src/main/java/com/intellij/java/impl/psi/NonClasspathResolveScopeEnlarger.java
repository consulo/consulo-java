package com.intellij.java.impl.psi;

import com.intellij.java.language.impl.JavaClassFileType;
import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.language.impl.psi.NonClasspathClassFinder;
import com.intellij.java.language.impl.psi.NonClasspathDirectoriesScope;
import com.intellij.java.language.psi.PsiElementFinder;
import consulo.annotation.component.ExtensionImpl;
import consulo.content.scope.SearchScope;
import consulo.language.psi.ResolveScopeEnlarger;
import consulo.module.content.ProjectFileIndex;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import jakarta.annotation.Nonnull;

import java.util.List;

/**
 * @author peter
 */
@ExtensionImpl
public class NonClasspathResolveScopeEnlarger extends ResolveScopeEnlarger {
    @Override
    public SearchScope getAdditionalResolveScope(@Nonnull VirtualFile file, Project project) {
        ProjectFileIndex index = ProjectFileIndex.getInstance(project);
        if (index.isInLibraryClasses(file) || index.isInContent(file)) {
            return null;
        }

        FileType fileType = file.getFileType();
        if (fileType == JavaFileType.INSTANCE || fileType == JavaClassFileType.INSTANCE) {
            return project.getExtensionPoint(PsiElementFinder.class).computeSafeIfAny(finder -> {
                if (finder instanceof NonClasspathClassFinder nonClasspathClassFinder) {
                    List<VirtualFile> roots = nonClasspathClassFinder.getClassRoots();
                    for (VirtualFile root : roots) {
                        if (VirtualFileUtil.isAncestor(root, file, true)) {
                            return NonClasspathDirectoriesScope.compose(roots);
                        }
                    }
                }
                return null;
            });
        }
        return null;
    }
}
