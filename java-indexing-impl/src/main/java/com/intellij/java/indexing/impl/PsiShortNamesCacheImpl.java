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
package com.intellij.java.indexing.impl;

import com.intellij.java.indexing.impl.search.JavaSourceFilterScope;
import com.intellij.java.indexing.impl.stubs.index.JavaFieldNameIndex;
import com.intellij.java.indexing.impl.stubs.index.JavaMethodNameIndex;
import com.intellij.java.indexing.impl.stubs.index.JavaShortClassNameIndex;
import com.intellij.java.language.impl.psi.impl.java.stubs.index.JavaStubIndexKeys;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiMember;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.search.PsiShortNameProvider;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.progress.ProgressIndicatorProvider;
import consulo.application.util.function.CommonProcessors;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.FilenameIndex;
import consulo.language.psi.stub.IdFilter;
import consulo.language.psi.stub.StubIndex;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.HashingStrategy;
import consulo.util.collection.Sets;
import consulo.util.collection.SmartList;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

import java.util.*;
import java.util.function.Predicate;

@ExtensionImpl
public class PsiShortNamesCacheImpl implements PsiShortNameProvider {
    private final Project myProject;

    @Inject
    public PsiShortNamesCacheImpl(Project project) {
        myProject = project;
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public PsiFile[] getFilesByName(@Nonnull String name) {
        return FilenameIndex.getFilesByName(myProject, name, GlobalSearchScope.projectScope(myProject));
    }

    @Override
    @Nonnull
    public String[] getAllFileNames() {
        return FilenameIndex.getAllFilenames(myProject);
    }

    @Override
    @Nonnull
    public PsiClass[] getClassesByName(@Nonnull String name, @Nonnull GlobalSearchScope scope) {
        Collection<PsiClass> classes = JavaShortClassNameIndex.getInstance().get(name, myProject, scope);

        if (classes.isEmpty()) {
            return PsiClass.EMPTY_ARRAY;
        }
        ArrayList<PsiClass> list = new ArrayList<>(classes.size());

        OuterLoop:
        for (PsiClass aClass : classes) {
            VirtualFile vFile = aClass.getContainingFile().getVirtualFile();
            if (!scope.contains(vFile)) {
                continue;
            }

            for (int j = 0; j < list.size(); j++) {
                PsiClass aClass1 = list.get(j);

                String qName = aClass.getQualifiedName();
                String qName1 = aClass1.getQualifiedName();
                if (qName != null && qName1 != null && qName.equals(qName1)) {
                    VirtualFile vFile1 = aClass1.getContainingFile().getVirtualFile();
                    int res = scope.compare(vFile1, vFile);
                    if (res > 0) {
                        continue OuterLoop; // aClass1 hides aClass
                    }
                    else if (res < 0) {
                        list.remove(j);
                        //noinspection AssignmentToForLoopParameter
                        j--;      // aClass hides aClass1
                    }
                }
            }

            list.add(aClass);
        }
        return list.toArray(new PsiClass[list.size()]);
    }

    @Override
    @Nonnull
    public String[] getAllClassNames() {
        return ArrayUtil.toStringArray(JavaShortClassNameIndex.getInstance().getAllKeys(myProject));
    }

    @Override
    public void getAllClassNames(@Nonnull HashSet<String> set) {
        processAllClassNames(new CommonProcessors.CollectProcessor<>(set));
    }

    @Override
    public boolean processAllClassNames(Predicate<String> processor) {
        return JavaShortClassNameIndex.getInstance().processAllKeys(myProject, processor);
    }

    @Override
    public boolean processAllClassNames(Predicate<String> processor, GlobalSearchScope scope, IdFilter filter) {
        return StubIndex.getInstance().processAllKeys(JavaStubIndexKeys.CLASS_SHORT_NAMES, processor, scope, filter);
    }

    @Override
    public boolean processAllMethodNames(Predicate<String> processor, GlobalSearchScope scope, IdFilter filter) {
        return StubIndex.getInstance().processAllKeys(JavaStubIndexKeys.METHODS, processor, scope, filter);
    }

    @Override
    public boolean processAllFieldNames(Predicate<String> processor, GlobalSearchScope scope, IdFilter filter) {
        return StubIndex.getInstance().processAllKeys(JavaStubIndexKeys.FIELDS, processor, scope, filter);
    }

    @Override
    @Nonnull
    public PsiMethod[] getMethodsByName(@Nonnull String name, @Nonnull GlobalSearchScope scope) {
        Collection<PsiMethod> methods = StubIndex.getElements(JavaStubIndexKeys.METHODS, name, myProject,
            new JavaSourceFilterScope(scope), PsiMethod.class
        );
        if (methods.isEmpty()) {
            return PsiMethod.EMPTY_ARRAY;
        }

        List<PsiMethod> list = filterMembers(methods, scope);
        return list.toArray(new PsiMethod[list.size()]);
    }


    @Override
    @Nonnull
    public PsiMethod[] getMethodsByNameIfNotMoreThan(@Nonnull String name, @Nonnull GlobalSearchScope scope, int maxCount) {
        List<PsiMethod> methods = new SmartList<>();
        StubIndex.getInstance().processElements(
            JavaStubIndexKeys.METHODS,
            name,
            myProject,
            scope,
            PsiMethod.class,
            new CommonProcessors.CollectProcessor<>(methods) {
                @Override
                public boolean process(PsiMethod method) {
                    return methods.size() != maxCount && super.process(method);
                }
            }
        );
        if (methods.isEmpty()) {
            return PsiMethod.EMPTY_ARRAY;
        }

        List<PsiMethod> list = filterMembers(methods, scope);
        return list.toArray(new PsiMethod[list.size()]);
    }

    @Override
    public boolean processMethodsWithName(
        @Nonnull String name,
        @Nonnull GlobalSearchScope scope,
        @Nonnull Predicate<PsiMethod> processor
    ) {
        return StubIndex.getInstance().processElements(JavaStubIndexKeys.METHODS, name, myProject, scope, PsiMethod.class, processor);
    }

    @Override
    @Nonnull
    public String[] getAllMethodNames() {
        return ArrayUtil.toStringArray(JavaMethodNameIndex.getInstance().getAllKeys(myProject));
    }

    @Override
    public void getAllMethodNames(@Nonnull HashSet<String> set) {
        JavaMethodNameIndex.getInstance().processAllKeys(myProject, new CommonProcessors.CollectProcessor<>(set));
    }

    @Override
    @Nonnull
    public PsiField[] getFieldsByNameIfNotMoreThan(@Nonnull String name, @Nonnull GlobalSearchScope scope, int maxCount) {
        List<PsiField> methods = new SmartList<>();
        StubIndex.getInstance().processElements(
            JavaStubIndexKeys.FIELDS,
            name,
            myProject,
            scope,
            PsiField.class,
            new CommonProcessors.CollectProcessor<>(methods) {
                @Override
                public boolean process(PsiField method) {
                    return methods.size() != maxCount && super.process(method);
                }
            }
        );
        if (methods.isEmpty()) {
            return PsiField.EMPTY_ARRAY;
        }

        List<PsiField> list = filterMembers(methods, scope);
        return list.toArray(new PsiField[list.size()]);
    }

    @Nonnull
    @Override
    public PsiField[] getFieldsByName(@Nonnull String name, @Nonnull GlobalSearchScope scope) {
        Collection<PsiField> fields = JavaFieldNameIndex.getInstance().get(name, myProject, scope);

        if (fields.isEmpty()) {
            return PsiField.EMPTY_ARRAY;
        }

        List<PsiField> list = filterMembers(fields, scope);
        return list.toArray(new PsiField[list.size()]);
    }

    @Override
    @Nonnull
    public String[] getAllFieldNames() {
        return ArrayUtil.toStringArray(JavaFieldNameIndex.getInstance().getAllKeys(myProject));
    }

    @Override
    public void getAllFieldNames(@Nonnull HashSet<String> set) {
        JavaFieldNameIndex.getInstance().processAllKeys(myProject, new CommonProcessors.CollectProcessor<>(set));
    }

    @Override
    public boolean processFieldsWithName(
        @Nonnull String name,
        @Nonnull Predicate<? super PsiField> processor,
        @Nonnull GlobalSearchScope scope,
        @Nullable IdFilter filter
    ) {
        return StubIndex.getInstance().processElements(
            JavaStubIndexKeys.FIELDS,
            name,
            myProject,
            new JavaSourceFilterScope(scope),
            filter,
            PsiField.class,
            processor
        );
    }

    @Override
    public boolean processMethodsWithName(
        @Nonnull String name,
        @Nonnull Predicate<? super PsiMethod> processor,
        @Nonnull GlobalSearchScope scope,
        @Nullable IdFilter filter
    ) {
        return StubIndex.getInstance()
            .processElements(
                JavaStubIndexKeys.METHODS,
                name,
                myProject,
                new JavaSourceFilterScope(scope),
                filter,
                PsiMethod.class,
                processor
            );
    }

    @Override
    public boolean processClassesWithName(
        @Nonnull String name,
        @Nonnull Predicate<? super PsiClass> processor,
        @Nonnull GlobalSearchScope scope,
        @Nullable IdFilter filter
    ) {
        return StubIndex.getInstance().processElements(
            JavaStubIndexKeys.CLASS_SHORT_NAMES,
            name,
            myProject,
            new JavaSourceFilterScope(scope),
            filter,
            PsiClass.class,
            processor
        );
    }

    private <T extends PsiMember> List<T> filterMembers(Collection<T> members, GlobalSearchScope scope) {
        List<T> result = new ArrayList<>(members.size());
        Set<PsiMember> set = Sets.newHashSet(members.size(), new HashingStrategy<PsiMember>() {
            @Override
            @RequiredReadAction
            public int hashCode(PsiMember member) {
                int code = 0;
                PsiClass clazz = member.getContainingClass();
                if (clazz != null) {
                    String name = clazz.getName();
                    if (name != null) {
                        code += name.hashCode();
                    }
                    else {
                        //anonymous classes are not equivalent
                        code += clazz.hashCode();
                    }
                }
                if (member instanceof PsiMethod method) {
                    code += 37 * method.getParameterList().getParametersCount();
                }
                return code;
            }

            @Override
            public boolean equals(PsiMember object, PsiMember object1) {
                return PsiManager.getInstance(myProject).areElementsEquivalent(object, object1);
            }
        });

        for (T member : members) {
            ProgressIndicatorProvider.checkCanceled();

            if (!scope.contains(member.getContainingFile().getVirtualFile())) {
                continue;
            }
            if (!set.add(member)) {
                continue;
            }
            result.add(member);
        }

        return result;
    }
}
