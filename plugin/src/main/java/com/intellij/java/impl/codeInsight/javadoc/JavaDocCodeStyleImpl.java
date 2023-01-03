package com.intellij.java.impl.codeInsight.javadoc;

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.impl.codeInsight.javadoc.JavaDocCodeStyle;
import consulo.annotation.component.ServiceImpl;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.language.codeStyle.CommonCodeStyleSettings;
import consulo.project.Project;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@ServiceImpl
public class JavaDocCodeStyleImpl extends JavaDocCodeStyle {
  private final Project myProject;

  @Inject
  public JavaDocCodeStyleImpl(Project project) {
    myProject = project;
  }

  @Override
  public boolean spaceBeforeComma() {
    CommonCodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(myProject).getCommonSettings(JavaLanguage.INSTANCE);
    return styleSettings.SPACE_BEFORE_COMMA;
  }

  @Override
  public boolean spaceAfterComma() {
    CommonCodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(myProject).getCommonSettings(JavaLanguage.INSTANCE);
    return styleSettings.SPACE_AFTER_COMMA;
  }
}
