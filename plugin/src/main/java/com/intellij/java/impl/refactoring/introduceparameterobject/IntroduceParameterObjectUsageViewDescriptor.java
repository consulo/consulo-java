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
package com.intellij.java.impl.refactoring.introduceparameterobject;

import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.impl.refactoring.RefactorJBundle;
import com.intellij.java.impl.refactoring.psi.MyUsageViewUtil;
import consulo.language.editor.refactoring.ui.UsageViewDescriptorAdapter;

class IntroduceParameterObjectUsageViewDescriptor extends UsageViewDescriptorAdapter {
    private final PsiMethod method;

    IntroduceParameterObjectUsageViewDescriptor(PsiMethod method) {

        this.method = method;
    }

    public PsiElement[] getElements() {
        return new PsiElement[]{method};
    }

    public String getProcessedElementsHeader() {
        return RefactorJBundle.message("method.whose.parameters.are.to.wrapped");
    }

    public String getCodeReferencesText(int usagesCount, int filesCount) {
        return RefactorJBundle.message("references.to.be.modified") +
            MyUsageViewUtil.getUsageCountInfo(usagesCount, filesCount, "reference");
    }
}
