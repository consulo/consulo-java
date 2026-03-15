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

import javax.swing.*;

@ExtensionImpl
public class UpdateJavaCopyrightsProvider extends UpdateCopyrightsProvider<CopyrightFileConfig> {
  @Override
  public FileType getFileType() {
    return JavaFileType.INSTANCE;
  }

  @Override
  public UpdatePsiFileCopyright<CopyrightFileConfig> createInstance(PsiFile file, CopyrightProfile copyrightProfile) {
    return new UpdateJavaFileCopyright(file, copyrightProfile);
  }

  @Override
  public CopyrightFileConfig createDefaultOptions() {
    return new CopyrightFileConfig();
  }

  @Override
  public TemplateCommentPanel createConfigurable(Project project, TemplateCommentPanel parentPane,
                                                 FileType fileType) {
    return new TemplateCommentPanel(fileType, parentPane, project) {
      @Override
      public void addAdditionalComponents(JPanel additionalPanel) {
        addLocationInFile(new String[]{
            "Before Package",
            "Before Imports",
            "Before Class"
        });
      }
    };
  }
}
