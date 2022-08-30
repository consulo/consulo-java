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
package com.intellij.java.execution.impl.ui;

import com.intellij.execution.ExecutionBundle;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.PlatformIcons;
import consulo.awt.TargetAWT;
import consulo.ui.TextBoxWithExpandAction;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;

public class ConfigurationArgumentsHelpArea extends JPanel
{
	private TextBoxWithExpandAction myHelpArea;
	private JLabel myLabel;
	private final FixedSizeButton myCopyButton;

	public ConfigurationArgumentsHelpArea()
	{
		super(new BorderLayout());

		myLabel = new JBLabel(ExecutionBundle.message("environment.variables.helper.use.arguments.label"));
		add(myLabel, BorderLayout.NORTH);

		myHelpArea = TextBoxWithExpandAction.create(null, "Command line", s -> StringUtil.split(s, "\n"), list -> String.join("\n", list));
		myHelpArea.setEditable(false);

		add(TargetAWT.to(myHelpArea), BorderLayout.CENTER);

		myCopyButton = new FixedSizeButton();
		myCopyButton.setIcon(PlatformIcons.COPY_ICON);
		myCopyButton.addActionListener(e -> {
			final StringSelection contents = new StringSelection(myHelpArea.getValueOrError().trim());
			CopyPasteManager.getInstance().setContents(contents);
		});
		myCopyButton.setVisible(false);

		add(myCopyButton, BorderLayout.EAST);
	}

	public void setToolbarVisible()
	{
		myCopyButton.setVisible(true);
	}

	private static ActionPopupMenu createPopupMenu(DefaultActionGroup group)
	{
		return ActionManager.getInstance().createActionPopupMenu(ActionPlaces.UNKNOWN, group);
	}

	public void updateText(final String text)
	{
		myHelpArea.setValue(text);
	}

	public void setLabelText(final String text)
	{
		myLabel.setText(text);
	}

	public String getLabelText()
	{
		return myLabel.getText();
	}
}
