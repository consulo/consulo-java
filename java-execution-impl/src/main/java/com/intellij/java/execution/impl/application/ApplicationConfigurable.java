/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.java.execution.impl.application;

import com.intellij.java.execution.CommonJavaRunConfigurationParameters;
import com.intellij.java.execution.JavaExecutionUtil;
import com.intellij.java.execution.ShortenCommandLine;
import com.intellij.java.execution.configurations.ConfigurationUtil;
import com.intellij.java.execution.impl.ui.*;
import com.intellij.java.execution.impl.util.JreVersionDetector;
import com.intellij.java.language.impl.ui.EditorTextFieldWithBrowseButton;
import com.intellij.java.language.psi.JavaCodeFragment;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.util.PsiMethodUtil;
import consulo.configurable.ConfigurationException;
import consulo.execution.configuration.ui.SettingsEditor;
import consulo.execution.localize.ExecutionLocalize;
import consulo.java.execution.JavaExecutionBundle;
import consulo.language.psi.PsiElement;
import consulo.module.ui.awt.ModuleDescriptionsComboBox;
import consulo.project.Project;
import consulo.ui.ex.awt.JBCheckBox;
import consulo.ui.ex.awt.LabeledComponent;
import consulo.ui.ex.awt.UIUtil;
import jakarta.annotation.Nonnull;

import javax.swing.*;

public class ApplicationConfigurable extends SettingsEditor<ApplicationConfiguration> {
  private final JreVersionDetector myVersionDetector;
  private final ConfigurationModuleSelector myModuleSelector;
  private final Project myProject;

  private ModuleDescriptionsComboBox myModuleDescriptionsComboBox;
  private ShortenCommandLineModeCombo myShortenCommandLineModeCombo;
  private CommonJavaParametersPanel myCommonJavaParametersPanel;
  private EditorTextFieldWithBrowseButton myMainClassField;
  private JBCheckBox myIncludeProviderDepsBox;
  private JBCheckBox myShowSwingInspectorBox;
  private JrePathEditor myJrePathEditor;

