/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.codeInsight.template.macro;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;

/**
* @author Max Medvedev
*/
public abstract class VariableTypeCalculator {
  public static final ExtensionPointName<VariableTypeCalculator> EP_NAME =
    ExtensionPointName.create("consulo.java.variableTypeCalculator");

  @Nullable
  public abstract PsiType inferVarTypeAt(@NotNull PsiVariable var, @NotNull PsiElement place);

  /**
   * @return inferred type of variable in the context of place
   */
  @NotNull
  public static PsiType getVarTypeAt(@NotNull PsiVariable var, @NotNull PsiElement place) {
    for (VariableTypeCalculator calculator : EP_NAME.getExtensions()) {
      final PsiType type = calculator.inferVarTypeAt(var, place);
      if (type != null) return type;
    }

    return var.getType();
  }
}