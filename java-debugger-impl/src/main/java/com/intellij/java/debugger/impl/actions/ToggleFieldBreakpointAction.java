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
package com.intellij.java.debugger.impl.actions;

import com.intellij.java.debugger.DebuggerBundle;
import com.intellij.java.debugger.impl.DebuggerManagerEx;
import com.intellij.java.debugger.impl.InstanceFilter;
import com.intellij.java.debugger.SourcePosition;
import com.intellij.java.debugger.impl.engine.DebugProcessImpl;
import com.intellij.java.debugger.engine.DebuggerUtils;
import com.intellij.java.debugger.impl.engine.SourcePositionProvider;
import com.intellij.java.debugger.impl.engine.events.DebuggerContextCommandImpl;
import com.intellij.java.debugger.impl.DebuggerContextImpl;
import com.intellij.java.debugger.impl.DebuggerSession;
import com.intellij.java.debugger.impl.ui.breakpoints.Breakpoint;
import com.intellij.java.debugger.impl.ui.breakpoints.BreakpointManager;
import com.intellij.java.debugger.impl.ui.breakpoints.FieldBreakpoint;
import com.intellij.java.debugger.impl.ui.impl.watch.DebuggerTree;
import com.intellij.java.debugger.impl.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.java.debugger.impl.ui.impl.watch.FieldDescriptorImpl;
import com.intellij.java.language.impl.JavaClassFileType;
import com.intellij.java.language.impl.JavaFileType;
import consulo.ui.ex.action.ActionPlaces;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.language.editor.CommonDataKeys;
import consulo.dataContext.DataContext;
import consulo.ui.ex.action.Presentation;
import consulo.application.ApplicationManager;
import consulo.document.Document;
import consulo.codeEditor.Editor;
import consulo.fileEditor.FileEditorManager;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.project.Project;
import consulo.util.lang.ref.Ref;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiField;
import consulo.language.psi.PsiFile;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.internal.com.sun.jdi.Field;
import consulo.internal.com.sun.jdi.ObjectReference;

import javax.annotation.Nullable;

public class ToggleFieldBreakpointAction extends AnAction
{

	@Override
	public void actionPerformed(AnActionEvent e)
	{
		Project project = e.getData(CommonDataKeys.PROJECT);
		if(project == null)
		{
			return;
		}
		final SourcePosition place = getPlace(e);

		if(place != null)
		{
			Document document = PsiDocumentManager.getInstance(project).getDocument(place.getFile());
			if(document != null)
			{
				DebuggerManagerEx debuggerManager = DebuggerManagerEx.getInstanceEx(project);
				BreakpointManager manager = debuggerManager.getBreakpointManager();
				final int offset = place.getOffset();
				final Breakpoint breakpoint = offset >= 0 ? manager.findBreakpoint(document, offset, FieldBreakpoint.CATEGORY) : null;

				if(breakpoint == null)
				{
					FieldBreakpoint fieldBreakpoint = manager.addFieldBreakpoint(document, offset);
					if(fieldBreakpoint != null)
					{
						if(DebuggerAction.isContextView(e))
						{
							final DebuggerTreeNodeImpl selectedNode = DebuggerAction.getSelectedNode(e.getDataContext());
							if(selectedNode != null && selectedNode.getDescriptor() instanceof FieldDescriptorImpl)
							{
								ObjectReference object = ((FieldDescriptorImpl) selectedNode.getDescriptor()).getObject();
								if(object != null)
								{
									long id = object.uniqueID();
									InstanceFilter[] instanceFilters = new InstanceFilter[]{InstanceFilter.create(Long.toString(id))};
									fieldBreakpoint.setInstanceFilters(instanceFilters);
									fieldBreakpoint.setInstanceFiltersEnabled(true);
								}
							}
						}

						final Editor editor = e.getData(CommonDataKeys.EDITOR);
						if(editor != null)
						{
							manager.editBreakpoint(fieldBreakpoint, editor);
						}
					}
				}
				else
				{
					manager.removeBreakpoint(breakpoint);
				}
			}
		}
	}

