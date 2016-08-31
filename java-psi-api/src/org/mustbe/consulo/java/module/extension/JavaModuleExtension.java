/*
 * Copyright 2013 Consulo.org
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
package org.mustbe.consulo.java.module.extension;

import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.compiler.impl.ModuleChunk;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import consulo.module.extension.ModuleExtensionWithSdk;

/**
 * @author VISTALL
 * @since 1:24/21.10.13
 */
public interface JavaModuleExtension<T extends JavaModuleExtension<T>> extends ModuleExtensionWithSdk<T>
{
	@NotNull
	LanguageLevel getLanguageLevel();

	@NotNull
	SpecialDirLocation getSpecialDirLocation();

	@Nullable
	Sdk getSdkForCompilation();

	@NotNull
	Set<VirtualFile> getCompilationClasspath(@NotNull CompileContext compileContext, @NotNull ModuleChunk moduleChunk);

	@NotNull
	Set<VirtualFile> getCompilationBootClasspath(@NotNull CompileContext compileContext, @NotNull ModuleChunk moduleChunk);

	@Nullable
	String getBytecodeVersion();
}
