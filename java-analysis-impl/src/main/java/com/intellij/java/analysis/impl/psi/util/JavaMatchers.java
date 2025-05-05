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
    public static PsiMatcherExpression isConstructor(boolean shouldBe) {
        return element -> element instanceof PsiMethod method && method.isConstructor() == shouldBe;
    }

    public static PsiMatcherExpression hasModifier(@PsiModifier.ModifierConstant String modifier) {
        return element -> element instanceof PsiModifierListOwner owner && owner.hasModifierProperty(modifier);
    }

    public static PsiMatcherExpression hasNoModifier(@PsiModifier.ModifierConstant String modifier) {
        return element -> element instanceof PsiModifierListOwner owner && !owner.hasModifierProperty(modifier);
    }

    public static PsiMatcherExpression hasModifier(@PsiModifier.ModifierConstant String modifier, boolean shouldHave) {
        return element -> element instanceof PsiModifierListOwner owner && owner.hasModifierProperty(modifier) == shouldHave;
    }
}
