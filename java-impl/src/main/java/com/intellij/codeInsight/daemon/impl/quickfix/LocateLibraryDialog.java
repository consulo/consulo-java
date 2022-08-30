/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.quickfix;

import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.event.DocumentEvent;

import org.jetbrains.annotations.NonNls;

import javax.annotation.Nullable;
import com.intellij.CommonBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBRadioButton;
import consulo.disposer.Disposer;
import consulo.java.analysis.impl.JavaQuickFixBundle;

public class LocateLibraryDialog extends DialogWrapper
{
	private final List<String> myDefaultLibraryPaths;
	private JPanel myContentPane;
	private TextFieldWithBrowseButton myCopyToDir;
	private JBRadioButton myUseBundledRadioButton;
	private JBRadioButton myCopyLibraryFilesRadioButton;

	private final Project myProject;
	private List<String> myResultingLibraryPaths;

	public LocateLibraryDialog(@Nonnull Module module, @Nonnull List<String> defaultLibraryPaths, @Nonnull @NonNls final String presentableName)
	{
		super(module.getProject(), true);
		myDefaultLibraryPaths = defaultLibraryPaths;
		setTitle(JavaQuickFixBundle.message("add.library.title.dialog", presentableName));

		myProject = module.getProject();
		myUseBundledRadioButton.setText(JavaQuickFixBundle.message("add.library.use.bundled.library.radio.button", presentableName, ApplicationNamesInfo.getInstance().getFullProductName()));
		myCopyLibraryFilesRadioButton.setText(JavaQuickFixBundle.message("add.library.copy.files.to.radio.button", presentableName));
		myCopyToDir.setText(new File(new File(module.getModuleDirPath()), "lib").getAbsolutePath());
		myCopyToDir.addBrowseFolderListener(JavaQuickFixBundle.message("add.library.title.choose.folder"), JavaQuickFixBundle.message("add.library.description.choose.folder"), myProject, 
				FileChooserDescriptorFactory.createSingleFolderDescriptor());

		final ItemListener listener = new ItemListener()
		{
			@Override
			public void itemStateChanged(ItemEvent e)
			{
				updateButtons();
			}
		};

		myUseBundledRadioButton.addItemListener(listener);
		myCopyLibraryFilesRadioButton.addItemListener(listener);

		myCopyToDir.getTextField().getDocument().addDocumentListener(new DocumentAdapter()
		{
			@Override
			protected void textChanged(DocumentEvent e)
			{
				updateButtons();
			}
		});

		updateButtons();
		init();
	}

	@Nonnull
	public List<String> showAndGetResult()
	{
		if(ApplicationManager.getApplication().isHeadlessEnvironment())
		{
			Disposer.dispose(myDisposable);
			return myDefaultLibraryPaths;
		}
		return showAndGet() ? getResultingLibraryPaths() : Collections.emptyList();
	}

	public List<String> getResultingLibraryPaths()
	{
		return myResultingLibraryPaths;
	}

	private void updateButtons()
	{
		final boolean copyFiles = myCopyLibraryFilesRadioButton.isSelected();
		myCopyToDir.setEnabled(copyFiles);
		setOKActionEnabled(!copyFiles || !myCopyToDir.getText().isEmpty());
	}

	@Override
	@NonNls
	protected String getDimensionServiceKey()
	{
		return "#com.intellij.codeInsight.daemon.impl.quickfix.LocateLibraryDialog";
	}

	@Nullable
	@Override
	public JComponent getPreferredFocusedComponent()
	{
		return myUseBundledRadioButton;
	}

	@Override
	@Nullable
	protected JComponent createCenterPanel()
	{
		return myContentPane;
	}

	@Override
	protected void doOKAction()
	{
		if(getOKAction().isEnabled())
		{
			myResultingLibraryPaths = computeResultingPaths();
			if(!myResultingLibraryPaths.isEmpty())
			{
				close(OK_EXIT_CODE);
			}
		}
	}

	private List<String> computeResultingPaths()
	{
		if(myUseBundledRadioButton.isSelected())
		{
			return myDefaultLibraryPaths;
		}

		final String dstDir = myCopyToDir.getText();
		if(dstDir.isEmpty())
		{
			return Collections.emptyList();
		}

		List<String> result = new ArrayList<>();
		for(String path : myDefaultLibraryPaths)
		{
			final File srcFile = new File(path);
			if(!srcFile.exists())
			{
				Messages.showErrorDialog(myProject, JavaQuickFixBundle.message("add.library.error.not.found", srcFile.getPath()), CommonBundle.getErrorTitle());
				return Collections.emptyList();
			}
			File dstFile = new File(dstDir, srcFile.getName());
			try
			{
				FileUtil.copy(srcFile, dstFile);
			}
			catch(IOException e)
			{
				Messages.showErrorDialog(myProject, JavaQuickFixBundle.message("add.library.error.cannot.copy", srcFile.getPath(), dstFile.getPath(), e.getMessage()), CommonBundle.getErrorTitle());
				return Collections.emptyList();
			}
			result.add(FileUtil.toSystemIndependentName(dstFile.getAbsolutePath()));
		}
		return result;
	}
}
