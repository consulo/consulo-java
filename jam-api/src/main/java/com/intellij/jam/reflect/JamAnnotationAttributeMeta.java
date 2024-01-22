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
import com.intellij.jam.JamService;
import com.intellij.java.language.patterns.PsiAnnotationPattern;
import com.intellij.java.language.patterns.PsiNameValuePairPattern;
import com.intellij.java.language.psi.PsiAnnotation;
import com.intellij.java.language.psi.PsiAnnotationMemberValue;
import com.intellij.java.language.psi.PsiArrayInitializerMemberValue;
import consulo.language.pattern.ElementPattern;
import consulo.language.pom.PomTarget;
import consulo.language.psi.PsiElementRef;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.sem.SemKey;
import consulo.language.sem.SemRegistrar;
import consulo.language.sem.SemService;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.function.PairConsumer;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.intellij.java.language.patterns.PsiJavaPatterns.*;

/**
 * @author peter
 */
public abstract class JamAnnotationAttributeMeta<T extends JamElement, JamType> extends JamAttributeMeta<JamType>{
  protected final JamAnnotationMeta myAnnoMeta;
  protected final SemKey<T> myJamKey;
  protected final JamInstantiator<PsiAnnotation, T> myInstantiator;
  private final List<PairConsumer<T, Consumer<PomTarget>>> myPomTargetProducers = ContainerUtil.newArrayList();

  private JamAnnotationAttributeMeta(String attrName, JamAnnotationMeta annoMeta, JamInstantiator<PsiAnnotation, T> instantiator) {
    super(attrName);
    myAnnoMeta = annoMeta;
    myInstantiator = instantiator;
    myJamKey = JamService.JAM_ELEMENT_KEY.subKey(attrName);
  }

  public JamAnnotationAttributeMeta<T, JamType> addPomTargetProducer(@Nonnull PairConsumer<T, Consumer<PomTarget>> producer) {
    myPomTargetProducers.add(producer);
    return this;
  }

  public List<PomTarget> getAssociatedTargets(@Nonnull T element) {
    final ArrayList<PomTarget> list = ContainerUtil.newArrayList();
    final Consumer<PomTarget> targetConsumer = target -> list.add(target);
    for (final PairConsumer<T, Consumer<PomTarget>> function : myPomTargetProducers) {
      function.consume(element, targetConsumer);
    }
    return list;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    JamAnnotationAttributeMeta that = (JamAnnotationAttributeMeta)o;

    if (myAnnoMeta != null ? !myAnnoMeta.equals(that.myAnnoMeta) : that.myAnnoMeta != null) return false;
    if (myInstantiator != null ? !myInstantiator.equals(that.myInstantiator) : that.myInstantiator != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myAnnoMeta != null ? myAnnoMeta.hashCode() : 0;
    result = 31 * result + (myInstantiator != null ? myInstantiator.hashCode() : 0);
    return result;
  }

  public JamAnnotationMeta getAnnotationMeta() {
    return myAnnoMeta;
  }

  public JamInstantiator<PsiAnnotation, T> getInstantiator() {
    return myInstantiator;
  }

  public abstract void registerSem(SemRegistrar registrar, ElementPattern<PsiAnnotation> annotationPattern, JamAnnotationMeta parentMeta);

  public static final class Single<T extends JamElement> extends JamAnnotationAttributeMeta<T, T> {
    public Single(String attrName, JamAnnotationMeta annoMeta, JamInstantiator<PsiAnnotation, T> instantiator) {
      super(attrName, annoMeta, instantiator);
    }

    @jakarta.annotation.Nonnull
    public T getJam(PsiElementRef<PsiAnnotation> anno) {
      final PsiAnnotation psiElement = anno.getPsiElement();
      assert psiElement != null;
      return ObjectUtil.assertNotNull(JamService.getJamService(anno.getPsiManager().getProject()).getJamElement(myJamKey, psiElement));
    }

    @Override
    public void registerSem(SemRegistrar registrar, ElementPattern<PsiAnnotation> annotationPattern, JamAnnotationMeta parentMeta) {
      final PsiNameValuePairPattern attrPattern =
        psiNameValuePair().withName(getAttributeLink().getAttributeName()).withSuperParent(2, annotationPattern);
      final PsiAnnotationPattern annoPattern = psiAnnotation().qName(myAnnoMeta.getAnnoName()).withParent(attrPattern);
      registrar.registerSemElementProvider(myJamKey, annoPattern, new JamCreator(parentMeta));

      myAnnoMeta.registerNestedSem(registrar, annoPattern, parentMeta);
    }

  }
  public static final class Collection<T extends JamElement> extends JamAnnotationAttributeMeta<T, List<T>> {
    public Collection(String attrName, @Nonnull JamAnnotationMeta annoMeta, JamInstantiator<PsiAnnotation, T> instantiator) {
      super(attrName, annoMeta, instantiator);
    }

    @jakarta.annotation.Nonnull
    public List<T> getJam(final PsiElementRef<PsiAnnotation> anno) {
      return getCollectionJam(anno, new Function<PsiAnnotationMemberValue, T>() {
        public T apply(PsiAnnotationMemberValue psiAnnotationMemberValue) {
          return getJam(psiAnnotationMemberValue);
        }
      });
    }

    @Nullable
    private T getJam(PsiAnnotationMemberValue element) {
      if (element instanceof PsiAnnotation) {
        return JamService.getJamService(element.getProject()).getJamElement(myJamKey, element);
      }
      return null;
    }

    @Nonnull
    public T addAttribute(PsiElementRef<PsiAnnotation> annoRef) {
      return ObjectUtil.assertNotNull(getJam(addAttribute(annoRef, "@" + myAnnoMeta.getAnnoName(), getAttributeLink())));
    }

    @Override
    public JamAnnotationAttributeMeta.Collection<T> addPomTargetProducer(@Nonnull PairConsumer<T, Consumer<PomTarget>> producer) {
      super.addPomTargetProducer(producer);
      return this;
    }

    @Override
    public void registerSem(SemRegistrar registrar, ElementPattern<PsiAnnotation> annotationPattern, JamAnnotationMeta parentMeta) {
      final PsiNameValuePairPattern attrPattern =
        psiNameValuePair().withName(getAttributeLink().getAttributeName()).withSuperParent(2, annotationPattern);
      final PsiAnnotationPattern annoPattern = psiAnnotation().qName(myAnnoMeta.getAnnoName())
        .withParent(or(attrPattern, psiElement(PsiArrayInitializerMemberValue.class).withParent(attrPattern)));

      registrar.registerSemElementProvider(myJamKey, annoPattern, new JamCreator(parentMeta));
      myAnnoMeta.registerNestedSem(registrar, annoPattern, parentMeta);
    }

  }

  protected class JamCreator implements Function<PsiAnnotation, T> {
    private final JamAnnotationMeta myParentMeta;

    private JamCreator(JamAnnotationMeta parentMeta) {
      myParentMeta = parentMeta;
    }

    public T apply(PsiAnnotation annotation) {
      final PsiAnnotation parentAnno = PsiTreeUtil.getParentOfType(annotation, PsiAnnotation.class, true);
      assert parentAnno != null;
      final JamAnnotationMeta annotationMeta = SemService.getSemService(parentAnno.getProject()).getSemElement(myParentMeta.getMetaKey(), parentAnno);
      if (annotationMeta == myParentMeta) {
        return myInstantiator.instantiate(PsiElementRef.real(annotation));
      }
      return null;
    }
  }

}
