package com.intellij.java.language.codeInsight;

import javax.annotation.Nonnull;

import consulo.component.extension.ExtensionPointName;
import consulo.language.psi.PsiFile;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class ImportFilter
{
	public static final ExtensionPointName<ImportFilter> EP_NAME = new ExtensionPointName<ImportFilter>("consulo.java.importFilter");

	public abstract boolean shouldUseFullyQualifiedName(@Nonnull PsiFile targetFile,
			@Nonnull String classQualifiedName);

	public static boolean shouldImport(@Nonnull PsiFile targetFile, @Nonnull String classQualifiedName)
	{
		for(ImportFilter filter : EP_NAME.getExtensions())
		{
			if(filter.shouldUseFullyQualifiedName(targetFile, classQualifiedName))
			{
				return false;
			}
		}
		return true;
	}
}
