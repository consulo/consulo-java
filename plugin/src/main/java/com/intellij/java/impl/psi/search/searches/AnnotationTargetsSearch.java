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

import javax.annotation.Nonnull;

import com.intellij.java.indexing.search.searches.AnnotatedMembersSearch;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMember;
import com.intellij.java.language.psi.PsiModifierListOwner;
import com.intellij.java.language.psi.PsiJavaPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.MergeQuery;
import com.intellij.util.Query;

/**
 * @author max
 */
public class AnnotationTargetsSearch {
  public static AnnotationTargetsSearch INSTANCE = new AnnotationTargetsSearch();

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

  private AnnotationTargetsSearch() {}

  public static Query<PsiModifierListOwner> search(@Nonnull PsiClass annotationClass, @Nonnull SearchScope scope) {
    final Query<PsiMember> members = AnnotatedMembersSearch.search(annotationClass, scope);
    final Query<PsiJavaPackage> packages = AnnotatedPackagesSearch.search(annotationClass, scope);
    return new MergeQuery<PsiModifierListOwner>(members, packages);
  }

  public static Query<PsiModifierListOwner> search(@Nonnull PsiClass annotationClass) {
    return search(annotationClass, GlobalSearchScope.allScope(annotationClass.getProject()));
  }
}