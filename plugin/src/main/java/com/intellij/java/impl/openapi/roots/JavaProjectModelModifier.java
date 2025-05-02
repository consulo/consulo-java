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
package com.intellij.java.impl.openapi.roots;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.projectRoots.roots.ExternalLibraryDescriptor;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.module.Module;
import consulo.module.content.layer.orderEntry.DependencyScope;
import consulo.content.library.Library;
import consulo.util.concurrent.AsyncResult;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collection;

/**
 * Register implementation of this extension to support custom dependency management system for {@link JavaProjectModelModificationService}.
 * The default implementation which modify IDEA's project model directly is registered as the last extension so it'll be executed if all other
 * extensions refuse to handle modification by returning {@code null}.
 *
 * @author nik
 * @see JavaProjectModelModificationService
 */
@ExtensionAPI(ComponentScope.PROJECT)
public abstract class JavaProjectModelModifier {
    public static final ExtensionPointName<JavaProjectModelModifier> EP_NAME = ExtensionPointName.create(JavaProjectModelModifier.class);

    /**
     * Implementation of this method should add dependency from module {@code from} to module {@code to} with scope {@code scope} accordingly
     * to this dependencies management system. If it takes some time to propagate changes in the external project configuration to IDEA's
     * project model the method may schedule this work for asynchronous execution and return {@link AsyncResult} instance which will be fulfilled
     * when the work is done.
     *
     * @return {@link AsyncResult} instance if dependencies between these modules can be handled by this dependencies management system or
     * {@code null} otherwise
     */
    @Nullable
    public abstract AsyncResult<Void> addModuleDependency(@Nonnull Module from, @Nonnull Module to, @Nonnull DependencyScope scope);

    /**
     * Implementation of this method should add dependency from modules {@code modules} to an external library with scope {@code scope} accordingly
     * to this dependencies management system. If it takes some time to propagate changes in the external project configuration to IDEA's
     * project model the method may schedule this work for asynchronous execution and return {@link AsyncResult} instance which will be fulfilled
     * when the work is done.
     *
     * @return {@link AsyncResult} instance if dependencies of these modules can be handled by this dependencies management system or
     * {@code null} otherwise
     */
    @Nullable
    public abstract AsyncResult<Void> addExternalLibraryDependency(
        @Nonnull Collection<Module> modules,
        @Nonnull ExternalLibraryDescriptor descriptor,
        @Nonnull DependencyScope scope
    );

    /**
     * Implementation of this method should add dependency from module {@code from} to {@code library} with scope {@code scope} accordingly
     * to this dependencies management system. If it takes some time to propagate changes in the external project configuration to IDEA's
     * project model the method may schedule this work for asynchronous execution and return {@link AsyncResult} instance which will be fulfilled
     * when the work is done.
     *
     * @return {@link AsyncResult} instance if dependencies between these modules can be handled by this dependencies management system or
     * {@code null} otherwise
     */
    @Nullable
    public abstract AsyncResult<Void> addLibraryDependency(@Nonnull Module from, @Nonnull Library library, @Nonnull DependencyScope scope);

    /**
     * Implementation of this method should set language level for module {@code module} to the specified value accordingly
     * to this dependencies management system. If it takes some time to propagate changes in the external project configuration to IDEA's
     * project model the method may schedule this work for asynchronous execution and return {@link AsyncResult} instance which will be fulfilled
     * when the work is done.
     *
     * @return {@link AsyncResult} instance if language level can be set by this dependencies management system or {@code null} otherwise
     */
    @Nullable
    public abstract AsyncResult<Void> changeLanguageLevel(@Nonnull Module module, @Nonnull LanguageLevel level);
}
