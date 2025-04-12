/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.search.PsiShortNameProvider;
import com.intellij.java.language.psi.search.PsiShortNamesCache;
import consulo.annotation.component.ServiceImpl;
import consulo.application.util.function.CommonProcessors;
import consulo.language.psi.PsiFile;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.stub.IdFilter;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

@Singleton
@ServiceImpl
public class CompositeShortNamesCache extends PsiShortNamesCache {
    private final List<PsiShortNameProvider> myCaches;

    @Inject
    public CompositeShortNamesCache(Project project) {
        myCaches = project.isDefault() ? List.of() : project.getExtensionList(PsiShortNameProvider.class);
    }

    @Override
    @Nonnull
    public PsiFile[] getFilesByName(@Nonnull String name) {
        Merger<PsiFile> merger = null;
        for (PsiShortNameProvider cache : myCaches) {
            PsiFile[] classes = cache.getFilesByName(name);
            if (classes.length != 0) {
                if (merger == null) {
                    merger = new Merger<>();
                }
                merger.add(classes);
            }
        }
        PsiFile[] result = merger == null ? null : merger.getResult();
        return result != null ? result : PsiFile.EMPTY_ARRAY;
    }

    @Override
    @Nonnull
    public String[] getAllFileNames() {
        Merger<String> merger = new Merger<>();
        for (PsiShortNameProvider cache : myCaches) {
            merger.add(cache.getAllFileNames());
        }
        String[] result = merger.getResult();
        return result != null ? result : ArrayUtil.EMPTY_STRING_ARRAY;
    }

    @Override
    @Nonnull
    public PsiClass[] getClassesByName(@Nonnull String name, @Nonnull GlobalSearchScope scope) {
        Merger<PsiClass> merger = null;
        for (PsiShortNameProvider cache : myCaches) {
            PsiClass[] classes = cache.getClassesByName(name, scope);
            if (classes.length != 0) {
                if (merger == null) {
                    merger = new Merger<>();
                }
                merger.add(classes);
            }
        }
        PsiClass[] result = merger == null ? null : merger.getResult();
        return result != null ? result : PsiClass.EMPTY_ARRAY;
    }

    @Override
    @Nonnull
    public String[] getAllClassNames() {
        Merger<String> merger = new Merger<>();
        for (PsiShortNameProvider cache : myCaches) {
            String[] names = cache.getAllClassNames();
            merger.add(names);
        }
        String[] result = merger.getResult();
        return result != null ? result : ArrayUtil.EMPTY_STRING_ARRAY;
    }

