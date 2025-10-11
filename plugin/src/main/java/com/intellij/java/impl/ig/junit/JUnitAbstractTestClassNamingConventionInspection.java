/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.junit;

import com.intellij.java.impl.ig.fixes.RenameFix;
import com.intellij.java.impl.ig.naming.ConventionInspection;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiTypeParameter;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class JUnitAbstractTestClassNamingConventionInspection extends ConventionInspection {
    private static final int DEFAULT_MIN_LENGTH = 12;
    private static final int DEFAULT_MAX_LENGTH = 64;

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.junitAbstractTestClassNamingConventionDisplayName();
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new RenameFix();
    }

    @Override
    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    @Override
    @Nonnull
    public String buildErrorString(Object... infos) {
        final String className = (String) infos[0];
        if (className.length() < getMinLength()) {
            return InspectionGadgetsLocalize.junitAbstractTestClassNamingConventionProblemDescriptorShort().get();
        }
        else if (className.length() > getMaxLength()) {
            return InspectionGadgetsLocalize.junitAbstractTestClassNamingConventionProblemDescriptorLong().get();
        }
        return InspectionGadgetsLocalize.junitAbstractTestClassNamingConventionProblemDescriptorRegexMismatch(getRegex()).get();
    }

    @Override
    protected String getDefaultRegex() {
        return "[A-Z][A-Za-z\\d]*TestCase";
    }

    @Override
    protected int getDefaultMinLength() {
        return DEFAULT_MIN_LENGTH;
    }

    @Override
    protected int getDefaultMaxLength() {
        return DEFAULT_MAX_LENGTH;
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new NamingConventionsVisitor();
    }

    private class NamingConventionsVisitor extends BaseInspectionVisitor {

        @Override
        public void visitElement(PsiElement element) {
            if (!(element instanceof PsiClass)) {
                super.visitElement(element);
                return;
            }

            PsiClass aClass = (PsiClass) element;
            if (aClass.isInterface() || aClass.isEnum() ||
                aClass.isAnnotationType()) {
                return;
            }
            if (aClass instanceof PsiTypeParameter) {
                return;
            }
            if (!aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
                return;
            }
            if (!InheritanceUtil.isInheritor(
                aClass,
                "junit.framework.TestCase"
            )) {
                return;
            }
            final String name = aClass.getName();
            if (name == null) {
                return;
            }
            if (isValid(name)) {
                return;
            }
            registerClassError(aClass, name);
        }
    }
}