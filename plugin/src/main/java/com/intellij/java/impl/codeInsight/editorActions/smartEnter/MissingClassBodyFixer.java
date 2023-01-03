package com.intellij.java.impl.codeInsight.editorActions.smartEnter;

import consulo.codeEditor.Editor;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiTypeParameter;
import consulo.language.util.IncorrectOperationException;

/**
 * @author peter
 */
public class MissingClassBodyFixer implements Fixer
{
	@Override
	public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException
	{
		if(!(psiElement instanceof PsiClass) || psiElement instanceof PsiTypeParameter)
		{
			return;
		}
		PsiClass psiClass = (PsiClass) psiElement;

		if(psiClass.getLBrace() == null)
		{
			int offset = psiClass.getTextRange().getEndOffset();
			editor.getDocument().insertString(offset, "{\n}");
			editor.getCaretModel().moveToOffset(offset);
		}
	}
}
