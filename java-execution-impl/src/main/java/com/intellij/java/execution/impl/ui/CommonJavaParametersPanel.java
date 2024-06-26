/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.java.execution.CommonJavaRunConfigurationParameters;
import consulo.execution.localize.ExecutionLocalize;
import consulo.execution.ui.awt.CommonProgramParametersPanel;
import consulo.execution.ui.awt.RawCommandLineEditor;
import consulo.ui.ex.awt.LabeledComponent;

import javax.swing.*;
import java.awt.*;

public class CommonJavaParametersPanel extends CommonProgramParametersPanel {
  private LabeledComponent<RawCommandLineEditor> myVMParametersComponent;

  public CommonJavaParametersPanel() {
    super(true);
  }

  public CommonJavaParametersPanel(boolean init) {
    super(init);
  }

  @Override
  public void init() {
    super.init();
  }

  @Override
  protected void addComponents() {
    myVMParametersComponent = LabeledComponent.create(
      new RawCommandLineEditor(),
      ExecutionLocalize.runConfigurationJavaVmParametersLabel().get()
    );
    copyDialogCaption(myVMParametersComponent);

    myVMParametersComponent.setLabelLocation(BorderLayout.WEST);

    add(myVMParametersComponent);
    super.addComponents();
  }

  public void setVMParameters(String text) {
    myVMParametersComponent.getComponent().setText(text);
  }

  public String getVMParameters() {
    return myVMParametersComponent.getComponent().getText();
  }

  @Override
  public void setAnchor(JComponent labelAnchor) {
    super.setAnchor(labelAnchor);
    myVMParametersComponent.setAnchor(labelAnchor);
  }

  public void applyTo(CommonJavaRunConfigurationParameters configuration) {
    super.applyTo(configuration);
    configuration.setVMParameters(getVMParameters());
  }

  public void reset(CommonJavaRunConfigurationParameters configuration) {
    super.reset(configuration);
    setVMParameters(configuration.getVMParameters());
  }
}
