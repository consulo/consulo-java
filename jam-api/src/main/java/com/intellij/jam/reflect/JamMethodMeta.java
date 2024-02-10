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
import com.intellij.java.language.psi.PsiMethod;
import consulo.language.pom.PomTarget;
import consulo.language.sem.SemKey;
import consulo.util.lang.function.PairConsumer;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.function.Consumer;

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
