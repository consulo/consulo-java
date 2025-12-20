/*
 * Copyright 2009-2010 Bas Leijdekkers
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
package com.intellij.java.impl.ig.bugs;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

import java.util.HashSet;
import java.util.Set;

@ExtensionImpl
public class ClassNewInstanceInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.classNewInstanceDisplayName();
    }

    @Override
    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.classNewInstanceProblemDescriptor().get();
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new ClassNewInstanceFix();
    }

    private static class ClassNewInstanceFix extends InspectionGadgetsFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.classNewInstanceQuickfix();
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            PsiElement element = descriptor.getPsiElement();
            PsiElement parent = element.getParent();
            if (!(parent instanceof PsiReferenceExpression)) {
                return;
            }
            PsiReferenceExpression methodExpression = (PsiReferenceExpression) parent;
            PsiExpression qualifier = methodExpression.getQualifierExpression();
            if (qualifier == null) {
                return;
            }
            PsiElement grandParent = parent.getParent();
            if (!(grandParent instanceof PsiMethodCallExpression)) {
                return;
            }
            PsiElement parentOfType = PsiTreeUtil.getParentOfType(element, PsiMethod.class, PsiTryStatement.class);
            if (parentOfType instanceof PsiTryStatement) {
                PsiTryStatement tryStatement =
                    (PsiTryStatement) parentOfType;
                addCatchBlock(
                    tryStatement,
                    "java.lang.NoSuchMethodException",
                    "java.lang.reflect.InvocationTargetException"
                );
            }
            else {
                PsiMethod method = (PsiMethod) parentOfType;
                addThrowsClause(
                    method,
                    "java.lang.NoSuchMethodException",
                    "java.lang.reflect.InvocationTargetException"
                );
            }
            PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) grandParent;
            @NonNls String newExpression = qualifier.getText() + ".getConstructor().newInstance()";
            replaceExpression(methodCallExpression, newExpression);
        }

        private static void addThrowsClause(PsiMethod method, String... exceptionNames) {
            PsiReferenceList throwsList = method.getThrowsList();
            PsiClassType[] referencedTypes = throwsList.getReferencedTypes();
            Set<String> presentExceptionNames = new HashSet();
            for (PsiClassType referencedType : referencedTypes) {
                String exceptionName = referencedType.getCanonicalText();
                presentExceptionNames.add(exceptionName);
            }
            Project project = method.getProject();
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
            JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
            GlobalSearchScope scope = method.getResolveScope();
            for (String exceptionName : exceptionNames) {
                if (presentExceptionNames.contains(exceptionName)) {
                    continue;
                }
                PsiJavaCodeReferenceElement throwsReference = factory.createReferenceElementByFQClassName(exceptionName, scope);
                PsiElement element = throwsList.add(throwsReference);
                codeStyleManager.shortenClassReferences(element);
            }
        }

        protected static void addCatchBlock(PsiTryStatement tryStatement, String... exceptionNames)
            throws IncorrectOperationException {
            Project project = tryStatement.getProject();
            PsiParameter[] parameters = tryStatement.getCatchBlockParameters();
            Set<String> presentExceptionNames = new HashSet();
            for (PsiParameter parameter : parameters) {
                PsiType type = parameter.getType();
                String exceptionName = type.getCanonicalText();
                presentExceptionNames.add(exceptionName);
            }
            JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
            String name = codeStyleManager.suggestUniqueVariableName("e", tryStatement.getTryBlock(), false);
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
            for (String exceptionName : exceptionNames) {
                if (presentExceptionNames.contains(exceptionName)) {
                    continue;
                }
                PsiClassType type = (PsiClassType) factory.createTypeFromText(exceptionName, tryStatement);
                PsiCatchSection section = factory.createCatchSection(type, name, tryStatement);
                PsiCatchSection element = (PsiCatchSection) tryStatement.add(section);
                codeStyleManager.shortenClassReferences(element);
            }
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new ClassNewInstanceVisitor();
    }

    private static class ClassNewInstanceVisitor extends BaseInspectionVisitor {
        @Override
        public void visitMethodCallExpression(
            PsiMethodCallExpression expression
        ) {
            PsiReferenceExpression methodExpression = expression.getMethodExpression();
            @NonNls String methodName = methodExpression.getReferenceName();
            if (!"newInstance".equals(methodName)) {
                return;
            }
            PsiExpression qualifier = methodExpression.getQualifierExpression();
            if (qualifier == null) {
                return;
            }
            PsiType qualifierType = qualifier.getType();
            if (!(qualifierType instanceof PsiClassType)) {
                return;
            }
            PsiClassType classType = (PsiClassType) qualifierType;
            PsiClass aClass = classType.resolve();
            if (aClass == null) {
                return;
            }
            String className = aClass.getQualifiedName();
            if (!CommonClassNames.JAVA_LANG_CLASS.equals(className)) {
                return;
            }
            registerMethodCallError(expression);
        }
    }
}
