package consulo.java.language.impl;

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.LanguageLevel;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.language.Language;
import consulo.language.psi.PsiElement;
import consulo.language.util.ModuleUtilCore;
import consulo.language.version.LanguageVersion;
import consulo.language.version.LanguageVersionResolver;
import consulo.module.Module;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author VISTALL
 * @since 19:43/30.05.13
 */
@ExtensionImpl
public class JavaLanguageVersionResolver implements LanguageVersionResolver {
  @RequiredReadAction
  @Nonnull
  @Override
  public LanguageVersion getLanguageVersion(@Nonnull Language language, @Nullable PsiElement element) {
    if (element == null) {
      return LanguageLevel.HIGHEST.toLangVersion();
    } else {
      final Module moduleForPsiElement = ModuleUtilCore.findModuleForPsiElement(element);
      if (moduleForPsiElement == null) {
        return LanguageLevel.HIGHEST.toLangVersion();
      }
      final JavaModuleExtension extension = ModuleUtilCore.getExtension(moduleForPsiElement, JavaModuleExtension.class);
      if (extension == null) {
        return LanguageLevel.HIGHEST.toLangVersion();
      }
      return extension.getLanguageLevel().toLangVersion();
    }
  }

  @Nonnull
  @RequiredReadAction
  @Override
  public LanguageVersion getLanguageVersion(@Nonnull Language language, @Nullable Project project, @Nullable VirtualFile virtualFile) {
    if (project == null || virtualFile == null) {
      return LanguageLevel.HIGHEST.toLangVersion();
    }
    final Module moduleForPsiElement = ModuleUtilCore.findModuleForFile(virtualFile, project);
    if (moduleForPsiElement == null) {
      return LanguageLevel.HIGHEST.toLangVersion();
    }
    final JavaModuleExtension extension = ModuleUtilCore.getExtension(moduleForPsiElement, JavaModuleExtension.class);
    if (extension == null) {
      return LanguageLevel.HIGHEST.toLangVersion();
    }
    return extension.getLanguageLevel().toLangVersion();
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }
}
