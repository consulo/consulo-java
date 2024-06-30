package com.intellij.java.impl.codeInsight;

import com.intellij.java.language.impl.ui.PackageChooser;
import com.intellij.java.language.impl.ui.PackageChooserFactory;
import consulo.annotation.component.ServiceImpl;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * @author VISTALL
 * @since 30-Jun-24
 */
@ServiceImpl
@Singleton
public class PackageChooserFactoryImpl implements PackageChooserFactory {
  private final Project myProject;

  @Inject
  public PackageChooserFactoryImpl(Project project) {
    myProject = project;
  }

  @Nonnull
  @Override
  public PackageChooser create() {
    return new PackageChooserDialog(CodeInsightLocalize.coveragePatternFilterEditorChoosePackageTitle().get(), myProject);
  }
}
