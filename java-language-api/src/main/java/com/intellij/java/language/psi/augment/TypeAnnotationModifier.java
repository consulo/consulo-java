/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.java.language.psi.augment;

import com.intellij.java.language.psi.PsiClassType;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.TypeAnnotationProvider;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class TypeAnnotationModifier {
  public static final ExtensionPointName<TypeAnnotationModifier> EP_NAME = ExtensionPointName.create(TypeAnnotationModifier.class);

  /**
   * Type annotations are ignored during inference process. When they are present on types which are bounds of the inference variables,
   * then the corresponding instantiations of inference variables would contain that type annotations.
   * If different bounds contain contradicting type annotations or type annotations on types repeat target type annotations,
   * it could be useful to ignore such annotations in the resulted instantiation.
   *
   * @param inferenceVariableType target type
   * @param boundType             bound which annotations should be changed according to present annotations
   *                              and annotations on target type
   * @return provider based on modified annotations or null if no applicable annotations found
   */
  @Nullable
  public abstract TypeAnnotationProvider modifyAnnotations(@Nonnull PsiType inferenceVariableType, @Nonnull PsiClassType boundType);

}
