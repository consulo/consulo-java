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
package com.intellij.java.impl.ig.maturity;

import com.intellij.java.impl.ig.psiutils.LibraryUtil;
import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.query.Query;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

@ExtensionImpl
public class ObsoleteCollectionInspection extends BaseInspection {

    @SuppressWarnings({"PublicField"})
    public boolean ignoreRequiredObsoleteCollectionTypes = false;

    @Override
    @Nonnull
    public String getID() {
        return "UseOfObsoleteCollectionType";
    }

    @Nonnull
    @Override
    @Deprecated(forRemoval = true)
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.useObsoleteCollectionTypeDisplayName();
    }

    @Override
    @Nonnull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.useObsoleteCollectionTypeProblemDescriptor().get();
    }

    @Override
    @Nullable
    public JComponent createOptionsPanel() {
        LocalizeValue message = InspectionGadgetsLocalize.useObsoleteCollectionTypeIgnoreLibraryArgumentsOption();
        return new SingleCheckboxOptionsPanel(message.get(), this, "ignoreRequiredObsoleteCollectionTypes");
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new ObsoleteCollectionVisitor();
    }

    private class ObsoleteCollectionVisitor
        extends BaseInspectionVisitor {

        @Override
        public void visitVariable(@Nonnull PsiVariable variable) {
            super.visitVariable(variable);
            PsiType type = variable.getType();
            if (!isObsoleteCollectionType(type)) {
                return;
            }
            if (LibraryUtil.isOverrideOfLibraryMethodParameter(variable)) {
                return;
            }
            PsiTypeElement typeElement = variable.getTypeElement();
            if (typeElement == null) {
                return;
            }
            if (ignoreRequiredObsoleteCollectionTypes &&
                isUsedAsParameterForLibraryMethod(variable)) {
                return;
            }
            registerError(typeElement);
        }

        @Override
        public void visitMethod(PsiMethod method) {
            super.visitMethod(method);
            PsiType returnType = method.getReturnType();
            if (!isObsoleteCollectionType(returnType)) {
                return;
            }
            if (LibraryUtil.isOverrideOfLibraryMethod(method)) {
                return;
            }
            PsiTypeElement typeElement = method.getReturnTypeElement();
            if (typeElement == null) {
                return;
            }
            if (ignoreRequiredObsoleteCollectionTypes &&
                isUsedAsParameterForLibraryMethod(method)) {
                return;
            }
            registerError(typeElement);
        }

        @Override
        public void visitNewExpression(
            @Nonnull PsiNewExpression newExpression
        ) {
            super.visitNewExpression(newExpression);
            PsiType type = newExpression.getType();
            if (!isObsoleteCollectionType(type)) {
                return;
            }
            if (ignoreRequiredObsoleteCollectionTypes &&
                isRequiredObsoleteCollectionElement(newExpression)) {
                return;
            }
            registerNewExpressionError(newExpression);
        }

        private boolean isObsoleteCollectionType(PsiType type) {
            if (type == null) {
                return false;
            }
            PsiType deepComponentType = type.getDeepComponentType();
            if (!(deepComponentType instanceof PsiClassType)) {
                return false;
            }
            PsiClassType classType = (PsiClassType) deepComponentType;
            @NonNls String className = classType.getClassName();
            if (!"Vector".equals(className) && !"Hashtable".equals(className)) {
                return false;
            }
            PsiClass aClass = classType.resolve();
            if (aClass == null) {
                return false;
            }
            String name = aClass.getQualifiedName();
            return "java.util.Vector".equals(name) ||
                "java.util.Hashtable".equals(name);
        }

        private boolean isUsedAsParameterForLibraryMethod(
            PsiNamedElement namedElement
        ) {
            PsiFile containingFile = namedElement.getContainingFile();
            Query<PsiReference> query =
                ReferencesSearch.search(
                    namedElement,
                    GlobalSearchScope.fileScope(containingFile)
                );
            for (PsiReference reference : query) {
                PsiElement element = reference.getElement();
                if (isRequiredObsoleteCollectionElement(element)) {
                    return true;
                }
            }
            return false;
        }

        private boolean isRequiredObsoleteCollectionElement(
            PsiElement element
        ) {
            PsiElement parent = element.getParent();
            if (parent instanceof PsiVariable) {
                PsiVariable variable = (PsiVariable) parent;
                PsiType variableType = variable.getType();
                if (isObsoleteCollectionType(variableType)) {
                    return true;
                }
            }
            else if (parent instanceof PsiReturnStatement) {
                PsiMethod method = PsiTreeUtil.getParentOfType(
                    parent,
                    PsiMethod.class
                );
                if (method != null) {
                    PsiType returnType = method.getReturnType();
                    if (isObsoleteCollectionType(returnType)) {
                        return true;
                    }
                }
            }
            else if (parent instanceof PsiAssignmentExpression) {
                PsiAssignmentExpression assignmentExpression =
                    (PsiAssignmentExpression) parent;
                PsiExpression lhs = assignmentExpression.getLExpression();
                PsiType lhsType = lhs.getType();
                if (isObsoleteCollectionType(lhsType)) {
                    return true;
                }
            }
            if (!(parent instanceof PsiExpressionList)) {
                return false;
            }
            PsiExpressionList argumentList = (PsiExpressionList) parent;
            PsiElement grandParent = parent.getParent();
            if (!(grandParent instanceof PsiCallExpression)) {
                return false;
            }
            PsiCallExpression callExpression =
                (PsiCallExpression) grandParent;
            int index = getIndexOfArgument(argumentList, element);
            PsiMethod method = callExpression.resolveMethod();
            if (method == null) {
                return false;
            }
            PsiParameterList parameterList =
                method.getParameterList();
            PsiParameter[] parameters = parameterList.getParameters();
            if (index >= parameters.length) {
                PsiParameter lastParameter =
                    parameters[parameters.length - 1];
                if (!lastParameter.isVarArgs()) {
                    return false;
                }
                PsiType type = lastParameter.getType();
                if (!(type instanceof PsiEllipsisType)) {
                    return false;
                }
                PsiEllipsisType ellipsisType = (PsiEllipsisType) type;
                PsiType componentType = ellipsisType.getComponentType();
                return isObsoleteCollectionType(componentType);
            }
            PsiParameter parameter = parameters[index];
            PsiType type = parameter.getType();
            return isObsoleteCollectionType(type);
        }

        private int getIndexOfArgument(
            PsiExpressionList argumentList,
            PsiElement argument
        ) {
            PsiExpression[] expressions =
                argumentList.getExpressions();
            int index = -1;
            for (PsiExpression expression : expressions) {
                index++;
                if (expression.equals(argument)) {
                    break;
                }
            }
            return index;
        }
    }
}