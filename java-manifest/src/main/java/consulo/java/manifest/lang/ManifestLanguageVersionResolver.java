package consulo.java.manifest.lang;

import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.version.LanguageVersion;
import consulo.language.version.LanguageVersionResolver;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import org.osmorc.manifest.lang.ManifestLanguage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author VISTALL
 * @since 22:03/24.06.13
 */
@ExtensionImpl
public class ManifestLanguageVersionResolver implements LanguageVersionResolver {
  @RequiredReadAction
  @Nonnull
  @Override
  public LanguageVersion getLanguageVersion(@Nonnull Language language, @Nullable PsiElement element) {
    if (element == null) {
      return ManifestLanguageVersion.Manifest;
    }
    final PsiFile containingFile = element.getContainingFile();
    if (containingFile == null) {
      return ManifestLanguageVersion.Manifest;
    }
    return getLanguageVersion(language, element.getProject(), containingFile.getVirtualFile());
  }

  @RequiredReadAction
  @Nonnull
  @Override
  public LanguageVersion getLanguageVersion(@Nonnull Language language, @Nullable Project project, @Nullable VirtualFile virtualFile) {
    if (virtualFile == null) {
      return ManifestLanguageVersion.Manifest;
    }
    return virtualFile.getFileType() == BndFileType.INSTANCE ? ManifestLanguageVersion.Bnd : ManifestLanguageVersion.Manifest;
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return ManifestLanguage.INSTANCE;
  }
}
