/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.java.impl.util.xml;

import com.intellij.java.analysis.impl.codeInspection.deprecation.DeprecationInspection;
import com.intellij.java.language.psi.PsiDocCommentOwner;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiReference;
import consulo.language.psi.ResolvingHint;
import consulo.util.collection.ArrayUtil;
import consulo.xml.codeInspection.XmlSuppressableInspectionTool;
import consulo.xml.lang.xml.XMLLanguage;
import consulo.xml.psi.XmlElementVisitor;
import consulo.xml.psi.xml.XmlAttribute;
import consulo.xml.psi.xml.XmlAttributeValue;
import consulo.xml.psi.xml.XmlTag;
import org.jetbrains.annotations.Nls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Dmitry Avdeev
 */
@ExtensionImpl
public class DeprecatedClassUsageInspection extends XmlSuppressableInspectionTool
{

	@Nullable
	@Override
	public Language getLanguage()
	{
		return XMLLanguage.INSTANCE;
	}

	@jakarta.annotation.Nonnull
	@Override
	public HighlightDisplayLevel getDefaultLevel()
	{
		return HighlightDisplayLevel.WARNING;
	}

	@Nonnull
	@Override
	public PsiElementVisitor buildVisitor(@Nonnull final ProblemsHolder holder,
										  boolean isOnTheFly,
										  @Nonnull LocalInspectionToolSession session,
										  Object state)
	{
		return new XmlElementVisitor()
		{
			@Override
			public void visitXmlTag(XmlTag tag)
			{
				if(tag.getValue().getTextElements().length > 0)
				{
					checkReferences(tag, holder);
				}
			}

			@Override
			public void visitXmlAttribute(XmlAttribute attribute)
			{
				checkReferences(attribute, holder);
			}

			@Override
			public void visitXmlAttributeValue(XmlAttributeValue value)
			{
				checkReferences(value, holder);
			}
		};
	}

	private static void checkReferences(PsiElement psiElement, ProblemsHolder holder)
	{
		PsiReference[] references = psiElement.getReferences();
		PsiReference last = ArrayUtil.getLastElement(references);
		if(last != null && (!(last instanceof ResolvingHint) || ((ResolvingHint) last).canResolveTo(PsiDocCommentOwner.class)))
		{
			PsiElement resolve = last.resolve();
			DeprecationInspection.checkDeprecated(resolve, psiElement, last.getRangeInElement(), holder);
		}
	}

	@Override
	public boolean isEnabledByDefault()
	{
		return true;
	}

	@Nls
	@Nonnull
	@Override
	public String getGroupDisplayName()
	{
		return "Java";
	}

	@Nls
	@Nonnull
	@Override
	public String getDisplayName()
	{
		return "Deprecated API usage in XML";
	}

	@Nonnull
	@Override
	public String getShortName()
	{
		return "DeprecatedClassUsageInspection";
	}
}
