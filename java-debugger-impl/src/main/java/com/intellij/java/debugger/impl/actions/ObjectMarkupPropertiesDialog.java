/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.java.debugger.impl.actions;

import consulo.ide.impl.idea.ide.util.PropertiesComponent;
import consulo.ide.impl.idea.xdebugger.impl.ui.tree.ValueMarkerPresentationDialogBase;
import consulo.ui.ex.awt.MultiLineLabel;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Feb 4, 2007
 */
public class ObjectMarkupPropertiesDialog extends ValueMarkerPresentationDialogBase {
  @NonNls private static final String MARK_ALL_REFERENCED_VALUES_KEY = "debugger.mark.all.referenced.values";
  private JCheckBox myCbMarkAdditionalFields;
  private final boolean mySuggestAdditionalMarkup;
  private JPanel myAdditionalPropertiesPanel;
  private MultiLineLabel myDescriptionLabel;

  public ObjectMarkupPropertiesDialog(@Nonnull final String defaultText, boolean suggestAdditionalMarkup) {
    super(defaultText);
    mySuggestAdditionalMarkup = suggestAdditionalMarkup;
    myDescriptionLabel.setText("If the value is referenced by a constant field of an abstract class,\n" +
                               "IDEA could additionally mark all values referenced from this class with the names of referencing fields.");
    myCbMarkAdditionalFields.setSelected(PropertiesComponent.getInstance().getBoolean(MARK_ALL_REFERENCED_VALUES_KEY, true));
    init();
  }

  @Override
  protected void doOKAction() {
    if (mySuggestAdditionalMarkup) {
      PropertiesComponent.getInstance().setValue(MARK_ALL_REFERENCED_VALUES_KEY, Boolean.toString(myCbMarkAdditionalFields.isSelected()));
    }
    super.doOKAction();
  }

  @Override
  protected JComponent createCenterPanel() {
    JComponent mainPanel = super.createCenterPanel();
    if (!mySuggestAdditionalMarkup) {
      return mainPanel;
    }
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(BorderLayout.CENTER, mainPanel);
    panel.add(BorderLayout.SOUTH, myAdditionalPropertiesPanel);
    return panel;
  }

  public boolean isMarkAdditionalFields() {
    return mySuggestAdditionalMarkup && myCbMarkAdditionalFields.isSelected();
  }
}
