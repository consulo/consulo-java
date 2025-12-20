/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.style;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class TypeParameterExtendsObjectInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.typeParameterExtendsObjectDisplayName();
    }

    @Override
    @Nonnull
    public String getID() {
        return "TypeParameterExplicitlyExtendsObject";
    }

    @Override
    @Nonnull
    protected String buildErrorString(Object... infos) {
        Integer type = (Integer) infos[0];
        return type == 1
            ? InspectionGadgetsLocalize.typeParameterExtendsObjectProblemDescriptor1().get()
            : InspectionGadgetsLocalize.typeParameterExtendsObjectProblemDescriptor2().get();
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new ExtendsObjectFix();
    }

    private static class ExtendsObjectFix extends InspectionGadgetsFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.extendsObjectRemoveQuickfix();
        }

        @Override
        public void doFix(@Nonnull Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            PsiElement identifier = descriptor.getPsiElement();
            PsiElement parent = identifier.getParent();
            if (parent instanceof PsiTypeParameter) {
                PsiTypeParameter typeParameter = (PsiTypeParameter) parent;
                PsiReferenceList extendsList = typeParameter.getExtendsList();
                PsiJavaCodeReferenceElement[] referenceElements = extendsList.getReferenceElements();
                for (PsiJavaCodeReferenceElement referenceElement : referenceElements) {
                    deleteElement(referenceElement);
                }
            }
            else {
                PsiTypeElement typeElement = (PsiTypeElement) parent;
                PsiElement child = typeElement.getLastChild();
                while (child != null) {
                    if (child instanceof PsiJavaToken) {
                        PsiJavaToken javaToken = (PsiJavaToken) child;
                        IElementType tokenType = javaToken.getTokenType();
                        if (tokenType == JavaTokenType.QUEST) {
                            return;
                        }
                    }
                    child.delete();
                    child = typeElement.getLastChild();
                }
            }
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ExtendsObjectVisitor();
    }

    private static class ExtendsObjectVisitor extends BaseInspectionVisitor {
        @Override
        public void visitTypeParameter(PsiTypeParameter parameter) {
            super.visitTypeParameter(parameter);
            PsiClassType[] extendsListTypes = parameter.getExtendsListTypes();
            if (extendsListTypes.length != 1) {
                return;
            }
            PsiClassType extendsType = extendsListTypes[0];
            if (!extendsType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
                return;
            }
            PsiIdentifier nameIdentifier = parameter.getNameIdentifier();
            if (nameIdentifier == null) {
                return;
            }
            registerError(nameIdentifier, Integer.valueOf(1));
        }

        @Override
        public void visitTypeElement(PsiTypeElement typeElement) {
            super.visitTypeElement(typeElement);
            PsiElement lastChild = typeElement.getLastChild();
            if (!(lastChild instanceof PsiTypeElement)) {
                return;
            }
            PsiType type = typeElement.getType();
            if (!(type instanceof PsiWildcardType)) {
                return;
            }
            PsiWildcardType wildcardType = (PsiWildcardType) type;
            if (!wildcardType.isExtends()) {
                return;
            }
            PsiType extendsBound = wildcardType.getBound();
            if (!TypeUtils.isJavaLangObject(extendsBound)) {
                return;
            }
            PsiElement firstChild = typeElement.getFirstChild();
            if (firstChild == null) {
                return;
            }
            registerError(firstChild, Integer.valueOf(2));
        }
    }
}