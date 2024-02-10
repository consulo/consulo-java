package com.intellij.java.impl.copyright.psi;

import com.intellij.java.language.impl.JavaFileType;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.copyright.UpdateCopyrightsProvider;
import consulo.language.copyright.UpdatePsiFileCopyright;
import consulo.language.copyright.config.CopyrightFileConfig;
import consulo.language.copyright.config.CopyrightProfile;
import consulo.language.copyright.ui.TemplateCommentPanel;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import consulo.virtualFileSystem.fileType.FileType;

import jakarta.annotation.Nonnull;
import javax.swing.*;

@ExtensionImpl
public class UpdateJavaCopyrightsProvider extends UpdateCopyrightsProvider<CopyrightFileConfig> {
  @Nonnull
  @Override
  public FileType getFileType() {
    return JavaFileType.INSTANCE;
  }

  @Nonnull
  @Override
  public UpdatePsiFileCopyright<CopyrightFileConfig> createInstance(@Nonnull PsiFile file, @Nonnull CopyrightProfile copyrightProfile) {
    return new UpdateJavaFileCopyright(file, copyrightProfile);
  }

  @Nonnull
  @Override
  public CopyrightFileConfig createDefaultOptions() {
    return new CopyrightFileConfig();
  }

  @Nonnull
  @Override
  public TemplateCommentPanel createConfigurable(@Nonnull Project project, @Nonnull TemplateCommentPanel parentPane,
                                                 @Nonnull FileType fileType) {
    return new TemplateCommentPanel(fileType, parentPane, project) {
      @Override
      public void addAdditionalComponents(@Nonnull JPanel additionalPanel) {
        addLocationInFile(new String[]{
            "Before Package",
            "Before Imports",
            "Before Class"
        });
      }
    };
  }
}
