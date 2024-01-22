package consulo.java.impl.ide;

import com.intellij.java.language.vfs.jrt.JrtFileSystem;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.java.language.fileTypes.JModFileType;
import consulo.language.icon.IconDescriptor;
import consulo.language.icon.IconDescriptorUpdater;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;

import jakarta.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 09-Jan-17
 */
@ExtensionImpl(id = "javaModule", order = "after directory")
public class JavaModuleIconDescriptorUpdater implements IconDescriptorUpdater {
  @RequiredReadAction
  @Override
  public void updateIcon(@Nonnull IconDescriptor iconDescriptor, @jakarta.annotation.Nonnull PsiElement psiElement, int i) {
//		if(psiElement instanceof PsiDirectory && isModuleDirectory((PsiDirectory) psiElement))
    //		{
    //			iconDescriptor.setMainIcon(AllIcons.Nodes.Module);
    //		}
  }

  @RequiredReadAction
  public static boolean isModuleDirectory(PsiDirectory directory) {
    String name = directory.getName();
    if (name.equals("classes")) {
      return JModFileType.isModuleRoot(directory.getVirtualFile());
    } else if (directory.getVirtualFile().getFileSystem() instanceof JrtFileSystem) {
      return JrtFileSystem.isModuleRoot(directory.getVirtualFile());
    }
    return false;
  }
}
