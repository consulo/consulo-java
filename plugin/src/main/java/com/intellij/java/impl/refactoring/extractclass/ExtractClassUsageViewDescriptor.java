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
package com.intellij.java.impl.refactoring.extractclass;

import com.intellij.java.language.psi.PsiClass;
import consulo.java.localize.JavaRefactoringLocalize;
import consulo.language.psi.PsiElement;
import consulo.usage.UsageViewDescriptor;
import jakarta.annotation.Nonnull;

class ExtractClassUsageViewDescriptor implements UsageViewDescriptor {
    private final PsiClass aClass;

    ExtractClassUsageViewDescriptor(PsiClass aClass) {
        super();
        this.aClass = aClass;
    }

    @Override
    public String getCodeReferencesText(int usagesCount, int filesCount) {
        return JavaRefactoringLocalize.referencesToExtract(usagesCount, filesCount).get();
    }

    @Override
    public String getProcessedElementsHeader() {
        return JavaRefactoringLocalize.extractingFromClass().get();
    }

    @Nonnull
    @Override
    public PsiElement[] getElements() {
        return new PsiElement[]{aClass};
    }

    @Override
    public String getCommentReferencesText(int usagesCount, int filesCount) {
        return null;
    }
}
