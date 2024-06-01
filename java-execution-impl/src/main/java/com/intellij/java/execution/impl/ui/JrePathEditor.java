/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.java.execution.impl.ui;

import com.intellij.java.language.projectRoots.JavaSdkType;
import consulo.annotation.DeprecationInfo;
import consulo.content.bundle.BundleHolder;
import consulo.ide.setting.ShowSettingsUtil;
import consulo.java.execution.JavaExecutionBundle;
import consulo.module.ui.awt.SdkComboBox;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.ex.awt.LabeledComponent;
import consulo.ui.ex.awt.PanelWithAnchor;
import consulo.ui.ex.awt.Wrapper;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.awt.event.ActionListener;

/**
 * @author nik
 */
public class JrePathEditor extends Wrapper implements PanelWithAnchor {
  private SdkComboBox mySdkComboBox;

  private LabeledComponent<SdkComboBox> myLabeledComponent;

  public JrePathEditor(DefaultJreSelector defaultJreSelector) {
    this();
  }

  /**
   * This constructor can be used in UI forms
   */
  public JrePathEditor() {
    BundleHolder model = ShowSettingsUtil.getInstance().getSdksModel();

    mySdkComboBox = new SdkComboBox(model, id -> id instanceof JavaSdkType, null, "Auto Select", PlatformIconGroup.actionsFind());

    myLabeledComponent = LabeledComponent.create(mySdkComboBox, JavaExecutionBundle.message("run.configuration.jre.label"));

    setContent(myLabeledComponent);
  }

  @Nullable
  public String getJrePathOrName() {
    return mySdkComboBox.getSelectedSdkName();
  }

  public boolean isAlternativeJreSelected() {
    SdkComboBox.SdkComboBoxItem selectedItem = mySdkComboBox.getSelectedItem();
    return !(selectedItem instanceof SdkComboBox.NullSdkComboBoxItem);
  }

  @Deprecated
  public void setDefaultJreSelector(DefaultJreSelector defaultJreSelector) {
  }

  @Deprecated
  @DeprecationInfo("Use #setByName()")
  public void setPathOrName(@Nullable String pathOrName, boolean useAlternativeJre) {
    setByName(pathOrName);
  }

  public void setByName(@Nullable String name) {
    mySdkComboBox.setSelectedSdk(name);
  }

  @Override
  public JComponent getAnchor() {
    return myLabeledComponent.getAnchor();
  }

  @Override
  public void setAnchor(JComponent anchor) {
    myLabeledComponent.setAnchor(anchor);
  }

  public void addActionListener(ActionListener listener) {
    mySdkComboBox.addActionListener(listener);
  }
}

