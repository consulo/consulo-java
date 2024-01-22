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
package com.intellij.java.language.psi.search.searches;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.java.language.psi.util.MethodSignatureUtil;
import consulo.application.util.query.ExtensibleQueryFactory;
import consulo.application.util.query.Query;
import jakarta.annotation.Nullable;

/**
 * @author max
 */
public class SuperMethodsSearch extends ExtensibleQueryFactory<MethodSignatureBackedByPsiMethod, SuperMethodsSearch.SearchParameters> {
  private static final SuperMethodsSearch INSTANCE = new SuperMethodsSearch();

  public static class SearchParameters {
    private final PsiMethod myMethod;
    //null means any class would be matched
    @jakarta.annotation.Nullable
    private final PsiClass myClass;
    private final boolean myCheckBases;
    private final boolean myAllowStaticMethod;

    public SearchParameters(final PsiMethod method,
                            @jakarta.annotation.Nullable final PsiClass aClass,
                            final boolean checkBases,
                            final boolean allowStaticMethod) {
      myCheckBases = checkBases;
      myClass = aClass;
      myMethod = method;
      myAllowStaticMethod = allowStaticMethod;
    }

    public final boolean isCheckBases() {
      return myCheckBases;
    }

    public final PsiMethod getMethod() {
      return myMethod;
    }

    @jakarta.annotation.Nullable
    public final PsiClass getPsiClass() {
      return myClass;
    }

    public final boolean isAllowStaticMethod() {
      return myAllowStaticMethod;
    }
  }

  private SuperMethodsSearch() {
    super(SuperMethodsSearchExecutor.class);
  }

  public static Query<MethodSignatureBackedByPsiMethod> search(final PsiMethod derivedMethod, @Nullable final PsiClass psiClass, boolean checkBases, boolean allowStaticMethod) {
    final SearchParameters parameters = new SearchParameters(derivedMethod, psiClass, checkBases, allowStaticMethod);
    return INSTANCE.createUniqueResultsQuery(parameters, MethodSignatureUtil.METHOD_BASED_HASHING_STRATEGY);
  }

}
