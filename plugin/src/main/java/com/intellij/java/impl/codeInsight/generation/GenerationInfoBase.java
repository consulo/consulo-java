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
package com.intellij.java.impl.codeInsight.generation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.intellij.java.language.impl.codeInsight.generation.GenerationInfo;
import consulo.codeEditor.Editor;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiMember;
import consulo.language.util.IncorrectOperationException;

/**
 * @author peter
 */
public abstract class GenerationInfoBase implements GenerationInfo
{
	@Override
	public abstract void insert(PsiClass aClass, PsiElement anchor, boolean before) throws IncorrectOperationException;

	@Override
	public abstract PsiMember getPsiMember();

	/**
	 * @param aClass
	 * @param leaf   leaf element. Is guaranteed to be a tree descendant of aClass.
	 * @return the value that will be passed to the {@link #insert(PsiClass, com.intellij.psi.PsiElement, boolean)} method later.
	 */
	@Override
	@Nullable
	public PsiElement findInsertionAnchor(@Nonnull PsiClass aClass, @Nonnull PsiElement leaf)
	{
		PsiElement element = leaf;
		while(element.getParent() != aClass)
		{
			element = element.getParent();
		}

		PsiElement lBrace = aClass.getLBrace();
		if(lBrace == null)
		{
			return null;
		}
		PsiElement rBrace = aClass.getRBrace();
		if(!GenerateMembersUtil.isChildInRange(element, lBrace.getNextSibling(), rBrace))
		{
			return null;
		}
		PsiElement prev = leaf.getPrevSibling();
		if(prev != null && prev.getNode() != null && prev.getNode().getElementType() == JavaTokenType.END_OF_LINE_COMMENT)
		{
			element = leaf.getNextSibling();
		}
		return element;
	}

	@Override
	public void positionCaret(Editor editor, boolean toEditMethodBody)
	{
		GenerateMembersUtil.positionCaret(editor, getPsiMember(), toEditMethodBody);
	}
}
