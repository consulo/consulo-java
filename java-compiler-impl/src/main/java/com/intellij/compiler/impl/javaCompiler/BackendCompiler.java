/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.compiler.impl.javaCompiler;

import java.io.IOException;

import javax.annotation.Nonnull;

import com.intellij.compiler.OutputParser;
import com.intellij.compiler.impl.ModuleChunk;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.extensions.ExtensionPointName;
import consulo.java.compiler.impl.javaCompiler.BackendCompilerEP;

public interface BackendCompiler
{
	ExtensionPointName<BackendCompilerEP> EP_NAME = ExtensionPointName.create("consulo.java.backendCompiler");

	@Nonnull
	String getPresentableName();

	@javax.annotation.Nullable
	OutputParser createErrorParser(@Nonnull String outputDir, Process process);

	@javax.annotation.Nullable
	OutputParser createOutputParser(@Nonnull String outputDir);

	boolean checkCompiler(final CompileScope scope);

	@Nonnull
	Process launchProcess( @Nonnull ModuleChunk chunk, @Nonnull String outputDir, @Nonnull CompileContext compileContext) throws IOException;

	void compileFinished();
}
