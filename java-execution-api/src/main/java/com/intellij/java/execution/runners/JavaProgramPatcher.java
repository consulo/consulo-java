package com.intellij.java.execution.runners;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.execution.configuration.RunProfile;
import consulo.execution.executor.Executor;
import consulo.java.execution.configurations.OwnJavaParameters;

/**
 * Patch Java command line before running/debugging
 *
 * @author peter
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class JavaProgramPatcher {
  public static final ExtensionPointName<JavaProgramPatcher> EP_NAME = ExtensionPointName.create(JavaProgramPatcher.class);

  public abstract void patchJavaParameters(Executor executor, RunProfile configuration, OwnJavaParameters javaParameters);
}
