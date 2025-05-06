/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.analysis.impl.psi.util;

import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiModifierListOwner;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiMatcherExpression;

public class JavaMatchers {
    public static PsiMatcherExpression isConstructor(final boolean shouldBe) {
        return new PsiMatcherExpression() {
            @Override
            public Boolean match(PsiElement element) {
                return element instanceof PsiMethod && ((PsiMethod)element).isConstructor() == shouldBe;
            }
        };
    }

    public static PsiMatcherExpression hasModifier(@PsiModifier.ModifierConstant final String modifier, final boolean shouldHave) {
        return new PsiMatcherExpression() {
            @Override
            public Boolean match(PsiElement element) {
                PsiModifierListOwner owner = element instanceof PsiModifierListOwner ? (PsiModifierListOwner)element : null;

                if (owner != null && owner.hasModifierProperty(modifier) == shouldHave) {
                    return Boolean.TRUE;
                }
                return Boolean.FALSE;
            }
        };
    }
}
