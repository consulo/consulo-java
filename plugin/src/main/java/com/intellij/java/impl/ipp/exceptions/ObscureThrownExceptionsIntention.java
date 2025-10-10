/*
 * Copyright 2011-2012 Bas Leijdekkers
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

import com.intellij.java.impl.ipp.base.MutablyNamedIntention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.language.psi.*;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.HashSet;
import java.util.Set;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ObscureThrownExceptionsIntention", fileExtensions = "java", categories = {"Java", "Other"})
public class ObscureThrownExceptionsIntention extends MutablyNamedIntention {

    @Nonnull
    @Override
    protected PsiElementPredicate getElementPredicate() {
        return new ObscureThrownExceptionsPredicate();
    }

    @Override
    protected void processIntention(@Nonnull PsiElement element) throws IncorrectOperationException {
        if (!(element instanceof PsiReferenceList)) {
            return;
        }
        final PsiReferenceList referenceList = (PsiReferenceList) element;
        final PsiClassType[] types = referenceList.getReferencedTypes();
        final PsiClass commonSuperClass = findCommonSuperClass(types);
        if (commonSuperClass == null) {
            return;
        }
        final PsiElementFactory factory = JavaPsiFacade.getElementFactory(element.getProject());
        final PsiClassType classType = factory.createType(commonSuperClass);
        final PsiJavaCodeReferenceElement referenceElement = factory.createReferenceElementByType(classType);
        final PsiReferenceList newReferenceList = factory.createReferenceList(new PsiJavaCodeReferenceElement[]{referenceElement});
        referenceList.replace(newReferenceList);
    }

    @Nullable
    public static PsiClass findCommonSuperClass(PsiClassType... types) {
        if (types.length == 0) {
            return null;
        }
        final PsiClass firstClass = types[0].resolve();
        if (firstClass == null || types.length == 1) {
            return firstClass;
        }
        Set<PsiClass> sourceSet = new HashSet();
        PsiClass aClass = firstClass;
        while (aClass != null) {
            sourceSet.add(aClass);
            aClass = aClass.getSuperClass();
        }
        if (sourceSet.isEmpty()) {
            return null;
        }
        Set<PsiClass> targetSet = new HashSet();
        final int max = types.length - 1;
        for (int i = 1; i < max; i++) {
            final PsiClassType classType = types[i];
            PsiClass aClass1 = classType.resolve();
            while (aClass1 != null) {
                if (sourceSet.contains(aClass1)) {
                    targetSet.add(aClass1);
                }
                aClass1 = aClass1.getSuperClass();
            }
            sourceSet = targetSet;
            targetSet = new HashSet();
        }
        PsiClass aClass1 = types[max].resolve();
        while (aClass1 != null && !sourceSet.contains(aClass1)) {
            aClass1 = aClass1.getSuperClass();
        }
        return aClass1;
    }

    @Override
    protected LocalizeValue getTextForElement(PsiElement element) {
        final PsiReferenceList referenceList = (PsiReferenceList) element;
        final PsiClassType[] types = referenceList.getReferencedTypes();
        final PsiClass commonSuperClass = findCommonSuperClass(types);
        if (commonSuperClass == null) {
            return LocalizeValue.of();
        }
        return IntentionPowerPackLocalize.obscureThrownExceptionsIntentionName(commonSuperClass.getName());
    }

    @Nonnull
    @Override
    public LocalizeValue getNeutralText() {
        return IntentionPowerPackLocalize.obscureThrownExceptionsIntentionFamilyName();
    }
}
