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
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.impl.openapi.roots.JavaProjectModelModificationService;
import consulo.codeEditor.Editor;
import consulo.content.library.Library;
import consulo.ide.impl.idea.openapi.roots.libraries.LibraryUtil;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.module.content.layer.orderEntry.DependencyScope;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author nik
 */
class AddLibraryToDependenciesFix extends AddOrderEntryFix {
    private final Library myLibrary;
    private final Module myCurrentModule;
    private final String myQualifiedClassName;

    public AddLibraryToDependenciesFix(
        @Nonnull Module currentModule,
        @Nonnull Library library,
        @Nonnull PsiReference reference,
        @Nullable String qualifiedClassName
    ) {
        super(reference);
        myLibrary = library;
        myCurrentModule = currentModule;
        myQualifiedClassName = qualifiedClassName;
    }

    @Nonnull
    @Override
    public LocalizeValue getText() {
        return JavaQuickFixLocalize.orderentryFixAddLibraryToClasspath(LibraryUtil.getPresentableName(myLibrary));
    }

    @Override
    public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
        return !project.isDisposed() && !myCurrentModule.isDisposed() && !((Library) myLibrary).isDisposed();
    }

    @Override
    public void invoke(@Nonnull Project project, @Nullable Editor editor, PsiFile file) {
        DependencyScope scope = suggestScopeByLocation(myCurrentModule, myReference.getElement());
        JavaProjectModelModificationService.getInstance(project).addDependency(myCurrentModule, myLibrary, scope);
        if (myQualifiedClassName != null && editor != null) {
            importClass(myCurrentModule, editor, myReference, myQualifiedClassName);
        }
    }
}