/*
 * Copyright 2011 Bas Leijdekkers
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
package com.intellij.java.impl.ipp.exceptions;

import com.intellij.java.impl.ipp.base.Intention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import com.intellij.java.language.psi.*;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

import static com.intellij.java.language.psi.PsiAnnotation.TargetType;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.SplitMultiCatchIntention", fileExtensions = "java", categories = {"Java", "Other"})
public class SplitMultiCatchIntention extends Intention {
    @Nonnull
    @Override
    public LocalizeValue getText() {
        return IntentionPowerPackLocalize.splitMultiCatchIntentionName();
    }

    @Nonnull
    @Override
    protected PsiElementPredicate getElementPredicate() {
        return new MulticatchPredicate();
    }

    @Override
    protected void processIntention(@Nonnull PsiElement element) throws IncorrectOperationException {
        PsiElement parent = element.getParent();
        if (!(parent instanceof PsiCatchSection)) {
            return;
        }
        PsiCatchSection catchSection = (PsiCatchSection) parent;
        PsiElement grandParent = catchSection.getParent();
        if (!(grandParent instanceof PsiTryStatement)) {
            return;
        }
        PsiParameter parameter = catchSection.getParameter();
        if (parameter == null) {
            return;
        }
        PsiType type = parameter.getType();
        if (!(type instanceof PsiDisjunctionType)) {
            return;
        }

        PsiModifierList modifierList = parameter.getModifierList();
        if (modifierList != null) {
            for (PsiAnnotation annotation : modifierList.getAnnotations()) {
                if (PsiImplUtil.findApplicableTarget(annotation, TargetType.TYPE_USE) == TargetType.TYPE_USE) {
                    annotation.delete();
                }
            }
        }

        PsiElementFactory factory = JavaPsiFacade.getElementFactory(element.getProject());
        for (PsiType disjunction : ((PsiDisjunctionType) type).getDisjunctions()) {
            PsiCatchSection copy = (PsiCatchSection) catchSection.copy();
            PsiParameter copyParameter = copy.getParameter();
            assert copyParameter != null : copy.getText();
            PsiTypeElement typeElement = copyParameter.getTypeElement();
            assert typeElement != null : copyParameter.getText();
            PsiTypeElement newTypeElement = factory.createTypeElement(disjunction);
            typeElement.replace(newTypeElement);
            grandParent.addBefore(copy, catchSection);
        }

        catchSection.delete();
    }
}
