/*
 * Copyright 2007-2012 Bas Leijdekkers
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
import com.intellij.java.language.psi.*;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

import java.util.List;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ConvertCatchToThrowsIntention", fileExtensions = "java", categories = {"Java", "Other"})
public class ConvertCatchToThrowsIntention extends Intention {

    @Nonnull
    @Override
    public LocalizeValue getText() {
        return IntentionPowerPackLocalize.convertCatchToThrowsIntentionName();
    }

    @Override
    @Nonnull
    protected PsiElementPredicate getElementPredicate() {
        return new ConvertCatchToThrowsPredicate();
    }

    @Override
    protected void processIntention(@Nonnull PsiElement element) throws IncorrectOperationException {
        final PsiCatchSection catchSection = (PsiCatchSection) element.getParent();
        final PsiMethod method = PsiTreeUtil.getParentOfType(catchSection, PsiMethod.class);
        if (method == null) {
            return;
        }
        // todo warn if method implements or overrides some base method
        //             Warning
        // "Method xx() of class XX implements/overrides method of class
        // YY. Do you want to modify the base method?"
        //                                             [Yes][No][Cancel]
        final PsiReferenceList throwsList = method.getThrowsList();
        final PsiType catchType = catchSection.getCatchType();
        addToThrowsList(throwsList, catchType);
        final PsiTryStatement tryStatement = catchSection.getTryStatement();
        final PsiCatchSection[] catchSections = tryStatement.getCatchSections();
        if (catchSections.length > 1 || tryStatement.getResourceList() != null) {
            catchSection.delete();
        }
        else {
            final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
            if (tryBlock == null) {
                return;
            }
            final PsiElement first = tryBlock.getFirstBodyElement();
            final PsiElement last = tryBlock.getLastBodyElement();
            if (first != null && last != null) {
                tryStatement.getParent().addRangeAfter(first, last, tryStatement);
            }
            tryStatement.delete();
        }
    }

    private static void addToThrowsList(PsiReferenceList throwsList, PsiType catchType) {
        if (catchType instanceof PsiClassType) {
            final PsiClassType classType = (PsiClassType) catchType;
            final PsiClassType[] types = throwsList.getReferencedTypes();
            for (PsiClassType type : types) {
                if (catchType.equals(type)) {
                    return;
                }
            }
            final Project project = throwsList.getProject();
            final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
            final PsiJavaCodeReferenceElement referenceElement = factory.createReferenceElementByType(classType);
            throwsList.add(referenceElement);
        }
        else if (catchType instanceof PsiDisjunctionType) {
            final PsiDisjunctionType disjunctionType = (PsiDisjunctionType) catchType;
            final List<PsiType> disjunctions = disjunctionType.getDisjunctions();
            for (PsiType disjunction : disjunctions) {
                addToThrowsList(throwsList, disjunction);
            }
        }
    }
}