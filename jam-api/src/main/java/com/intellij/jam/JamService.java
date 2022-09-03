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
package com.intellij.jam;

import com.intellij.jam.reflect.JamAnnotationMeta;
import com.intellij.jam.reflect.JamMemberMeta;
import com.intellij.java.language.psi.*;
import consulo.application.util.function.Processor;
import consulo.ide.ServiceManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiInvalidElementAccessException;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.sem.SemKey;
import consulo.language.sem.SemService;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;

import static com.intellij.jam.model.util.JamCommonUtil.findAnnotatedElements;
import static com.intellij.jam.model.util.JamCommonUtil.getSuperClassList;

/**
 * @author peter
 */
@Singleton
public class JamService {
  public static final SemKey<JamMemberMeta> MEMBER_META_KEY = SemKey.createKey("JamMemberMeta");
  public static final SemKey<JamAnnotationMeta> ANNO_META_KEY = SemKey.createKey("JamAnnotationMeta");
  public static final SemKey<JamElement> JAM_ELEMENT_KEY = SemKey.createKey("JamElement");

  private final PsiManager myPsiManager;
  private final SemService mySemService;

  @Inject
  protected JamService(PsiManager psiManager, SemService semService) {
    myPsiManager = psiManager;
    mySemService = semService;
  }

  public static JamService getJamService(Project p) {
    return ServiceManager.getService(p, JamService.class);
  }

  public static void processMembers(PsiClass psiClass,
                                                                 boolean checkClass,
                                                                 boolean checkMethods,
                                                                 boolean checkFields,
                                                                 boolean checkDeep,
                                                                 Processor<PsiMember> processor) {
    if (checkClass) {
      if (checkDeep) {
        for (final PsiClass curClass : getSuperClassList(psiClass)) {
          processor.process(curClass);
        }
      }
      else {
        processor.process(psiClass);
      }
    }
    if (checkMethods) {
      ContainerUtil.process(Arrays.asList(checkDeep ? psiClass.getAllMethods() : psiClass.getMethods()), processor);
    }
    if (checkFields) {
      ContainerUtil.process(Arrays.asList(checkDeep ? psiClass.getAllFields() : psiClass.getFields()), processor);
    }
  }

  @Nullable
  @Deprecated
  public <T extends JamElement> T getJamElement(Class<T> key, PsiElement psi) {
    for (JamElement element : mySemService.getSemElements(JAM_ELEMENT_KEY, psi)) {
      if (key.isInstance(element)) {
        return (T)element;
      }
    }
    return null;
  }

  @Nullable
  public <T extends JamElement> T getJamElement(@Nonnull PsiElement psi, JamMemberMeta<? extends PsiModifierListOwner, ? extends T>... metas) {
    for (JamMemberMeta<? extends PsiModifierListOwner, ? extends T> meta : metas) {
      final JamElement element = mySemService.getSemElement(meta.getJamKey(), psi);
      if (element != null) return (T)element;
    }
    return null;
  }

  @Nullable
  public <T extends JamElement> T getJamElement(SemKey<T> key, @Nonnull PsiElement psi) {
    if (!psi.isValid()) {
      throw new PsiInvalidElementAccessException(psi);
    }
    try {
      return mySemService.getSemElement(key, psi);
    }
    catch (PsiInvalidElementAccessException e) {
      throw new RuntimeException("Element invalidated: old=" + psi + "; new=" + e.getPsiElement(), e);
    }
  }

  @Nonnull
  public <T extends PsiModifierListOwner> List<JamMemberMeta> getMetas(@Nonnull T psi) {
    return mySemService.getSemElements(JamService.MEMBER_META_KEY, psi);
  }

  @Nullable
  public <T extends PsiModifierListOwner> JamMemberMeta<T,?> getMeta(@Nonnull T psi, SemKey<? extends JamMemberMeta<T,?>> key) {
    return mySemService.getSemElement(key, psi);
  }

  @Nullable
  public JamAnnotationMeta getMeta(@Nonnull PsiAnnotation anno) {
    return mySemService.getSemElement(JamService.ANNO_META_KEY, anno);

  }

