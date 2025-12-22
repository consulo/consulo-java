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
package com.intellij.java.impl.refactoring.rename;

import com.intellij.java.language.psi.PsiMethod;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.rename.UnresolvableCollisionUsageInfo;
import consulo.language.editor.refactoring.ui.RefactoringUIUtil;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.usage.UsageViewUtil;
import jakarta.annotation.Nonnull;

/**
 * @author dsl
 * @since 2002-06-05
 */
public class SubmemberHidesMemberUsageInfo extends UnresolvableCollisionUsageInfo {
    public SubmemberHidesMemberUsageInfo(PsiElement element, PsiElement referencedElement) {
        super(element, referencedElement);
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public LocalizeValue getDescription() {
        PsiElement element = getElement();
        LocalizeValue descr = element instanceof PsiMethod method
            ? RefactoringLocalize.zeroWillOverrideRenamed1(RefactoringUIUtil.getDescription(element, true), UsageViewUtil.getType(element))
            : RefactoringLocalize.zeroWillHideRenamed1(RefactoringUIUtil.getDescription(element, true), UsageViewUtil.getType(element));
        return descr.capitalize();
    }
}
