// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.java.stubs.impl;

import com.intellij.java.language.psi.*;
import com.intellij.psi.impl.cache.TypeAnnotationContainer;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.impl.compiled.ClsJavaCodeReferenceElementImpl;
import com.intellij.psi.impl.java.stubs.JavaClassReferenceListElementType;
import com.intellij.psi.impl.java.stubs.PsiClassReferenceListStub;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import javax.annotation.Nonnull;

public class PsiClassReferenceListStubImpl extends StubBase<PsiReferenceList> implements PsiClassReferenceListStub
{
	@Nonnull
	private final TypeInfo[] myInfos;
	private volatile PsiClassType[] myTypes;

	public PsiClassReferenceListStubImpl(@Nonnull JavaClassReferenceListElementType type, StubElement parent, @Nonnull String[] names)
	{
		this(type, parent, ContainerUtil.map2Array(names, TypeInfo.class, TypeInfo::new));
	}

	public PsiClassReferenceListStubImpl(@Nonnull JavaClassReferenceListElementType type, StubElement parent,
										 @Nonnull TypeInfo[] infos)
	{
		super(parent, type);
		for(TypeInfo info : infos)
		{
			if(info == null)
			{
				throw new IllegalArgumentException();
			}
			if(info.text == null)
			{
				throw new IllegalArgumentException();
			}
		}
		myInfos = infos;
	}

	@Override
	@Nonnull
	public PsiClassType[] getReferencedTypes()
	{
		PsiClassType[] types = myTypes;
		if(types == null)
		{
			myTypes = types = createTypes();
		}
		return types.clone();
	}

	private boolean shouldSkipSoleObject()
	{
		final boolean compiled = ((JavaClassReferenceListElementType) getStubType()).isCompiled(this);
		return compiled && myInfos.length == 1 && myInfos[0].text.equals(CommonClassNames.JAVA_LANG_OBJECT) &&
				myInfos[0].getTypeAnnotations().isEmpty();
	}

	@Nonnull
	private PsiClassType[] createTypes()
	{
		PsiClassType[] types = myInfos.length == 0 ? PsiClassType.EMPTY_ARRAY : new PsiClassType[myInfos.length];

		final boolean compiled = ((JavaClassReferenceListElementType) getStubType()).isCompiled(this);
		if(compiled)
		{
			if(shouldSkipSoleObject())
			{
				return PsiClassType.EMPTY_ARRAY;
			}
			for(int i = 0; i < types.length; i++)
			{
				TypeInfo info = myInfos[i];
				TypeAnnotationContainer annotations = info.getTypeAnnotations();
				ClsJavaCodeReferenceElementImpl reference = new ClsJavaCodeReferenceElementImpl(getPsi(), info.text, annotations);
				types[i] = new PsiClassReferenceType(reference, null).annotate(annotations.getProvider(reference));
			}
		}
		else
		{
			final PsiElementFactory factory = JavaPsiFacade.getElementFactory(getProject());

			int nullCount = 0;
			final PsiReferenceList psi = getPsi();
			for(int i = 0; i < types.length; i++)
			{
				try
				{
					final PsiJavaCodeReferenceElement ref = factory.createReferenceFromText(myInfos[i].text, psi);
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
		return types;
	}

	@Override
	@Nonnull
	public String[] getReferencedNames()
	{
		if(myInfos.length == 0 || shouldSkipSoleObject())
		{
			return ArrayUtil.EMPTY_STRING_ARRAY;
		}
		return ContainerUtil.map2Array(myInfos, String.class, info -> info.text);
	}

	@Override
	@Nonnull
	public TypeInfo[] getTypes()
	{
		if(myInfos.length == 0 || shouldSkipSoleObject())
		{
			return TypeInfo.EMPTY_ARRAY;
		}
		return myInfos.clone();
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
		return "PsiRefListStub[" + getRole() + ':' + String.join(", ", getReferencedNames()) + ']';
	}
}
