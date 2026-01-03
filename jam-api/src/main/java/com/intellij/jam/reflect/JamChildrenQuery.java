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
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.patterns.PsiJavaPatterns;
import com.intellij.java.language.psi.*;
import consulo.application.util.CachedValue;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.CachedValuesManager;
import consulo.language.pattern.ElementPattern;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementRef;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.sem.SemRegistrar;
import consulo.util.collection.ContainerUtil;
import consulo.util.dataholder.Key;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * @author peter
 */
public abstract class JamChildrenQuery<Jam extends JamElement> {
  private final Key<CachedValue<List<Jam>>> myCacheKey;

  protected JamChildrenQuery() {
    myCacheKey = Key.create(getClass().getName());
  }

  protected JamChildrenQuery(@NonNls String debugName) {
    myCacheKey = Key.create(debugName);
  }

  @Nullable
  public abstract JamMemberMeta<?, ? extends Jam> getMeta(@Nonnull PsiModifierListOwner member);

  protected abstract List<Jam> findChildren(@Nonnull PsiMember parent);

  

  public final List<Jam> findChildren(@Nonnull PsiElementRef<? extends PsiMember> parentRef) {
    final PsiMember parent = parentRef.getPsiElement();
    if (parent == null) {
      return Collections.emptyList();
    }

    CachedValue<List<Jam>> data = parent.getUserData(myCacheKey);
    if (data == null) {
      parent.putUserData(myCacheKey, data = CachedValuesManager.getManager(parent.getProject()).createCachedValue(new CachedValueProvider<List<Jam>>() {
        public Result<List<Jam>> compute() {
          return Result.create(findChildren(parent), PsiModificationTracker.MODIFICATION_COUNT);
        }
      }, false));
    }

    return data.getValue();
  }

  public static boolean isAnnotated(PsiModifierListOwner member, String fqn) {
    return AnnotationUtil.isAnnotated(member, fqn, false, true);
  }

  public static <Jam extends JamElement> JamChildrenQuery<Jam> composite(final JamChildrenQuery<? extends Jam>... components) {
    return new CompositeQuery<Jam>(components);
  }
  
  public static <Jam extends JamElement> JamChildrenQuery<Jam> annotatedMethods(final JamAnnotationMeta annotationMeta, final Class<Jam> jamClass) {
    return annotatedMethods(annotationMeta, new JamMethodMeta<Jam>(jamClass).addAnnotation(annotationMeta));
  }

  public static <Jam extends JamElement> JamChildrenQuery<Jam> annotatedMethods(final JamAnnotationMeta annotationMeta, final JamMemberMeta<PsiMethod, Jam> methodMeta) {
    return new AnnotatedMethodsQuery<Jam>(annotationMeta, methodMeta);
  }

  public static <Jam extends JamElement> JamChildrenQuery<Jam> annotatedFields(@NonNls final String annotation, final Class<Jam> jamClass) {
    return annotatedFields(new JamAnnotationMeta(annotation), jamClass);
  }

  public static <Jam extends JamElement> JamChildrenQuery<Jam> annotatedFields(final JamAnnotationMeta annotationMeta, final Class<Jam> jamClass) {
    return annotatedFields(annotationMeta, new JamFieldMeta<Jam>(jamClass).addAnnotation(annotationMeta));
  }

  public static <Jam extends JamElement> JamChildrenQuery<Jam> annotatedFields(final JamAnnotationMeta annotationMeta, final JamMemberMeta<PsiField, Jam> fieldMeta) {
    return new AnnotatedFieldsQuery<Jam>(annotationMeta, fieldMeta);
  }

  public static <Jam extends JamElement> JamChildrenQuery<Jam> annotatedParameters(final JamAnnotationMeta annotationMeta, final Class<Jam> jamClass) {
    return annotatedParameters(annotationMeta, new JamParameterMeta<Jam>(jamClass).addAnnotation(annotationMeta));
  }

  public static <Jam extends JamElement> JamChildrenQuery<Jam> annotatedParameters(final JamAnnotationMeta annotationMeta, final JamMemberMeta<PsiParameter, Jam> paramMeta) {
    return new AnnotatedParametersQuery<Jam>(annotationMeta, paramMeta);
  }

  private static class AnnotatedMethodsQuery<Jam extends JamElement> extends JamAnnotatedChildrenQuery<Jam> implements JamRegisteringChildrenQuery {
    private final JamMemberMeta<PsiMethod, Jam> myMethodMeta;

