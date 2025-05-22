/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.template.macro;

import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.PsiVariable;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Max Medvedev
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class VariableTypeCalculator {
    public static final ExtensionPointName<VariableTypeCalculator> EP_NAME = ExtensionPointName.create(VariableTypeCalculator.class);

    @Nullable
    public abstract PsiType inferVarTypeAt(@Nonnull PsiVariable var, @Nonnull PsiElement place);

    /**
     * @return inferred type of variable in the context of place
     */
    @Nonnull
    public static PsiType getVarTypeAt(@Nonnull PsiVariable var, @Nonnull PsiElement place) {
        PsiType varType = var.getApplication().getExtensionPoint(VariableTypeCalculator.class)
            .computeSafeIfAny(calculator -> calculator.inferVarTypeAt(var, place));
        return varType != null ? varType : var.getType();
    }
}
