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
package com.intellij.java.impl.ig.inheritance;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaCodeReferenceElement;
import com.intellij.java.language.psi.PsiReferenceList;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.intellij.lang.annotations.Pattern;

@ExtensionImpl
public class ExtendsAnnotationInspection extends BaseInspection {
    @Nonnull
    @Override
    @Pattern(VALID_ID_PATTERN)
    public String getID() {
        return "ClassExplicitlyAnnotation";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.extendsAnnotationDisplayName();
    }

    public boolean isEnabledByDefault() {
        return true;
    }

    @Nonnull
    @RequiredReadAction
    public String buildErrorString(Object... infos) {
        PsiClass containingClass = (PsiClass) infos[0];
        return InspectionGadgetsLocalize.extendsAnnotationProblemDescriptor(containingClass.getName()).get();
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ExtendsAnnotationVisitor();
    }

    private static class ExtendsAnnotationVisitor
        extends BaseInspectionVisitor {

        @Override
        public void visitClass(@Nonnull PsiClass aClass) {
            if (!PsiUtil.isLanguageLevel5OrHigher(aClass)) {
                return;
            }
            if (aClass.isAnnotationType()) {
                return;
            }
            PsiReferenceList extendsList = aClass.getExtendsList();
            checkReferenceList(extendsList, aClass);
            PsiReferenceList implementsList = aClass.getImplementsList();
            checkReferenceList(implementsList, aClass);
        }

        private void checkReferenceList(
            PsiReferenceList referenceList,
            PsiClass containingClass
        ) {
            if (referenceList == null) {
                return;
            }
            PsiJavaCodeReferenceElement[] elements =
                referenceList.getReferenceElements();
            for (PsiJavaCodeReferenceElement element : elements) {
                PsiElement referent = element.resolve();
                if (!(referent instanceof PsiClass)) {
                    continue;
                }
                PsiClass psiClass = (PsiClass) referent;
                psiClass.isAnnotationType();
                if (psiClass.isAnnotationType()) {
                    registerError(element, containingClass);
                }
            }
        }
    }
}