/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.encapsulateFields;

import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiMethod;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Max Medvedev
 */
public class FieldDescriptorImpl implements FieldDescriptor {
  private PsiField myField;
  private final String myGetterName;
  private final String mySetterName;
  private final PsiMethod myGetterPrototype;
  private final PsiMethod mySetterPrototype;

  public FieldDescriptorImpl(@Nonnull PsiField field,
                             @Nonnull String getterName,
                             @Nonnull String setterName,
                             @Nullable PsiMethod getterPrototype,
                             @Nullable PsiMethod setterPrototype) {
    myField = field;
    myGetterName = getterName;
    mySetterName = setterName;
    myGetterPrototype = getterPrototype;
    mySetterPrototype = setterPrototype;
  }

  @Nonnull
  @Override
  public PsiField getField() {
    return myField;
  }

  @Nonnull
  @Override
  public String getGetterName() {
    return myGetterName;
  }

  @Nonnull
  @Override
  public String getSetterName() {
    return mySetterName;
  }

  @Nullable
  @Override
  public PsiMethod getGetterPrototype() {
    return myGetterPrototype;
  }

  @Nullable
  @Override
  public PsiMethod getSetterPrototype() {
    return mySetterPrototype;
  }

  @Override
  public void refreshField(@Nonnull PsiField newField) {
    myField = newField;
  }
}
