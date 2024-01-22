/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.java.language.psi.PsiAnnotation;
import com.intellij.java.language.psi.PsiModifierListOwner;
import consulo.language.pattern.ElementPattern;
import consulo.language.pom.PomTarget;
import consulo.language.sem.SemRegistrar;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.function.PairConsumer;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author peter
 */
public class JamMemberArchetype<Psi extends PsiModifierListOwner, Jam extends JamElement> {
  private final List<JamAnnotationMeta> myAnnos = ContainerUtil.newArrayList();
  private final List<JamChildrenQuery<?>> myChildren = ContainerUtil.newArrayList();
  @Nullable
  private final JamMemberArchetype<? super Psi, ? super Jam> myParent;
  private List<PairConsumer<Jam, Consumer<PomTarget>>> myPomTargetProducers = ContainerUtil.newArrayList();

  public JamMemberArchetype() {
    this(null);
  }

  public JamMemberArchetype(@Nullable JamMemberArchetype<? super Psi, ? super Jam> parent) {
    myParent = parent;
  }

  protected final void registerChildren(SemRegistrar registrar, ElementPattern<? extends Psi> pattern) {
    for (JamChildrenQuery<?> child : myChildren) {
      if (child instanceof JamRegisteringChildrenQuery) {
        ((JamRegisteringChildrenQuery)child).registerSem(registrar, pattern);
      }
    }
  }


  @jakarta.annotation.Nullable
  public JamAnnotationMeta findAnnotationMeta(@Nonnull PsiAnnotation annotation) {
    final String qname = annotation.getQualifiedName();
    for (final JamAnnotationMeta anno : myAnnos) {
      if (anno.getAnnoName().equals(qname)) {
        return anno;
      }
    }
    if (myParent != null) {
      return myParent.findAnnotationMeta(annotation);
    }

    return null;
  }

  public JamMemberArchetype<Psi, Jam> addAnnotation(JamAnnotationMeta meta) {
    myAnnos.add(meta);
    return this;
  }

  public List<JamAnnotationMeta> getAnnotations() {
    return myAnnos;
  }

  @Nullable
  public JamMemberArchetype<? super Psi, ? super Jam> getParent() {
    return myParent;
  }

  public JamMemberArchetype<Psi, Jam> addChildrenQuery(JamChildrenQuery<?> childrenQuery) {
    myChildren.add(childrenQuery);
    return this;
  }

  public JamMemberArchetype<Psi, Jam> addPomTargetProducer(@Nonnull PairConsumer<Jam, Consumer<PomTarget>> producer) {
    myPomTargetProducers.add(producer);
    return this;
  }

  public List<PomTarget> getAssociatedTargets(@Nonnull Jam element) {
    final ArrayList<PomTarget> list = ContainerUtil.newArrayList();
    final Consumer<PomTarget> targetConsumer = new Consumer<PomTarget>() {
      public void accept(PomTarget target) {
        list.add(target);
      }
    };
    for (final PairConsumer<Jam, Consumer<PomTarget>> function : myPomTargetProducers) {
      function.consume(element, targetConsumer);
    }
    if (myParent != null) {
      list.addAll(myParent.getAssociatedTargets(element));
    }
    return list;
  }

  @jakarta.annotation.Nullable
  public JamMemberMeta findChildMeta(@Nonnull PsiModifierListOwner member) {
    for (final JamChildrenQuery<?> child : myChildren) {
      final JamMemberMeta meta = ((JamChildrenQuery)child).getMeta(member);
      if (meta != null) {
        return meta;
      }
    }
    if (myParent != null) {
      return myParent.findChildMeta(member);
    }
    return null;
  }
}
