/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.serialization;

import com.intellij.java.impl.ig.psiutils.SerializationUtils;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiType;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class SerialPersistentFieldsWithWrongSignatureInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.serialpersistentfieldsWithWrongSignatureDisplayName();
    }

    @Nonnull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.serialpersistentfieldsWithWrongSignatureProblemDescriptor().get();
    }

    public BaseInspectionVisitor buildVisitor() {
        return new SerialPersistentFieldsWithWrongSignatureVisitor();
    }

    private static class SerialPersistentFieldsWithWrongSignatureVisitor extends BaseInspectionVisitor {
        @Override
        public void visitClass(@Nonnull PsiClass aClass) {
            // no call to super, so it doesn't drill down
            if (aClass.isInterface() || aClass.isAnnotationType()) {
                return;
            }
            PsiField badSerialPersistentFields = null;
            PsiField[] fields = aClass.getFields();
            for (PsiField field : fields) {
                if (isSerialPersistentFields(field)) {
                    if (!field.hasModifierProperty(PsiModifier.PRIVATE) ||
                        !field.hasModifierProperty(PsiModifier.STATIC) ||
                        !field.hasModifierProperty(PsiModifier.FINAL)) {
                        badSerialPersistentFields = field;
                        break;
                    }
                    else {
                        PsiType type = field.getType();
                        if (!type.equalsToText("java.io.ObjectStreamField[]")) {
                            badSerialPersistentFields = field;
                            break;
                        }
                    }
                }
            }
            if (badSerialPersistentFields == null) {
                return;
            }
            if (!SerializationUtils.isSerializable(aClass)) {
                return;
            }
            registerFieldError(badSerialPersistentFields);
        }

        private static boolean isSerialPersistentFields(PsiField field) {
            String fieldName = field.getName();
            return "serialPersistentFields".equals(fieldName);
        }
    }
}