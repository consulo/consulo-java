package consulo.java.manifest.lang;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import consulo.language.Language;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.version.LanguageVersion;
import consulo.language.version.LanguageVersionResolver;

/**
 * @author VISTALL
 * @since 22:03/24.06.13
 */
public class ManifestLanguageVersionResolver implements LanguageVersionResolver
{
	@RequiredReadAction
	@Nonnull
	@Override
	public LanguageVersion getLanguageVersion(@Nonnull Language language, @Nullable PsiElement element)
	{
		if(element == null)
		{
			return ManifestLanguageVersion.Manifest;
		}
		final PsiFile containingFile = element.getContainingFile();
		if(containingFile == null)
		{
			return ManifestLanguageVersion.Manifest;
		}
		return getLanguageVersion(language, element.getProject(), containingFile.getVirtualFile());
	}

	@RequiredReadAction
	@Nonnull
	@Override
	public LanguageVersion getLanguageVersion(@Nonnull Language language, @Nullable Project project, @Nullable VirtualFile virtualFile)
	{
		if(virtualFile == null)
		{
			return ManifestLanguageVersion.Manifest;
		}
		return virtualFile.getFileType() == BndFileType.INSTANCE ? ManifestLanguageVersion.Bnd : ManifestLanguageVersion.Manifest;
	}
}
