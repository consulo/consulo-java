package com.intellij.lang.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mustbe.consulo.java.module.extension.JavaModuleExtension;
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
	@NotNull
  @Override
  public LanguageVersion getLanguageVersion(@NotNull Language language, @Nullable PsiElement element) {
    if (element == null) {
      return LanguageLevel.HIGHEST;
    }
    else {
      final Module moduleForPsiElement = ModuleUtilCore.findModuleForPsiElement(element);
      if (moduleForPsiElement == null) {
        return LanguageLevel.HIGHEST;
      }
      final JavaModuleExtension extension = ModuleUtilCore.getExtension(moduleForPsiElement, JavaModuleExtension.class);
      if (extension == null) {
        return LanguageLevel.HIGHEST;
      }
      return extension.getLanguageLevel();
    }
  }

  @Override
  public LanguageVersion getLanguageVersion(@NotNull Language language, @Nullable Project project, @Nullable VirtualFile virtualFile) {
    if (project == null || virtualFile == null) {
      return LanguageLevel.HIGHEST;
    }
    final Module moduleForPsiElement = ModuleUtilCore.findModuleForFile(virtualFile, project);
    if (moduleForPsiElement == null) {
      return LanguageLevel.HIGHEST;
    }
    final JavaModuleExtension extension = ModuleUtilCore.getExtension(moduleForPsiElement, JavaModuleExtension.class);
    if (extension == null) {
      return LanguageLevel.HIGHEST;
    }
    return extension.getLanguageLevel();
  }
}
