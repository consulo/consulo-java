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

import javax.annotation.Nonnull;

import com.intellij.jam.JamElement;
import com.intellij.psi.PsiMethod;
import com.intellij.pom.PomTarget;
import com.intellij.semantic.SemKey;
import com.intellij.util.Consumer;
import com.intellij.util.PairConsumer;
import javax.annotation.Nullable;

/**
 * @author peter
 */
public class JamMethodMeta<Jam extends JamElement> extends JamMemberMeta<PsiMethod, Jam>{
  public JamMethodMeta(@Nullable JamMemberArchetype<? super PsiMethod, ? super Jam> parent, Class<Jam> jamClass) {
    super(parent, jamClass);
  }

  public JamMethodMeta(Class<? extends Jam> jamClass) {
    super(jamClass);
  }

  public JamMethodMeta(@Nullable JamMemberArchetype<? super PsiMethod, ? super Jam> parent, Class<? extends Jam> jamClass, SemKey<Jam> jamKey) {
    super(parent, jamClass, jamKey);
  }

  @Override
  public JamMethodMeta<Jam> addChildrenQuery(JamChildrenQuery<?> childrenQuery) {
    super.addChildrenQuery(childrenQuery);
    return this;
  }

  @Override
  public JamMethodMeta<Jam> addAnnotation(JamAnnotationMeta meta) {
    super.addAnnotation(meta);
    return this;
  }

  @Override
  public JamMethodMeta<Jam> addRootAnnotation(JamAnnotationMeta meta) {
    super.addRootAnnotation(meta);
    return this;
  }

  public <T extends JamElement> JamChildrenQuery<T> addAnnotatedParametersQuery(JamAnnotationMeta anno, Class<T> jamClass) {
    final JamChildrenQuery<T> query = JamChildrenQuery.annotatedParameters(anno, jamClass);
    addChildrenQuery(query);
    return query;
  }

  @Override
  public JamMethodMeta<Jam> addPomTargetProducer(@Nonnull PairConsumer<Jam, Consumer<PomTarget>> producer) {
    super.addPomTargetProducer(producer);
    return this;
  }
}
