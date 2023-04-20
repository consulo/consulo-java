package com.intellij.java.impl.psi.impl.source.resolve.reference.impl.providers;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.psi.PsiReferenceContributor;
import consulo.language.psi.PsiReferenceRegistrar;
import consulo.xml.lang.xml.XMLLanguage;
import consulo.xml.patterns.XmlPatterns;

import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 20/04/2023
 */
@ExtensionImpl
public class XmlJavaReferenceContributor extends PsiReferenceContributor
{
	@Override
	public void registerReferenceProviders(@Nonnull PsiReferenceRegistrar registrar)
	{
		final JavaClassListReferenceProvider classListProvider = new JavaClassListReferenceProvider();
		registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue(), classListProvider, PsiReferenceRegistrar.LOWER_PRIORITY);
		registrar.registerReferenceProvider(XmlPatterns.xmlTag(), classListProvider, PsiReferenceRegistrar.LOWER_PRIORITY);
	}

	@Nonnull
	@Override
	public Language getLanguage()
	{
		return XMLLanguage.INSTANCE;
	}
}
