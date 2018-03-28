package com.intellij.coverage;

import java.io.File;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;

/**
 * User: anna
 * Date: 2/14/11
 */
public abstract class JavaCoverageEngineExtension
{
	public static final ExtensionPointName<JavaCoverageEngineExtension> EP_NAME =
			ExtensionPointName.create("consulo.java.coverageEngineExtension");

	public abstract boolean isApplicableTo(@Nullable RunConfigurationBase conf);

	public boolean suggestQualifiedName(@Nonnull PsiFile sourceFile, PsiClass[] classes, Set<String> names)
	{
		return false;
	}

	public boolean collectOutputFiles(@Nonnull final PsiFile srcFile, @javax.annotation.Nullable final VirtualFile output, @javax.annotation.Nullable final VirtualFile testoutput,
			@Nonnull final CoverageSuitesBundle suite, @Nonnull final Set<File> classFiles)
	{
		return false;
	}
}
