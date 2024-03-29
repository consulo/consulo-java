/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.java.language.impl.psi.impl.compiled;

import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.java.language.psi.JavaTokenType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import com.intellij.java.language.psi.PsiIdentifier;
import com.intellij.java.language.psi.PsiJavaFile;
import com.intellij.java.language.psi.PsiJavaToken;
import consulo.language.impl.ast.TreeElement;
import consulo.language.ast.IElementType;
import jakarta.annotation.Nonnull;

class ClsIdentifierImpl extends ClsElementImpl implements PsiIdentifier, PsiJavaToken
{
	private final PsiElement myParent;
	private final String myText;

	ClsIdentifierImpl(@Nonnull PsiElement parent, String text)
	{
		myParent = parent;
		myText = text;
	}

	@Override
	public IElementType getTokenType()
	{
		return JavaTokenType.IDENTIFIER;
	}

	@Override
	public String getText()
	{
		return myText;
	}

	@Override
	@Nonnull
	public PsiElement[] getChildren()
	{
		return PsiElement.EMPTY_ARRAY;
	}

	@Override
	public PsiElement getParent()
	{
		return myParent;
	}

	private boolean isCorrectName(String name)
	{
		return name != null && ClsParsingUtil.isJavaIdentifier(name, ((PsiJavaFile) getContainingFile()).getLanguageLevel());
	}

	@Override
	public void appendMirrorText(int indentLevel, @Nonnull StringBuilder buffer)
	{
		String original = getText();
		if(isCorrectName(original))
		{
			buffer.append(original);
		}
		else
		{
			buffer.append("$$").append(original).append(" /* Real name is '").append(original).append("' */");
		}
	}

	@Override
	public void setMirror(@Nonnull TreeElement element) throws InvalidMirrorException
	{
		setMirrorCheckingType(element, JavaTokenType.IDENTIFIER);
	}

	@Override
	public void accept(@Nonnull PsiElementVisitor visitor)
	{
		if(visitor instanceof JavaElementVisitor)
		{
			((JavaElementVisitor) visitor).visitIdentifier(this);
		}
		else
		{
			visitor.visitElement(this);
		}
	}

	@Override
	public String toString()
	{
		return "PsiIdentifier:" + getText();
	}
}
