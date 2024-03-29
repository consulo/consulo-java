/*
 * Copyright 2004-2006 Alexey Efimov
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
package com.intellij.java.debugger.impl.ui.breakpoints;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;

import consulo.project.Project;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.Messages;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 10, 2006
 */
public class AddWildcardBreakpointDialog extends DialogWrapper
{
	private JPanel myPanel;
	private JTextField myClassPatternField;
	private JTextField myMethodNameField;

	public AddWildcardBreakpointDialog(Project project)
	{
		super(project, true);
		setTitle("Add Method Breakpoint");
		init();
	}

	protected void doOKAction()
	{
		if(getClassPattern().length() == 0)
		{
			Messages.showErrorDialog(myPanel, "Class pattern not specified");
			return;
		}
		if(getMethodName().length() == 0)
		{
			Messages.showErrorDialog(myPanel, "Method name not specified");
			return;
		}
		super.doOKAction();
	}

	public JComponent getPreferredFocusedComponent()
	{
		return myClassPatternField;
	}

	public String getClassPattern()
	{
		return myClassPatternField.getText().trim();
	}

	public String getMethodName()
	{
		return myMethodNameField.getText().trim();
	}

	protected JComponent createCenterPanel()
	{
		return myPanel;
	}
}
