package consulo.java;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import consulo.annotation.access.RequiredReadAction;
import consulo.java.language.module.extension.JavaModuleExtension;
import com.intellij.lang.Language;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.java.language.LanguageLevel;
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
	@Nonnull
	@Override
	public LanguageVersion getLanguageVersion(@Nonnull Language language, @Nullable PsiElement element)
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

	@Nonnull
	@RequiredReadAction
	@Override
	public LanguageVersion getLanguageVersion(@Nonnull Language language, @Nullable Project project, @Nullable VirtualFile virtualFile)
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
