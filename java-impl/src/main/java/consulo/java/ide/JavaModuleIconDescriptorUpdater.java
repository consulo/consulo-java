package consulo.java.ide;

import com.intellij.openapi.vfs.jrt.JrtFileSystem;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import consulo.annotation.access.RequiredReadAction;
import consulo.ide.IconDescriptor;
import consulo.ide.IconDescriptorUpdater;
import consulo.java.JavaIcons;
import consulo.java.fileTypes.JModFileType;

import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 09-Jan-17
 */
public class JavaModuleIconDescriptorUpdater implements IconDescriptorUpdater
{
	@RequiredReadAction
	@Override
	public void updateIcon(@Nonnull IconDescriptor iconDescriptor, @Nonnull PsiElement psiElement, int i)
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
			return JModFileType.isModuleRoot(directory.getVirtualFile());
		}
		else if(directory.getVirtualFile().getFileSystem() instanceof JrtFileSystem)
		{
			return JrtFileSystem.isModuleRoot(directory.getVirtualFile());
		}
		return false;
	}
}
