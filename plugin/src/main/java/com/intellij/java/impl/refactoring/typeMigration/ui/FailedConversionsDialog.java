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
package com.intellij.java.impl.refactoring.typeMigration.ui;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;

import jakarta.annotation.Nonnull;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.jetbrains.annotations.NonNls;
import consulo.project.Project;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.ui.ex.awt.ScrollPaneFactory;
import consulo.ui.ex.awt.JBUI;
import consulo.ui.ex.awt.UIUtil;

/**
 * created at Sep 12, 2001
 *
 * @author Jeka
 */
public class FailedConversionsDialog extends DialogWrapper
{
	private final String[] myConflictDescriptions;
	public static final int VIEW_USAGES_EXIT_CODE = NEXT_USER_EXIT_CODE;

	public FailedConversionsDialog(String[] conflictDescriptions, Project project)
	{
		super(project, true);
		myConflictDescriptions = conflictDescriptions;
		setTitle(RefactoringBundle.message("usages.detected.title"));
		setOKButtonText(RefactoringBundle.message("ignore.button"));
		getOKAction().putValue(Action.MNEMONIC_KEY, new Integer('I'));
		init();
	}

	@Override
	@Nonnull
	protected Action[] createActions()
	{
		return new Action[]{
				getOKAction(),
				new ViewUsagesAction(),
				new CancelAction()
		};
	}

	@Override
	protected JComponent createCenterPanel()
	{
		JPanel panel = new JPanel(new BorderLayout());
		final JEditorPane messagePane = new JEditorPane(UIUtil.HTML_MIME, "");
		messagePane.setEditable(false);
		JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(messagePane);
		scrollPane.setPreferredSize(JBUI.size(500, 400));
		panel.add(new JLabel(RefactoringBundle.message("the.following.problems.were.found")), BorderLayout.NORTH);
		panel.add(scrollPane, BorderLayout.CENTER);

		@NonNls StringBuilder buf = new StringBuilder();
		for(String description : myConflictDescriptions)
		{
			buf.append(description);
			buf.append("<br><br>");
		}
		messagePane.setText(buf.toString());
		return panel;
	}

	@Override
	protected String getDimensionServiceKey()
	{
		return "#com.intellij.refactoring.typeMigration.ui.FailedConversionsDialog";
	}

	private class CancelAction extends AbstractAction
	{
		public CancelAction()
		{
			super(RefactoringBundle.message("cancel.button"));
		}

		@Override
		public void actionPerformed(ActionEvent e)
		{
			doCancelAction();
		}
	}

	private class ViewUsagesAction extends AbstractAction
	{
		public ViewUsagesAction()
		{
			super(RefactoringBundle.message("view.usages"));
			putValue(Action.MNEMONIC_KEY, new Integer('V'));
			putValue(DialogWrapper.DEFAULT_ACTION, Boolean.TRUE);
		}

		@Override
		public void actionPerformed(ActionEvent e)
		{
			close(VIEW_USAGES_EXIT_CODE);
		}
	}
}
