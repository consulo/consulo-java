// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * Copyright 2013-2026 consulo.io
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
package com.intellij.java.language.impl.psi.util;

import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;

import org.jspecify.annotations.Nullable;

/**
 * Utility methods related to manifest declarations.
 */
public final class JavaManifestUtil {
  private JavaManifestUtil() {
  }

  /**
   * @param module    module to find manifest in
   * @param attribute attribute name from a manifest file
   * @return attribute value, null if not found
   */
  @Nullable
  public static String getManifestAttributeValue(Module module, String attribute) {
    JavaModuleExtension<?> extension = ModuleUtilCore.getExtension(module, JavaModuleExtension.class);
    if (extension == null) {
      return null;
    }
    return extension.getManifestAttributes().get(attribute);
  }
}
