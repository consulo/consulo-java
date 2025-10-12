/*
 * Copyright 2003-2009 Dave Griffith, Bas Leijdekkers
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

import com.intellij.java.impl.ig.fixes.MakeSerializableFix;
import com.intellij.java.impl.ig.psiutils.SerializationUtils;
import com.intellij.java.language.psi.PsiAnonymousClass;
import com.intellij.java.language.psi.PsiClass;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class NonSerializableWithSerializationMethodsInspection extends BaseInspection {
    @Override
    @Nonnull
    public String getID() {
        return "NonSerializableClassWithSerializationMethods";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.nonSerializableClassWithReadwriteobjectDisplayName();
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        if (infos[2] instanceof PsiAnonymousClass) {
            return null;
        }
        return new MakeSerializableFix();
    }

    @Override
    @Nonnull
    public String buildErrorString(Object... infos) {
        final boolean hasReadObject = (Boolean) infos[0];
        final boolean hasWriteObject = (Boolean) infos[1];
        final PsiClass aClass = (PsiClass) infos[2];
        if (aClass instanceof PsiAnonymousClass) {
            if (hasReadObject && hasWriteObject) {
                return InspectionGadgetsLocalize.nonSerializableAnonymousWithReadwriteobjectProblemDescriptorBoth().get();
            }
            else if (hasWriteObject) {
                return InspectionGadgetsLocalize.nonSerializableAnonymousWithReadwriteobjectProblemDescriptorWrite().get();
            }
            else {
                return InspectionGadgetsLocalize.nonSerializableAnonymousWithReadwriteobjectProblemDescriptorRead().get();
            }
        }
        else {
            if (hasReadObject && hasWriteObject) {
                return InspectionGadgetsLocalize.nonSerializableClassWithReadwriteobjectProblemDescriptorBoth().get();
            }
            else if (hasWriteObject) {
                return InspectionGadgetsLocalize.nonSerializableClassWithReadwriteobjectProblemDescriptorWrite().get();
            }
            else {
                return InspectionGadgetsLocalize.nonSerializableClassWithReadwriteobjectProblemDescriptorRead().get();
            }
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new NonserializableDefinesSerializationMethodsVisitor();
    }

    private static class NonserializableDefinesSerializationMethodsVisitor extends BaseInspectionVisitor {
        @Override
        public void visitClass(@Nonnull PsiClass aClass) {
            // no call to super, so it doesn't drill down
            if (aClass.isInterface() || aClass.isAnnotationType()) {
                return;
            }
            final boolean hasReadObject = SerializationUtils.hasReadObject(aClass);
            final boolean hasWriteObject = SerializationUtils.hasWriteObject(aClass);
            if (!hasWriteObject && !hasReadObject) {
                return;
            }
            if (SerializationUtils.isSerializable(aClass)) {
                return;
            }
            registerClassError(aClass, Boolean.valueOf(hasReadObject), Boolean.valueOf(hasWriteObject), aClass);
        }
    }
}