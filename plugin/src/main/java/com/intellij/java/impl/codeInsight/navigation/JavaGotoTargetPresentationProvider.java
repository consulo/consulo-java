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
package com.intellij.java.impl.codeInsight.navigation;

import com.intellij.java.impl.ide.util.MethodOrFunctionalExpressionPresentationProvider;
import com.intellij.java.impl.ide.util.PsiClassOrFunctionalExpressionPresentationProvider;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.ide.navigation.GotoTargetPresentationProvider;
import consulo.language.psi.PsiElement;
import consulo.navigation.TargetPresentation;
import org.jspecify.annotations.Nullable;

/**
 * @author yole
 */
@ExtensionImpl
public class JavaGotoTargetPresentationProvider implements GotoTargetPresentationProvider {
    private final PsiClassOrFunctionalExpressionPresentationProvider myClassProvider =
        new PsiClassOrFunctionalExpressionPresentationProvider();

    @Override
    @RequiredReadAction
    public @Nullable TargetPresentation getPresentation(PsiElement element, Options options) {
        if (element instanceof PsiMethod method) {
            return new MethodOrFunctionalExpressionPresentationProvider(options::hasDifferentNames).getPresentation(method);
        }
        if (element instanceof PsiClass psiClass) {
            return myClassProvider.getPresentation(psiClass);
        }
        return null;
    }
}
