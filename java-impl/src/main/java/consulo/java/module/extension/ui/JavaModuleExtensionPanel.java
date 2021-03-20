/*
 * Copyright 2013-2015 must-be.org
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

package consulo.java.module.extension.ui;

import com.intellij.compiler.impl.javaCompiler.TargetOptionsComponent;
import com.intellij.core.JavaCoreBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionToolbarPosition;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.util.ObjectUtil;
import com.intellij.util.ui.JBUI;
import consulo.annotation.access.RequiredReadAction;
import consulo.extension.ui.ModuleExtensionSdkBoxBuilder;
import consulo.java.module.extension.JavaModuleExtension;
import consulo.java.module.extension.JavaMutableModuleExtension;
import consulo.java.module.extension.SpecialDirLocation;
import consulo.module.extension.ModuleExtension;
import consulo.module.extension.ModuleExtensionWithSdk;
import consulo.module.extension.MutableModuleInheritableNamedPointer;
import consulo.ui.annotation.RequiredUIAccess;

import javax.annotation.Nonnull;
import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.event.ItemEvent;

/**
 * @author VISTALL
 * @since 15.03.2015
 */
public class JavaModuleExtensionPanel extends JPanel
{
	private final JavaMutableModuleExtension<?> myExtension;

	private ComboBox<Object> myLanguageLevelComboBox;
	private JRadioButton myModuleDirRadioButton;
	private JRadioButton mySourceDirRadioButton;

