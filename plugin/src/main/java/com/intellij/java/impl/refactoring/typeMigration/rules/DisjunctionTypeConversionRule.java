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
package com.intellij.java.impl.refactoring.typeMigration.rules;

import com.intellij.java.language.psi.PsiDisjunctionType;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiIntersectionType;
import com.intellij.java.language.psi.PsiMember;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.impl.refactoring.typeMigration.TypeConversionDescriptorBase;
import com.intellij.java.impl.refactoring.typeMigration.TypeMigrationLabeler;

public class DisjunctionTypeConversionRule extends TypeConversionRule {
    @Override
    public TypeConversionDescriptorBase findConversion(
        PsiType from,
        PsiType to,
        PsiMember member,
        PsiExpression context,
        TypeMigrationLabeler labeler
    ) {
        if (from instanceof PsiDisjunctionType disjunctionType) {
            PsiType lub = disjunctionType.getLeastUpperBound();
            if (lub instanceof PsiIntersectionType intersectionType) {
                for (PsiType type : intersectionType.getConjuncts()) {
                    TypeConversionDescriptorBase conversion = labeler.getRules().findConversion(type, to, member, context, labeler);
                    if (conversion != null) {
                        return conversion;
                    }
                }
            }
            else {
                TypeConversionDescriptorBase conversion = labeler.getRules().findConversion(lub, to, member, context, labeler);
                if (conversion != null) {
                    return conversion;
                }
            }
        }

        if (to instanceof PsiDisjunctionType disjunctionType) {
            PsiType lub = disjunctionType.getLeastUpperBound();
            if (lub instanceof PsiIntersectionType intersectionType) {
                for (PsiType type : intersectionType.getConjuncts()) {
                    TypeConversionDescriptorBase conversion = labeler.getRules().findConversion(from, type, member, context, labeler);
                    if (conversion != null) {
                        return conversion;
                    }
                }
            }
            else {
                TypeConversionDescriptorBase conversion = labeler.getRules().findConversion(from, lub, member, context, labeler);
                if (conversion != null) {
                    return conversion;
                }
            }
        }

        return null;
    }
}
