/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiParameterList;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

import java.util.HashSet;
import java.util.Set;

@ExtensionImpl
public class SerializableWithUnconstructableAncestorInspection extends BaseInspection {
    @Override
    @Nonnull
    public String getID() {
        return "SerializableClassWithUnconstructableAncestor";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.serializableWithUnconstructableAncestorDisplayName();
    }

    @Override
    @Nonnull
    public String buildErrorString(Object... infos) {
        PsiClass ancestor = (PsiClass) infos[0];
        return InspectionGadgetsLocalize.serializableWithUnconstructableAncestorProblemDescriptor(ancestor.getName()).get();
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new SerializableWithUnconstructableAncestorVisitor();
    }

    private static class SerializableWithUnconstructableAncestorVisitor extends BaseInspectionVisitor {

        @Override
        public void visitClass(@Nonnull PsiClass aClass) {
            if (aClass.isInterface() || aClass.isAnnotationType()) {
                return;
            }
            if (!SerializationUtils.isSerializable(aClass) || SerializationUtils.hasWriteReplace(aClass)) {
                return;
            }
            PsiClass ancestor = aClass.getSuperClass();
            Set<PsiClass> visitedClasses = new HashSet<PsiClass>(8);
            while (ancestor != null && SerializationUtils.isSerializable(ancestor)) {
                if (SerializationUtils.hasWriteReplace(ancestor)) {
                    return;
                }
                ancestor = ancestor.getSuperClass();
                if (!visitedClasses.add(ancestor)) {
                    return;
                }
            }
            if (ancestor == null || classHasNoArgConstructor(ancestor)) {
                return;
            }
            registerClassError(aClass, ancestor);
        }

        private static boolean classHasNoArgConstructor(PsiClass aClass) {
            boolean hasConstructor = false;
            boolean hasNoArgConstructor = false;
            for (PsiMethod constructor : aClass.getConstructors()) {
                hasConstructor = true;
                PsiParameterList parameterList = constructor.getParameterList();
                if (parameterList.getParametersCount() == 0 &&
                    (constructor.hasModifierProperty(PsiModifier.PUBLIC) || constructor.hasModifierProperty(PsiModifier.PROTECTED))) {
                    hasNoArgConstructor = true;
                }
            }
            return hasNoArgConstructor || !hasConstructor;
        }
    }
}