    @Override
    public boolean processAllClassNames(Predicate<String> processor) {
        CommonProcessors.UniqueProcessor<String> uniqueProcessor = new CommonProcessors.UniqueProcessor<>(processor);
        for (PsiShortNameProvider cache : myCaches) {
            if (!cache.processAllClassNames(uniqueProcessor)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean processAllClassNames(Predicate<String> processor, GlobalSearchScope scope, IdFilter filter) {
        for (PsiShortNameProvider cache : myCaches) {
            if (!cache.processAllClassNames(processor, scope, filter)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean processAllMethodNames(Predicate<String> processor, GlobalSearchScope scope, IdFilter filter) {
        for (PsiShortNameProvider cache : myCaches) {
            if (!cache.processAllMethodNames(processor, scope, filter)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean processAllFieldNames(Predicate<String> processor, GlobalSearchScope scope, IdFilter filter) {
        for (PsiShortNameProvider cache : myCaches) {
            if (!cache.processAllFieldNames(processor, scope, filter)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void getAllClassNames(@Nonnull HashSet<String> dest) {
        for (PsiShortNameProvider cache : myCaches) {
            cache.getAllClassNames(dest);
        }
    }

    @Override
    @Nonnull
    public PsiMethod[] getMethodsByName(@Nonnull String name, @Nonnull GlobalSearchScope scope) {
        Merger<PsiMethod> merger = null;
        for (PsiShortNameProvider cache : myCaches) {
            PsiMethod[] methods = cache.getMethodsByName(name, scope);
            if (methods.length != 0) {
                if (merger == null) {
                    merger = new Merger<>();
                }
                merger.add(methods);
            }
        }
        PsiMethod[] result = merger == null ? null : merger.getResult();
        return result == null ? PsiMethod.EMPTY_ARRAY : result;
    }

    @Override
    @Nonnull
    public PsiMethod[] getMethodsByNameIfNotMoreThan(@Nonnull String name, @Nonnull GlobalSearchScope scope, int maxCount) {
        Merger<PsiMethod> merger = null;
        for (PsiShortNameProvider cache : myCaches) {
            PsiMethod[] methods = cache.getMethodsByNameIfNotMoreThan(name, scope, maxCount);
            if (methods.length == maxCount) {
                return methods;
            }
            if (methods.length != 0) {
                if (merger == null) {
                    merger = new Merger<>();
                }
                merger.add(methods);
            }
        }
        PsiMethod[] result = merger == null ? null : merger.getResult();
        return result == null ? PsiMethod.EMPTY_ARRAY : result;
    }

    @Nonnull
    @Override
    public PsiField[] getFieldsByNameIfNotMoreThan(@Nonnull String name, @Nonnull GlobalSearchScope scope, int maxCount) {
        Merger<PsiField> merger = null;
        for (PsiShortNameProvider cache : myCaches) {
            PsiField[] fields = cache.getFieldsByNameIfNotMoreThan(name, scope, maxCount);
            if (fields.length == maxCount) {
                return fields;
            }
            if (fields.length != 0) {
                if (merger == null) {
                    merger = new Merger<>();
                }
                merger.add(fields);
            }
        }
        PsiField[] result = merger == null ? null : merger.getResult();
        return result == null ? PsiField.EMPTY_ARRAY : result;
    }

    @Override
    public boolean processMethodsWithName(
        @Nonnull String name,
        @Nonnull GlobalSearchScope scope,
        @Nonnull Predicate<PsiMethod> processor
    ) {
        return processMethodsWithName(name, processor, scope, null);
    }

    @Override
    public boolean processMethodsWithName(
        @Nonnull String name,
        @Nonnull Predicate<? super PsiMethod> processor,
        @Nonnull GlobalSearchScope scope,
        @Nullable IdFilter idFilter
    ) {
        for (PsiShortNameProvider cache : myCaches) {
            if (!cache.processMethodsWithName(name, processor, scope, idFilter)) {
                return false;
            }
        }
        return true;
    }

    @Override
    @Nonnull
    public String[] getAllMethodNames() {
        Merger<String> merger = new Merger<>();
        for (PsiShortNameProvider cache : myCaches) {
            merger.add(cache.getAllMethodNames());
        }
        String[] result = merger.getResult();
        return result != null ? result : ArrayUtil.EMPTY_STRING_ARRAY;
    }

    @Override
    public void getAllMethodNames(@Nonnull HashSet<String> set) {
        for (PsiShortNameProvider cache : myCaches) {
            cache.getAllMethodNames(set);
        }
    }

    @Override
    @Nonnull
    public PsiField[] getFieldsByName(@Nonnull String name, @Nonnull GlobalSearchScope scope) {
        Merger<PsiField> merger = null;
        for (PsiShortNameProvider cache : myCaches) {
            PsiField[] classes = cache.getFieldsByName(name, scope);
            if (classes.length != 0) {
                if (merger == null) {
                    merger = new Merger<>();
                }
                merger.add(classes);
            }
        }
        PsiField[] result = merger == null ? null : merger.getResult();
        return result == null ? PsiField.EMPTY_ARRAY : result;
    }

    @Override
    @Nonnull
    public String[] getAllFieldNames() {
        Merger<String> merger = null;
        for (PsiShortNameProvider cache : myCaches) {
            String[] classes = cache.getAllFieldNames();
            if (classes.length != 0) {
                if (merger == null) {
                    merger = new Merger<>();
                }
                merger.add(classes);
            }
        }
        String[] result = merger == null ? null : merger.getResult();
        return result == null ? ArrayUtil.EMPTY_STRING_ARRAY : result;
    }

    @Override
    public void getAllFieldNames(@Nonnull HashSet<String> set) {
        for (PsiShortNameProvider cache : myCaches) {
            cache.getAllFieldNames(set);
        }
    }

    @Override
    public boolean processFieldsWithName(
        @Nonnull String key,
        @Nonnull Predicate<? super PsiField> processor,
        @Nonnull GlobalSearchScope scope,
        @Nullable IdFilter filter
    ) {
        for (PsiShortNameProvider cache : myCaches) {
            if (!cache.processFieldsWithName(key, processor, scope, filter)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean processClassesWithName(
        @Nonnull String key,
        @Nonnull Predicate<? super PsiClass> processor,
        @Nonnull GlobalSearchScope scope,
        @Nullable IdFilter filter
    ) {
        for (PsiShortNameProvider cache : myCaches) {
            if (!cache.processClassesWithName(key, processor, scope, filter)) {
                return false;
            }
        }
        return true;
    }

    private static class Merger<T> {
        private T[] mySingleItem;
        private Set<T> myAllItems;

        public void add(@Nonnull T[] items) {
            if (items.length == 0) {
                return;
            }
            if (mySingleItem == null) {
                mySingleItem = items;
                return;
            }
            if (myAllItems == null) {
                T[] elements = mySingleItem;
                myAllItems = ContainerUtil.addAll(new HashSet<>(elements.length), elements);
            }
            ContainerUtil.addAll(myAllItems, items);
        }

        public T[] getResult() {
            if (myAllItems == null) {
                return mySingleItem;
            }
            return myAllItems.toArray(mySingleItem);
        }
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    @Override
    public String toString() {
        return "Composite cache: " + Arrays.asList(myCaches);
    }
}
