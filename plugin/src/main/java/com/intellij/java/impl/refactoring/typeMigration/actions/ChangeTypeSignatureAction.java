/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.typeMigration.actions;

import jakarta.annotation.Nonnull;

import consulo.dataContext.DataContext;
import consulo.codeEditor.Editor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiReferenceParameterList;
import com.intellij.java.language.psi.PsiTypeElement;
import com.intellij.java.language.psi.PsiVariable;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.editor.refactoring.action.BaseRefactoringAction;
import com.intellij.java.impl.refactoring.typeMigration.ChangeTypeSignatureHandler;
import consulo.language.editor.TargetElementUtil;

public class ChangeTypeSignatureAction extends BaseRefactoringAction
{
	@Override
	public boolean isAvailableInEditorOnly()
	{
		return false;
	}

	@Override
	public boolean isEnabledOnElements(@Nonnull PsiElement[] elements)
	{
		if(elements.length > 1)
		{
			return false;
		}

		for(PsiElement element : elements)
		{
			if(!(element instanceof PsiMethod || element instanceof PsiVariable))
			{
				return false;
			}
		}

		return true;
	}

	@Override
	protected boolean isAvailableOnElementInEditorAndFile(@Nonnull final PsiElement element, @Nonnull final Editor editor, @Nonnull PsiFile file, @Nonnull DataContext context)
	{
		final int offset = TargetElementUtil.adjustOffset(file, editor.getDocument(), editor.getCaretModel().getOffset());
		final PsiElement psiElement = file.findElementAt(offset);
		final PsiReferenceParameterList referenceParameterList = PsiTreeUtil.getParentOfType(psiElement, PsiReferenceParameterList.class);
		if(referenceParameterList != null)
		{
			return referenceParameterList.getTypeArguments().length > 0;
		}
		return PsiTreeUtil.getParentOfType(psiElement, PsiTypeElement.class) != null;
	}

	@Override
	public RefactoringActionHandler getHandler(@Nonnull DataContext dataContext)
	{
		return new ChangeTypeSignatureHandler();
	}
}
