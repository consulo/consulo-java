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
package com.intellij.java.impl.refactoring.migration;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.IOException;

import javax.annotation.Nullable;
import javax.swing.*;

import javax.annotation.Nonnull;
import consulo.find.FindSettings;
import consulo.ui.ex.awt.scopeChooser.ScopeChooserCombo;
import consulo.logging.Logger;
import consulo.application.HelpManager;
import consulo.project.Project;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.disposer.Disposer;
import consulo.content.scope.SearchScope;
import com.intellij.java.impl.refactoring.HelpID;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.ui.annotation.RequiredUIAccess;

public class MigrationDialog extends DialogWrapper
{
	private static final Logger LOG = Logger.getInstance(MigrationDialog.class);

	private JPanel myPanel;
	private JComboBox myMapComboBox;
	private JTextArea myDescriptionTextArea;
	private JButton myEditMapButton;
	private JButton myNewMapButton;
	private JButton myRemoveMapButton;
	private final Project myProject;
	private final MigrationMapSet myMigrationMapSet;
	private JLabel promptLabel;
	private JSeparator mySeparator;
	private JScrollPane myDescriptionScroll;
	private JPanel myScopePanel;

	private ScopeChooserCombo myScopeChooserCombo;

	public MigrationDialog(Project project, MigrationMapSet migrationMapSet)
	{
		super(project, true);
		myProject = project;
		myMigrationMapSet = migrationMapSet;

		myScopeChooserCombo = new ScopeChooserCombo(project, false, true, FindSettings.getInstance().getDefaultScopeName());
		Disposer.register(myDisposable, myScopeChooserCombo);
		myScopePanel.add(myScopeChooserCombo, BorderLayout.CENTER);

		setTitle(RefactoringBundle.message("migration.dialog.title"));
		setHorizontalStretch(1.2f);
		setOKButtonText(RefactoringBundle.message("migration.dialog.ok.button.text"));
		init();
	}

	@Override
	@Nonnull
	protected Action[] createActions()
	{
		return new Action[]{
				getOKAction(),
				getCancelAction(),
				getHelpAction()
		};
	}

	@RequiredUIAccess
	@Override
	public JComponent getPreferredFocusedComponent()
	{
		return myMapComboBox;
	}

	@Override
	protected JComponent createCenterPanel()
	{
		class MyTextArea extends JTextArea
		{
			public MyTextArea(String s, int a, int b)
			{
				super(s, a, b);
				setFocusable(false);
			}
		}

		initMapCombobox();
		myDescriptionTextArea = new MyTextArea("", 10, 40);
		myDescriptionScroll.getViewport().add(myDescriptionTextArea);
		myDescriptionScroll.setBorder(null);
		myDescriptionScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		myDescriptionScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
		myDescriptionTextArea.setEditable(false);
		myDescriptionTextArea.setFont(promptLabel.getFont());
		myDescriptionTextArea.setBackground(myPanel.getBackground());
		myDescriptionTextArea.setLineWrap(true);
		updateDescription();

		myMapComboBox.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent event)
			{
				updateDescription();
			}
		});

		myEditMapButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent event)
			{
				editMap();
			}
		});

		myRemoveMapButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent event)
			{
				removeMap();
			}
		});

		myNewMapButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent event)
			{
				addNewMap();
			}
		});

		myMapComboBox.registerKeyboardAction(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				if(myMapComboBox.isPopupVisible())
				{
					myMapComboBox.setPopupVisible(false);
				}
				else
				{
					clickDefaultButton();
				}
			}
		}, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

		return myPanel;
	}

	private void updateDescription()
	{
		if(myDescriptionTextArea == null)
		{
			return;
		}
		MigrationMap map = getMigrationMap();
		if(map == null)
		{
			myDescriptionTextArea.setText("");
			return;
		}
		myDescriptionTextArea.setText(map.getDescription());
	}

	private void editMap()
	{
		MigrationMap oldMap = getMigrationMap();
		if(oldMap == null)
		{
			return;
		}
		MigrationMap newMap = oldMap.cloneMap();
		if(editMap(newMap))
		{
			myMigrationMapSet.replaceMap(oldMap, newMap);
			initMapCombobox();
			myMapComboBox.setSelectedItem(newMap);
			try
			{
				myMigrationMapSet.saveMaps();
			}
			catch(IOException e)
			{
				LOG.error("Cannot save migration maps", e);
			}
		}
	}

	private boolean editMap(MigrationMap map)
	{
		if(map == null)
		{
			return false;
		}
		EditMigrationDialog dialog = new EditMigrationDialog(myProject, map);
		if(!dialog.showAndGet())
		{
			return false;
		}
		map.setName(dialog.getName());
		map.setDescription(dialog.getDescription());
		return true;
	}

	private void addNewMap()
	{
		MigrationMap migrationMap = new MigrationMap();
		if(editMap(migrationMap))
		{
			myMigrationMapSet.addMap(migrationMap);
			initMapCombobox();
			myMapComboBox.setSelectedItem(migrationMap);
			try
			{
				myMigrationMapSet.saveMaps();
			}
			catch(IOException e)
			{
				LOG.error("Cannot save migration maps", e);
			}
		}
	}

	private void removeMap()
	{
		MigrationMap map = getMigrationMap();
		if(map == null)
		{
			return;
		}
		myMigrationMapSet.removeMap(map);
		MigrationMap[] maps = myMigrationMapSet.getMaps();
		initMapCombobox();
		if(maps.length > 0)
		{
			myMapComboBox.setSelectedItem(maps[0]);
		}
		try
		{
			myMigrationMapSet.saveMaps();
		}
		catch(IOException e)
		{
			LOG.error("Cannot save migration maps", e);
		}
	}

	public MigrationMap getMigrationMap()
	{
		return (MigrationMap) myMapComboBox.getSelectedItem();
	}

	@Override
	protected void doOKAction()
	{
		FindSettings.getInstance().setDefaultScopeName(myScopeChooserCombo.getSelectedScopeName());

		super.doOKAction();
	}

	@Override
	protected void doHelpAction()
	{
		HelpManager.getInstance().invokeHelp(HelpID.MIGRATION);
	}

	@Nullable
	public SearchScope getScope()
	{
		return myScopeChooserCombo.getSelectedScope();
	}

	private void initMapCombobox()
	{
		if(myMapComboBox.getItemCount() > 0)
		{
			myMapComboBox.removeAllItems();
		}
		MigrationMap[] maps = myMigrationMapSet.getMaps();
		for(MigrationMap map : maps)
		{
			myMapComboBox.addItem(map);
		}
		updateDescription();
	}
}
