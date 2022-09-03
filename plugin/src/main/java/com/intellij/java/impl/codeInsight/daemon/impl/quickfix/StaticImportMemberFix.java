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
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import consulo.language.editor.CodeInsightSettings;
import consulo.language.editor.FileModificationService;
import com.intellij.java.impl.codeInsight.JavaProjectCodeInsightSettings;
import consulo.ide.impl.idea.codeInsight.daemon.impl.ShowAutoImportPass;
import consulo.language.editor.hint.HintManager;
import consulo.language.editor.hint.QuestionAction;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.editor.intention.HintAction;
import consulo.application.ApplicationManager;
import consulo.codeEditor.Editor;
import consulo.project.Project;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import com.intellij.java.language.psi.PsiJavaFile;
import com.intellij.java.language.psi.PsiMember;
import com.intellij.java.language.psi.util.PsiUtil;

public abstract class StaticImportMemberFix<T extends PsiMember> implements IntentionAction, HintAction
{
	private List<T> candidates;

	@Nonnull
	protected abstract String getBaseText();

	@Nonnull
	protected abstract String getMemberPresentableText(T t);

	@Override
	@Nonnull
	public String getText()
	{
		String text = getBaseText();
		if(candidates != null && candidates.size() == 1)
		{
			text += " '" + getMemberPresentableText(candidates.get(0)) + "'";
		}
		else
		{
			text += "...";
		}
		return text;
	}

	@Override
	@Nonnull
	public String getFamilyName()
	{
		return getText();
	}

	@Override
	public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file)
	{
		return PsiUtil.isLanguageLevel5OrHigher(file) && file instanceof PsiJavaFile && getElement() != null && getElement().isValid() && getQualifierExpression() == null && resolveRef() == null &&
				file.getManager().isInProject(file) && !(candidates == null ? candidates = getMembersToImport(false) : candidates).isEmpty();
	}

	public final List<T> getMembersToImport()
	{
		return getMembersToImport(false);
	}

	@Nonnull
	protected abstract List<T> getMembersToImport(boolean applicableOnly);

	public static boolean isExcluded(PsiMember method)
	{
		String name = PsiUtil.getMemberQualifiedName(method);
		return name != null && JavaProjectCodeInsightSettings.getSettings(method.getProject()).isExcluded(name);
	}

	@Nonnull
	protected abstract QuestionAction createQuestionAction(List<T> methodsToImport, @Nonnull Project project, Editor editor);

	@Nullable
	protected abstract PsiElement getElement();

	@Nullable
	protected abstract PsiElement getQualifierExpression();

	@Nullable
	protected abstract PsiElement resolveRef();

	@Override
	public void invoke(@Nonnull final Project project, final Editor editor, PsiFile file)
	{
		if(!FileModificationService.getInstance().prepareFileForWrite(file))
		{
			return;
		}
		ApplicationManager.getApplication().runWriteAction(() ->
		{
			final List<T> methodsToImport = getMembersToImport(false);
			if(methodsToImport.isEmpty())
			{
				return;
			}
			createQuestionAction(methodsToImport, project, editor).execute();
		});
	}

	private ImportClassFixBase.Result doFix(Editor editor)
	{
		if(!CodeInsightSettings.getInstance().ADD_MEMBER_IMPORTS_ON_THE_FLY)
		{
			return ImportClassFixBase.Result.POPUP_NOT_SHOWN;
		}
		final List<T> candidates = getMembersToImport(true);
		if(candidates.isEmpty())
		{
			return ImportClassFixBase.Result.POPUP_NOT_SHOWN;
		}

		final PsiElement element = getElement();
		if(element == null)
		{
			return ImportClassFixBase.Result.POPUP_NOT_SHOWN;
		}

		final QuestionAction action = createQuestionAction(candidates, element.getProject(), editor);
	/* PsiFile psiFile = element.getContainingFile();
   if (candidates.size() == 1 &&
        ImportClassFixBase.isAddUnambiguousImportsOnTheFlyEnabled(psiFile) &&
        (ApplicationManager.getApplication().isUnitTestMode() || DaemonListeners.canChangeFileSilently(psiFile)) &&
        !LaterInvocator.isInModalContext()) {
      CommandProcessor.getInstance().runUndoTransparentAction(() -> action.execute());
      return ImportClassFixBase.Result.CLASS_AUTO_IMPORTED;
    }
*/
		String hintText = ShowAutoImportPass.getMessage(candidates.size() > 1, getMemberPresentableText(candidates.get(0)));
		if(!ApplicationManager.getApplication().isUnitTestMode() && !HintManager.getInstance().hasShownHintsThatWillHideByOtherHint(true))
		{
			final TextRange textRange = element.getTextRange();
			HintManager.getInstance().showQuestionHint(editor, hintText, textRange.getStartOffset(), textRange.getEndOffset(), action);
		}
		return ImportClassFixBase.Result.POPUP_SHOWN;
	}


	@Override
	public boolean startInWriteAction()
	{
		return false;
	}

	@Override
	public boolean showHint(@Nonnull Editor editor)
	{
		final PsiElement callExpression = getElement();
		if(callExpression == null || getQualifierExpression() != null)
		{
			return false;
		}
		ImportClassFixBase.Result result = doFix(editor);
		return result == ImportClassFixBase.Result.POPUP_SHOWN || result == ImportClassFixBase.Result.CLASS_AUTO_IMPORTED;
	}

}
