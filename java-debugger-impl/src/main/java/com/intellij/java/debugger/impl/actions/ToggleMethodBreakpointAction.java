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

import com.intellij.java.debugger.impl.DebuggerManagerEx;
import com.intellij.java.debugger.impl.DebuggerUtilsEx;
import com.intellij.java.debugger.impl.ui.breakpoints.Breakpoint;
import com.intellij.java.debugger.impl.ui.breakpoints.BreakpointManager;
import com.intellij.java.debugger.impl.ui.breakpoints.MethodBreakpoint;
import com.intellij.java.language.impl.JavaClassFileType;
import com.intellij.java.language.impl.JavaFileType;
import consulo.fileEditor.FileEditorManager;
import consulo.ui.ex.action.ActionPlaces;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.document.Document;
import consulo.codeEditor.Editor;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import com.intellij.java.language.psi.PsiMethod;
import consulo.document.util.DocumentUtil;
import consulo.util.lang.CharArrayUtil;

import jakarta.annotation.Nullable;

public class ToggleMethodBreakpointAction extends AnAction
{
	public void update(AnActionEvent event)
	{
		boolean toEnable = getPlace(event) != null;

		if (ActionPlaces.isPopupPlace(event.getPlace()))
		{
			event.getPresentation().setVisible(toEnable);
		}
		else
		{
			event.getPresentation().setEnabled(toEnable);
		}
	}


	public void actionPerformed(AnActionEvent e)
	{
		Project project = e.getData(Project.KEY);
		if (project == null)
		{
			return;
		}
		DebuggerManagerEx debugManager = DebuggerManagerEx.getInstanceEx(project);
		if (debugManager == null)
		{
			return;
		}
		final BreakpointManager manager = debugManager.getBreakpointManager();
		final PlaceInDocument place = getPlace(e);
		if (place != null && DocumentUtil.isValidOffset(place.getOffset(), place.getDocument()))
		{
			Breakpoint breakpoint = manager.findBreakpoint(place.getDocument(), place.getOffset(), MethodBreakpoint.CATEGORY);
			if (breakpoint == null)
			{
				manager.addMethodBreakpoint(place.getDocument(), place.getDocument().getLineNumber(place.getOffset()));
			}
			else
			{
				manager.removeBreakpoint(breakpoint);
			}
		}
	}

	@Nullable
	private static PlaceInDocument getPlace(AnActionEvent event)
	{
		final Project project = event.getData(Project.KEY);
		if (project == null)
		{
			return null;
		}

		PsiElement method = null;
		Document document = null;

		if (ActionPlaces.PROJECT_VIEW_POPUP.equals(event.getPlace()) || ActionPlaces.STRUCTURE_VIEW_POPUP.equals(event.getPlace())
			|| ActionPlaces.FAVORITES_VIEW_POPUP.equals(event.getPlace()) || ActionPlaces.NAVIGATION_BAR_POPUP.equals(event.getPlace()))
		{
			final PsiElement psiElement = event.getData(PsiElement.KEY);
			if (psiElement instanceof PsiMethod)
			{
				final PsiFile containingFile = psiElement.getContainingFile();
				if (containingFile != null)
				{
					method = psiElement;
					document = PsiDocumentManager.getInstance(project).getDocument(containingFile);
				}
			}
		}
		else
		{
			Editor editor = event.getData(Editor.KEY);
			if (editor == null)
			{
				editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
			}
			if (editor != null)
			{
				document = editor.getDocument();
				PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
				if (file != null)
				{
					final VirtualFile virtualFile = file.getVirtualFile();
					FileType fileType = virtualFile != null ? virtualFile.getFileType() : null;
					if (JavaFileType.INSTANCE == fileType || JavaClassFileType.INSTANCE == fileType)
					{
						method = findMethod(project, editor);
					}
				}
			}
		}

		return method != null ? new PlaceInDocument(document, method.getTextOffset()) : null;
	}

	@Nullable
	private static PsiMethod findMethod(Project project, Editor editor)
	{
		if (editor == null)
		{
			return null;
		}
		PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
		if (psiFile == null)
		{
			return null;
		}
		final int offset = CharArrayUtil.shiftForward(editor.getDocument().getCharsSequence(), editor.getCaretModel().getOffset(), " \t");
		return DebuggerUtilsEx.findPsiMethod(psiFile, offset);
	}
}