/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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
import com.intellij.java.language.psi.*;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class SerialVersionUIDNotStaticFinalInspection extends BaseInspection {
    @Override
    @Nonnull
    public String getID() {
        return "SerialVersionUIDWithWrongSignature";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.serialversionuidPrivateStaticFinalLongDisplayName();
    }

    @Override
    @Nonnull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.serialversionuidPrivateStaticFinalLongProblemDescriptor().get();
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        if ((Boolean) infos[0]) {
            return null;
        }
        return new SerialVersionUIDNotStaticFinalFix();
    }

    private static class SerialVersionUIDNotStaticFinalFix extends InspectionGadgetsFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.serialversionuidPrivateStaticFinalLongQuickfix();
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            PsiElement element = descriptor.getPsiElement();
            PsiElement parent = element.getParent();
            if (!(parent instanceof PsiField)) {
                return;
            }
            PsiField field = (PsiField) parent;
            PsiModifierList modifierList = field.getModifierList();
            if (modifierList == null) {
                return;
            }
            modifierList.setModifierProperty(PsiModifier.PRIVATE, true);
            modifierList.setModifierProperty(PsiModifier.STATIC, true);
            modifierList.setModifierProperty(PsiModifier.FINAL, true);
        }
    }


    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new SerialVersionUIDNotStaticFinalVisitor();
    }

    private static class SerialVersionUIDNotStaticFinalVisitor extends BaseInspectionVisitor {
        @Override
        public void visitClass(@Nonnull PsiClass aClass) {
            // no call to super, so it doesn't drill down
            if (aClass.isInterface() || aClass.isAnnotationType()) {
                return;
            }
            PsiField field =
                aClass.findFieldByName(
                    HardcodedMethodConstants.SERIAL_VERSION_UID, false);
            if (field == null) {
                return;
            }
            PsiType type = field.getType();
            boolean wrongType = !PsiType.LONG.equals(type);
            if (field.hasModifierProperty(PsiModifier.STATIC) &&
                field.hasModifierProperty(PsiModifier.PRIVATE) &&
                field.hasModifierProperty(PsiModifier.FINAL) &&
                !wrongType) {
                return;
            }
            if (!SerializationUtils.isSerializable(aClass)) {
                return;
            }
            registerFieldError(field, Boolean.valueOf(wrongType));
        }
    }
}