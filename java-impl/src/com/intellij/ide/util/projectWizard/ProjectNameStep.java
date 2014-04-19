/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ide.util.projectWizard;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 21, 2004
 */
public class ProjectNameStep extends ModuleWizardStep {
  private final NamePathComponent myNamePathComponent;
  private final JPanel myPanel;
  private final WizardContext myWizardContext;

  public ProjectNameStep(WizardContext wizardContext) {
    myWizardContext = wizardContext;
    myNamePathComponent = new NamePathComponent(IdeBundle.message("label.project.name"), IdeBundle.message("label.component.file.location",
                                                                                                           StringUtil.capitalize(myWizardContext.getPresentationName())), 'a', 'l',
                                                IdeBundle.message("title.select.project.file.directory", myWizardContext.getPresentationName()),
                                                IdeBundle.message("description.select.project.file.directory", myWizardContext.getPresentationName()));
    myPanel = new JPanel(new GridBagLayout());
    myPanel.setBorder(BorderFactory.createEtchedBorder());

    ApplicationInfo info = ApplicationInfo.getInstance();
    String appName = info.getVersionName();
    myPanel.add(new JLabel(IdeBundle.message("label.please.enter.project.name", appName, wizardContext.getPresentationName())),
                new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(8, 10, 8, 10), 0, 0));

    myPanel.add(myNamePathComponent, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(8, 10, 8, 10), 0, 0));
  }

  public JComponent getPreferredFocusedComponent() {
    return myNamePathComponent.getNameComponent();
  }

  public String getHelpId() {
    return "reference.dialogs.new.project.import.name";
  }

  public JComponent getComponent() {
    return myPanel;
  }

  public void updateStep() {
    super.updateStep();
    myNamePathComponent.setPath(FileUtil.toSystemDependentName(myWizardContext.getProjectFileDirectory()));
    String name = myWizardContext.getProjectName();
    if (name == null) {
      List<String> components = StringUtil.split(FileUtil.toSystemIndependentName(myWizardContext.getProjectFileDirectory()), "/");
      if (!components.isEmpty()) {
        name = components.get(components.size()-1);
      }
    }
    myNamePathComponent.setNameValue(name);
    if (name != null) {
      myNamePathComponent.getNameComponent().setSelectionStart(0);
      myNamePathComponent.getNameComponent().setSelectionEnd(name.length());
    }
  }

  public void updateDataModel() {
    myWizardContext.setProjectName(getProjectName());
    myWizardContext.setProjectFileDirectory(getProjectFileDirectory());
  }

  public Icon getIcon() {
    return myWizardContext.getStepIcon();
  }

  public boolean validate() throws ConfigurationException {
    String name = myNamePathComponent.getNameValue();
    if (name.length() == 0) {
      final ApplicationInfo info = ApplicationManager.getApplication().getComponent(ApplicationInfo.class);
      throw new ConfigurationException(IdeBundle.message("prompt.new.project.file.name", info.getVersionName(), myWizardContext.getPresentationName()));
    }

    final String projectFileDirectory = getProjectFileDirectory();
    if (projectFileDirectory.length() == 0) {
      throw new ConfigurationException(IdeBundle.message("prompt.enter.project.file.location", myWizardContext.getPresentationName()));
    }

    final boolean shouldPromptCreation = myNamePathComponent.isPathChangedByUser();
    if (!ProjectWizardUtil.createDirectoryIfNotExists(IdeBundle.message("directory.project.file.directory",myWizardContext.getPresentationName()), projectFileDirectory, shouldPromptCreation)) {
      return false;
    }

    boolean shouldContinue = true;

    final String path = getProjectFileDirectory() + "/" + Project.DIRECTORY_STORE_FOLDER;
    final File projectFile = new File(path);
    if (projectFile.exists()) {
      final String title = myWizardContext.isCreatingNewProject()
                           ? IdeBundle.message("title.new.project")
                           : IdeBundle.message("title.add.module");
      final String message = IdeBundle.message("prompt.overwrite.project.folder",
                                                 Project.DIRECTORY_STORE_FOLDER, projectFile.getParentFile().getAbsolutePath());
      int answer = Messages.showYesNoDialog(message, title, Messages.getQuestionIcon());
      shouldContinue = answer == Messages.OK;
    }

    return shouldContinue;
  }

  public String getProjectFileDirectory() {
    return FileUtil.toSystemIndependentName(myNamePathComponent.getPath());
  }

  public String getProjectName() {
    return myNamePathComponent.getNameValue();
  }

  @Override
  public String getName() {
    return "Name";
  }

  public boolean isStepVisible() {
    final ProjectBuilder builder = myWizardContext.getProjectBuilder();
    if (builder != null && builder.isUpdate()) return false;
    return super.isStepVisible();
  }
}