	@Override
	public void update(AnActionEvent event)
	{
		SourcePosition place = getPlace(event);
		boolean toEnable = place != null;

		Presentation presentation = event.getPresentation();
		if(ActionPlaces.PROJECT_VIEW_POPUP.equals(event.getPlace()) || ActionPlaces.STRUCTURE_VIEW_POPUP.equals(event.getPlace()) || ActionPlaces.FAVORITES_VIEW_POPUP.equals(event.getPlace()))
		{
			presentation.setVisible(toEnable);
		}
		else if(DebuggerAction.isContextView(event))
		{
			presentation.setText(DebuggerBundle.message("action.add.field.watchpoint.text"));
			Project project = event.getData(CommonDataKeys.PROJECT);
			if(project != null && place != null)
			{
				Document document = PsiDocumentManager.getInstance(project).getDocument(place.getFile());
				if(document != null)
				{
					final int offset = place.getOffset();
					final BreakpointManager breakpointManager = (DebuggerManagerEx.getInstanceEx(project)).getBreakpointManager();
					final Breakpoint fieldBreakpoint = offset >= 0 ? breakpointManager.findBreakpoint(document, offset, FieldBreakpoint.CATEGORY) : null;
					if(fieldBreakpoint != null)
					{
						presentation.setEnabled(false);
						return;
					}
				}
			}
		}
		presentation.setVisible(toEnable);
	}

	@Nullable
	public static SourcePosition getPlace(AnActionEvent event)
	{
		final DataContext dataContext = event.getDataContext();
		final Project project = event.getData(CommonDataKeys.PROJECT);
		if(project == null)
		{
			return null;
		}
		if(ActionPlaces.PROJECT_VIEW_POPUP.equals(event.getPlace()) || ActionPlaces.STRUCTURE_VIEW_POPUP.equals(event.getPlace()) || ActionPlaces.FAVORITES_VIEW_POPUP.equals(event.getPlace()))
		{
			final PsiElement psiElement = event.getData(CommonDataKeys.PSI_ELEMENT);
			if(psiElement instanceof PsiField)
			{
				return SourcePosition.createFromElement(psiElement);
			}
			return null;
		}

		final DebuggerTreeNodeImpl selectedNode = DebuggerAction.getSelectedNode(dataContext);
		if(selectedNode != null && selectedNode.getDescriptor() instanceof FieldDescriptorImpl)
		{
			final DebuggerContextImpl debuggerContext = DebuggerAction.getDebuggerContext(dataContext);
			final DebugProcessImpl debugProcess = debuggerContext.getDebugProcess();
			if(debugProcess != null)
			{ // if there is an active debug session
				final Ref<SourcePosition> positionRef = new Ref<>(null);
				debugProcess.getManagerThread().invokeAndWait(new DebuggerContextCommandImpl(debuggerContext)
				{
					@Override
					public Priority getPriority()
					{
						return Priority.HIGH;
					}

					@Override
					public void threadAction()
					{
						ApplicationManager.getApplication().runReadAction(() -> positionRef.set(SourcePositionProvider.getSourcePosition(selectedNode.getDescriptor(), project, debuggerContext)));
					}
				});
				final SourcePosition sourcePosition = positionRef.get();
				if(sourcePosition != null)
				{
					return sourcePosition;
				}
			}
		}

		if(DebuggerAction.isContextView(event))
		{
			DebuggerTree tree = event.getData(DebuggerTree.DATA_KEY);
			if(tree != null && tree.getSelectionPath() != null)
			{
				DebuggerTreeNodeImpl node = ((DebuggerTreeNodeImpl) tree.getSelectionPath().getLastPathComponent());
				if(node != null && node.getDescriptor() instanceof FieldDescriptorImpl)
				{
					Field field = ((FieldDescriptorImpl) node.getDescriptor()).getField();
					DebuggerSession session = tree.getDebuggerContext().getDebuggerSession();
					PsiClass psiClass = DebuggerUtils.findClass(field.declaringType().name(), project, (session != null) ? session.getSearchScope() : GlobalSearchScope.allScope(project));
					if(psiClass != null)
					{
						psiClass = (PsiClass) psiClass.getNavigationElement();
						final PsiField psiField = psiClass.findFieldByName(field.name(), true);
						if(psiField != null)
						{
							return SourcePosition.createFromElement(psiField);
						}
					}
				}
			}
			return null;
		}

		Editor editor = event.getData(CommonDataKeys.EDITOR);
		if(editor == null)
		{
			editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
		}
		if(editor != null)
		{
			final Document document = editor.getDocument();
			PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
			if(file != null)
			{
				final VirtualFile virtualFile = file.getVirtualFile();
				FileType fileType = virtualFile != null ? virtualFile.getFileType() : null;
				if(fileType == JavaFileType.INSTANCE || fileType == JavaClassFileType.INSTANCE)
				{
					final PsiField field = FieldBreakpoint.findField(project, document, editor.getCaretModel().getOffset());
					if(field != null)
					{
						return SourcePosition.createFromElement(field);
					}
				}
			}
		}
		return null;
	}

}
