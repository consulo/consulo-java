// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.ig.fixes;

import com.intellij.java.language.psi.PsiSwitchBlock;
import consulo.codeEditor.Editor;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.psi.PsiFile;
import consulo.language.psi.SmartPointerManager;
import consulo.language.psi.SmartPsiElementPointer;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;

import jakarta.annotation.Nonnull;

public abstract class BaseSwitchFix implements LocalQuickFix, SyntheticIntentionAction
{
	protected final SmartPsiElementPointer<PsiSwitchBlock> myBlock;

	public BaseSwitchFix(@Nonnull PsiSwitchBlock block)
	{
		myBlock = SmartPointerManager.createPointer(block);
	}

	@Override
	public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor)
	{
		invoke();
	}

	@Override
	public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException
	{
		invoke();
	}

	@Override
	public boolean startInWriteAction()
	{
		return true;
	}

	abstract protected void invoke();

	@Override
	public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file)
	{
		PsiSwitchBlock startSwitch = myBlock.getElement();
		if(startSwitch == null)
			return false;
		int offset = Math.min(editor.getCaretModel().getOffset(), startSwitch.getTextRange().getEndOffset() - 1);
		PsiSwitchBlock currentSwitch = PsiTreeUtil.getNonStrictParentOfType(file.findElementAt(offset), PsiSwitchBlock.class);
		return currentSwitch == startSwitch;
	}
}
