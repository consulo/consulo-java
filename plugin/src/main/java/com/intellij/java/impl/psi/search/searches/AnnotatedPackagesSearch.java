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
package com.intellij.java.impl.psi.search.searches;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaPackage;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.content.scope.SearchScope;
import consulo.application.util.query.ExtensibleQueryFactory;
import consulo.application.util.query.Query;

import javax.annotation.Nonnull;

/**
 * @author max
 */
public class AnnotatedPackagesSearch extends ExtensibleQueryFactory<PsiJavaPackage, AnnotatedPackagesSearch.Parameters> {
  public static final AnnotatedPackagesSearch INSTANCE = new AnnotatedPackagesSearch();

  public static class Parameters {
    private final PsiClass myAnnotationClass;
    private final SearchScope myScope;

    public Parameters(final PsiClass annotationClass, final SearchScope scope) {
      myAnnotationClass = annotationClass;
      myScope = scope;
    }

    public PsiClass getAnnotationClass() {
      return myAnnotationClass;
    }

    public SearchScope getScope() {
      return myScope;
    }
  }

  private AnnotatedPackagesSearch() {
    super(AnnotatedPackagesSearchExecutor.class);
  }

  public static Query<PsiJavaPackage> search(@Nonnull PsiClass annotationClass, @Nonnull SearchScope scope) {
    return INSTANCE.createQuery(new Parameters(annotationClass, scope));
  }

  public static Query<PsiJavaPackage> search(@Nonnull PsiClass annotationClass) {
    return search(annotationClass, GlobalSearchScope.allScope(annotationClass.getProject()));
  }
}