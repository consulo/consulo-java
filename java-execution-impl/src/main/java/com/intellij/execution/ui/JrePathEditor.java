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
package com.intellij.execution.ui;

import java.awt.event.ActionListener;
import java.util.Objects;

import javax.swing.JComponent;

import javax.annotation.Nullable;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.ui.components.panels.Wrapper;
import consulo.annotations.DeprecationInfo;
import consulo.java.execution.JavaExecutionBundle;
import consulo.roots.ui.configuration.SdkComboBox;

/**
 * @author nik
 */
public class JrePathEditor extends Wrapper implements PanelWithAnchor
{
	private SdkComboBox mySdkComboBox;

	private LabeledComponent<SdkComboBox> myLabeledComponent;

	public JrePathEditor(DefaultJreSelector defaultJreSelector)
	{
		this();
	}

	/**
	 * This constructor can be used in UI forms
	 */
	public JrePathEditor()
	{
		ProjectSdksModel model = new ProjectSdksModel();
		model.reset();

		mySdkComboBox = new SdkComboBox(model, id -> Objects.equals(JavaSdk.getInstance(), id), null, "Auto Select", AllIcons.Actions.FindPlain);

		myLabeledComponent = LabeledComponent.create(mySdkComboBox, JavaExecutionBundle.message("run.configuration.jre.label"));

		setContent(myLabeledComponent);
	}

	@Nullable
	public String getJrePathOrName()
	{
		return mySdkComboBox.getSelectedSdkName();
	}

	public boolean isAlternativeJreSelected()
	{
		SdkComboBox.SdkComboBoxItem selectedItem = mySdkComboBox.getSelectedItem();
		return !(selectedItem instanceof SdkComboBox.NullSdkComboBoxItem);
	}

	@Deprecated
	public void setDefaultJreSelector(DefaultJreSelector defaultJreSelector)
	{
	}

	@Deprecated
	@DeprecationInfo("Use #setByName()")
	public void setPathOrName(@javax.annotation.Nullable String pathOrName, boolean useAlternativeJre)
	{
		setByName(pathOrName);
	}

	public void setByName(@Nullable String name)
	{
		mySdkComboBox.setSelectedSdk(name);
	}

	@Override
	public JComponent getAnchor()
	{
		return myLabeledComponent.getAnchor();
	}

	@Override
	public void setAnchor(JComponent anchor)
	{
		myLabeledComponent.setAnchor(anchor);
	}

	public void addActionListener(ActionListener listener)
	{
		mySdkComboBox.addActionListener(listener);
	}
}

