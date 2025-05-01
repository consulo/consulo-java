/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.java.impl.openapi.roots.impl;

import com.intellij.java.impl.openapi.roots.JavaProjectModelModificationService;
import com.intellij.java.impl.openapi.roots.JavaProjectModelModifier;
import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.projectRoots.roots.ExternalLibraryDescriptor;
import consulo.annotation.component.ServiceImpl;
import consulo.content.library.Library;
import consulo.module.Module;
import consulo.module.content.layer.orderEntry.DependencyScope;
import consulo.project.Project;
import consulo.util.concurrent.AsyncResult;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import jakarta.annotation.Nonnull;

import java.util.Collection;

/**
 * @author nik
 */
@Singleton
@ServiceImpl
public class JavaProjectModelModificationServiceImpl extends JavaProjectModelModificationService {
    private final Project myProject;

    @Inject
    public JavaProjectModelModificationServiceImpl(Project project) {
        myProject = project;
    }

    @Override
    public AsyncResult<Void> addDependency(@Nonnull Module from, @Nonnull Module to, @Nonnull DependencyScope scope) {
        for (JavaProjectModelModifier modifier : getModelModifiers()) {
            AsyncResult<Void> asyncResult = modifier.addModuleDependency(from, to, scope);
            if (asyncResult != null) {
                return asyncResult;
            }
        }
        return AsyncResult.rejected();
    }

    @Override
    public AsyncResult<Void> addDependency(
        @Nonnull Collection<Module> from,
        @Nonnull ExternalLibraryDescriptor libraryDescriptor,
        @Nonnull DependencyScope scope
    ) {
        for (JavaProjectModelModifier modifier : getModelModifiers()) {
            AsyncResult<Void> asyncResult = modifier.addExternalLibraryDependency(from, libraryDescriptor, scope);
            if (asyncResult != null) {
                return asyncResult;
            }
        }
        return AsyncResult.rejected();
    }

    @Override
    public AsyncResult<Void> addDependency(@Nonnull Module from, @Nonnull Library library, @Nonnull DependencyScope scope) {
        for (JavaProjectModelModifier modifier : getModelModifiers()) {
            AsyncResult<Void> asyncResult = modifier.addLibraryDependency(from, library, scope);
            if (asyncResult != null) {
                return asyncResult;
            }
        }
        return AsyncResult.rejected();
    }

    @Override
    public AsyncResult<Void> changeLanguageLevel(@Nonnull Module module, @Nonnull LanguageLevel languageLevel) {
        for (JavaProjectModelModifier modifier : getModelModifiers()) {
            AsyncResult<Void> asyncResult = modifier.changeLanguageLevel(module, languageLevel);
            if (asyncResult != null) {
                return asyncResult;
            }
        }
        return AsyncResult.rejected();
    }

    @Nonnull
    private JavaProjectModelModifier[] getModelModifiers() {
        return JavaProjectModelModifier.EP_NAME.getExtensions(myProject);
    }
}
