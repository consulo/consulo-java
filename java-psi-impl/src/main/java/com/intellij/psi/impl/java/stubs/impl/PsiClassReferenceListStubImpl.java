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
package com.intellij.psi.impl.java.stubs.impl;

import javax.annotation.Nonnull;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.impl.compiled.ClsJavaCodeReferenceElementImpl;
import com.intellij.psi.impl.java.stubs.JavaClassReferenceListElementType;
import com.intellij.psi.impl.java.stubs.PsiClassReferenceListStub;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtil;

/**
 * @author max
 */
public class PsiClassReferenceListStubImpl extends StubBase<PsiReferenceList> implements PsiClassReferenceListStub
{
	private final String[] myNames;
	private PsiClassType[] myTypes;

	public PsiClassReferenceListStubImpl(@Nonnull JavaClassReferenceListElementType type, StubElement parent, @Nonnull String[] names)
	{
		super(parent, type);
		ObjectUtil.assertAllElementsNotNull(names);
		myNames = names;
	}

	@Nonnull
	@Override
	public PsiClassType[] getReferencedTypes()
	{
		if(myTypes != null)
		{
			return myTypes;
		}

		if(myNames.length == 0)
		{
			myTypes = PsiClassType.EMPTY_ARRAY;
			return myTypes;
		}

		PsiClassType[] types = new PsiClassType[myNames.length];

		final boolean compiled = ((JavaClassReferenceListElementType) getStubType()).isCompiled(this);
		if(compiled)
		{
			for(int i = 0; i < types.length; i++)
			{
				types[i] = new PsiClassReferenceType(new ClsJavaCodeReferenceElementImpl(getPsi(), myNames[i]), null);
			}
		}
		else
		{
			final PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory();

			int nullCount = 0;
			final PsiReferenceList psi = getPsi();
			for(int i = 0; i < types.length; i++)
			{
				try
				{
					final PsiJavaCodeReferenceElement ref = factory.createReferenceFromText(myNames[i], psi);
					((PsiJavaCodeReferenceElementImpl) ref).setKindWhenDummy(PsiJavaCodeReferenceElementImpl.Kind.CLASS_NAME_KIND);
					types[i] = factory.createType(ref);
				}
				catch(IncorrectOperationException e)
				{
					types[i] = null;
					nullCount++;
				}
			}

			if(nullCount > 0)
			{
				PsiClassType[] newTypes = new PsiClassType[types.length - nullCount];
				int cnt = 0;
				for(PsiClassType type : types)
				{
					if(type != null)
					{
						newTypes[cnt++] = type;
					}
				}
				types = newTypes;
			}
		}

		myTypes = types;
		return types.clone();
	}

	@Nonnull
	@Override
	public String[] getReferencedNames()
	{
		return myNames.clone();
	}

	@Nonnull
	@Override
	public PsiReferenceList.Role getRole()
	{
		return JavaClassReferenceListElementType.elementTypeToRole(getStubType());
	}

	@Override
	public String toString()
	{
		StringBuilder builder = new StringBuilder();
		builder.append("PsiRefListStub[").append(getRole()).append(':');
		for(int i = 0; i < myNames.length; i++)
		{
			if(i > 0)
			{
				builder.append(", ");
			}
			builder.append(myNames[i]);
		}
		builder.append(']');
		return builder.toString();
	}
}