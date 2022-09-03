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
package com.intellij.java.debugger.impl.ui.breakpoints;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.JComponent;

import com.intellij.java.debugger.impl.breakpoints.properties.JavaFieldBreakpointProperties;
import consulo.application.CommonBundle;
import com.intellij.java.debugger.DebuggerBundle;
import com.intellij.java.debugger.impl.HelpID;
import consulo.application.AllIcons;
import consulo.application.ApplicationManager;
import consulo.document.Document;
import consulo.execution.debug.breakpoint.XLineBreakpoint;
import consulo.execution.debug.breakpoint.ui.XBreakpointCustomPropertiesPanel;
import consulo.project.Project;
import consulo.ui.ex.awt.Messages;
import consulo.util.lang.ref.Ref;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiDocumentManager;
import com.intellij.java.language.psi.PsiField;
import consulo.language.psi.PsiFile;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.execution.debug.XDebuggerManager;
import consulo.execution.debug.breakpoint.XBreakpoint;
import consulo.ui.image.Image;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 26, 2005
 */
public class JavaFieldBreakpointType extends JavaLineBreakpointTypeBase<JavaFieldBreakpointProperties> implements JavaBreakpointType
{
	@Nonnull
	public static JavaFieldBreakpointType getInstance()
	{
		return EXTENSION_POINT_NAME.findExtension(JavaFieldBreakpointType.class);
	}

	public JavaFieldBreakpointType()
	{
		super("java-field", DebuggerBundle.message("field.watchpoints.tab.title"));
	}

	@Override
	public boolean isAddBreakpointButtonVisible()
	{
		return true;
	}

	@Nonnull
	@Override
	public Image getEnabledIcon()
	{
		return AllIcons.Debugger.Db_field_breakpoint;
	}

	@Nonnull
	@Override
	public Image getDisabledIcon()
	{
		return AllIcons.Debugger.Db_disabled_field_breakpoint;
	}

	//@Override
	protected String getHelpID()
	{
		return HelpID.FIELD_WATCHPOINTS;
	}

	//@Override
	public String getDisplayName()
	{
		return DebuggerBundle.message("field.watchpoints.tab.title");
	}

	@Override
	public String getShortText(XLineBreakpoint<JavaFieldBreakpointProperties> breakpoint)
	{
		return getText(breakpoint);
	}

	public String getText(XLineBreakpoint<JavaFieldBreakpointProperties> breakpoint)
	{
		//if(!isValid()) {
		//  return DebuggerBundle.message("status.breakpoint.invalid");
		//}

		JavaFieldBreakpointProperties properties = breakpoint.getProperties();
		final String className = properties.myClassName;
		return className != null && !className.isEmpty() ? className + "." + properties.myFieldName : properties.myFieldName;
	}

	@Nullable
	@Override
	public XBreakpointCustomPropertiesPanel<XLineBreakpoint<JavaFieldBreakpointProperties>> createCustomPropertiesPanel()
	{
		return new FieldBreakpointPropertiesPanel();
	}

	@Nullable
	@Override
	public JavaFieldBreakpointProperties createProperties()
	{
		return new JavaFieldBreakpointProperties();
	}

	@Nullable
	@Override
	public JavaFieldBreakpointProperties createBreakpointProperties(@Nonnull VirtualFile file, int line)
	{
		return new JavaFieldBreakpointProperties();
	}

	@Nullable
	@Override
	public XLineBreakpoint<JavaFieldBreakpointProperties> addBreakpoint(final Project project, JComponent parentComponent)
	{
		final Ref<XLineBreakpoint> result = Ref.create(null);
		AddFieldBreakpointDialog dialog = new AddFieldBreakpointDialog(project)
		{
			protected boolean validateData()
			{
				final String className = getClassName();
				if(className.length() == 0)
				{
					Messages.showMessageDialog(project, DebuggerBundle.message("error.field.breakpoint.class.name.not.specified"),
							DebuggerBundle.message("add.field.breakpoint.dialog.title"), Messages.getErrorIcon());
					return false;
				}
				final String fieldName = getFieldName();
				if(fieldName.length() == 0)
				{
					Messages.showMessageDialog(project, DebuggerBundle.message("error.field.breakpoint.field.name.not.specified"),
							DebuggerBundle.message("add.field.breakpoint.dialog.title"), Messages.getErrorIcon());
					return false;
				}
				PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(className, GlobalSearchScope.allScope(project));
				if(psiClass != null)
				{
					final PsiFile psiFile = psiClass.getContainingFile();
					Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);
					if(document != null)
					{
						PsiField field = psiClass.findFieldByName(fieldName, true);
						if(field != null)
						{
							final int line = document.getLineNumber(field.getTextOffset());
							ApplicationManager.getApplication().runWriteAction(new Runnable()
							{
								@Override
								public void run()
								{
									XLineBreakpoint<JavaFieldBreakpointProperties> fieldBreakpoint = XDebuggerManager.getInstance(project)
											.getBreakpointManager().addLineBreakpoint(JavaFieldBreakpointType.this,
													psiFile.getVirtualFile().getUrl(), line, new JavaFieldBreakpointProperties(fieldName,
													className));
									result.set(fieldBreakpoint);
								}
							});
							return true;
						}
						else
						{
							Messages.showMessageDialog(project, DebuggerBundle.message("error.field.breakpoint.field.not.found", className,
									fieldName, fieldName), CommonBundle.getErrorTitle(), Messages.getErrorIcon());
						}
					}
				}
				else
				{
					Messages.showMessageDialog(project, DebuggerBundle.message("error.field.breakpoint.class.sources.not.found", className,
							fieldName, className), CommonBundle.getErrorTitle(), Messages.getErrorIcon());
				}
				return false;
			}
		};
		dialog.show();
		return result.get();
	}

	@Override
	public Breakpoint createJavaBreakpoint(Project project, XBreakpoint breakpoint)
	{
		return new FieldBreakpoint(project, breakpoint);
	}

	@Override
	public boolean canBeHitInOtherPlaces()
	{
		return true;
	}
}
