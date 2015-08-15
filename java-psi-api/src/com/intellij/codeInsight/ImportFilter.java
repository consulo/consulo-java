package com.intellij.codeInsight;

import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class ImportFilter
{
	public static final ExtensionPointName<ImportFilter> EP_NAME = new ExtensionPointName<ImportFilter>("org.consulo" +
			".java.importFilter");

	public abstract boolean shouldUseFullyQualifiedName(@NotNull PsiFile targetFile,
			@NotNull String classQualifiedName);

	public static boolean shouldImport(@NotNull PsiFile targetFile, @NotNull String classQualifiedName)
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
