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
package com.intellij.java.impl.ig.classlayout;

import com.intellij.java.impl.codeInspection.util.SpecialAnnotationsUtil;
import com.intellij.java.impl.ig.fixes.AddToIgnoreIfAnnotatedByListQuickFix;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.ui.ExternalizableStringSet;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.CheckBox;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;

@ExtensionImpl
public class EmptyClassInspection extends BaseInspection {
    @SuppressWarnings({"PublicField"})
    public final ExternalizableStringSet ignorableAnnotations = new ExternalizableStringSet();

    @SuppressWarnings({"PublicField"})
    public boolean ignoreClassWithParameterization = false;

    @SuppressWarnings({"PublicField"})
    public boolean ignoreThrowables = true;

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.emptyClassDisplayName();
    }

    @Override
    @Nonnull
    protected String buildErrorString(Object... infos) {
        final Object element = infos[0];
        if (element instanceof PsiAnonymousClass) {
            return InspectionGadgetsLocalize.emptyAnonymousClassProblemDescriptor().get();
        }
        else if (element instanceof PsiClass) {
            return InspectionGadgetsLocalize.emptyClassProblemDescriptor().get();
        }
        else {
            return InspectionGadgetsLocalize.emptyClassFileWithoutClassProblemDescriptor().get();
        }
    }

    @Override
    public JComponent createOptionsPanel() {
        final JPanel panel = new JPanel(new GridBagLayout());
        final JPanel annotationsListControl = SpecialAnnotationsUtil.createSpecialAnnotationsListControl(
            ignorableAnnotations,
            InspectionGadgetsLocalize.ignoreIfAnnotatedBy().get()
        );
        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.BOTH;
        panel.add(annotationsListControl, constraints);
        constraints.gridy++;
        constraints.weighty = 0.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        final CheckBox checkBox1 = new CheckBox(
            InspectionGadgetsLocalize.emptyClassIgnoreParameterizationOption().get(),
            this,
            "ignoreClassWithParameterization"
        );
        panel.add(checkBox1, constraints);
        constraints.gridy++;
        final CheckBox checkBox2 = new CheckBox("Ignore subclasses of java.lang.Throwable", this, "ignoreThrowables");
        panel.add(checkBox2, constraints);
        return panel;
    }

    @Nonnull
    @Override
    protected InspectionGadgetsFix[] buildFixes(Object... infos) {
        final Object info = infos[0];
        if (!(info instanceof PsiModifierListOwner)) {
            return InspectionGadgetsFix.EMPTY_ARRAY;
        }
        return AddToIgnoreIfAnnotatedByListQuickFix.build((PsiModifierListOwner) info, ignorableAnnotations);
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new EmptyClassVisitor();
    }

    private class EmptyClassVisitor extends BaseInspectionVisitor {

        @Override
        public void visitFile(PsiFile file) {
            if (!(file instanceof PsiJavaFile)) {
                return;
            }
            final PsiJavaFile javaFile = (PsiJavaFile) file;
            if (javaFile.getClasses().length != 0) {
                return;
            }
            @NonNls final String fileName = javaFile.getName();
            if ("package-info.java".equals(fileName)) {
                return;
            }
            registerError(file, file);
        }

        @Override
        public void visitClass(@Nonnull PsiClass aClass) {
            //don't call super, to prevent drilldown
    /*  if (JspPsiUtil.isInJspFile(aClass.getContainingFile())) {
        return;
      } */
            if (aClass.isInterface() || aClass.isEnum() || aClass.isAnnotationType()) {
                return;
            }
            if (aClass instanceof PsiTypeParameter) {
                return;
            }
            final PsiMethod[] constructors = aClass.getConstructors();
            if (constructors.length > 0) {
                return;
            }
            final PsiMethod[] methods = aClass.getMethods();
            if (methods.length > 0) {
                return;
            }
            final PsiField[] fields = aClass.getFields();
            if (fields.length > 0) {
                return;
            }
            final PsiClassInitializer[] initializers = aClass.getInitializers();
            if (initializers.length > 0) {
                return;
            }
            if (ignoreClassWithParameterization && isSuperParametrization(aClass)) {
                return;
            }
            if (AnnotationUtil.isAnnotated(aClass, ignorableAnnotations)) {
                return;
            }
            if (ignoreThrowables && InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_LANG_THROWABLE)) {
                return;
            }
            registerClassError(aClass, aClass);
        }

        private boolean hasTypeArguments(PsiReferenceList extendsList) {
            if (extendsList == null) {
                return false;
            }
            final PsiJavaCodeReferenceElement[] referenceElements = extendsList.getReferenceElements();
            for (PsiJavaCodeReferenceElement referenceElement : referenceElements) {
                final PsiReferenceParameterList parameterList = referenceElement.getParameterList();
                if (parameterList == null) {
                    continue;
                }
                final PsiType[] typeArguments = parameterList.getTypeArguments();
                if (typeArguments.length != 0) {
                    return true;
                }
            }
            return false;
        }

        private boolean isSuperParametrization(PsiClass aClass) {
            if (!(aClass instanceof PsiAnonymousClass)) {
                final PsiReferenceList extendsList = aClass.getExtendsList();
                final PsiReferenceList implementsList = aClass.getImplementsList();
                return hasTypeArguments(extendsList) || hasTypeArguments(implementsList);
            }
            final PsiAnonymousClass anonymousClass = (PsiAnonymousClass) aClass;
            final PsiJavaCodeReferenceElement reference = anonymousClass.getBaseClassReference();
            final PsiReferenceParameterList parameterList = reference.getParameterList();
            if (parameterList == null) {
                return false;
            }
            final PsiTypeElement[] elements = parameterList.getTypeParameterElements();
            for (PsiTypeElement element : elements) {
                if (element != null) {
                    return true;
                }
            }
            return false;
        }
    }
}