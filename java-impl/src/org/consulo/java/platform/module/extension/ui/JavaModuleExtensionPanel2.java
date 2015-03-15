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

package org.consulo.java.platform.module.extension.ui;

import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import javax.swing.ButtonGroup;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.consulo.java.module.extension.JavaModuleExtension;
import org.consulo.java.module.extension.JavaMutableModuleExtension;
import org.consulo.java.platform.module.extension.SpecialDirLocation;
import org.consulo.module.extension.ModuleExtension;
import org.consulo.module.extension.ModuleExtensionWithSdk;
import org.consulo.module.extension.MutableModuleInheritableNamedPointer;
import org.consulo.module.extension.ui.ModuleExtensionSdkBoxBuilder;
import org.mustbe.consulo.RequiredDispatchThread;
import org.mustbe.consulo.RequiredReadAction;
import com.intellij.core.JavaCoreBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.ui.ColoredListCellRendererWrapper;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Consumer;

/**
 * @author VISTALL
 * @since 15.03.2015
 */
public class JavaModuleExtensionPanel2 extends JPanel
{
	private final JavaMutableModuleExtension<?> myMutableModuleExtension;

	private ComboBox myLanguageLevelComboBox;
	private JRadioButton myModuleDirRadioButton;
	private JRadioButton mySourceDirRadioButton;

	@RequiredDispatchThread
	public JavaModuleExtensionPanel2(final JavaMutableModuleExtension<?> mutableModuleExtension, Runnable classpathStateUpdater)
	{
		super(new VerticalFlowLayout());
		myMutableModuleExtension = mutableModuleExtension;

		ModuleExtensionSdkBoxBuilder sdkBoxBuilder = ModuleExtensionSdkBoxBuilder.createAndDefine(mutableModuleExtension, classpathStateUpdater);

		myLanguageLevelComboBox = new ComboBox();
		myLanguageLevelComboBox.setRenderer(new ColoredListCellRendererWrapper<Object>()
		{
			@Override
			protected void doCustomize(JList list, Object value, int index, boolean selected, boolean hasFocus)
			{
				if(value instanceof LanguageLevel)
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


		add(sdkBoxBuilder.build());

		add(LabeledComponent.left(myLanguageLevelComboBox, "Language Level"));

		processLanguageLevelItems();

		myModuleDirRadioButton = new JRadioButton("Module dir");
		mySourceDirRadioButton = new JRadioButton("Source dir");

		ButtonGroup buttonGroup = new ButtonGroup();
		buttonGroup.add(myModuleDirRadioButton);
		buttonGroup.add(mySourceDirRadioButton);

		final JRadioButton radioButton = mutableModuleExtension.getSpecialDirLocation() == SpecialDirLocation.MODULE_DIR ? myModuleDirRadioButton :
				mySourceDirRadioButton;
		radioButton.setSelected(true);

		ChangeListener changeListener = new ChangeListener()
		{
			@Override
			public void stateChanged(ChangeEvent e)
			{
				if(mySourceDirRadioButton.isSelected())
				{
					mutableModuleExtension.setSpecialDirLocation(SpecialDirLocation.SOURCE_DIR);
				}
				else if(myModuleDirRadioButton.isSelected())
				{
					mutableModuleExtension.setSpecialDirLocation(SpecialDirLocation.MODULE_DIR);
				}
			}
		};

		myModuleDirRadioButton.addChangeListener(changeListener);
		mySourceDirRadioButton.addChangeListener(changeListener);


		JPanel specialRootPanel = new JPanel(new VerticalFlowLayout());
		specialRootPanel.setBorder(IdeBorderFactory.createTitledBorder(JavaCoreBundle.message("paths.to.special.roots")));
		specialRootPanel.add(myModuleDirRadioButton);
		specialRootPanel.add(mySourceDirRadioButton);

		add(specialRootPanel);
	}

	@RequiredReadAction
	private void processLanguageLevelItems()
	{
		for(LanguageLevel languageLevel : LanguageLevel.values())
		{
			myLanguageLevelComboBox.addItem(languageLevel);
		}

		for(Module module : ModuleManager.getInstance(myMutableModuleExtension.getModule().getProject()).getModules())
		{
			// dont add self module
			if(module == myMutableModuleExtension.getModule())
			{
				continue;
			}

			final ModuleExtension extension = ModuleUtilCore.getExtension(module, myMutableModuleExtension.getId());
			if(extension instanceof ModuleExtensionWithSdk)
			{
				final ModuleExtensionWithSdk sdkExtension = (ModuleExtensionWithSdk) extension;
				// recursive depend
				if(sdkExtension.getInheritableSdk().getModule() == myMutableModuleExtension.getModule())
				{
					continue;
				}
				myLanguageLevelComboBox.addItem(sdkExtension.getModule());
			}
		}

		final MutableModuleInheritableNamedPointer<LanguageLevel> inheritableLanguageLevel = myMutableModuleExtension.getInheritableLanguageLevel();

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
			myLanguageLevelComboBox.setSelectedItem(inheritableLanguageLevel.get());
		}

		myLanguageLevelComboBox.addItemListener(new ItemListener()
		{
			@Override
			public void itemStateChanged(ItemEvent e)
			{
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
					else
					{
						inheritableLanguageLevel.set(selectedItem.toString(), null);
					}
				}
			}
		});
	}
}
