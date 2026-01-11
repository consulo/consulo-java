/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.inlineSuperClass.usageInfo;

import com.intellij.java.impl.refactoring.util.FixableUsageInfo;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;

/**
 * @author anna
 * @since 2008-08-27
 */
public class ReplaceWithSubtypeUsageInfo extends FixableUsageInfo {
    public static final Logger LOG = Logger.getInstance(ReplaceWithSubtypeUsageInfo.class);
    private final PsiTypeElement myTypeElement;
    private final PsiClassType myTargetClassType;
    private final PsiType myOriginalType;
    @Nonnull
    private LocalizeValue myConflict;

    @RequiredReadAction
    public ReplaceWithSubtypeUsageInfo(PsiTypeElement typeElement, PsiClassType classType, PsiClass[] targetClasses) {
        super(typeElement);
        myTypeElement = typeElement;
        myTargetClassType = classType;
        myOriginalType = myTypeElement.getType();
        if (targetClasses.length > 1) {
            myConflict = LocalizeValue.localizeTODO(
                typeElement.getText() + " can be replaced with any of " +
                    StringUtil.join(targetClasses, psiClass -> psiClass.getQualifiedName(), ", ")
            );
        }
    }

    @Override
    @RequiredWriteAction
    public void fixUsage() throws IncorrectOperationException {
        if (myTypeElement.isValid()) {
            PsiElementFactory elementFactory = JavaPsiFacade.getInstance(myTypeElement.getProject()).getElementFactory();
            myTypeElement.replace(elementFactory.createTypeElement(myTargetClassType));
        }
    }

    @Override
    @RequiredReadAction
    public LocalizeValue getConflictMessage() {
        if (!TypeConversionUtil.isAssignable(myOriginalType, myTargetClassType)) {
            LocalizeValue conflict = LocalizeValue.localizeTODO(
                "No consistent substitution found for " + getElement().getText() + ". " +
                    "Expected \'" + myOriginalType.getPresentableText() + "\' " +
                    "but found \'" + myTargetClassType.getPresentableText() + "\'."
            );
            myConflict = myConflict.isEmpty() ? conflict : LocalizeValue.join(myConflict, LocalizeValue.of("\n"), conflict);
        }
        return myConflict;
    }
}
