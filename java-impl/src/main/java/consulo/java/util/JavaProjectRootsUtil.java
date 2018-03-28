package consulo.java.util;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.projectView.impl.ProjectRootsUtil;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiFile;
import consulo.java.module.extension.JavaModuleExtension;
import consulo.roots.ContentFolderTypeProvider;
import consulo.roots.impl.ProductionResourceContentFolderTypeProvider;
import consulo.roots.impl.TestResourceContentFolderTypeProvider;

/**
 * @author VISTALL
 * @since 13:10/21.05.13
 */
public class JavaProjectRootsUtil extends ProjectRootsUtil
{
	public static boolean isJavaSourceFile(@Nonnull Project project, @Nonnull VirtualFile file, boolean withLibrary)
	{
		FileTypeManager fileTypeManager = FileTypeManager.getInstance();
		if(file.isDirectory())
		{
			return false;
		}
		if(file.getFileType() != JavaFileType.INSTANCE && !withLibrary)
		{
			return false;
		}
		if(fileTypeManager.isFileIgnored(file))
		{
			return false;
		}

		if(!withLibrary)
		{
			Module module = ModuleUtilCore.findModuleForFile(file, project);
			if(module == null)
			{
				return false;
			}

			ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
			if(moduleRootManager.getExtension(JavaModuleExtension.class) == null)
			{
				return false;
			}
		}

		final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
		if(isInsideResourceRoot(file, fileIndex))
		{
			return false;
		}
		return fileIndex.isInSource(file) || withLibrary && fileIndex.isInLibraryClasses(file);
	}

	public static boolean isOutsideSourceRoot(@Nullable PsiFile psiFile)
	{
		if(psiFile == null)
		{
			return false;
		}
		if(psiFile instanceof PsiCodeFragment)
		{
			return false;
		}
		final VirtualFile file = psiFile.getVirtualFile();
		if(file == null)
		{
			return false;
		}

		ProjectFileIndex fileIndex = ProjectRootManager.getInstance(psiFile.getProject()).getFileIndex();

		if(fileIndex.isInSource(file) && !fileIndex.isInLibraryClasses(file))
		{
			if(isInsideResourceRoot(file, fileIndex))
			{
				return true;
			}

			return ModuleUtilCore.getExtension(psiFile, JavaModuleExtension.class) == null;
		}
		return false;
	}

	private static boolean isInsideResourceRoot(VirtualFile file, ProjectFileIndex fileIndex)
	{
		ContentFolderTypeProvider provider = fileIndex.getContentFolderTypeForFile(file);
		return provider == ProductionResourceContentFolderTypeProvider.getInstance() || provider == TestResourceContentFolderTypeProvider.getInstance();
	}
}
