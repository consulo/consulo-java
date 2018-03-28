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
package com.intellij.execution.application;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.swing.JComponent;
import javax.swing.JPanel;

import com.intellij.application.options.ModuleDescriptionsComboBox;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.ShortenCommandLine;
import com.intellij.execution.configurations.ConfigurationUtil;
import com.intellij.execution.ui.ClassBrowser;
import com.intellij.execution.ui.CommonJavaParametersPanel;
import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.execution.ui.DefaultJreSelector;
import com.intellij.execution.ui.JrePathEditor;
import com.intellij.execution.ui.ShortenCommandLineModeCombo;
import com.intellij.execution.util.JreVersionDetector;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.psi.JavaCodeFragment;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiMethodUtil;
import com.intellij.ui.EditorTextFieldWithBrowseButton;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.UIUtil;
import consulo.java.execution.JavaExecutionBundle;

public class ApplicationConfigurable extends SettingsEditor<ApplicationConfiguration>
{
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
	private JPanel myRootPanel;

	public ApplicationConfigurable(final Project project)
	{
		myProject = project;
		myVersionDetector = new JreVersionDetector();

		myMainClassField = new EditorTextFieldWithBrowseButton(project, true, new JavaCodeFragment.VisibilityChecker()
		{
			@Override
			public Visibility isDeclarationVisible(PsiElement declaration, PsiElement place)
			{
				if(declaration instanceof PsiClass)
				{
					final PsiClass aClass = (PsiClass) declaration;
					if(ConfigurationUtil.MAIN_CLASS.value(aClass) && PsiMethodUtil.findMainMethod(aClass) != null || place.getParent() != null && myModuleSelector.findClass(((PsiClass) declaration)
							.getQualifiedName()) != null)
					{
						return Visibility.VISIBLE;
					}
				}
				return Visibility.NOT_VISIBLE;
			}
		});

		List<PanelWithAnchor> list = new ArrayList<>();

		myModuleDescriptionsComboBox = new ModuleDescriptionsComboBox();
		myJrePathEditor = new JrePathEditor();
		myJrePathEditor.setDefaultJreSelector(DefaultJreSelector.fromSourceRootsDependencies(myModuleDescriptionsComboBox, getMainClassField()));
		list.add(myJrePathEditor);

		myShortenCommandLineModeCombo = new ShortenCommandLineModeCombo(myProject, myJrePathEditor, myModuleDescriptionsComboBox);
		myIncludeProviderDepsBox = new JBCheckBox(JavaExecutionBundle.message("application.configuration.include.provided.scope"));

		myRootPanel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, false));
		myRootPanel.add(labeled(myMainClassField, JavaExecutionBundle.message("application.configuration.main.class.label"), list));

		myCommonJavaParametersPanel = new CommonJavaParametersPanel();
		myCommonJavaParametersPanel.setPreferredSize(null);
		list.add(myCommonJavaParametersPanel);

		myRootPanel.add(myCommonJavaParametersPanel);

		myModuleSelector = new ConfigurationModuleSelector(project, myModuleDescriptionsComboBox);
		myModuleDescriptionsComboBox.addActionListener(e -> myCommonJavaParametersPanel.setModuleContext(myModuleSelector.getModule()));

		myRootPanel.add(labeled(myModuleDescriptionsComboBox, JavaExecutionBundle.message("application.configuration.use.classpath.and.jdk.of.module.label"), list));

		myRootPanel.add(myIncludeProviderDepsBox);

		myRootPanel.add(myJrePathEditor);

		myRootPanel.add(labeled(myShortenCommandLineModeCombo, JavaExecutionBundle.message("application.configuration.shorten.command.line.label"), list));

		myCommonJavaParametersPanel.setModuleContext(myModuleSelector.getModule());

		myShowSwingInspectorBox = new JBCheckBox(JavaExecutionBundle.message("show.swing.inspector"));

		myRootPanel.add(myShowSwingInspectorBox);

		ClassBrowser.createApplicationClassBrowser(project, myModuleSelector).setField(getMainClassField());

		UIUtil.mergeComponentsWithAnchor(list);
	}

	private JComponent labeled(JComponent component, String label, List<PanelWithAnchor> list)
	{
		LabeledComponent labeledComponent = LabeledComponent.create(component, label);
		list.add(labeledComponent);
		return labeledComponent;
	}

	@Override
	public void applyEditorTo(@Nonnull final ApplicationConfiguration configuration) throws ConfigurationException
	{
		myCommonJavaParametersPanel.applyTo(configuration);
		myModuleSelector.applyTo(configuration);
		final String className = getMainClassField().getText();
		final PsiClass aClass = myModuleSelector.findClass(className);
		configuration.MAIN_CLASS_NAME = aClass != null ? JavaExecutionUtil.getRuntimeQualifiedName(aClass) : className;
		configuration.ALTERNATIVE_JRE_PATH = myJrePathEditor.getJrePathOrName();
		configuration.ALTERNATIVE_JRE_PATH_ENABLED = myJrePathEditor.isAlternativeJreSelected();
		configuration.ENABLE_SWING_INSPECTOR = (myVersionDetector.isJre50Configured(configuration) || myVersionDetector.isModuleJre50Configured(configuration)) && myShowSwingInspectorBox
				.isSelected();
		configuration.setShortenCommandLine((ShortenCommandLine) myShortenCommandLineModeCombo.getSelectedItem());
		configuration.setIncludeProvidedScope(myIncludeProviderDepsBox.isSelected());

		updateShowSwingInspector(configuration);
	}

	@Override
	public void resetEditorFrom(@Nonnull final ApplicationConfiguration configuration)
	{
		myCommonJavaParametersPanel.reset(configuration);
		myModuleSelector.reset(configuration);
		getMainClassField().setText(configuration.MAIN_CLASS_NAME != null ? configuration.MAIN_CLASS_NAME.replaceAll("\\$", "\\.") : "");
		myJrePathEditor.setPathOrName(configuration.ALTERNATIVE_JRE_PATH, configuration.ALTERNATIVE_JRE_PATH_ENABLED);
		myShortenCommandLineModeCombo.setSelectedItem(configuration.getShortenCommandLine());
		myIncludeProviderDepsBox.setSelected(configuration.isProvidedScopeIncluded());

		updateShowSwingInspector(configuration);
	}

	private void updateShowSwingInspector(final ApplicationConfiguration configuration)
	{
		if(myVersionDetector.isJre50Configured(configuration) || myVersionDetector.isModuleJre50Configured(configuration))
		{
			myShowSwingInspectorBox.setEnabled(true);
			myShowSwingInspectorBox.setSelected(configuration.ENABLE_SWING_INSPECTOR);
			myShowSwingInspectorBox.setText(ExecutionBundle.message("show.swing.inspector"));
		}
		else
		{
			myShowSwingInspectorBox.setEnabled(false);
			myShowSwingInspectorBox.setSelected(false);
			myShowSwingInspectorBox.setText(ExecutionBundle.message("show.swing.inspector.disabled"));
		}
	}

	public EditorTextFieldWithBrowseButton getMainClassField()
	{
		return myMainClassField;
	}

	public CommonJavaParametersPanel getCommonProgramParameters()
	{
		return myCommonJavaParametersPanel;
	}

	@Override
	@Nonnull
	public JComponent createEditor()
	{
		return myRootPanel;
	}
}