  public <T extends JamElement> List<T> getJamClassElements(final JamMemberMeta<? super PsiClass, T> meta, @NonNls final String anno, final GlobalSearchScope scope) {
    return getJamClassElements(meta.getJamKey(), anno, scope);
  }

  public <T extends JamElement> List<T> getJamMethodElements(final JamMemberMeta<? super PsiMethod, T> meta, @NonNls final String anno, final GlobalSearchScope scope) {
    return getJamMethodElements(meta.getJamKey(), anno, scope);
  }

  public <T extends JamElement> List<T> getJamFieldElements(final JamMemberMeta<? super PsiField, T> meta, @NonNls final String anno, final GlobalSearchScope scope) {
    return getJamFieldElements(meta.getJamKey(), anno, scope);
  }

  public <T extends JamElement> List<T> getAnnotatedMembersList(@Nonnull final PsiClass psiClass, final boolean checkClass, final boolean checkMethods,
                                                                final boolean checkFields,
                                                                final boolean checkDeep,
                                                                final JamMemberMeta<? extends PsiMember, ? extends T>... metas) {
    final List<T> result = ContainerUtil.newArrayList();
    processMembers(psiClass, checkClass, checkMethods, checkFields, checkDeep, new Processor<PsiMember>() {
      public boolean process(PsiMember member) {
        for (JamMemberMeta<? extends PsiMember, ? extends T> meta : metas) {
          ContainerUtil.addIfNotNull(result, mySemService.getSemElement(meta.getJamKey(), member));
        }
        return true;
      }
    });
    return result;
  }

  public <T extends JamElement> List<T> getJamClassElements(final SemKey<T> c, @NonNls final String anno, final GlobalSearchScope scope) {
    final List<T> result = ContainerUtil.newArrayList();
    findAnnotatedElements(PsiClass.class, anno, myPsiManager, scope, new Processor<PsiClass>() {
      public boolean process(final PsiClass psiMember) {
        ContainerUtil.addIfNotNull(result, getJamElement(c, psiMember));
        return true;
      }
    });
    return result;
  }

  public <T extends JamElement> List<T> getJamMethodElements(final SemKey<T> c, @NonNls final String anno, final GlobalSearchScope scope) {
    final List<T> result = ContainerUtil.newArrayList();
    findAnnotatedElements(PsiMethod.class, anno, myPsiManager, scope, new Processor<PsiMethod>() {
      public boolean process(final PsiMethod psiMember) {
        ContainerUtil.addIfNotNull(result, getJamElement(c, psiMember));
        return true;
      }
    });
    return result;
  }

  public <T extends JamElement> List<T> getJamFieldElements(final SemKey<T> c, @NonNls final String anno, final GlobalSearchScope scope) {
    final List<T> result = ContainerUtil.newArrayList();
    findAnnotatedElements(PsiField.class, anno, myPsiManager, scope, new Processor<PsiField>() {
      public boolean process(final PsiField psiMember) {
        ContainerUtil.addIfNotNull(result, getJamElement(c, psiMember));
        return true;
      }
    });
    return result;
  }

  public <T extends JamElement> List<T> getJamParameterElements(final SemKey<T> c, @NonNls final String anno, final GlobalSearchScope scope) {
    final List<T> result = ContainerUtil.newArrayList();
    findAnnotatedElements(PsiParameter.class, anno, myPsiManager, scope, new Processor<PsiParameter>() {
      public boolean process(final PsiParameter psiParameter) {
        ContainerUtil.addIfNotNull(result, getJamElement(c, psiParameter));
        return true;
      }
    });
    return result;
  }

  public <T extends JamElement> List<T> getAnnotatedMembersList(@Nonnull final PsiClass psiClass,
                                                                final SemKey<T> clazz,
                                                                final boolean checkClass,
                                                                final boolean checkMethods,
                                                                final boolean checkFields,
                                                                final boolean checkDeep) {
    final List<T> result = ContainerUtil.newArrayList();
    processMembers(psiClass, checkClass, checkMethods, checkFields, checkDeep, new Processor<PsiMember>() {
      public boolean process(PsiMember member) {
        ContainerUtil.addIfNotNull(result, getJamElement(clazz, member));
        return true;
      }
    });
    return result;
  }

}
