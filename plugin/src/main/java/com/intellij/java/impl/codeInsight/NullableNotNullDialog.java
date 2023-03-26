// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.codeInsight;

import consulo.language.editor.inspection.InspectionsBundle;
import consulo.dataContext.DataManager;
import com.intellij.java.language.codeInsight.NullableNotNullManager;
import consulo.language.editor.CommonDataKeys;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.project.ProjectManager;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.Splitter;
import consulo.ui.ex.awt.JBUI;
import consulo.ui.ex.awtUnsafe.TargetAWT;

import javax.annotation.Nonnull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.HashSet;

public class NullableNotNullDialog extends DialogWrapper
{
	private final Project myProject;
	private final AnnotationsPanel myNullablePanel;
	private final AnnotationsPanel myNotNullPanel;
	private final boolean myShowInstrumentationOptions;

	public NullableNotNullDialog(@Nonnull Project project)
	{
		this(project, false);
	}

	private NullableNotNullDialog(@Nonnull Project project, boolean showInstrumentationOptions)
	{
		super(project, true);
		myProject = project;
		myShowInstrumentationOptions = showInstrumentationOptions;

		NullableNotNullManager manager = NullableNotNullManager.getInstance(myProject);
		myNullablePanel = new AnnotationsPanel(project,
				"Nullable",
				manager.getDefaultNullable(),
				manager.getNullables(), NullableNotNullManager.DEFAULT_NULLABLES,
				Collections.emptySet(), false, true);
		myNotNullPanel = new AnnotationsPanel(project,
				"NotNull",
				manager.getDefaultNotNull(),
				manager.getNotNulls(), NullableNotNullManager.DEFAULT_NOT_NULLS,
				new HashSet<>(manager.getInstrumentedNotNulls()), showInstrumentationOptions, true);

		init();
		setTitle("Nullable/NotNull Configuration");
	}

	public static JButton createConfigureAnnotationsJButton(Component context)
	{
		final JButton button = new JButton(InspectionsBundle.message("configure.annotations.option"));
		button.addActionListener(createActionListener(context));
		return button;
	}

	@Nonnull
	public static consulo.ui.Button createConfigureAnnotationsButton()
	{
		consulo.ui.Button button = consulo.ui.Button.create(LocalizeValue.localizeTODO("Configure annotations"));
		button.addClickListener(clickEvent -> showDialog(TargetAWT.to(clickEvent.getComponent()), false));
		return button;
	}

	/**
	 * Creates an action listener showing this dialog.
	 *
	 * @param context component where project context will be retrieved from
	 * @return the action listener
	 */
	public static ActionListener createActionListener(Component context)
	{
		return new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				showDialog(context, false);
			}
		};
	}

	public static void showDialogWithInstrumentationOptions(@Nonnull Component context)
	{
		showDialog(context, true);
	}

	private static void showDialog(Component context, boolean showInstrumentationOptions)
	{
		Project project = DataManager.getInstance().getDataContext(context).getData(CommonDataKeys.PROJECT);
		if(project == null)
		{
			project = ProjectManager.getInstance().getDefaultProject();
		}
		NullableNotNullDialog dialog = new NullableNotNullDialog(project, showInstrumentationOptions);
		dialog.show();
	}

	@Override
	protected JComponent createCenterPanel()
	{
		final Splitter splitter = new Splitter(true);
		splitter.setFirstComponent(myNullablePanel.getComponent());
		splitter.setSecondComponent(myNotNullPanel.getComponent());
		splitter.setHonorComponentsMinimumSize(true);
		splitter.setPreferredSize(JBUI.size(300, 400));
		return splitter;
	}

	@Override
	protected void doOKAction()
	{
		final NullableNotNullManager manager = NullableNotNullManager.getInstance(myProject);

		manager.setNotNulls(myNotNullPanel.getAnnotations());
		manager.setDefaultNotNull(myNotNullPanel.getDefaultAnnotation());

		manager.setNullables(myNullablePanel.getAnnotations());
		manager.setDefaultNullable(myNullablePanel.getDefaultAnnotation());

		if(myShowInstrumentationOptions)
		{
			manager.setInstrumentedNotNulls(myNotNullPanel.getCheckedAnnotations());
		}

		super.doOKAction();
	}
}
