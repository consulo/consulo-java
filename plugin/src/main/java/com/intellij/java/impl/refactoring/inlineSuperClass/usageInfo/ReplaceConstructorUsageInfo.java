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
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;

/**
 * @author anna
 * @since 2008-08-29
 */
public class ReplaceConstructorUsageInfo extends FixableUsageInfo {
    private final PsiType myNewType;
    @Nonnull
    private LocalizeValue myConflict;
    private static final LocalizeValue CONSTRUCTOR_MATCHING_SUPER_NOT_FOUND =
        LocalizeValue.localizeTODO("Constructor matching super not found");

    @RequiredReadAction
    public ReplaceConstructorUsageInfo(PsiNewExpression element, PsiType newType, PsiClass[] targetClasses) {
        super(element);
        myNewType = newType;
        PsiMethod[] constructors = targetClasses[0].getConstructors();
        PsiMethod constructor = element.resolveConstructor();
        if (constructor == null) {
            if (constructors.length == 1 && constructors[0].getParameterList().getParametersCount() > 0 || constructors.length > 1) {
                myConflict = CONSTRUCTOR_MATCHING_SUPER_NOT_FOUND;
            }
        }
        else {
            PsiParameter[] superParameters = constructor.getParameterList().getParameters();
            boolean foundMatchingConstructor = constructors.length == 0 && superParameters.length == 0;
            constr:
            for (PsiMethod method : constructors) {
                PsiParameter[] parameters = method.getParameterList().getParameters();
                if (superParameters.length == parameters.length) {
                    for (int i = 0; i < parameters.length; i++) {
                        PsiParameter parameter = parameters[i];
                        if (!TypeConversionUtil.isAssignable(
                            TypeConversionUtil.erasure(parameter.getType()),
                            TypeConversionUtil.erasure(superParameters[i].getType())
                        )) {
                            continue constr;
                        }
                    }
                    foundMatchingConstructor = true;
                }
            }
            if (!foundMatchingConstructor) {
                myConflict = CONSTRUCTOR_MATCHING_SUPER_NOT_FOUND;
            }

        }

        PsiType type = element.getType();
        if (type == null) {
            appendConflict(LocalizeValue.localizeTODO("Type is unknown"));
            return;
        }
        else {
            type = type.getDeepComponentType();
        }

        if (!TypeConversionUtil.isAssignable(type, newType)) {
            LocalizeValue conflict = LocalizeValue.localizeTODO(
                "Type parameters do not agree in " + element.getText() + ". " +
                    "Expected " + newType.getPresentableText() + " but found " + type.getPresentableText()
            );
            appendConflict(conflict);
        }

        if (targetClasses.length > 1) {
            LocalizeValue conflict = LocalizeValue.localizeTODO(
                "Constructor " + element.getText() + " can be replaced with any of " +
                    StringUtil.join(targetClasses, PsiClass::getQualifiedName, ", ")
            );
            appendConflict(conflict);
        }
    }

    private void appendConflict(@Nonnull LocalizeValue conflict) {
        if (myConflict == LocalizeValue.empty()) {
            myConflict = conflict;
        }
        else {
            myConflict = LocalizeValue.join(myConflict, LocalizeValue.of("\n"), conflict);
        }
    }

    @Override
    @RequiredWriteAction
    public void fixUsage() throws IncorrectOperationException {
        PsiNewExpression newExpression = (PsiNewExpression) getElement();
        if (newExpression != null) {
            PsiElementFactory elementFactory = JavaPsiFacade.getInstance(newExpression.getProject()).getElementFactory();

            StringBuilder buf = new StringBuilder();
            buf.append("new ").append(myNewType.getCanonicalText());
            PsiArrayInitializerExpression arrayInitializer = newExpression.getArrayInitializer();
            PsiType newExpressionType = newExpression.getType();
            assert newExpressionType != null;
            if (arrayInitializer != null) {
                for (int i = 0; i < newExpressionType.getArrayDimensions(); i++) {
                    buf.append("[]");
                }
                buf.append(arrayInitializer.getText());
            }
            else {
                PsiExpression[] arrayDimensions = newExpression.getArrayDimensions();
                if (arrayDimensions.length > 0) {
                    buf.append("[");
                    buf.append(StringUtil.join(arrayDimensions, PsiElement::getText, "]["));
                    buf.append("]");
                    for (int i = 0; i < newExpressionType.getArrayDimensions() - arrayDimensions.length; i++) {
                        buf.append("[]");
                    }
                }
                else {
                    PsiExpressionList list = newExpression.getArgumentList();
                    if (list != null) {
                        buf.append(list.getText());
                    }
                }
            }

            newExpression.replace(elementFactory.createExpressionFromText(buf.toString(), newExpression));
        }
    }

    @Override
    public LocalizeValue getConflictMessage() {
        return myConflict;
    }
}