	@RequiredUIAccess
	public JavaModuleExtensionPanel(final JavaMutableModuleExtension<?> extension, Runnable classpathStateUpdater)
	{
		super(new VerticalFlowLayout());
		myExtension = extension;

		ModuleExtensionSdkBoxBuilder<?> sdkBoxBuilder = ModuleExtensionSdkBoxBuilder.createAndDefine(extension, classpathStateUpdater);

		myLanguageLevelComboBox = new ComboBox<>();
		myLanguageLevelComboBox.setRenderer(new ColoredListCellRenderer<Object>()
		{
			@Override
			protected void customizeCellRenderer(@Nonnull JList<?> jList, Object value, int i, boolean b, boolean b1)
			{
				if(value == ObjectUtil.NULL)
				{
					append(TargetOptionsComponent.COMPILER_DEFAULT, SimpleTextAttributes.GRAY_ATTRIBUTES);
				}
				else if(value instanceof LanguageLevel)
				{
					final LanguageLevel languageLevel = (LanguageLevel) value;
					append(languageLevel.getShortText(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
					append(" ");
					append(languageLevel.getDescription(), SimpleTextAttributes.GRAY_ATTRIBUTES);
				}
				else if(value instanceof Module)
				{
					setIcon(AllIcons.Nodes.Module);
					append(((Module) value).getName(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);

					final JavaModuleExtension extension = ModuleUtilCore.getExtension((Module) value, JavaModuleExtension.class);
					if(extension != null)
					{
						final LanguageLevel languageLevel = extension.getLanguageLevel();
						append("(" + languageLevel.getShortText() + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
					}
				}
				else if(value instanceof String)
				{
					setIcon(AllIcons.Nodes.Module);
					append((String) value, SimpleTextAttributes.ERROR_BOLD_ATTRIBUTES);
				}
			}
		});

		sdkBoxBuilder.postConsumer((oldValue, newValue) -> {
			Object selectedItem = myLanguageLevelComboBox.getSelectedItem();
			if(selectedItem instanceof LanguageLevel && newValue != null && oldValue != null)
			{
				JavaSdkVersion oldSdkVersion = JavaSdk.getInstance().getVersion(oldValue);

				// if old sdk version exists and lang version is equal sdk lang version
				if(oldSdkVersion != null && oldSdkVersion.getMaxLanguageLevel() == selectedItem)
				{
					JavaSdkVersion newSdkVersion = JavaSdk.getInstance().getVersion(newValue);
					if(newSdkVersion != null)
					{
						myLanguageLevelComboBox.setSelectedItem(newSdkVersion.getMaxLanguageLevel());
					}
				}
			}
		});

		add(sdkBoxBuilder.build());

		add(LabeledComponent.left(myLanguageLevelComboBox, "Language Level"));

		processLanguageLevelItems();

		myModuleDirRadioButton = new JRadioButton("Module dir");
		mySourceDirRadioButton = new JRadioButton("Source dir");

		ButtonGroup buttonGroup = new ButtonGroup();
		buttonGroup.add(myModuleDirRadioButton);
		buttonGroup.add(mySourceDirRadioButton);

		final JRadioButton radioButton = extension.getSpecialDirLocation() == SpecialDirLocation.MODULE_DIR ? myModuleDirRadioButton : mySourceDirRadioButton;
		radioButton.setSelected(true);

		ChangeListener changeListener = e -> {
			if(mySourceDirRadioButton.isSelected())
			{
				extension.setSpecialDirLocation(SpecialDirLocation.SOURCE_DIR);
			}
			else if(myModuleDirRadioButton.isSelected())
			{
				extension.setSpecialDirLocation(SpecialDirLocation.MODULE_DIR);
			}
		};

		myModuleDirRadioButton.addChangeListener(changeListener);
		mySourceDirRadioButton.addChangeListener(changeListener);

		add(new TitledSeparator(JavaCoreBundle.message("paths.to.special.roots")));
		JPanel specialRootPanel = new JPanel(new VerticalFlowLayout());
		specialRootPanel.add(myModuleDirRadioButton);
		specialRootPanel.add(mySourceDirRadioButton);
		add(specialRootPanel);

		add(new TitledSeparator("Compiler Options"));
		ComboBox targetOptionsCombo = TargetOptionsComponent.createTargetOptionsCombo();
		targetOptionsCombo.setSelectedItem(StringUtil.notNullize(myExtension.getBytecodeVersion()));

		targetOptionsCombo.addItemListener(e -> {
			Object selectedItem = targetOptionsCombo.getSelectedItem();

			myExtension.setBytecodeVersion(StringUtil.nullize((String) selectedItem, true));
		});

		add(LabeledComponent.create(targetOptionsCombo, "Bytecode version"));

		CollectionListModel<String> argsModel = new CollectionListModel<>(extension.getCompilerArguments(), true);
		JList<String> compilerArgs = new JBList<>(argsModel);

		add(new JBLabel("Additional compiler arguments:"));
		JPanel panel = ToolbarDecorator.createDecorator(compilerArgs)
				.setToolbarPosition(ActionToolbarPosition.RIGHT)
				.setAddAction(button -> {
					String argument = Messages.showInputDialog(extension.getProject(), "Compiler argument", "Enter Compiler Argument", null);
					if(argument == null)
					{
						return;
					}

					argsModel.add(argument);
				})
				.setToolbarBorder(JBUI.Borders.empty()).createPanel();

		add(panel);
	}

	@RequiredReadAction
	private void processLanguageLevelItems()
	{
		myLanguageLevelComboBox.addItem(ObjectUtil.NULL);
		for(LanguageLevel languageLevel : LanguageLevel.values())
		{
			myLanguageLevelComboBox.addItem(languageLevel);
		}

		for(Module module : ModuleManager.getInstance(myExtension.getModule().getProject()).getModules())
		{
			// dont add self module
			if(module == myExtension.getModule())
			{
				continue;
			}

			final ModuleExtension extension = ModuleUtilCore.getExtension(module, myExtension.getId());
			if(extension instanceof ModuleExtensionWithSdk)
			{
				final ModuleExtensionWithSdk sdkExtension = (ModuleExtensionWithSdk) extension;
				// recursive depend
				if(sdkExtension.getInheritableSdk().getModule() == myExtension.getModule())
				{
					continue;
				}
				myLanguageLevelComboBox.addItem(sdkExtension.getModule());
			}
		}

		final MutableModuleInheritableNamedPointer<LanguageLevel> inheritableLanguageLevel = myExtension.getInheritableLanguageLevel();

		final String moduleName = inheritableLanguageLevel.getModuleName();
		if(moduleName != null)
		{
			final Module module = inheritableLanguageLevel.getModule();
			if(module != null)
			{
				myLanguageLevelComboBox.setSelectedItem(module);
			}
			else
			{
				myLanguageLevelComboBox.addItem(moduleName);
			}
		}
		else
		{
			myLanguageLevelComboBox.setSelectedItem(ObjectUtil.notNull(inheritableLanguageLevel.get(), ObjectUtil.NULL));
		}

		myLanguageLevelComboBox.addItemListener(e -> {
			if(e.getStateChange() == ItemEvent.SELECTED)
			{
				final Object selectedItem = myLanguageLevelComboBox.getSelectedItem();
				if(selectedItem instanceof Module)
				{
					inheritableLanguageLevel.set(((Module) selectedItem).getName(), null);
				}
				else if(selectedItem instanceof LanguageLevel)
				{
					inheritableLanguageLevel.set(null, ((LanguageLevel) selectedItem).getName());
				}
				else if(selectedItem == ObjectUtil.NULL)
				{
					inheritableLanguageLevel.set((String) null, (String) null);
				}
				else
				{
					inheritableLanguageLevel.set(selectedItem.toString(), null);
				}
			}
		});
	}
}
