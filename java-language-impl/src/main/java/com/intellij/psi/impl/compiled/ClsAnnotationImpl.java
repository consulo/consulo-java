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
package com.intellij.psi.impl.compiled;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NonNls;

import javax.annotation.Nullable;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.pom.Navigatable;
import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.java.language.psi.PsiAnnotation;
import com.intellij.java.language.psi.PsiAnnotationMemberValue;
import com.intellij.java.language.psi.PsiAnnotationOwner;
import com.intellij.java.language.psi.PsiAnnotationParameterList;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.java.language.psi.PsiJavaCodeReferenceElement;
import com.intellij.java.language.psi.PsiNameValuePair;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.java.stubs.PsiAnnotationStub;
import com.intellij.psi.impl.meta.MetaRegistry;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * @author ven
 */
public class ClsAnnotationImpl extends ClsRepositoryPsiElement<PsiAnnotationStub> implements PsiAnnotation, Navigatable
{
	private final NotNullLazyValue<ClsJavaCodeReferenceElementImpl> myReferenceElement;
	private final NotNullLazyValue<ClsAnnotationParameterListImpl> myParameterList;

	public ClsAnnotationImpl(final PsiAnnotationStub stub)
	{
		super(stub);
		myReferenceElement = new AtomicNotNullLazyValue<ClsJavaCodeReferenceElementImpl>()
		{
			@Nonnull
			@Override
			protected ClsJavaCodeReferenceElementImpl compute()
			{
				String annotationText = getStub().getText();
				int index = annotationText.indexOf('(');
				String refText = index > 0 ? annotationText.substring(1, index) : annotationText.substring(1);
				return new ClsJavaCodeReferenceElementImpl(ClsAnnotationImpl.this, refText);
			}
		};
		myParameterList = new AtomicNotNullLazyValue<ClsAnnotationParameterListImpl>()
		{
			@Nonnull
			@Override
			protected ClsAnnotationParameterListImpl compute()
			{
				PsiNameValuePair[] attrs = getStub().getText().indexOf('(') > 0 ? PsiTreeUtil.getRequiredChildOfType(getStub().getPsiElement(), PsiAnnotationParameterList.class).getAttributes() :
						PsiNameValuePair.EMPTY_ARRAY;
				return new ClsAnnotationParameterListImpl(ClsAnnotationImpl.this, attrs);
			}
		};
	}

	@Override
	public void appendMirrorText(int indentLevel, @Nonnull StringBuilder buffer)
	{
		buffer.append('@').append(myReferenceElement.getValue().getCanonicalText());
		appendText(getParameterList(), indentLevel, buffer);
	}

	@Override
	public void setMirror(@Nonnull TreeElement element) throws InvalidMirrorException
	{
		setMirrorCheckingType(element, null);
		PsiAnnotation mirror = SourceTreeToPsiMap.treeToPsiNotNull(element);
		setMirror(getNameReferenceElement(), mirror.getNameReferenceElement());
		setMirror(getParameterList(), mirror.getParameterList());
	}

	@Override
	@Nonnull
	public PsiElement[] getChildren()
	{
		return new PsiElement[]{
				myReferenceElement.getValue(),
				getParameterList()
		};
	}

	@Override
	public void accept(@Nonnull PsiElementVisitor visitor)
	{
		if(visitor instanceof JavaElementVisitor)
		{
			((JavaElementVisitor) visitor).visitAnnotation(this);
		}
		else
		{
			visitor.visitElement(this);
		}
	}

	@Override
	@Nonnull
	public PsiAnnotationParameterList getParameterList()
	{
		return myParameterList.getValue();
	}

	@Override
	@Nullable
	public String getQualifiedName()
	{
		return myReferenceElement.getValue().getCanonicalText();
	}

	@Override
	public PsiJavaCodeReferenceElement getNameReferenceElement()
	{
		return myReferenceElement.getValue();
	}

	@Override
	public PsiAnnotationMemberValue findAttributeValue(String attributeName)
	{
		return PsiImplUtil.findAttributeValue(this, attributeName);
	}

	@Override
	@Nullable
	public PsiAnnotationMemberValue findDeclaredAttributeValue(@NonNls final String attributeName)
	{
		return PsiImplUtil.findDeclaredAttributeValue(this, attributeName);
	}

	@Override
	public <T extends PsiAnnotationMemberValue> T setDeclaredAttributeValue(@NonNls String attributeName, T value)
	{
		throw cannotModifyException(this);
	}

	@Override
	public String getText()
	{
		final StringBuilder buffer = new StringBuilder();
		appendMirrorText(0, buffer);
		return buffer.toString();
	}

	@Override
	public PsiMetaData getMetaData()
	{
		return MetaRegistry.getMetaBase(this);
	}

	@Override
	public PsiAnnotationOwner getOwner()
	{
		return (PsiAnnotationOwner) getParent();//todo
	}
}
