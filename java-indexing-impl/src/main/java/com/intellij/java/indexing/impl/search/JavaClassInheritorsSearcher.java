/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.java.indexing.search.searches.AllClassesSearch;
import com.intellij.java.indexing.search.searches.ClassInheritorsSearch;
import com.intellij.java.indexing.search.searches.DirectClassInheritorsSearch;
import com.intellij.java.language.psi.PsiAnonymousClass;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiModifier;
import consulo.application.ApplicationManager;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressIndicatorProvider;
import consulo.application.progress.ProgressManager;
import consulo.application.util.ReadActionProcessor;
import consulo.application.util.function.Computable;
import consulo.application.util.function.Processor;
import consulo.content.scope.SearchScope;
import consulo.java.language.module.util.JavaClassNames;
import consulo.language.impl.psi.PsiAnchor;
import consulo.language.psi.PsiBundle;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.scope.PsiSearchScopeUtil;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.project.util.query.QueryExecutorBase;
import consulo.util.collection.Stack;
import consulo.util.lang.ref.Ref;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Set;

public class JavaClassInheritorsSearcher extends QueryExecutorBase<PsiClass, ClassInheritorsSearch.SearchParameters> {
  private static final Logger LOG = Logger.getInstance(JavaClassInheritorsSearcher.class);

  @Override
  public void processQuery(@Nonnull ClassInheritorsSearch.SearchParameters parameters, @Nonnull Processor<? super PsiClass> consumer) {
    final PsiClass baseClass = parameters.getClassToProcess();
    final SearchScope searchScope = parameters.getScope();

    LOG.assertTrue(searchScope != null);

    ProgressIndicator progress = ProgressIndicatorProvider.getGlobalProgressIndicator();
    if (progress != null) {
      progress.pushState();
      String className = ApplicationManager.getApplication().runReadAction((Computable<String>) () -> baseClass.getName());
      progress.setText(className != null ? PsiBundle.message("psi.search.inheritors.of.class.progress", className) : PsiBundle.message("psi.search.inheritors.progress"));
    }

    processInheritors(consumer, baseClass, searchScope, parameters);

    if (progress != null) {
      progress.popState();
    }
  }

  private static void processInheritors(@Nonnull final Processor<? super PsiClass> consumer,
                                        @Nonnull final PsiClass baseClass,
                                        @Nonnull final SearchScope searchScope,
                                        @Nonnull final ClassInheritorsSearch.SearchParameters parameters) {
    if (baseClass instanceof PsiAnonymousClass || isFinal(baseClass)) {
      return;
    }

    Project project = PsiUtilCore.getProjectInReadAction(baseClass);
    if (isJavaLangObject(baseClass)) {
      AllClassesSearch.search(searchScope, project, parameters.getNameCondition()).forEach(new Processor<PsiClass>() {
        @Override
        public boolean process(final PsiClass aClass) {
          ProgressManager.checkCanceled();
          return isJavaLangObject(aClass) || consumer.process(aClass);
        }
      });
      return;
    }

    final Ref<PsiClass> currentBase = Ref.create(null);
    final Stack<PsiAnchor> stack = new Stack<PsiAnchor>();
    final Set<PsiAnchor> processed = new HashSet<>();

    final Processor<PsiClass> processor = new ReadActionProcessor<PsiClass>() {
      @Override
      public boolean processInReadAction(PsiClass candidate) {
        ProgressManager.checkCanceled();

        if (parameters.isCheckInheritance() || parameters.isCheckDeep() && !(candidate instanceof PsiAnonymousClass)) {
          if (!candidate.isInheritor(currentBase.get(), false)) {
            return true;
          }
        }

        if (PsiSearchScopeUtil.isInScope(searchScope, candidate)) {
          if (candidate instanceof PsiAnonymousClass) {
            return consumer.process(candidate);
          }

          final String name = candidate.getName();
          if (name != null && parameters.getNameCondition().value(name) && !consumer.process(candidate)) {
            return false;
          }
        }

        if (parameters.isCheckDeep() && !(candidate instanceof PsiAnonymousClass) && !isFinal(candidate)) {
          stack.push(PsiAnchor.create(candidate));
        }
        return true;
      }
    };

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        stack.push(PsiAnchor.create(baseClass));
      }
    });
    final GlobalSearchScope projectScope = GlobalSearchScope.allScope(project);

    while (!stack.isEmpty()) {
      ProgressManager.checkCanceled();

      final PsiAnchor anchor = stack.pop();
      if (!processed.add(anchor)) {
        continue;
      }

      PsiClass psiClass = ApplicationManager.getApplication().runReadAction(new Computable<PsiClass>() {
        @Override
        public PsiClass compute() {
          return (PsiClass) anchor.retrieve();
        }
      });
      if (psiClass == null) {
        continue;
      }

      currentBase.set(psiClass);
      if (!DirectClassInheritorsSearch.search(psiClass, projectScope, parameters.isIncludeAnonymous(), false).forEach(processor)) {
        return;
      }
    }
  }

  private static boolean isJavaLangObject(@Nonnull final PsiClass baseClass) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        return baseClass.isValid() && JavaClassNames.JAVA_LANG_OBJECT.equals(baseClass.getQualifiedName());
      }
    });
  }

  private static boolean isFinal(@Nonnull final PsiClass baseClass) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        return Boolean.valueOf(baseClass.hasModifierProperty(PsiModifier.FINAL));
      }
    }).booleanValue();
  }

}
