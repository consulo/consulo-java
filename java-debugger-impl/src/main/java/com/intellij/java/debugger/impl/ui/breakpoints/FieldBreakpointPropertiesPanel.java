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

/**
 * class FieldBreakpointPropertiesPanel
 * @author Jeka
 */
package com.intellij.java.debugger.impl.ui.breakpoints;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.annotation.Nonnull;
import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;

import com.intellij.java.debugger.impl.breakpoints.properties.JavaFieldBreakpointProperties;
import com.intellij.java.debugger.DebuggerBundle;
import consulo.execution.debug.breakpoint.XLineBreakpoint;
import consulo.execution.debug.breakpoint.ui.XBreakpointCustomPropertiesPanel;
import consulo.ui.ex.awt.IdeBorderFactory;
import consulo.ui.ex.awt.util.DialogUtil;

public class FieldBreakpointPropertiesPanel extends XBreakpointCustomPropertiesPanel<XLineBreakpoint<JavaFieldBreakpointProperties>>
{
	private JCheckBox myWatchAccessCheckBox;
	private JCheckBox myWatchModificationCheckBox;

	//public FieldBreakpointPropertiesPanel(final Project project, boolean compact) {
	//  super(project, FieldBreakpoint.CATEGORY, compact);
	//}


	@Nonnull
	@Override
	public JComponent getComponent()
	{
		JPanel _panel;
		JPanel _panel0;
		myWatchAccessCheckBox = new JCheckBox(DebuggerBundle.message("label.filed.breakpoint.properties.panel.field.access"));
		myWatchModificationCheckBox = new JCheckBox(DebuggerBundle.message("label.filed.breakpoint.properties.panel.field.modification"));
		DialogUtil.registerMnemonic(myWatchAccessCheckBox);
		DialogUtil.registerMnemonic(myWatchModificationCheckBox);


		Box watchBox = Box.createVerticalBox();
		_panel = new JPanel(new BorderLayout());
		_panel.add(myWatchAccessCheckBox, BorderLayout.NORTH);
		watchBox.add(_panel);
		_panel = new JPanel(new BorderLayout());
		_panel.add(myWatchModificationCheckBox, BorderLayout.NORTH);
		watchBox.add(_panel);

		_panel = new JPanel(new BorderLayout());
		_panel0 = new JPanel(new BorderLayout());
		_panel0.add(watchBox, BorderLayout.CENTER);
		_panel0.add(Box.createHorizontalStrut(3), BorderLayout.WEST);
		_panel0.add(Box.createHorizontalStrut(3), BorderLayout.EAST);
		_panel.add(_panel0, BorderLayout.NORTH);
		_panel.setBorder(IdeBorderFactory.createTitledBorder(DebuggerBundle.message("label.group.watch.events"), true));

		ActionListener listener = new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				JCheckBox toCheck = null;
				if(!myWatchAccessCheckBox.isSelected() && !myWatchModificationCheckBox.isSelected())
				{
					Object source = e.getSource();
					if(myWatchAccessCheckBox.equals(source))
					{
						toCheck = myWatchModificationCheckBox;
					}
					else if(myWatchModificationCheckBox.equals(source))
					{
						toCheck = myWatchAccessCheckBox;
					}
					if(toCheck != null)
					{
						toCheck.setSelected(true);
					}
				}
			}
		};
		myWatchAccessCheckBox.addActionListener(listener);
		myWatchModificationCheckBox.addActionListener(listener);

		return _panel;
	}

	@Override
	public void loadFrom(@Nonnull XLineBreakpoint<JavaFieldBreakpointProperties> breakpoint)
	{
		myWatchAccessCheckBox.setSelected(breakpoint.getProperties().WATCH_ACCESS);
		myWatchModificationCheckBox.setSelected(breakpoint.getProperties().WATCH_MODIFICATION);
	}

	@Override
	public void saveTo(@Nonnull XLineBreakpoint<JavaFieldBreakpointProperties> breakpoint)
	{
		breakpoint.getProperties().WATCH_ACCESS = myWatchAccessCheckBox.isSelected();
		breakpoint.getProperties().WATCH_MODIFICATION = myWatchModificationCheckBox.isSelected();
	}
}