  public ApplicationConfigurable(final Project project) {
    myProject = project;
    myVersionDetector = new JreVersionDetector();

    myMainClassField = new EditorTextFieldWithBrowseButton(project, true, new JavaCodeFragment.VisibilityChecker() {
      @Override
      public Visibility isDeclarationVisible(PsiElement declaration, PsiElement place) {
        if (declaration instanceof PsiClass aClass) {
          if (ConfigurationUtil.MAIN_CLASS.test(aClass) && PsiMethodUtil.findMainMethod(aClass) != null
            || place.getParent() != null && myModuleSelector.findClass(aClass.getQualifiedName()) != null) {
            return Visibility.VISIBLE;
          }
        }
        return Visibility.NOT_VISIBLE;
      }
    });

    myModuleDescriptionsComboBox = new ModuleDescriptionsComboBox();
    myJrePathEditor = new JrePathEditor();
    myJrePathEditor.setDefaultJreSelector(DefaultJreSelector.fromSourceRootsDependencies(myModuleDescriptionsComboBox, myMainClassField));

    myShortenCommandLineModeCombo = new ShortenCommandLineModeCombo(myProject, myJrePathEditor, myModuleDescriptionsComboBox);
    myIncludeProviderDepsBox = new JBCheckBox(JavaExecutionBundle.message("application.configuration.include.provided.scope"));

    myModuleSelector = new ConfigurationModuleSelector(project, myModuleDescriptionsComboBox);
    myModuleDescriptionsComboBox.addActionListener(e -> myCommonJavaParametersPanel.setModuleContext(myModuleSelector.getModule()));

    myShowSwingInspectorBox = new JBCheckBox(JavaExecutionBundle.message("show.swing.inspector"));

    myCommonJavaParametersPanel = new CommonJavaParametersPanel(false) {
      private LabeledComponent myClassFieldLabel;
      private LabeledComponent myModuleBoxLabel;
      private LabeledComponent myShortenLabel;

      @Override
      protected void addComponents() {
        add(myClassFieldLabel = LabeledComponent.create(myMainClassField, JavaExecutionBundle.message("application.configuration.main.class.label")));

        super.addComponents();

        add(myModuleBoxLabel = LabeledComponent.create(myModuleDescriptionsComboBox, JavaExecutionBundle.message("application.configuration.use.classpath.and.jdk.of.module.label")));

        add(myIncludeProviderDepsBox);

        add(myJrePathEditor);

        add(myShortenLabel = LabeledComponent.create(myShortenCommandLineModeCombo, JavaExecutionBundle.message("application.configuration.shorten.command.line.label")));

        add(myShowSwingInspectorBox);
      }

      @Override
      public void setAnchor(JComponent labelAnchor) {
        myClassFieldLabel.setAnchor(labelAnchor);
        super.setAnchor(labelAnchor);
        myModuleBoxLabel.setAnchor(labelAnchor);
        myShortenLabel.setAnchor(labelAnchor);
      }

      @Override
      public void applyTo(CommonJavaRunConfigurationParameters configuration) {
        super.applyTo(configuration);

        ApplicationConfiguration app = (ApplicationConfiguration) configuration;

        final String className = myMainClassField.getText();
        final PsiClass aClass = myModuleSelector.findClass(className);
        app.MAIN_CLASS_NAME = aClass != null ? JavaExecutionUtil.getRuntimeQualifiedName(aClass) : className;

        myModuleSelector.applyTo(app);

        app.ALTERNATIVE_JRE_PATH = myJrePathEditor.getJrePathOrName();
        app.ALTERNATIVE_JRE_PATH_ENABLED = myJrePathEditor.isAlternativeJreSelected();
        app.ENABLE_SWING_INSPECTOR = (myVersionDetector.isJre50Configured(configuration) || myVersionDetector.isModuleJre50Configured(app)) && myShowSwingInspectorBox
            .isSelected();
        app.setShortenCommandLine((ShortenCommandLine) myShortenCommandLineModeCombo.getSelectedItem());
        app.setIncludeProvidedScope(myIncludeProviderDepsBox.isSelected());

        updateShowSwingInspector(app);
      }

      @Override
      public void reset(CommonJavaRunConfigurationParameters configuration) {
        super.reset(configuration);

        ApplicationConfiguration app = (ApplicationConfiguration) configuration;

        myMainClassField.setText(app.MAIN_CLASS_NAME != null ? app.MAIN_CLASS_NAME.replaceAll("\\$", "\\.") : "");

        myModuleSelector.reset(app);
        myJrePathEditor.setPathOrName(app.ALTERNATIVE_JRE_PATH, app.ALTERNATIVE_JRE_PATH_ENABLED);
        myShortenCommandLineModeCombo.setSelectedItem(app.getShortenCommandLine());
        myIncludeProviderDepsBox.setSelected(app.isProvidedScopeIncluded());

        updateShowSwingInspector(app);
      }
    };

    myCommonJavaParametersPanel.init();
    myCommonJavaParametersPanel.setPreferredSize(null);

    myCommonJavaParametersPanel.setModuleContext(myModuleSelector.getModule());

    UIUtil.mergeComponentsWithAnchor(myCommonJavaParametersPanel);

    ClassBrowser.createApplicationClassBrowser(project, myModuleSelector).setField(myMainClassField);
  }

  @Override
  public void applyEditorTo(@Nonnull final ApplicationConfiguration configuration) throws ConfigurationException {
    myCommonJavaParametersPanel.applyTo(configuration);
  }

  @Override
  public void resetEditorFrom(@Nonnull final ApplicationConfiguration configuration) {
    myCommonJavaParametersPanel.reset(configuration);
  }

  private void updateShowSwingInspector(final ApplicationConfiguration configuration) {
    if (myVersionDetector.isJre50Configured(configuration) || myVersionDetector.isModuleJre50Configured(configuration)) {
      myShowSwingInspectorBox.setEnabled(true);
      myShowSwingInspectorBox.setSelected(configuration.ENABLE_SWING_INSPECTOR);
      myShowSwingInspectorBox.setText(ExecutionLocalize.showSwingInspector().get());
    } else {
      myShowSwingInspectorBox.setEnabled(false);
      myShowSwingInspectorBox.setSelected(false);
      myShowSwingInspectorBox.setText(ExecutionLocalize.showSwingInspectorDisabled().get());
    }
  }

  public EditorTextFieldWithBrowseButton getMainClassField() {
    return myMainClassField;
  }

  public CommonJavaParametersPanel getCommonProgramParameters() {
    return myCommonJavaParametersPanel;
  }

  @Override
  @Nonnull
  public JComponent createEditor() {
    return myCommonJavaParametersPanel;
  }
}
