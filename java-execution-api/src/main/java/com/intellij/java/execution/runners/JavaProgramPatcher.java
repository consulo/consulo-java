package com.intellij.java.execution.runners;

import consulo.execution.configuration.RunProfile;
import consulo.execution.executor.Executor;
import consulo.component.extension.ExtensionPointName;
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
