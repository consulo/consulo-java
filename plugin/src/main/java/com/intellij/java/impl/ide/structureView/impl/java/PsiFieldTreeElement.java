// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.ide.structureView.impl.java;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.SortableTreeElement;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.java.language.psi.PsiEnumConstant;
import com.intellij.java.language.psi.PsiEnumConstantInitializer;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiSubstitutor;
import javax.annotation.Nonnull;

import java.util.Collection;
import java.util.Collections;

import static com.intellij.java.language.psi.util.PsiFormatUtil.*;

public class PsiFieldTreeElement extends JavaClassTreeElementBase<PsiField> implements SortableTreeElement
{
	public PsiFieldTreeElement(PsiField field, boolean isInherited)
	{
		super(isInherited, field);
	}

	@Override
	@Nonnull
	public Collection<StructureViewTreeElement> getChildrenBase()
	{
		PsiField field = getField();
		if(field instanceof PsiEnumConstant)
		{
			PsiEnumConstantInitializer initializingClass = ((PsiEnumConstant) field).getInitializingClass();
			if(initializingClass != null)
			{
				return JavaClassTreeElement.getClassChildren(initializingClass);
			}
		}
		return Collections.emptyList();
	}

	@Override
	public String getPresentableText()
	{
		final PsiField field = getElement();
		if(field == null)
		{
			return "";
		}

		final boolean dumb = DumbService.isDumb(field.getProject());
		return StringUtil.replace(formatVariable(
				field,
				SHOW_NAME | (dumb ? 0 : SHOW_TYPE) | TYPE_AFTER | (dumb ? 0 : SHOW_INITIALIZER),
				PsiSubstitutor.EMPTY
		), ":", ": ");
	}

	public PsiField getField()
	{
		return getElement();
	}

	@Override
	@Nonnull
	public String getAlphaSortKey()
	{
		final PsiField field = getElement();
		if(field != null)
		{
			return field.getName();
		}
		return "";
	}
}
