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
import com.intellij.java.language.psi.PsiClassInitializer;
import com.intellij.java.language.psi.PsiMethod;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.intellij.lang.annotations.Pattern;

@ExtensionImpl
public class ClassComplexityInspection extends ClassMetricInspection {
    private static final int DEFAULT_COMPLEXITY_LIMIT = 80;

    @Nonnull
    @Override
    @Pattern(VALID_ID_PATTERN)
    public String getID() {
        return "OverlyComplexClass";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.overlyComplexClassDisplayName();
    }

    protected int getDefaultLimit() {
        return DEFAULT_COMPLEXITY_LIMIT;
    }

    protected String getConfigurationLabel() {
        return InspectionGadgetsLocalize.cyclomaticComplexityLimitOption().get();
    }

    @Nonnull
    public String buildErrorString(Object... infos) {
        final Integer totalComplexity = (Integer) infos[0];
        return InspectionGadgetsLocalize.overlyComplexClassProblemDescriptor(totalComplexity).get();
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ClassComplexityVisitor();
    }

    private class ClassComplexityVisitor extends BaseInspectionVisitor {

        @Override
        public void visitClass(@Nonnull PsiClass aClass) {
            // note: no call to super
            final int totalComplexity = calculateTotalComplexity(aClass);
            if (totalComplexity <= getLimit()) {
                return;
            }
            registerClassError(aClass, Integer.valueOf(totalComplexity));
        }

        private int calculateTotalComplexity(PsiClass aClass) {
            final PsiMethod[] methods = aClass.getMethods();
            int totalComplexity = calculateComplexityForMethods(methods);
            totalComplexity += calculateInitializerComplexity(aClass);
            return totalComplexity;
        }

        private int calculateInitializerComplexity(PsiClass aClass) {
            final ComplexityVisitor visitor = new ComplexityVisitor();
            int complexity = 0;
            final PsiClassInitializer[] initializers = aClass.getInitializers();
            for (final PsiClassInitializer initializer : initializers) {
                visitor.reset();
                initializer.accept(visitor);
                complexity += visitor.getComplexity();
            }
            return complexity;
        }

        private int calculateComplexityForMethods(PsiMethod[] methods) {
            final ComplexityVisitor visitor = new ComplexityVisitor();
            int complexity = 0;
            for (final PsiMethod method : methods) {
                visitor.reset();
                method.accept(visitor);
                complexity += visitor.getComplexity();
            }
            return complexity;
        }
    }
}