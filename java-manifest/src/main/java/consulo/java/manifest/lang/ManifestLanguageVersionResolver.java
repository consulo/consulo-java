package consulo.java.manifest.lang;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import consulo.annotation.access.RequiredReadAction;
import consulo.lang.LanguageVersion;
import consulo.lang.LanguageVersionResolver;

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
