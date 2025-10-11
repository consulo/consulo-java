/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.migration;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@ExtensionImpl
public class StringBufferReplaceableByStringBuilderInspection extends BaseInspection {

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    @Nonnull
    public String getID() {
        return "StringBufferMayBeStringBuilder";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.stringBufferReplaceableByStringBuilderDisplayName();
    }

    @Override
    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.stringBufferReplaceableByStringBuilderProblemDescriptor().get();
    }

    @Override
    public InspectionGadgetsFix buildFix(Object... infos) {
        return new StringBufferMayBeStringBuilderFix();
    }

    @Nullable
    private static PsiNewExpression getNewStringBuffer(PsiExpression expression) {
        if (expression == null) {
            return null;
        }
        else if (expression instanceof PsiNewExpression) {
            return (PsiNewExpression) expression;
        }
        else if (expression instanceof PsiMethodCallExpression) {
            final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) expression;
            final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
            @NonNls final String methodName = methodExpression.getReferenceName();
            if (!"append".equals(methodName)) {
                return null;
            }
            final PsiExpression qualifier = methodExpression.getQualifierExpression();
            return getNewStringBuffer(qualifier);
        }
        return null;
    }

    private static class StringBufferMayBeStringBuilderFix extends InspectionGadgetsFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.stringBufferReplaceableByStringBuilderReplaceQuickfix();
        }

        @Override
        public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            final PsiElement parent = element.getParent();
            final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
            final PsiClass stringBuilderClass = psiFacade.findClass(CommonClassNames.JAVA_LANG_STRING_BUILDER, element.getResolveScope());
            if (stringBuilderClass == null) {
                return;
            }
            final PsiElementFactory factory = psiFacade.getElementFactory();
            final PsiJavaCodeReferenceElement stringBuilderClassReference = factory.createClassReferenceElement(stringBuilderClass);
            final PsiClassType stringBuilderType = factory.createType(stringBuilderClass);
            final PsiTypeElement stringBuilderTypeElement = factory.createTypeElement(stringBuilderType);
            final PsiElement grandParent = parent.getParent();
            final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement) grandParent;
            final PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
            for (PsiElement declaredElement : declaredElements) {
                if (!(declaredElement instanceof PsiVariable)) {
                    continue;
                }
                replaceWithStringBuilder(stringBuilderClassReference, stringBuilderTypeElement, (PsiVariable) declaredElement);
            }
        }

        private static void replaceWithStringBuilder(
            PsiJavaCodeReferenceElement newClassReference,
            PsiTypeElement newTypeElement,
            PsiVariable variable
        ) {
            final PsiExpression initializer = getNewStringBuffer(variable.getInitializer());
            if (initializer == null) {
                return;
            }
            final PsiNewExpression newExpression = (PsiNewExpression) initializer;
            final PsiJavaCodeReferenceElement classReference =
                newExpression.getClassReference(); // no anonymous classes because StringBuffer is final
            if (classReference == null) {
                return;
            }
            final PsiTypeElement typeElement = variable.getTypeElement();
            if (typeElement != null && typeElement.getParent() == variable) {
                typeElement.replace(newTypeElement);
            }
            classReference.replace(newClassReference);
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new StringBufferReplaceableByStringBuilderVisitor();
    }

    private static class StringBufferReplaceableByStringBuilderVisitor extends BaseInspectionVisitor {

        private static final Set<String> excludes =
            new HashSet(Arrays.asList(CommonClassNames.JAVA_LANG_STRING_BUILDER, CommonClassNames.JAVA_LANG_STRING_BUFFER));

        @Override
        public void visitDeclarationStatement(PsiDeclarationStatement statement) {
            if (!PsiUtil.isLanguageLevel5OrHigher(statement)) {
                return;
            }
            super.visitDeclarationStatement(statement);
            final PsiElement[] declaredElements = statement.getDeclaredElements();
            if (declaredElements.length == 0) {
                return;
            }
            for (PsiElement declaredElement : declaredElements) {
                if (!(declaredElement instanceof PsiLocalVariable)) {
                    return;
                }
                final PsiLocalVariable variable = (PsiLocalVariable) declaredElement;
                final PsiElement context = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class, true, PsiClass.class);
                if (!isReplaceableStringBuffer(variable, context)) {
                    return;
                }
            }
            final PsiLocalVariable firstVariable = (PsiLocalVariable) declaredElements[0];
            registerVariableError(firstVariable);
        }

        private static boolean isReplaceableStringBuffer(PsiVariable variable, PsiElement context) {
            if (context == null) {
                return false;
            }
            final PsiType type = variable.getType();
            if (!TypeUtils.typeEquals(CommonClassNames.JAVA_LANG_STRING_BUFFER, type)) {
                return false;
            }
            final PsiExpression initializer = variable.getInitializer();
            if (initializer == null) {
                return false;
            }
            if (getNewStringBuffer(initializer) == null) {
                return false;
            }
            if (VariableAccessUtils.variableIsAssigned(variable, context)) {
                return false;
            }
            if (VariableAccessUtils.variableIsAssignedFrom(variable, context)) {
                return false;
            }
            if (VariableAccessUtils.variableIsReturned(variable, context, true)) {
                return false;
            }
            if (VariableAccessUtils.variableIsPassedAsMethodArgument(variable, excludes, context, true)) {
                return false;
            }
            if (VariableAccessUtils.variableIsUsedInInnerClass(variable, context)) {
                return false;
            }
            return true;
        }
    }
}