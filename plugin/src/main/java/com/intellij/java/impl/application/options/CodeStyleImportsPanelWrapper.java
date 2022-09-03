/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.impl.application.options;

import consulo.application.ApplicationBundle;
import consulo.language.codeStyle.ui.setting.CodeStyleAbstractPanel;
import com.intellij.java.language.impl.JavaFileType;
import consulo.colorScheme.EditorColorsScheme;
import consulo.codeEditor.EditorHighlighter;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.language.codeStyle.CodeStyleSettings;

import javax.annotation.Nonnull;
import javax.swing.*;

/**
 * @author Rustam Vishnyakov
 */
public class CodeStyleImportsPanelWrapper extends CodeStyleAbstractPanel {

  private CodeStyleImportsPanel myImporsPanel;

  protected CodeStyleImportsPanelWrapper(CodeStyleSettings settings) {
    super(settings);
    myImporsPanel = new CodeStyleImportsPanel();
  }


  @Override
  protected int getRightMargin() {
    return 0;
  }

  @Override
  protected EditorHighlighter createHighlighter(EditorColorsScheme scheme) {
    return null;
  }

  @Nonnull
  @Override
  protected FileType getFileType() {
    return JavaFileType.INSTANCE;
  }

  @Override
  protected String getPreviewText() {
    return null;
  }

  @Override
  public void apply(CodeStyleSettings settings) {
    myImporsPanel.apply(settings);
  }

  @Override
  public boolean isModified(CodeStyleSettings settings) {
    return myImporsPanel.isModified(settings);
  }

  @Override
  public JComponent getPanel() {
    return myImporsPanel;
  }

  @Override
  protected void resetImpl(CodeStyleSettings settings) {
    myImporsPanel.reset(settings);
  }

  @Override
  protected String getTabTitle() {
    return ApplicationBundle.message("title.imports");
  }
}
