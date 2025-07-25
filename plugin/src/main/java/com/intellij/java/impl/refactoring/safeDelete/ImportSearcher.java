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
package com.intellij.java.impl.refactoring.safeDelete;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.language.psi.PsiElement;

import jakarta.annotation.Nullable;

/**
 * @author Max Medvedev
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class ImportSearcher {
    private static final ExtensionPointName<ImportSearcher> EP_NAME = ExtensionPointName.create(ImportSearcher.class);

    /**
     * @return found import or null
     */
    @Nullable
    public abstract PsiElement findImport(PsiElement element, boolean onlyNonStatic);

    @Nullable
    public static PsiElement getImport(PsiElement element, boolean onlyNonStatic) {
        return element.getApplication().getExtensionPoint(ImportSearcher.class)
            .computeSafeIfAny(searcher -> searcher.findImport(element, onlyNonStatic));
    }
}
