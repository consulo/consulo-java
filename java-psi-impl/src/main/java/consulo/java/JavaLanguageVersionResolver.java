package consulo.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import consulo.annotations.RequiredReadAction;
import consulo.java.module.extension.JavaModuleExtension;
import com.intellij.lang.Language;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import consulo.lang.LanguageVersion;
import consulo.lang.LanguageVersionResolver;

/**
 * @author VISTALL
 * @since 19:43/30.05.13
 */
public class JavaLanguageVersionResolver implements LanguageVersionResolver
{
	@RequiredReadAction
	@NotNull
	@Override
	public LanguageVersion getLanguageVersion(@NotNull Language language, @Nullable PsiElement element)
	{
		if(element == null)
		{
			return LanguageLevel.HIGHEST.toLangVersion();
		}
		else
		{
			final Module moduleForPsiElement = ModuleUtilCore.findModuleForPsiElement(element);
			if(moduleForPsiElement == null)
			{
				return LanguageLevel.HIGHEST.toLangVersion();
			}
			final JavaModuleExtension extension = ModuleUtilCore.getExtension(moduleForPsiElement, JavaModuleExtension.class);
			if(extension == null)
			{
				return LanguageLevel.HIGHEST.toLangVersion();
			}
			return extension.getLanguageLevel().toLangVersion();
		}
	}

	@NotNull
	@RequiredReadAction
	@Override
	public LanguageVersion getLanguageVersion(@NotNull Language language, @Nullable Project project, @Nullable VirtualFile virtualFile)
	{
		if(project == null || virtualFile == null)
		{
			return LanguageLevel.HIGHEST.toLangVersion();
		}
		final Module moduleForPsiElement = ModuleUtilCore.findModuleForFile(virtualFile, project);
		if(moduleForPsiElement == null)
		{
			return LanguageLevel.HIGHEST.toLangVersion();
		}
		final JavaModuleExtension extension = ModuleUtilCore.getExtension(moduleForPsiElement, JavaModuleExtension.class);
		if(extension == null)
		{
			return LanguageLevel.HIGHEST.toLangVersion();
		}
		return extension.getLanguageLevel().toLangVersion();
	}
}
