package consulo.java.ide;

import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import consulo.annotations.RequiredReadAction;
import consulo.ide.IconDescriptor;
import consulo.ide.IconDescriptorUpdater;
import consulo.java.JavaIcons;
import consulo.java.fileTypes.JModFileType;
import consulo.vfs.util.ArchiveVfsUtil;

/**
 * @author VISTALL
 * @since 09-Jan-17
 */
public class JavaModuleIconDescriptorUpdater implements IconDescriptorUpdater
{
	@RequiredReadAction
	@Override
	public void updateIcon(@NotNull IconDescriptor iconDescriptor, @NotNull PsiElement psiElement, int i)
	{
		if(psiElement instanceof PsiDirectory && isModuleDirectory((PsiDirectory) psiElement))
		{
			iconDescriptor.setMainIcon(JavaIcons.Nodes.JavaModuleRoot);
		}
	}

	@RequiredReadAction
	public static boolean isModuleDirectory(PsiDirectory directory)
	{
		String name = directory.getName();
		if(name.equals("classes"))
		{
			VirtualFile archiveFile = ArchiveVfsUtil.getVirtualFileForArchive(directory.getVirtualFile());
			return archiveFile != null && archiveFile.getFileType() == JModFileType.INSTANCE;
		}
		return false;
	}
}
