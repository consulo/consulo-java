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
package com.intellij.java.indexing.search.searches;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ExtensibleQueryFactory;
import com.intellij.util.Function;
import com.intellij.util.Query;
import consulo.util.collection.HashingStrategy;

import javax.annotation.Nonnull;

/**
 * @author max
 */
public class ClassInheritorsSearch extends ExtensibleQueryFactory<PsiClass, ClassInheritorsSearch.SearchParameters> {
  public static final ClassInheritorsSearch INSTANCE = new ClassInheritorsSearch();

  public static class SearchParameters {
    private final PsiClass myClass;
    private final SearchScope myScope;
    private final boolean myCheckDeep;
    private final boolean myCheckInheritance;
    private final boolean myIncludeAnonymous;
    private final Condition<String> myNameCondition;

    public SearchParameters(@Nonnull final PsiClass aClass, @Nonnull SearchScope scope, final boolean checkDeep, final boolean checkInheritance, boolean includeAnonymous) {
      this(aClass, scope, checkDeep, checkInheritance, includeAnonymous, Conditions.<String>alwaysTrue());
    }

    public SearchParameters(@Nonnull final PsiClass aClass,
                            @Nonnull SearchScope scope,
                            final boolean checkDeep,
                            final boolean checkInheritance,
                            boolean includeAnonymous,
                            @Nonnull final Condition<String> nameCondition) {
      myClass = aClass;
      myScope = scope;
      myCheckDeep = checkDeep;
      myCheckInheritance = checkInheritance;
      myIncludeAnonymous = includeAnonymous;
      myNameCondition = nameCondition;
    }

    @Nonnull
    public PsiClass getClassToProcess() {
      return myClass;
    }

    @Nonnull
    public Condition<String> getNameCondition() {
      return myNameCondition;
    }

    public boolean isCheckDeep() {
      return myCheckDeep;
    }

    public SearchScope getScope() {
      return myScope;
    }

    public boolean isCheckInheritance() {
      return myCheckInheritance;
    }

    public boolean isIncludeAnonymous() {
      return myIncludeAnonymous;
    }
  }

  private ClassInheritorsSearch() {
    super("consulo.java");
  }

  public static Query<PsiClass> search(@Nonnull final PsiClass aClass, @Nonnull SearchScope scope, final boolean checkDeep, final boolean checkInheritance, boolean includeAnonymous) {
    return search(new SearchParameters(aClass, scope, checkDeep, checkInheritance, includeAnonymous));
  }

  public static Query<PsiClass> search(@Nonnull SearchParameters parameters) {
    return INSTANCE.createUniqueResultsQuery(parameters, HashingStrategy.canonical(), new Function<PsiClass, SmartPsiElementPointer<PsiClass>>() {
      @Override
      public SmartPsiElementPointer<PsiClass> fun(final PsiClass psiClass) {
        return ApplicationManager.getApplication().runReadAction((Computable<SmartPsiElementPointer<PsiClass>>) () -> SmartPointerManager.getInstance(psiClass.getProject()).createSmartPsiElementPointer(psiClass));
      }
    });
  }

  public static Query<PsiClass> search(@Nonnull final PsiClass aClass, @Nonnull SearchScope scope, final boolean checkDeep, final boolean checkInheritance) {
    return search(aClass, scope, checkDeep, checkInheritance, true);
  }

  public static Query<PsiClass> search(@Nonnull final PsiClass aClass, @Nonnull SearchScope scope, final boolean checkDeep) {
    return search(aClass, scope, checkDeep, true);
  }

  public static Query<PsiClass> search(@Nonnull final PsiClass aClass, final boolean checkDeep) {
    return search(aClass, ApplicationManager.getApplication().runReadAction(new Computable<SearchScope>() {
      @Override
      public SearchScope compute() {
        if (!aClass.isValid()) {
          throw new ProcessCanceledException();
        }
        return aClass.getUseScope();
      }
    }), checkDeep);
  }

  public static Query<PsiClass> search(@Nonnull PsiClass aClass) {
    return search(aClass, true);
  }

}
