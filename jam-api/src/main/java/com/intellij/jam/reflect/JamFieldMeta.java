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

import com.intellij.jam.JamElement;
import com.intellij.java.language.psi.PsiField;
import com.intellij.semantic.SemKey;
import javax.annotation.Nullable;

/**
 * @author peter
 */
public class JamFieldMeta<Jam extends JamElement> extends JamMemberMeta<PsiField, Jam>{

  public JamFieldMeta(Class<? extends Jam> jamClass) {
    super(jamClass);
  }

  public JamFieldMeta(@Nullable JamMemberArchetype<? super PsiField, ? super Jam> parent, Class<? extends Jam> jamClass, SemKey<Jam> jamKey) {
    super(parent, jamClass, jamKey);
  }

  @Override
  public JamFieldMeta<Jam> addAnnotation(JamAnnotationMeta meta) {
    super.addAnnotation(meta);
    return this;
  }

  @Override
  public JamFieldMeta<Jam> addRootAnnotation(JamAnnotationMeta meta) {
    super.addRootAnnotation(meta);
    return this;
  }

}
