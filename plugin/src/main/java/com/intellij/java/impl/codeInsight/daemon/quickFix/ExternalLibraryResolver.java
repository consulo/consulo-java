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
package com.intellij.java.impl.codeInsight.daemon.quickFix;

import com.intellij.java.language.projectRoots.roots.ExternalLibraryDescriptor;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.module.Module;
import consulo.util.lang.ThreeState;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author nik
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class ExternalLibraryResolver {
    public static final ExtensionPointName<ExternalLibraryResolver> EP_NAME =
        ExtensionPointName.create(ExternalLibraryResolver.class);

    @Nullable
    public abstract ExternalClassResolveResult resolveClass(
        @Nonnull String shortClassName,
        @Nonnull ThreeState isAnnotation,
        @Nonnull Module contextModule
    );

    @Nullable
    public ExternalLibraryDescriptor resolvePackage(@Nonnull String packageName) {
        return null;
    }

    public static class ExternalClassResolveResult {
        private final String myQualifiedClassName;
        private final ExternalLibraryDescriptor myLibrary;

        public ExternalClassResolveResult(String qualifiedClassName, ExternalLibraryDescriptor library) {
            myQualifiedClassName = qualifiedClassName;
            myLibrary = library;
        }

        public String getQualifiedClassName() {
            return myQualifiedClassName;
        }

        public ExternalLibraryDescriptor getLibrary() {
            return myLibrary;
        }
    }
}
