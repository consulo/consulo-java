/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.packaging.impl.ui.properties;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;

import javax.annotation.Nonnull;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.impl.ManifestFileUtil;
import com.intellij.packaging.impl.elements.CompositeElementWithManifest;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.ManifestFileConfiguration;
import com.intellij.packaging.ui.PackagingElementPropertiesPanel;
import com.intellij.ui.DocumentAdapter;

/**
 * @author nik
 */
public abstract class ElementWithManifestPropertiesPanel<E extends CompositeElementWithManifest<?>> extends PackagingElementPropertiesPanel
{
	private final E myElement;
	private final ArtifactEditorContext myContext;
	private JPanel myMainPanel;
	private TextFieldWithBrowseButton myMainClassField;
	private TextFieldWithBrowseButton myClasspathField;
	private JLabel myTitleLabel;
	private JButton myCreateManifestButton;
	private JButton myUseExistingManifestButton;
	private JPanel myPropertiesPanel;
	private JTextField myManifestPathField;
	private JLabel myManifestNotFoundLabel;
	private ManifestFileConfiguration myManifestFileConfiguration;

	public ElementWithManifestPropertiesPanel(E element, final ArtifactEditorContext context)
	{
		myElement = element;
		myContext = context;

		ManifestFileUtil.setupMainClassField(context.getProject(), myMainClassField);

		myClasspathField.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				Messages.showTextAreaDialog(myClasspathField.getTextField(), "Edit Classpath", "classpath-attribute-editor");
			}
		});
		myClasspathField.getTextField().getDocument().addDocumentListener(new DocumentAdapter()
		{
			@Override
			protected void textChanged(DocumentEvent e)
			{
				myContext.queueValidation();
			}
		});
		myUseExistingManifestButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				chooseManifest();
			}
		});
		myCreateManifestButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				createManifest();
			}
		});
	}

	private void createManifest()
	{
		final VirtualFile file = ManifestFileUtil.showDialogAndCreateManifest(myContext, myElement);
		if(file == null)
		{
			return;
		}

		ManifestFileUtil.addManifestFileToLayout(file.getPath(), myContext, myElement);
		updateManifest();
		myContext.getThisArtifactEditor().updateLayoutTree();
	}

	private void chooseManifest()
	{
		final FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, false)
		{
			@Override
			public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles)
			{
				return super.isFileVisible(file, showHiddenFiles) && (file.isDirectory() || file.getName().equalsIgnoreCase(ManifestFileUtil
						.MANIFEST_FILE_NAME));
			}
		};
		descriptor.setTitle("Specify Path to MANIFEST.MF file");
		final VirtualFile file = FileChooser.chooseFile(descriptor, myContext.getProject(), null);
		if(file == null)
		{
			return;
		}

		ManifestFileUtil.addManifestFileToLayout(file.getPath(), myContext, myElement);
		updateManifest();
		myContext.getThisArtifactEditor().updateLayoutTree();
	}

	private void updateManifest()
	{
		/*myManifestFileConfiguration = myContext.getManifestFile(myElement, myContext.getArtifactType());
		final String card;
		if(myManifestFileConfiguration != null)
		{
			card = "properties";
			myManifestPathField.setText(FileUtil.toSystemDependentName(myManifestFileConfiguration.getManifestFilePath()));
			myMainClassField.setText(StringUtil.notNullize(myManifestFileConfiguration.getMainClass()));
			myMainClassField.setEnabled(myManifestFileConfiguration.isWritable());
			myClasspathField.setText(StringUtil.join(myManifestFileConfiguration.getClasspath(), " "));
			myClasspathField.setEnabled(myManifestFileConfiguration.isWritable());
		}
		else
		{
			card = "buttons";
			myManifestPathField.setText("");
		}
		((CardLayout) myPropertiesPanel.getLayout()).show(myPropertiesPanel, card);  */
	}

	@Override
	public void reset()
	{
		myTitleLabel.setText("'" + myElement.getName() + "' manifest properties:");
		myManifestNotFoundLabel.setText("META-INF/MANIFEST.MF file not found in '" + myElement.getName() + "'");
		updateManifest();
	}

	@Override
	public boolean isModified()
	{
		return myManifestFileConfiguration != null && (!myManifestFileConfiguration.getClasspath().equals(getConfiguredClasspath()) || !Comparing
				.equal(myManifestFileConfiguration.getMainClass(), getConfiguredMainClass()) || !Comparing.equal(myManifestFileConfiguration
				.getManifestFilePath(), getConfiguredManifestPath()));
	}

	@javax.annotation.Nullable
	private String getConfiguredManifestPath()
	{
		final String path = myManifestPathField.getText();
		return path.length() != 0 ? FileUtil.toSystemIndependentName(path) : null;
	}

	@Override
	public void apply()
	{
		if(myManifestFileConfiguration != null)
		{
			myManifestFileConfiguration.setMainClass(getConfiguredMainClass());
			myManifestFileConfiguration.setClasspath(getConfiguredClasspath());
			myManifestFileConfiguration.setManifestFilePath(getConfiguredManifestPath());
		}
	}

	private List<String> getConfiguredClasspath()
	{
		return StringUtil.split(myClasspathField.getText(), " ");
	}

	@Override
	@Nonnull
	public JComponent createComponent()
	{
		return myMainPanel;
	}

	@javax.annotation.Nullable
	private String getConfiguredMainClass()
	{
		final String className = myMainClassField.getText();
		return className.length() != 0 ? className : null;
	}

}
