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

import com.intellij.java.indexing.impl.stubs.index.JavaAnonymousClassBaseRefOccurenceIndex;
import com.intellij.java.indexing.impl.stubs.index.JavaSuperClassNameOccurenceIndex;
import com.intellij.java.indexing.search.searches.AllClassesSearch;
import com.intellij.java.indexing.search.searches.DirectClassInheritorsSearch;
import com.intellij.java.indexing.search.searches.DirectClassInheritorsSearchExecutor;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.application.progress.ProgressManager;
import consulo.content.scope.SearchScope;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.scope.EverythingGlobalScope;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * @author max
 */
@ExtensionImpl
public class JavaDirectInheritorsSearcher implements DirectClassInheritorsSearchExecutor {
    @Override
    public boolean execute(
        @Nonnull DirectClassInheritorsSearch.SearchParameters p,
        @Nonnull Predicate<? super PsiClass> consumer
    ) {
        PsiClass aClass = p.getClassToProcess();

        Application app = Application.get();
        SearchScope useScope = app.runReadAction((Supplier<SearchScope>)aClass::getUseScope);
        String qualifiedName = app.runReadAction((Supplier<String>)aClass::getQualifiedName);

        Project project = PsiUtilCore.getProjectInReadAction(aClass);
        if (CommonClassNames.JAVA_LANG_OBJECT.equals(qualifiedName)) {
            //[pasynkov]: WTF?
            //SearchScope scope = useScope.intersectWith(GlobalSearchScope.notScope(GlobalSearchScope.getScopeRestrictedByFileTypes(
            //    GlobalSearchScope.allScope(psiManager.getProject()), StdFileTypes.JSP, StdFileTypes.JSPX)));

            return AllClassesSearch.search(useScope, project).forEach(psiClass -> {
                ProgressManager.checkCanceled();
                if (psiClass.isInterface()) {
                    return consumer.test(psiClass);
                }
                PsiClass superClass = psiClass.getSuperClass();
                return !(superClass != null
                    && CommonClassNames.JAVA_LANG_OBJECT.equals(app.runReadAction((Supplier<String>)superClass::getQualifiedName)))
                    || consumer.test(psiClass);
            });
        }

        GlobalSearchScope scope =
            useScope instanceof GlobalSearchScope globalSearchScope ? globalSearchScope : new EverythingGlobalScope(project);
        String searchKey = app.runReadAction((Supplier<String>)aClass::getName);
        if (StringUtil.isEmpty(searchKey)) {
            return true;
        }

        Collection<PsiReferenceList> candidates = MethodUsagesSearcher.resolveInReadAction(
            project,
            () -> JavaSuperClassNameOccurenceIndex.getInstance().get(searchKey, project, scope)
        );

        Map<String, List<PsiClass>> classes = new HashMap<>();

        for (PsiReferenceList referenceList : candidates) {
            ProgressManager.checkCanceled();
            PsiClass candidate = (PsiClass)app.runReadAction((Supplier<PsiElement>)referenceList::getParent);
            if (!checkInheritance(p, aClass, candidate, project)) {
                continue;
            }

            String fqn = app.runReadAction((Supplier<String>)candidate::getQualifiedName);
            List<PsiClass> list = classes.get(fqn);
            if (list == null) {
                list = new ArrayList<>();
                classes.put(fqn, list);
            }
            list.add(candidate);
        }

        if (!classes.isEmpty()) {
            VirtualFile jarFile = getArchiveFile(aClass);
            for (List<PsiClass> sameNamedClasses : classes.values()) {
                ProgressManager.checkCanceled();
                if (!processSameNamedClasses(consumer, sameNamedClasses, jarFile)) {
                    return false;
                }
            }
        }

        if (p.includeAnonymous()) {
            Collection<PsiAnonymousClass> anonymousCandidates =
                MethodUsagesSearcher.resolveInReadAction(
                    project,
                    () -> JavaAnonymousClassBaseRefOccurenceIndex.getInstance().get(searchKey, project, scope)
                );

            for (PsiAnonymousClass candidate : anonymousCandidates) {
                ProgressManager.checkCanceled();
                if (!checkInheritance(p, aClass, candidate, project)) {
                    continue;
                }

                if (!consumer.test(candidate)) {
                    return false;
                }
            }

            boolean isEnum = app.runReadAction((Supplier<Boolean>)aClass::isEnum);
            if (isEnum) {
                // abstract enum can be subclassed in the body
                PsiField[] fields = app.runReadAction((Supplier<PsiField[]>)aClass::getFields);
                for (PsiField field : fields) {
                    ProgressManager.checkCanceled();
                    if (field instanceof PsiEnumConstant enumConstant) {
                        PsiEnumConstantInitializer initializingClass =
                            app.runReadAction((Supplier<PsiEnumConstantInitializer>)enumConstant::getInitializingClass);
                        if (initializingClass != null && !consumer.test(initializingClass)) {
                            return false;
                        }
                    }
                }
            }
        }

        return true;
    }

    private static boolean checkInheritance(
        DirectClassInheritorsSearch.SearchParameters p,
        PsiClass aClass,
        PsiClass candidate,
        Project project
    ) {
        return MethodUsagesSearcher.resolveInReadAction(project, () -> !p.isCheckInheritance() || candidate.isInheritor(aClass, false));
    }

    private static boolean processSameNamedClasses(
        Predicate<? super PsiClass> consumer,
        List<PsiClass> sameNamedClasses,
        VirtualFile jarFile
    ) {
        // if there is a class from the same jar, prefer it
        boolean sameJarClassFound = false;

        if (jarFile != null && sameNamedClasses.size() > 1) {
            for (PsiClass sameNamedClass : sameNamedClasses) {
                ProgressManager.checkCanceled();
                boolean fromSameJar = Comparing.equal(getArchiveFile(sameNamedClass), jarFile);
                if (fromSameJar) {
                    sameJarClassFound = true;
                    if (!consumer.test(sameNamedClass)) {
                        return false;
                    }
                }
            }
        }

        return sameJarClassFound || ContainerUtil.process(sameNamedClasses, consumer);
    }

    private static VirtualFile getArchiveFile(PsiClass aClass) {
        return Application.get().runReadAction((Supplier<VirtualFile>)() -> PsiUtil.getJarFile(aClass));
    }
}
