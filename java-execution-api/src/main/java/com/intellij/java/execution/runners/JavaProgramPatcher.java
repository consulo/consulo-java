package com.intellij.java.execution.runners;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.openapi.extensions.ExtensionPointName;
import consulo.java.execution.configurations.OwnJavaParameters;

/**
 * Patch Java command line before running/debugging
 *
 * @author peter
 */
public abstract class JavaProgramPatcher
{
	public static final ExtensionPointName<JavaProgramPatcher> EP_NAME = ExtensionPointName.create("consulo.java.programPatcher");

	public abstract void patchJavaParameters(Executor executor, RunProfile configuration, OwnJavaParameters javaParameters);
}
