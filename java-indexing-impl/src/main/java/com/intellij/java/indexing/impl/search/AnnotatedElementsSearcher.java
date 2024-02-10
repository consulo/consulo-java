/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.java.indexing.impl.search;

import com.intellij.java.indexing.impl.stubs.index.JavaAnnotationIndex;
import com.intellij.java.indexing.search.searches.AnnotatedElementsSearch;
import com.intellij.java.indexing.search.searches.AnnotatedElementsSearchExecutor;
import com.intellij.java.language.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.ApplicationManager;
import consulo.application.util.function.Computable;
import consulo.application.util.function.Processor;
import consulo.content.scope.SearchScope;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.collection.ContainerUtil;

import jakarta.annotation.Nonnull;
import java.util.Collection;
import java.util.List;

/**
 * @author max
 */
@ExtensionImpl
public class AnnotatedElementsSearcher implements AnnotatedElementsSearchExecutor {
  @Override
  public boolean execute(@Nonnull final AnnotatedElementsSearch.Parameters p, @Nonnull final Processor<? super PsiModifierListOwner> consumer) {
    final PsiClass annClass = p.getAnnotationClass();
    assert annClass.isAnnotationType() : "Annotation type should be passed to annotated members search";

    final String annotationFQN = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Override
      public String compute() {
        return annClass.getQualifiedName();
      }
    });
    assert annotationFQN != null;

    final PsiManager psiManager = ApplicationManager.getApplication().runReadAction(new Computable<PsiManager>() {
      @Override
      public PsiManager compute() {
        return annClass.getManager();
      }
    });

    final SearchScope useScope = p.getScope();
    final Class<? extends PsiModifierListOwner>[] types = p.getTypes();

    for (final PsiAnnotation ann : getAnnotationCandidates(annClass, useScope)) {
      final PsiModifierListOwner candidate = ApplicationManager.getApplication().runReadAction(new Computable<PsiModifierListOwner>() {
        @Override
        public PsiModifierListOwner compute() {
          PsiElement parent = ann.getParent();
          if (!(parent instanceof PsiModifierList)) {
            return null; // Can be a PsiNameValuePair, if annotation is used to annotate annotation parameters
          }

          final PsiElement owner = parent.getParent();
          if (!isInstanceof(owner, types)) {
            return null;
          }

          final PsiJavaCodeReferenceElement ref = ann.getNameReferenceElement();
          if (ref == null || !psiManager.areElementsEquivalent(ref.resolve(), annClass)) {
            return null;
          }

          return (PsiModifierListOwner) owner;
        }
      });

      if (candidate != null && !consumer.process(candidate)) {
        return false;
      }
    }

    return true;
  }

  private static Collection<PsiAnnotation> getAnnotationCandidates(final PsiClass annClass, final SearchScope useScope) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Collection<PsiAnnotation>>() {
      @Override
      public Collection<PsiAnnotation> compute() {
        if (useScope instanceof GlobalSearchScope) {
          return JavaAnnotationIndex.getInstance().get(annClass.getName(), annClass.getProject(), (GlobalSearchScope) useScope);
        }

        final List<PsiAnnotation> result = ContainerUtil.newArrayList();
        for (PsiElement element : ((LocalSearchScope) useScope).getScope()) {
          result.addAll(PsiTreeUtil.findChildrenOfType(element, PsiAnnotation.class));
        }
        return result;
      }
    });
  }

  public static boolean isInstanceof(PsiElement owner, Class<? extends PsiModifierListOwner>[] types) {
    for (Class<? extends PsiModifierListOwner> type : types) {
      if (type.isInstance(owner)) {
        return true;
      }
    }
    return false;
  }

}
