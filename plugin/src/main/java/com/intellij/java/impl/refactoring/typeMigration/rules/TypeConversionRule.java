/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.typeMigration.rules;

import com.intellij.java.impl.refactoring.typeMigration.TypeConversionDescriptorBase;
import com.intellij.java.impl.refactoring.typeMigration.TypeMigrationLabeler;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiMember;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiType;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.util.lang.Couple;
import jakarta.annotation.Nullable;

/**
 * @author anna
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class TypeConversionRule {
    public static final ExtensionPointName<TypeConversionRule> EP_NAME = ExtensionPointName.create(TypeConversionRule.class);

    @Nullable
    public abstract TypeConversionDescriptorBase findConversion(
        PsiType from,
        PsiType to,
        PsiMember member,
        PsiExpression context,
        TypeMigrationLabeler labeler
    );

    @Nullable
    public Couple<PsiType> bindTypeParameters(
        PsiType from,
        PsiType to,
        PsiMethod method,
        PsiExpression context,
        TypeMigrationLabeler labeler
    ) {
        return null;
    }

    public boolean shouldConvertNullInitializer(PsiType from, PsiType to, PsiExpression context) {
        return false;
    }
}