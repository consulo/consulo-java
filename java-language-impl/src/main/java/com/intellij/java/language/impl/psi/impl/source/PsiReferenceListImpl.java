/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.language.impl.psi.impl.source;

import javax.annotation.Nonnull;

import consulo.language.ast.ASTNode;
import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClassType;
import com.intellij.java.language.psi.PsiElementFactory;
import consulo.language.psi.PsiElementVisitor;
import com.intellij.java.language.psi.PsiJavaCodeReferenceElement;
import com.intellij.java.language.psi.PsiReferenceList;
import com.intellij.java.language.impl.psi.impl.java.stubs.JavaClassReferenceListElementType;
import com.intellij.java.language.impl.psi.impl.java.stubs.PsiClassReferenceListStub;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;

/**
 * @author max
 */
public class PsiReferenceListImpl extends JavaStubPsiElement<PsiClassReferenceListStub> implements PsiReferenceList
{
	public PsiReferenceListImpl(@Nonnull PsiClassReferenceListStub stub)
	{
		super(stub, stub.getStubType());
	}

	public PsiReferenceListImpl(@Nonnull ASTNode node)
	{
		super(node);
	}

	@Override
	@Nonnull
	public PsiJavaCodeReferenceElement[] getReferenceElements()
	{
		return calcTreeElement().getChildrenAsPsiElements(JavaElementType.JAVA_CODE_REFERENCE, PsiJavaCodeReferenceElement.ARRAY_FACTORY);
	}

	@Override
	@Nonnull
	public PsiClassType[] getReferencedTypes()
	{
		PsiClassReferenceListStub stub = getGreenStub();
		if(stub != null)
		{
			return stub.getReferencedTypes();
		}

		PsiJavaCodeReferenceElement[] refs = getReferenceElements();
		PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
		PsiClassType[] types = new PsiClassType[refs.length];
		for(int i = 0; i < types.length; i++)
		{
			types[i] = factory.createType(refs[i]);
		}

		return types;
	}

	@Override
	public Role getRole()
	{
		return JavaClassReferenceListElementType.elementTypeToRole(getElementType());
	}

	@Override
	public void accept(@Nonnull PsiElementVisitor visitor)
	{
		if(visitor instanceof JavaElementVisitor)
		{
			((JavaElementVisitor) visitor).visitReferenceList(this);
		}
		else
		{
			visitor.visitElement(this);
		}
	}

	@Override
	public String toString()
	{
		return "PsiReferenceList";
	}
}