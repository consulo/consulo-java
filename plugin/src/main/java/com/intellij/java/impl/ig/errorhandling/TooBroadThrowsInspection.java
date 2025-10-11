/*
 * Copyright 2010-2012 Bas Leijdekkers
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
package com.intellij.java.impl.ig.errorhandling;

import com.intellij.java.impl.ig.psiutils.ExceptionUtils;
import com.intellij.java.impl.ig.psiutils.LibraryUtil;
import com.intellij.java.language.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TestUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.deadCodeNotWorking.impl.MultipleCheckboxOptionsPanel;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.util.*;

public abstract class TooBroadThrowsInspection extends BaseInspection {
    @SuppressWarnings({"PublicField"})
    public boolean onlyWarnOnRootExceptions = false;

    @SuppressWarnings("PublicField")
    public boolean ignoreInTestCode = false;

    @SuppressWarnings("PublicField")
    public boolean ignoreLibraryOverrides = false;

    @SuppressWarnings("PublicField")
    public boolean ignoreThrown = false;

    @Override
    @Nonnull
    public String getID() {
        return "OverlyBroadThrowsClause";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.overlyBroadThrowsClauseDisplayName();
    }

    @Override
    @Nonnull
    protected String buildErrorString(Object... infos) {
        final List<SmartTypePointer> typesMasked = (List<SmartTypePointer>) infos[0];
        final PsiType type = typesMasked.get(0).getType();
        String typesMaskedString = type != null ? type.getPresentableText() : "";
        if (typesMasked.size() == 1) {
            return InspectionGadgetsLocalize.overlyBroadThrowsClauseProblemDescriptor1(typesMaskedString).get();
        }
        else {
            final int lastTypeIndex = typesMasked.size() - 1;
            for (int i = 1; i < lastTypeIndex; i++) {
                final PsiType psiType = typesMasked.get(i).getType();
                if (psiType != null) {
                    typesMaskedString += ", ";
                    typesMaskedString += psiType.getPresentableText();
                }
            }
            final PsiType psiType = typesMasked.get(lastTypeIndex).getType();
            final String lastTypeString = psiType != null ? psiType.getPresentableText() : "";
            return InspectionGadgetsLocalize.overlyBroadThrowsClauseProblemDescriptor2(typesMaskedString, lastTypeString).get();
        }
    }

    @Override
    public JComponent createOptionsPanel() {
        final MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
        panel.addCheckbox(InspectionGadgetsLocalize.tooBroadCatchOption().get(), "onlyWarnOnRootExceptions");
        panel.addCheckbox(InspectionGadgetsBundle.message("ignore.exceptions.declared.in.tests.option"), "ignoreInTestCode");
        panel.addCheckbox(InspectionGadgetsLocalize.ignoreExceptionsDeclaredOnLibraryOverrideOption().get(), "ignoreLibraryOverrides");
        panel.addCheckbox(InspectionGadgetsLocalize.overlyBroadThrowsClauseIgnoreThrownOption().get(), "ignoreThrown");
        return panel;
    }

    @Nonnull
    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        final Collection<SmartTypePointer> maskedExceptions = (Collection<SmartTypePointer>) infos[0];
        final Boolean originalNeeded = (Boolean) infos[1];
        return new AddThrowsClauseFix(maskedExceptions, originalNeeded.booleanValue());
    }

    private static class AddThrowsClauseFix extends InspectionGadgetsFix {

        private final Collection<SmartTypePointer> types;
        private final boolean originalNeeded;

        AddThrowsClauseFix(Collection<SmartTypePointer> types, boolean originalNeeded) {
            this.types = types;
            this.originalNeeded = originalNeeded;
        }

        @Nonnull
        @Override
        public LocalizeValue getName() {
            return originalNeeded
                ? InspectionGadgetsLocalize.overlyBroadThrowsClauseQuickfix1()
                : InspectionGadgetsLocalize.overlyBroadThrowsClauseQuickfix2();
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            final PsiElement parent = element.getParent();
            if (!(parent instanceof PsiReferenceList)) {
                return;
            }
            final PsiReferenceList referenceList = (PsiReferenceList) parent;
            final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
            if (!originalNeeded) {
                element.delete();
            }
            for (SmartTypePointer type : types) {
                final PsiType psiType = type.getType();
                if (psiType instanceof PsiClassType) {
                    final PsiJavaCodeReferenceElement referenceElement = factory.createReferenceElementByType((PsiClassType) psiType);
                    referenceList.add(referenceElement);
                }
            }
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new TooBroadThrowsVisitor();
    }

    private class TooBroadThrowsVisitor extends BaseInspectionVisitor {

        @Override
        public void visitMethod(PsiMethod method) {
            super.visitMethod(method);
            final PsiReferenceList throwsList = method.getThrowsList();
            if (!throwsList.isPhysical()) {
                return;
            }
            final PsiJavaCodeReferenceElement[] throwsReferences = throwsList.getReferenceElements();
            if (throwsReferences.length == 0) {
                return;
            }
            final PsiCodeBlock body = method.getBody();
            if (body == null) {
                return;
            }
            if (ignoreInTestCode && TestUtils.isInTestCode(method)) {
                return;
            }
            if (ignoreLibraryOverrides && LibraryUtil.isOverrideOfLibraryMethod(method)) {
                return;
            }
            final Set<PsiClassType> exceptionsThrown = ExceptionUtils.calculateExceptionsThrown(body);
            final PsiClassType[] referencedExceptions = throwsList.getReferencedTypes();
            final Set<PsiClassType> exceptionsDeclared = new HashSet(referencedExceptions.length);
            ContainerUtil.addAll(exceptionsDeclared, referencedExceptions);
            final int referencedExceptionsLength = referencedExceptions.length;
            for (int i = 0; i < referencedExceptionsLength; i++) {
                final PsiClassType referencedException = referencedExceptions[i];
                if (onlyWarnOnRootExceptions) {
                    if (!ExceptionUtils.isGenericExceptionClass(
                        referencedException)) {
                        continue;
                    }
                }
                final List<SmartTypePointer> exceptionsMasked = new ArrayList();
                final SmartTypePointerManager pointerManager = SmartTypePointerManager.getInstance(body.getProject());
                for (PsiClassType exceptionThrown : exceptionsThrown) {
                    if (referencedException.isAssignableFrom(exceptionThrown) && !exceptionsDeclared.contains(exceptionThrown)) {
                        exceptionsMasked.add(pointerManager.createSmartTypePointer(exceptionThrown));
                    }
                }
                if (!exceptionsMasked.isEmpty()) {
                    final PsiJavaCodeReferenceElement throwsReference = throwsReferences[i];
                    final boolean originalNeeded = exceptionsThrown.contains(referencedException);
                    if (ignoreThrown && originalNeeded) {
                        continue;
                    }
                    registerError(throwsReference, exceptionsMasked, Boolean.valueOf(originalNeeded));
                }
            }
        }
    }
}