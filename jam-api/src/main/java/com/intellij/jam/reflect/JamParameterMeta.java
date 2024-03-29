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
package com.intellij.jam.reflect;

import jakarta.annotation.Nullable;

import com.intellij.jam.JamElement;
import com.intellij.java.language.psi.PsiParameter;

/**
 * @author peter
 */
public class JamParameterMeta<Jam extends JamElement> extends JamMemberMeta<PsiParameter, Jam>{

  public JamParameterMeta(@Nullable JamMemberArchetype<? super PsiParameter, ? super Jam> parent, Class<Jam> jamClass) {
    super(parent, jamClass);
  }

  public JamParameterMeta(Class<Jam> jamClass) {
    super(jamClass);
  }

}
