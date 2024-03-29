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
package com.intellij.java.impl.refactoring.introduceparameterobject;

import com.intellij.java.language.psi.PsiParameter;
import com.intellij.java.language.psi.PsiType;

class ParameterSpec {
  private final PsiParameter myParameter;
  private final boolean setterRequired;
    private final String name;
    private final PsiType type;

  ParameterSpec(final PsiParameter parameter, final String name, final PsiType type, final boolean setterRequired) {
    myParameter = parameter;
    this.setterRequired = setterRequired;
    this.name = name;
    this.type = type;
  }

  public String getName() {
    return name;
  }

  public PsiType getType() {
    return type;
  }

  public PsiParameter getParameter() {
    return myParameter;
  }

  public boolean isSetterRequired() {
        return setterRequired;
    }
}
