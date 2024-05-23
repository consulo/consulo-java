/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package consulo.java.analysis.codeInspection;

import com.intellij.java.language.psi.PsiVariable;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.Contract;

@ExtensionAPI(ComponentScope.APPLICATION)
public interface ImplicitResourceCloser {
  /**
   * Method used to understand if {@link AutoCloseable} variable closed properly.
   * This extension point may be useful for framework, that provides additional ways to close AutoCloseables, like Lombok.
   *
   * @param variable {@link AutoCloseable} variable to check
   * @return true if variable closed properly
   */
  @Contract(pure = true)
  boolean isSafelyClosed(@Nonnull PsiVariable variable);
}
