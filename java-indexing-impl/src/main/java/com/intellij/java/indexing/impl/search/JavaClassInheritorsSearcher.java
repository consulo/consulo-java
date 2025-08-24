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
import com.intellij.java.indexing.search.searches.ClassInheritorsSearchExecutor;
import com.intellij.java.indexing.search.searches.DirectClassInheritorsSearch;
import com.intellij.java.language.psi.CommonClassNames;
import com.intellij.java.language.psi.PsiAnonymousClass;
import com.intellij.java.language.psi.PsiClass;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressIndicatorProvider;
import consulo.application.progress.ProgressManager;
import consulo.application.util.ReadActionProcessor;
import consulo.content.scope.SearchScope;
import consulo.language.psi.PsiAnchor;
import consulo.language.psi.PsiBundle;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.scope.PsiSearchScopeUtil;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.project.util.query.QueryExecutorBase;
import consulo.util.collection.Stack;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

@ExtensionImpl
public class JavaClassInheritorsSearcher extends QueryExecutorBase<PsiClass, ClassInheritorsSearch.SearchParameters> implements ClassInheritorsSearchExecutor {
    private static final Logger LOG = Logger.getInstance(JavaClassInheritorsSearcher.class);

    @Nonnull
    private final Application myApplication;

    @Inject
    public JavaClassInheritorsSearcher(@Nonnull Application application) {
        myApplication = application;
    }

    @Override
    public void processQuery(@Nonnull ClassInheritorsSearch.SearchParameters parameters, @Nonnull Predicate<? super PsiClass> consumer) {
        PsiClass baseClass = parameters.getClassToProcess();
        SearchScope searchScope = parameters.getScope();

        LOG.assertTrue(searchScope != null);

        ProgressIndicator progress = ProgressIndicatorProvider.getGlobalProgressIndicator();
        if (progress != null) {
            progress.pushState();
            String className = myApplication.runReadAction((Supplier<String>)baseClass::getName);
            progress.setText(
                className != null
                    ? PsiBundle.message("psi.search.inheritors.of.class.progress", className)
                    : PsiBundle.message("psi.search.inheritors.progress")
            );
        }

        processInheritors(consumer, baseClass, searchScope, parameters);

        if (progress != null) {
            progress.popState();
        }
    }

    private static void processInheritors(
        @Nonnull Predicate<? super PsiClass> consumer,
        @Nonnull PsiClass baseClass,
        @Nonnull SearchScope searchScope,
        @Nonnull ClassInheritorsSearch.SearchParameters parameters
    ) {
        Project project = PsiUtilCore.getProjectInReadAction(baseClass);
        Application app = project.getApplication();
        if (baseClass instanceof PsiAnonymousClass || isFinal(app, baseClass)) {
            return;
        }

        if (isJavaLangObject(app, baseClass)) {
            AllClassesSearch.search(searchScope, project, parameters.getNameCondition()).forEach(aClass -> {
                ProgressManager.checkCanceled();
                return isJavaLangObject(app, aClass) || consumer.test(aClass);
            });
            return;
        }

        SimpleReference<PsiClass> currentBase = SimpleReference.create(null);
        Stack<PsiAnchor> stack = new Stack<>();
        Set<PsiAnchor> processed = new HashSet<>();

        Predicate<PsiClass> processor = new ReadActionProcessor<>() {
            @Override
            @RequiredReadAction
            public boolean processInReadAction(PsiClass candidate) {
                ProgressManager.checkCanceled();

                if (parameters.isCheckInheritance() || parameters.isCheckDeep() && !(candidate instanceof PsiAnonymousClass)) {
                    if (!candidate.isInheritor(currentBase.get(), false)) {
                        return true;
                    }
                }

                if (PsiSearchScopeUtil.isInScope(searchScope, candidate)) {
                    if (candidate instanceof PsiAnonymousClass) {
                        return consumer.test(candidate);
                    }

                    String name = candidate.getName();
                    if (name != null && parameters.getNameCondition().test(name) && !consumer.test(candidate)) {
                        return false;
                    }
                }

                if (parameters.isCheckDeep() && !(candidate instanceof PsiAnonymousClass) && !isFinal(app, candidate)) {
                    stack.push(PsiAnchor.create(candidate));
                }
                return true;
            }
        };

        app.runReadAction(() -> stack.push(PsiAnchor.create(baseClass)));
        GlobalSearchScope projectScope = GlobalSearchScope.allScope(project);

        while (!stack.isEmpty()) {
            ProgressManager.checkCanceled();

            PsiAnchor anchor = stack.pop();
            if (!processed.add(anchor)) {
                continue;
            }

            PsiClass psiClass = app.runReadAction((Supplier<PsiClass>)() -> (PsiClass)anchor.retrieve());
            if (psiClass == null) {
                continue;
            }

            currentBase.set(psiClass);
            if (!DirectClassInheritorsSearch.search(psiClass, projectScope, parameters.isIncludeAnonymous(), false).forEach(processor)) {
                return;
            }
        }
    }

    private static boolean isJavaLangObject(Application application, @Nonnull PsiClass baseClass) {
        return application.runReadAction(
            (Supplier<Boolean>)() -> baseClass.isValid() && CommonClassNames.JAVA_LANG_OBJECT.equals(baseClass.getQualifiedName())
        );
    }

    private static boolean isFinal(Application application, @Nonnull PsiClass baseClass) {
        return application.runReadAction((Supplier<Boolean>)baseClass::isFinal);
    }
}
