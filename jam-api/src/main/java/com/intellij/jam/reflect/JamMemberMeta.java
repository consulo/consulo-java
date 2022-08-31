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

import com.intellij.jam.JamClassGenerator;
import com.intellij.jam.JamElement;
import com.intellij.jam.JamService;
import com.intellij.patterns.ElementPattern;
import com.intellij.pom.PomTarget;
import com.intellij.psi.PsiElementRef;
import com.intellij.java.language.psi.PsiModifierListOwner;
import com.intellij.semantic.SemElement;
import com.intellij.semantic.SemKey;
import com.intellij.semantic.SemRegistrar;
import com.intellij.semantic.SemService;
import com.intellij.util.*;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author peter
 */
public class JamMemberMeta<Psi extends PsiModifierListOwner, Jam extends JamElement> extends JamMemberArchetype<Psi, Jam> implements SemElement {
  private final SemKey<Jam> myJamKey;
  private final SemKey<JamMemberMeta> myMetaKey;
  private final NotNullFunction<PsiElementRef,? extends Jam> myCreator;

  private final List<JamAnnotationMeta> myRootAnnos = new ArrayList<JamAnnotationMeta>(1);

  protected JamMemberMeta(Class<? extends Jam> jamClass) {
    this(null, jamClass);
  }

  public JamMemberMeta(@Nullable JamMemberArchetype<? super Psi, ? super Jam> parent, Class<? extends Jam> jamClass) {
    this(parent, jamClass, JamService.JAM_ELEMENT_KEY.<Jam>subKey(jamClass.getSimpleName()));
  }

  public JamMemberMeta(@Nullable JamMemberArchetype<? super Psi, ? super Jam> parent, Class<? extends Jam> jamClass, final SemKey<Jam> jamKey) {
    super(parent);
    myJamKey = jamKey;
    myCreator = JamClassGenerator.getInstance().generateJamElementFactory(jamClass);
    myMetaKey = JamService.MEMBER_META_KEY.subKey(myJamKey + "Meta");
  }

  public SemKey<Jam> getJamKey() {
    return myJamKey;
  }

  public SemKey<JamMemberMeta> getMetaKey() {
    return myMetaKey;
  }

  public List<JamAnnotationMeta> getRootAnnotations() {
    return myRootAnnos;
  }

  public void register(SemRegistrar registrar, ElementPattern<? extends Psi> pattern) {
    registrar.registerSemElementProvider(myMetaKey, pattern, new ConstantFunction<Psi, JamMemberMeta>(this));
    registrar.registerSemElementProvider(myJamKey, pattern, new NullableFunction<Psi, Jam>() {
      public Jam fun(Psi psi) {
        return createJamElement(PsiElementRef.real(psi));
      }
    });
    registerChildren(registrar, pattern);
    for (JamMemberArchetype<?, ?> cur = this; cur != null; cur = cur.getParent()) {
      for (final JamAnnotationMeta anno : cur.getAnnotations()) {
        anno.registerTopLevelSem(registrar, pattern, this);
      }
    }
  }

  @Override
  public JamMemberMeta<Psi, Jam> addPomTargetProducer(@Nonnull PairConsumer<Jam, Consumer<PomTarget>> producer) {
    super.addPomTargetProducer(producer);
    return this;
  }

  @Nullable
  public final Jam getJamElement(@Nonnull Psi member) {
    return SemService.getSemService(member.getProject()).getSemElement(myJamKey, member);
  }

  @Nullable
  public Jam createJamElement(PsiElementRef<Psi> ref) {
    return myCreator.fun(ref);
  }

  public JamMemberMeta<Psi, Jam> addAnnotation(JamAnnotationMeta meta) {
    super.addAnnotation(meta);
    return this;
  }

  public JamMemberMeta<Psi, Jam> addRootAnnotation(JamAnnotationMeta meta) {
    super.addAnnotation(meta);
    myRootAnnos.add(meta);
    return this;
  }

}
