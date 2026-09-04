// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.psi.util;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.module.Module;
import org.jspecify.annotations.Nullable;

/**
 * An extension point that allows getting the mapping from an additional multi-release module to a main multi-release module.
 * Such a mapping is extra-linguistic (not mandated by Java specification) and could be mandated by the build system used.
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface JavaMultiReleaseModuleSupport {
  /**
   * @param additionalModule additional module (where release-specific code resides)
   * @return main module (where common code for different releases resides); null if the supplied module is not recognized as
   * an additional module, or the module uses a different build system.
   */
  @Nullable Module getMainMultiReleaseModule(Module additionalModule);
}
