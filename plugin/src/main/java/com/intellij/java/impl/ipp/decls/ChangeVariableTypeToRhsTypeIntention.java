/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

/*
 * (c) 2012 Desert Island BV
 * created: 14 08 2012
 */
package com.intellij.java.impl.ipp.decls;

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

/**
 * @author Bas Leijdekkers
 */
@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ChangeVariableTypeToRhsTypeIntention", fileExtensions = "java", categories = {"Java", "Declaration"})
public class ChangeVariableTypeToRhsTypeIntention extends MutablyNamedIntention {

    @Nonnull
    @Override
    protected PsiElementPredicate getElementPredicate() {
        return new ChangeVariableTypeToRhsTypePredicate();
    }

    @Nonnull
    @Override
    public LocalizeValue getNeutralText() {
        return IntentionPowerPackLocalize.changeVariableTypeToRhsTypeIntentionFamilyName();
    }

    @Override
    protected LocalizeValue getTextForElement(PsiElement element) {
        final PsiVariable variable = (PsiVariable) element.getParent();
        final PsiExpression initializer = variable.getInitializer();
        assert initializer != null;
        final PsiType type = initializer.getType();
        assert type != null;
        return IntentionPowerPackLocalize.changeVariableTypeToRhsTypeIntentionName(variable.getName(), type.getPresentableText());
    }

    @Override
    protected void processIntention(@Nonnull PsiElement element) throws IncorrectOperationException {
        final PsiElement parent = element.getParent();
        if (!(parent instanceof PsiVariable)) {
            return;
        }
        final PsiVariable variable = (PsiVariable) parent;
        final PsiExpression initializer = variable.getInitializer();
        if (initializer == null) {
            return;
        }
        final PsiType type = initializer.getType();
        if (type == null) {
            return;
        }
        final PsiElementFactory factory = JavaPsiFacade.getElementFactory(element.getProject());
        final PsiTypeElement typeElement = factory.createTypeElement(type);
        final PsiTypeElement variableTypeElement = variable.getTypeElement();
        if (variableTypeElement == null) {
            return;
        }
        variableTypeElement.replace(typeElement);
    }
}
