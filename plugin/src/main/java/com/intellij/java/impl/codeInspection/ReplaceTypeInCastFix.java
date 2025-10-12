// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.codeInspection;

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.PsiTypeElement;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.util.lang.ObjectUtil;
import jakarta.annotation.Nonnull;

public class ReplaceTypeInCastFix implements LocalQuickFix {
    private final String myExistingTypeText;
    private final String myWantedTypeText;
    private final String myWantedTypeCanonicalText;

    public ReplaceTypeInCastFix(PsiType existingType, PsiType wantedType) {
        myExistingTypeText = existingType.getPresentableText();
        myWantedTypeText = wantedType.getPresentableText();
        myWantedTypeCanonicalText = wantedType.getCanonicalText();
    }

    @Nonnull
    @Override
    public LocalizeValue getName() {
        return InspectionGadgetsLocalize.castConflictsWithInstanceofQuickfix1(myExistingTypeText, myWantedTypeText);
    }

    @Override
    public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
        PsiTypeElement typeElement = ObjectUtil.tryCast(descriptor.getStartElement(), PsiTypeElement.class);
        if (typeElement == null) {
            return;
        }
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        PsiTypeElement replacement = factory.createTypeElement(factory.createTypeFromText(myWantedTypeCanonicalText, typeElement));
        typeElement.replace(replacement);
    }
}
