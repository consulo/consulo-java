package consulo.java.impl.util;

import com.intellij.java.language.impl.JavaFileType;
import consulo.content.ContentFolderTypeProvider;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.language.content.ProductionResourceContentFolderTypeProvider;
import consulo.language.content.ProjectRootsUtil;
import consulo.language.content.TestResourceContentFolderTypeProvider;
import consulo.language.file.FileTypeManager;
import consulo.language.psi.PsiCodeFragment;
import consulo.language.psi.PsiFile;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;

import jakarta.annotation.Nullable;

/**
 * @author VISTALL
 * @since 13:10/21.05.13
 */
public class JavaProjectRootsUtil extends ProjectRootsUtil {
  public static boolean isJavaSourceFile(@jakarta.annotation.Nonnull Project project, @Nonnull VirtualFile file, boolean withLibrary) {
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    if (file.isDirectory()) {
      return false;
    }
    if (file.getFileType() != JavaFileType.INSTANCE && !withLibrary) {
      return false;
    }
    if (fileTypeManager.isFileIgnored(file)) {
      return false;
    }

    if (!withLibrary) {
      Module module = ModuleUtilCore.findModuleForFile(file, project);
      if (module == null) {
        return false;
      }

      ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
      if (moduleRootManager.getExtension(JavaModuleExtension.class) == null) {
        return false;
      }
    }

    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    if (isInsideResourceRoot(file, fileIndex)) {
      return false;
    }
    return fileIndex.isInSource(file) || withLibrary && fileIndex.isInLibraryClasses(file);
  }

  public static boolean isOutsideSourceRoot(@Nullable PsiFile psiFile) {
    if (psiFile == null) {
      return false;
    }
    if (psiFile instanceof PsiCodeFragment) {
      return false;
    }
    final VirtualFile file = psiFile.getVirtualFile();
    if (file == null) {
      return false;
    }

    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(psiFile.getProject()).getFileIndex();

    if (fileIndex.isInSource(file) && !fileIndex.isInLibraryClasses(file)) {
      if (isInsideResourceRoot(file, fileIndex)) {
        return true;
      }

      return ModuleUtilCore.getExtension(psiFile, JavaModuleExtension.class) == null;
    }
    return false;
  }

  private static boolean isInsideResourceRoot(VirtualFile file, ProjectFileIndex fileIndex) {
    ContentFolderTypeProvider provider = fileIndex.getContentFolderTypeForFile(file);
    return provider == ProductionResourceContentFolderTypeProvider.getInstance() || provider == TestResourceContentFolderTypeProvider.getInstance();
  }
}
