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
package com.intellij.java.impl.ig.classmetrics;

import com.intellij.java.impl.ig.fixes.MoveAnonymousToInnerClassFix;
import com.intellij.java.language.psi.PsiAnonymousClass;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiEnumConstantInitializer;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.intellij.lang.annotations.Pattern;

@ExtensionImpl
public class AnonymousClassMethodCountInspection extends ClassMetricInspection {
    private static final int DEFAULT_METHOD_COUNT_LIMIT = 1;

    @Nonnull
    @Override
    @Pattern(VALID_ID_PATTERN)
    public String getID() {
        return "AnonymousInnerClassWithTooManyMethods";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.anonymousInnerClassWithTooManyMethodsDisplayName();
    }

    @Override
    protected int getDefaultLimit() {
        return DEFAULT_METHOD_COUNT_LIMIT;
    }

    @Override
    protected String getConfigurationLabel() {
        return InspectionGadgetsLocalize.methodCountLimitOption().get();
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new MoveAnonymousToInnerClassFix();
    }

    @Override
    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    @Override
    @Nonnull
    public String buildErrorString(Object... infos) {
        Integer count = (Integer) infos[0];
        return InspectionGadgetsLocalize.anonymousInnerClassWithTooManyMethodsProblemDescriptor(count).get();
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new AnonymousClassMethodCountVisitor();
    }

    private class AnonymousClassMethodCountVisitor
        extends BaseInspectionVisitor {

        @Override
        public void visitClass(@Nonnull PsiClass psiClass) {
            // no call to super, to prevent double counting
        }

        @Override
        public void visitAnonymousClass(
            @Nonnull PsiAnonymousClass aClass
        ) {
            if (aClass instanceof PsiEnumConstantInitializer) {
                return;
            }
            int totalMethodCount = calculateTotalMethodCount(aClass);
            if (totalMethodCount <= getLimit()) {
                return;
            }
            registerClassError(aClass, Integer.valueOf(totalMethodCount));
        }

        private int calculateTotalMethodCount(PsiClass aClass) {
            return aClass.getMethods().length - aClass.getConstructors().length;
        }
    }
}