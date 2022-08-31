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
package com.intellij.jam.reflect;

import javax.annotation.Nonnull;

import com.intellij.jam.JamElement;
import com.intellij.pom.PomTarget;
import com.intellij.java.language.psi.PsiJavaPackage;
import com.intellij.semantic.SemKey;
import com.intellij.util.Consumer;
import com.intellij.util.PairConsumer;

import javax.annotation.Nullable;

/**
 * @author Gregory.Shrago
 */
public class JamPackageMeta<Jam extends JamElement> extends JamMemberMeta<PsiJavaPackage, Jam> {
  public JamPackageMeta(@Nullable JamMemberArchetype<? super PsiJavaPackage, ? super Jam> parent, Class<Jam> jamClass) {
    super(parent, jamClass);
  }

  public JamPackageMeta(Class<Jam> jamClass) {
    super(jamClass);
  }

  public JamPackageMeta(@Nullable JamMemberArchetype<? super PsiJavaPackage, ? super Jam> parent, Class<? extends Jam> jamClass, SemKey<Jam> jamKey) {
    super(parent, jamClass, jamKey);
  }

  @Override
  public JamPackageMeta<Jam> addChildrenQuery(JamChildrenQuery<?> childrenQuery) {
    super.addChildrenQuery(childrenQuery);
    return this;
  }

  @Override
  public JamPackageMeta<Jam> addPomTargetProducer(@Nonnull PairConsumer<Jam, Consumer<PomTarget>> producer) {
    super.addPomTargetProducer(producer);
    return this;
  }

  public <T extends JamElement> JamChildrenQuery<T> addAnnotatedMethodsQuery(JamAnnotationMeta anno, Class<T> jamClass) {
    return addAnnotatedMethodsQuery(anno, new JamMethodMeta<T>(jamClass));
  }

  public <T extends JamElement> JamChildrenQuery<T> addAnnotatedMethodsQuery(JamAnnotationMeta anno, JamMethodMeta<T> methodMeta) {
    final JamChildrenQuery<T> query = JamChildrenQuery.annotatedMethods(anno, methodMeta.addAnnotation(anno));
    addChildrenQuery(query);
    return query;
  }

  public <T extends JamElement> JamChildrenQuery<T> addAnnotatedFieldsQuery(JamAnnotationMeta anno, Class<T> jamClass) {
    return addAnnotatedFieldsQuery(anno, new JamFieldMeta<T>(jamClass));
  }

  public <T extends JamElement> JamChildrenQuery<T> addAnnotatedFieldsQuery(JamAnnotationMeta anno, final JamFieldMeta<T> fieldMeta) {
    final JamChildrenQuery<T> query = JamChildrenQuery.annotatedFields(anno, fieldMeta.addAnnotation(anno));
    addChildrenQuery(query);
    return query;
  }

  @Override
  public JamPackageMeta<Jam> addAnnotation(JamAnnotationMeta meta) {
    super.addAnnotation(meta);
    return this;
  }

  @Override
  public JamPackageMeta<Jam> addRootAnnotation(JamAnnotationMeta meta) {
    super.addRootAnnotation(meta);
    return this;
  }

}