    public AnnotatedMethodsQuery(JamAnnotationMeta annotationMeta, JamMemberMeta<PsiMethod, Jam> methodMeta) {
      super(annotationMeta);
      myMethodMeta = methodMeta;
    }

    public JamMemberMeta<?, ? extends Jam> getMemberMeta(@Nonnull PsiModifierListOwner member) {
      return member instanceof PsiMethod ? myMethodMeta : null;
    }

    public PsiModifierListOwner[] getAllChildren(@Nonnull PsiMember parent) {
      return ((PsiClass) parent).getMethods();
    }

    public void registerSem(SemRegistrar registrar, ElementPattern<? extends PsiElement> parentPattern) {
      myMethodMeta.register(registrar, PsiJavaPatterns.psiMethod().withAnnotation(getAnnoName()).inClass(parentPattern));
    }
  }

  private static class AnnotatedFieldsQuery<Jam extends JamElement> extends JamAnnotatedChildrenQuery<Jam> implements JamRegisteringChildrenQuery{
    private final JamMemberMeta<PsiField, Jam> myFieldMeta;

    public AnnotatedFieldsQuery(JamAnnotationMeta annotationMeta, JamMemberMeta<PsiField, Jam> fieldMeta) {
      super(annotationMeta);
      myFieldMeta = fieldMeta;
    }

    public JamMemberMeta<?, ? extends Jam> getMemberMeta(@Nonnull PsiModifierListOwner member) {
      return member instanceof PsiField ? myFieldMeta : null;
    }

    public PsiModifierListOwner[] getAllChildren(@Nonnull PsiMember parent) {
      return ((PsiClass) parent).getFields();
    }

    public void registerSem(SemRegistrar registrar, ElementPattern<? extends PsiElement> parentPattern) {
      myFieldMeta.register(registrar, PsiJavaPatterns.psiField().withAnnotation(getAnnoName()).inClass(parentPattern));
    }
  }

  private static class CompositeQuery<Jam extends JamElement> extends JamChildrenQuery<Jam> implements JamRegisteringChildrenQuery {
    private final JamChildrenQuery<? extends Jam>[] myComponents;

    public CompositeQuery(JamChildrenQuery<? extends Jam>... components) {
      myComponents = components;
    }

    @Override
    public JamMemberMeta<?, ? extends Jam> getMeta(@Nonnull PsiModifierListOwner member) {
      for (final JamChildrenQuery<? extends Jam> component : myComponents) {
        final JamMemberMeta<?, ? extends Jam> meta = component.getMeta(member);
        if (meta != null) {
          return meta;
        }
      }
      return null;
    }

    public List<Jam> findChildren(@Nonnull final PsiMember parent) {
      return ContainerUtil.concat(myComponents, new Function<JamChildrenQuery<? extends Jam>, Collection<? extends Jam>>() {
        public Collection<? extends Jam> apply(JamChildrenQuery<? extends Jam> jamChildrenQuery) {
          return jamChildrenQuery.findChildren(parent);
        }
      });
    }

    public void registerSem(SemRegistrar registrar, ElementPattern<? extends PsiElement> parentPattern) {
      for (final JamChildrenQuery<? extends Jam> component : myComponents) {
        if (component instanceof JamRegisteringChildrenQuery) {
          ((JamRegisteringChildrenQuery)component).registerSem(registrar, parentPattern);
        }
      }
    }
  }

  private static class AnnotatedParametersQuery<Jam extends JamElement> extends JamAnnotatedChildrenQuery<Jam> implements JamRegisteringChildrenQuery {
    private final JamMemberMeta<PsiParameter, Jam> myParamMeta;

    public AnnotatedParametersQuery(JamAnnotationMeta annotationMeta, JamMemberMeta<PsiParameter, Jam> paramMeta) {
      super(annotationMeta);
      myParamMeta = paramMeta;
    }

    @Nullable
    public JamMemberMeta<?, ? extends Jam> getMemberMeta(@Nonnull PsiModifierListOwner member) {
      return member instanceof PsiParameter ? myParamMeta : null;
    }

    public PsiModifierListOwner[] getAllChildren(@Nonnull PsiMember parent) {
      return ((PsiMethod) parent).getParameterList().getParameters();
    }

    public void registerSem(SemRegistrar registrar, ElementPattern<? extends PsiElement> parentPattern) {
      myParamMeta.register(registrar, PsiJavaPatterns.psiParameter().withAnnotation(getAnnoName()).withSuperParent(2, parentPattern));
    }

  }
}
