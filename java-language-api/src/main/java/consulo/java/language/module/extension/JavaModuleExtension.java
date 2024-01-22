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
package consulo.java.language.module.extension;

import com.intellij.java.language.LanguageLevel;
import consulo.compiler.CompileContext;
import consulo.compiler.ModuleChunk;
import consulo.content.bundle.Sdk;
import consulo.module.extension.ModuleExtensionWithSdk;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Set;

/**
 * @author VISTALL
 * @since 1:24/21.10.13
 */
public interface JavaModuleExtension<T extends JavaModuleExtension<T>> extends ModuleExtensionWithSdk<T> {
  /**
   * @return user set language version or resolved language version from sdk
   */
  @Nonnull
  LanguageLevel getLanguageLevel();

  /**
   * @return user set language version. If version is not set return null
   */
  @jakarta.annotation.Nullable
  default LanguageLevel getLanguageLevelNoDefault() {
    return getLanguageLevel();
  }

  @jakarta.annotation.Nonnull
  SpecialDirLocation getSpecialDirLocation();

  @jakarta.annotation.Nullable
  Sdk getSdkForCompilation();

  @jakarta.annotation.Nonnull
  Set<VirtualFile> getCompilationClasspath(@Nonnull CompileContext compileContext, @jakarta.annotation.Nonnull ModuleChunk moduleChunk);

  @jakarta.annotation.Nonnull
  Set<VirtualFile> getCompilationBootClasspath(@jakarta.annotation.Nonnull CompileContext compileContext, @Nonnull ModuleChunk moduleChunk);

  @Nullable
  String getBytecodeVersion();

  @jakarta.annotation.Nonnull
  List<String> getCompilerArguments();
}
