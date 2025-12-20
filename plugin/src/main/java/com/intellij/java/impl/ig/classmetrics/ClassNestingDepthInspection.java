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
package com.intellij.java.impl.ig.classmetrics;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiTypeParameter;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.intellij.lang.annotations.Pattern;

@ExtensionImpl
public class ClassNestingDepthInspection extends ClassMetricInspection {
    private static final int CLASS_NESTING_LIMIT = 1;

    @Nonnull
    @Override
    @Pattern(VALID_ID_PATTERN)
    public String getID() {
        return "InnerClassTooDeeplyNested";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.innerClassTooDeeplyNestedDisplayName();
    }

    protected int getDefaultLimit() {
        return CLASS_NESTING_LIMIT;
    }

    protected String getConfigurationLabel() {
        return InspectionGadgetsLocalize.innerClassTooDeeplyNestedNestingLimitOption().get();
    }

    @Nonnull
    public String buildErrorString(Object... infos) {
        Integer nestingLevel = (Integer) infos[0];
        return InspectionGadgetsLocalize.innerClassTooDeeplyNestedProblemDescriptor(nestingLevel).get();
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ClassNestingLevel();
    }

    private class ClassNestingLevel extends BaseInspectionVisitor {

        @Override
        public void visitClass(@Nonnull PsiClass aClass) {
            // note: no call to super
            if (aClass instanceof PsiTypeParameter) {
                return;
            }
            int nestingLevel = getNestingLevel(aClass);
            if (nestingLevel <= getLimit()) {
                return;
            }
            registerClassError(aClass, Integer.valueOf(nestingLevel));
        }

        private int getNestingLevel(PsiClass aClass) {
            PsiElement ancestor = aClass.getParent();
            int nestingLevel = 0;
            while (ancestor != null) {
                if (ancestor instanceof PsiClass) {
                    nestingLevel++;
                }
                ancestor = ancestor.getParent();
            }
            return nestingLevel;
        }
    }
}