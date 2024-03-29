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
package com.intellij.java.impl.refactoring.extractclass.usageInfo;

import com.intellij.java.language.psi.PsiJavaCodeReferenceElement;
import com.intellij.java.impl.refactoring.psi.MutationUtils;
import com.intellij.java.impl.refactoring.util.FixableUsageInfo;
import consulo.language.util.IncorrectOperationException;

public class ReplaceClassReference extends FixableUsageInfo {
    private final PsiJavaCodeReferenceElement reference;
    private final String newClassName;

    public ReplaceClassReference(PsiJavaCodeReferenceElement reference, String newClassName) {
        super(reference);
        this.reference = reference;
        this.newClassName = newClassName;
    }

    public void fixUsage() throws IncorrectOperationException {
        MutationUtils.replaceReference(newClassName, reference);
    }
}
