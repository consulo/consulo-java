// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiSwitchBlock;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import javax.annotation.Nonnull;

public abstract class BaseSwitchFix implements LocalQuickFix, IntentionAction
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
