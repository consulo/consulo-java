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
package com.intellij.debugger.ui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

import javax.swing.JList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.JavaStackFrame;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.ide.util.PsiElementModuleRenderer;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import consulo.util.dataholder.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import com.intellij.ui.components.JBList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import consulo.internal.com.sun.jdi.Location;

/**
 * @author egor
 */
public class AlternativeSourceNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel>
{
	private static final Key<EditorNotificationPanel> KEY = Key.create("AlternativeSource");
	private static final Key<Boolean> FILE_PROCESSED_KEY = Key.create("AlternativeSourceCheckDone");
	private final Project myProject;

	public AlternativeSourceNotificationProvider(Project project)
	{
		myProject = project;
	}

	@Nonnull
	@Override
	public Key<EditorNotificationPanel> getKey()
	{
		return KEY;
	}

	@Nullable
	@Override
	public EditorNotificationPanel createNotificationPanel(@Nonnull VirtualFile file, @Nonnull FileEditor fileEditor)
	{
		if(!DebuggerSettings.getInstance().SHOW_ALTERNATIVE_SOURCE)
		{
			return null;
		}
		XDebugSession session = XDebuggerManager.getInstance(myProject).getCurrentSession();
		if(session == null)
		{
			FILE_PROCESSED_KEY.set(file, null);
			return null;
		}

		XSourcePosition position = session.getCurrentPosition();
		if(position == null || !file.equals(position.getFile()))
		{
			FILE_PROCESSED_KEY.set(file, null);
			return null;
		}

		final PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
		if(psiFile == null)
		{
			return null;
		}

		if(!(psiFile instanceof PsiJavaFile))
		{
			return null;
		}

		PsiClass[] classes = ((PsiJavaFile) psiFile).getClasses();
		if(classes.length == 0)
		{
			return null;
		}

		PsiClass baseClass = classes[0];
		String name = baseClass.getQualifiedName();

		if(name == null)
		{
			return null;
		}

		if(DumbService.getInstance(myProject).isDumb())
		{
			return null;
		}

		ArrayList<PsiClass> alts = ContainerUtil.newArrayList(JavaPsiFacade.getInstance(myProject).findClasses(name, GlobalSearchScope.allScope(myProject)));
		ContainerUtil.removeDuplicates(alts);

		FILE_PROCESSED_KEY.set(file, true);

		if(alts.size() > 1)
		{
			for(PsiClass cls : alts)
			{
				if(cls.equals(baseClass) || cls.getNavigationElement().equals(baseClass))
				{
					alts.remove(cls);
					break;
				}
			}
			alts.add(0, baseClass);

			ComboBoxClassElement[] elems = ContainerUtil.map2Array(alts, ComboBoxClassElement.class, psiClass -> new ComboBoxClassElement((PsiClass) psiClass.getNavigationElement()));

			String locationDeclName = null;
			XStackFrame frame = session.getCurrentStackFrame();
			if(frame instanceof JavaStackFrame)
			{
				Location location = ((JavaStackFrame) frame).getDescriptor().getLocation();
				if(location != null)
				{
					locationDeclName = location.declaringType().name();
				}
			}

			return new AlternativeSourceNotificationPanel(elems, baseClass, myProject, file, locationDeclName);
		}
		return null;
	}

	private static class ComboBoxClassElement
	{
		private final PsiClass myClass;
		private String myText;

		public ComboBoxClassElement(PsiClass aClass)
		{
			myClass = aClass;
		}

		private static JList ourDummyList = new JBList(); // to use ModuleRendererFactory

		@Override
		public String toString()
		{
			if(myText == null)
			{
				PsiElementModuleRenderer renderer = new PsiElementModuleRenderer();
				renderer.getListCellRendererComponent(ourDummyList, myClass, 1, false, false);
				myText = renderer.getText();
			}
			return myText;
		}
	}

	public static boolean fileProcessed(VirtualFile file)
	{
		return FILE_PROCESSED_KEY.get(file) != null;
	}

	private static class AlternativeSourceNotificationPanel extends EditorNotificationPanel
	{
		public AlternativeSourceNotificationPanel(ComboBoxClassElement[] alternatives, final PsiClass aClass, final Project project, final VirtualFile file, String locationDeclName)
		{
			setText(DebuggerBundle.message("editor.notification.alternative.source", aClass.getQualifiedName()));
			final ComboBox<ComboBoxClassElement> switcher = new ComboBox<>(alternatives);
			switcher.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent e)
				{
					final DebuggerContextImpl context = DebuggerManagerEx.getInstanceEx(project).getContext();
					final DebuggerSession session = context.getDebuggerSession();
					final PsiClass item = ((ComboBoxClassElement) switcher.getSelectedItem()).myClass;
					final VirtualFile vFile = item.getContainingFile().getVirtualFile();
					if(session != null && vFile != null)
					{
						session.getProcess().getManagerThread().schedule(new DebuggerCommandImpl()
						{
							@Override
							protected void action() throws Exception
							{
								if(!StringUtil.isEmpty(locationDeclName))
								{
									DebuggerUtilsEx.setAlternativeSourceUrl(locationDeclName, vFile.getUrl(), project);
								}
								DebuggerUIUtil.invokeLater(() ->
								{
									FileEditorManager.getInstance(project).closeFile(file);
									session.refresh(true);
								});
							}
						});
					}
					else
					{
						FileEditorManager.getInstance(project).closeFile(file);
						item.navigate(true);
					}
				}
			});
			myLinksPanel.add(switcher);
			createActionLabel(DebuggerBundle.message("action.disable.text"), () ->
			{
				DebuggerSettings.getInstance().SHOW_ALTERNATIVE_SOURCE = false;
				FILE_PROCESSED_KEY.set(file, null);
				FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
				FileEditor editor = fileEditorManager.getSelectedEditor(file);
				if(editor != null)
				{
					fileEditorManager.removeTopComponent(editor, this);
				}
			});
		}
	}
}
