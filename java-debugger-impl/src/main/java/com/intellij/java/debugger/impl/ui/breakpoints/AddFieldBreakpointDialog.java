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

/*
 * @author: Eugene Zhuravlev
 * Date: Sep 11, 2002
 * Time: 5:23:47 PM
 */
package com.intellij.java.debugger.impl.ui.breakpoints;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.event.DocumentEvent;

import com.intellij.codeInsight.generation.PsiElementClassMember;
import com.intellij.java.debugger.DebuggerBundle;
import com.intellij.ide.util.MemberChooser;
import com.intellij.java.language.util.TreeClassChooser;
import com.intellij.java.language.util.TreeClassChooserFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.java.language.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.java.language.psi.PsiSubstitutor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.containers.ContainerUtil;

abstract class AddFieldBreakpointDialog extends DialogWrapper
{
	private static final class FieldMember extends PsiElementClassMember<PsiField>
	{
		protected FieldMember(PsiField field)
		{
			super(field, PsiFormatUtil.formatVariable(field, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.TYPE_AFTER, PsiSubstitutor.EMPTY));
		}
	}

	private final Project myProject;
	private JPanel myPanel;
	private TextFieldWithBrowseButton myFieldChooser;
	private TextFieldWithBrowseButton myClassChooser;

	public AddFieldBreakpointDialog(Project project)
	{
		super(project, true);
		myProject = project;
		setTitle(DebuggerBundle.message("add.field.breakpoint.dialog.title"));
		init();
	}

	protected JComponent createCenterPanel()
	{
		myClassChooser.getTextField().getDocument().addDocumentListener(new DocumentAdapter()
		{
			public void textChanged(DocumentEvent event)
			{
				updateUI();
			}
		});

		myClassChooser.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				PsiClass currentClass = getSelectedClass();
				TreeClassChooser chooser = TreeClassChooserFactory.getInstance(myProject).createAllProjectScopeChooser(DebuggerBundle.message("add.field.breakpoint.dialog.classchooser.title"));
				if(currentClass != null)
				{
					PsiFile containingFile = currentClass.getContainingFile();
					if(containingFile != null)
					{
						PsiDirectory containingDirectory = containingFile.getContainingDirectory();
						if(containingDirectory != null)
						{
							chooser.selectDirectory(containingDirectory);
						}
					}
				}
				chooser.showDialog();
				PsiClass selectedClass = chooser.getSelected();
				if(selectedClass != null)
				{
					myClassChooser.setText(selectedClass.getQualifiedName());
				}
			}
		});

		myFieldChooser.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				PsiClass selectedClass = getSelectedClass();
				if(selectedClass != null)
				{
					PsiField[] fields = selectedClass.getFields();
					MemberChooser<FieldMember> chooser = new MemberChooser<FieldMember>(ContainerUtil.map2Array(fields, FieldMember.class, FieldMember::new), false, false, myProject);
					chooser.setTitle(DebuggerBundle.message("add.field.breakpoint.dialog.field.chooser.title", fields.length));
					chooser.setCopyJavadocVisible(false);
					chooser.show();
					List<FieldMember> selectedElements = chooser.getSelectedElements();
					if(selectedElements != null && selectedElements.size() == 1)
					{
						PsiField field = selectedElements.get(0).getElement();
						myFieldChooser.setText(field.getName());
					}
				}
			}
		});
		myFieldChooser.setEnabled(false);
		return myPanel;
	}

	private void updateUI()
	{
		PsiClass selectedClass = getSelectedClass();
		myFieldChooser.setEnabled(selectedClass != null);
	}

	private PsiClass getSelectedClass()
	{
		final PsiManager psiManager = PsiManager.getInstance(myProject);
		String classQName = myClassChooser.getText();
		if("".equals(classQName))
		{
			return null;
		}
		return JavaPsiFacade.getInstance(psiManager.getProject()).findClass(classQName, GlobalSearchScope.allScope(myProject));
	}

	public JComponent getPreferredFocusedComponent()
	{
		return myClassChooser.getTextField();
	}

	public String getClassName()
	{
		return myClassChooser.getText();
	}

	protected String getDimensionServiceKey()
	{
		return "#com.intellij.debugger.ui.breakpoints.BreakpointsConfigurationDialogFactory.BreakpointsConfigurationDialog.AddFieldBreakpointDialog";
	}

	public String getFieldName()
	{
		return myFieldChooser.getText();
	}

	protected abstract boolean validateData();

	protected void doOKAction()
	{
		if(validateData())
		{
			super.doOKAction();
		}
	}
}
