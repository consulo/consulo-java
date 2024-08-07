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
package com.intellij.java.language.impl.psi.impl.light;

import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.java.language.psi.PsiTypeParameter;
import com.intellij.java.language.psi.PsiTypeParameterList;
import consulo.language.Language;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiManager;
import consulo.language.impl.psi.LightElement;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class LightTypeParameterListBuilder extends LightElement implements PsiTypeParameterList {
  private final List<PsiTypeParameter> myParameters = new ArrayList<PsiTypeParameter>();
  private PsiTypeParameter[] cached = null;

  public LightTypeParameterListBuilder(PsiManager manager, final Language language) {
    super(manager, language);
  }

  @Override
  public String toString() {
    return "Light type parameter list";
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor) visitor).visitTypeParameterList(this);
    } else {
      visitor.visitElement(this);
    }
  }

  @Override
  public PsiTypeParameter[] getTypeParameters() {
    if (cached == null) {
      if (myParameters.size() == 0) {
        cached = PsiTypeParameter.EMPTY_ARRAY;
      } else {
        cached = myParameters.toArray(new PsiTypeParameter[myParameters.size()]);
      }
    }
    return cached;
  }

  @Override
  public int getTypeParameterIndex(PsiTypeParameter typeParameter) {
    return myParameters.indexOf(typeParameter);
  }

  public void addParameter(PsiTypeParameter parameter) {
    cached = null;
    myParameters.add(parameter);
  }
}
