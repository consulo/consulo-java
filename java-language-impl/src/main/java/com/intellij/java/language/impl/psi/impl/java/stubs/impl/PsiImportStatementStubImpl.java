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
package com.intellij.java.language.impl.psi.impl.java.stubs.impl;

import org.jspecify.annotations.Nullable;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiImportStatementBase;
import com.intellij.java.language.psi.PsiJavaCodeReferenceElement;
import com.intellij.java.language.psi.PsiJavaModuleReferenceElement;
import com.intellij.java.language.psi.PsiJavaParserFacade;
import com.intellij.java.language.impl.psi.impl.java.stubs.JavaImportStatementElementType;
import com.intellij.java.language.impl.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.java.language.impl.psi.impl.java.stubs.PsiImportStatementStub;
import com.intellij.java.language.impl.psi.impl.source.PsiJavaCodeReferenceElementImpl;
import consulo.language.psi.stub.StubBase;
import consulo.language.psi.stub.StubElement;
import consulo.util.lang.ref.SoftReference;
import consulo.util.lang.BitUtil;
import consulo.language.util.IncorrectOperationException;

/**
 * @author max
 */
public class PsiImportStatementStubImpl extends StubBase<PsiImportStatementBase> implements PsiImportStatementStub
{
	private final byte myFlags;
	private final String myText;
	private SoftReference<PsiJavaCodeReferenceElement> myReference;
	private SoftReference<PsiJavaModuleReferenceElement> myModuleReference;

	private static final int ON_DEMAND = 0x01;
	private static final int STATIC = 0x02;
	private static final int MODULE = 0x04;

	public PsiImportStatementStubImpl(final StubElement parent, final String text, final byte flags)
	{
		super(parent, getImportType(flags));
		myText = text;
		myFlags = flags;
	}

	private static JavaImportStatementElementType getImportType(final byte flags)
	{
		if(isStatic(flags))
		{
			return JavaStubElementTypes.IMPORT_STATIC_STATEMENT;
		}
		if(isModule(flags))
		{
			return JavaStubElementTypes.IMPORT_MODULE_STATEMENT;
		}
		return JavaStubElementTypes.IMPORT_STATEMENT;
	}

	@Override
	public boolean isStatic()
	{
		return isStatic(myFlags);
	}

	private static boolean isStatic(final byte flags)
	{
		return BitUtil.isSet(flags, STATIC);
	}

	@Override
	public boolean isModule()
	{
		return isModule(myFlags);
	}

	private static boolean isModule(final byte flags)
	{
		return BitUtil.isSet(flags, MODULE);
	}

	@Override
	public boolean isOnDemand()
	{
		return BitUtil.isSet(myFlags, ON_DEMAND);
	}

	public byte getFlags()
	{
		return myFlags;
	}

	@Override
	public String getImportReferenceText()
	{
		return myText;
	}

	@Override
	@Nullable
	public PsiJavaCodeReferenceElement getReference()
	{
		PsiJavaCodeReferenceElement ref = SoftReference.dereference(myReference);
		if(ref == null && !isModule())
		{
			ref = isStatic() ? getStaticReference() : getRegularReference();
			myReference = new SoftReference<>(ref);
		}
		return ref;
	}

	@Override
	@Nullable
	public PsiJavaModuleReferenceElement getModuleReference()
	{
		if(!isModule())
		{
			return null;
		}
		PsiJavaModuleReferenceElement ref = SoftReference.dereference(myModuleReference);
		if(ref == null)
		{
			ref = createModuleReference();
			myModuleReference = new SoftReference<>(ref);
		}
		return ref;
	}

	public static byte packFlags(boolean isOnDemand, boolean isStatic, boolean isModule)
	{
		byte flags = 0;
		if(isOnDemand)
		{
			flags |= ON_DEMAND;
		}
		if(isStatic)
		{
			flags |= STATIC;
		}
		if(isModule)
		{
			flags |= MODULE;
		}
		return flags;
	}

	@Nullable
	private PsiJavaModuleReferenceElement createModuleReference()
	{
		final String refText = getImportReferenceText();
		if(refText == null)
		{
			return null;
		}
		try
		{
			final PsiJavaParserFacade parserFacade = JavaPsiFacade.getInstance(getProject()).getParserFacade();
			return parserFacade.createModuleReferenceFromText(refText, getPsi());
		}
		catch(IncorrectOperationException ignore)
		{
			return null;
		}
	}

	@Nullable
	private PsiJavaCodeReferenceElement getStaticReference()
	{
		final PsiJavaCodeReferenceElement refElement = createReference();
		if(refElement == null)
		{
			return null;
		}
		if(isOnDemand() && refElement instanceof PsiJavaCodeReferenceElementImpl)
		{
			((PsiJavaCodeReferenceElementImpl) refElement).setKindWhenDummy(PsiJavaCodeReferenceElementImpl.Kind.CLASS_FQ_NAME_KIND);
		}
		return refElement;
	}

	@Nullable
	private PsiJavaCodeReferenceElement getRegularReference()
	{
		final PsiJavaCodeReferenceElement refElement = createReference();
		if(refElement == null)
		{
			return null;
		}
		((PsiJavaCodeReferenceElementImpl) refElement).setKindWhenDummy(
				isOnDemand() ? PsiJavaCodeReferenceElementImpl.Kind.CLASS_FQ_OR_PACKAGE_NAME_KIND
						: PsiJavaCodeReferenceElementImpl.Kind.CLASS_FQ_NAME_KIND);
		return refElement;
	}

	@Nullable
	private PsiJavaCodeReferenceElement createReference()
	{
		final String refText = getImportReferenceText();
		if(refText == null)
		{
			return null;
		}

		final PsiJavaParserFacade parserFacade = JavaPsiFacade.getInstance(getProject()).getParserFacade();
		try
		{
			return parserFacade.createReferenceFromText(refText, getPsi());
		}
		catch(IncorrectOperationException e)
		{
			return null;
		}
	}

	@Override
	@SuppressWarnings({"HardCodedStringLiteral"})
	public String toString()
	{
		StringBuilder builder = new StringBuilder();
		builder.append("PsiImportStatementStub[");

		if(isStatic())
		{
			builder.append("static ");
		}
		if(isModule())
		{
			builder.append("module ");
		}

		builder.append(getImportReferenceText());

		if(isOnDemand() && !isModule())
		{
			builder.append(".*");
		}

		builder.append("]");
		return builder.toString();
	}
}
