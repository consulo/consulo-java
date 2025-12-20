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
package com.intellij.java.impl.ig.errorhandling;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.function.Processor;
import consulo.application.util.query.Query;
import consulo.deadCodeNotWorking.impl.MultipleCheckboxOptionsPanel;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.util.HashSet;
import java.util.Set;

@ExtensionImpl
public class ExceptionFromCatchWhichDoesntWrapInspection extends BaseInspection {
    @SuppressWarnings("PublicField")
    public boolean ignoreGetMessage = false;

    @SuppressWarnings("PublicField")
    public boolean ignoreCantWrap = false;

    @Nonnull
    @Override
    @Pattern(VALID_ID_PATTERN)
    public String getID() {
        return "ThrowInsideCatchBlockWhichIgnoresCaughtException";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.exceptionFromCatchWhichDoesntWrapDisplayName();
    }

    @Override
    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.exceptionFromCatchWhichDoesntWrapProblemDescriptor().get();
    }

    @Override
    @Nullable
    public JComponent createOptionsPanel() {
        MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
        panel.addCheckbox(InspectionGadgetsLocalize.exceptionFromCatchWhichDoesntwrapIgnoreOption().get(), "ignoreGetMessage");
        panel.addCheckbox(InspectionGadgetsLocalize.exceptionFromCatchWhichDoesntwrapIgnoreCantWrapOption().get(), "ignoreCantWrap");
        return panel;
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new ExceptionFromCatchWhichDoesntWrapVisitor();
    }

    private class ExceptionFromCatchWhichDoesntWrapVisitor extends BaseInspectionVisitor {

        @Override
        public void visitThrowStatement(PsiThrowStatement statement) {
            super.visitThrowStatement(statement);
            PsiCatchSection catchSection = PsiTreeUtil.getParentOfType(statement, PsiCatchSection.class, true, PsiClass.class);
            if (catchSection == null) {
                return;
            }
            PsiParameter parameter = catchSection.getParameter();
            if (parameter == null) {
                return;
            }
            @NonNls String parameterName = parameter.getName();
            if ("ignore".equals(parameterName) || "ignored".equals(parameterName)) {
                return;
            }
            PsiExpression exception = statement.getException();
            if (exception == null) {
                return;
            }
            if (ignoreCantWrap) {
                PsiType thrownType = exception.getType();
                if (thrownType instanceof PsiClassType) {
                    PsiClassType classType = (PsiClassType) thrownType;
                    PsiClass exceptionClass = classType.resolve();
                    if (exceptionClass != null) {
                        PsiMethod[] constructors = exceptionClass.getConstructors();
                        PsiClassType throwableType = TypeUtils.getType(CommonClassNames.JAVA_LANG_THROWABLE, statement);
                        boolean canWrap = false;
                        outer:
                        for (PsiMethod constructor : constructors) {
                            PsiParameterList parameterList = constructor.getParameterList();
                            PsiParameter[] parameters = parameterList.getParameters();
                            for (PsiParameter constructorParameter : parameters) {
                                PsiType type = constructorParameter.getType();
                                if (throwableType.equals(type)) {
                                    canWrap = true;
                                    break outer;
                                }
                            }
                        }
                        if (!canWrap) {
                            return;
                        }
                    }
                }
            }
            ReferenceFinder visitor = new ReferenceFinder(parameter);
            exception.accept(visitor);
            if (visitor.usesParameter()) {
                return;
            }
            registerStatementError(statement);
        }
    }

    private class ReferenceFinder extends JavaRecursiveElementVisitor {

        private final Set<PsiReferenceExpression> visited = new HashSet();
        private boolean argumentsContainCatchParameter = false;
        private final PsiParameter parameter;

        public ReferenceFinder(PsiParameter parameter) {
            this.parameter = parameter;
        }

        @Override
        public void visitReferenceExpression(PsiReferenceExpression expression) {
            if (argumentsContainCatchParameter || !visited.add(expression)) {
                return;
            }
            super.visitReferenceExpression(expression);
            PsiElement target = expression.resolve();
            if (!parameter.equals(target)) {
                if (target instanceof PsiLocalVariable) {
                    PsiLocalVariable variable = (PsiLocalVariable) target;
                    Query<PsiReference> query = ReferencesSearch.search(variable, variable.getUseScope(), false);
                    query.forEach(new Processor<PsiReference>() {
                        @Override
                        public boolean process(PsiReference reference) {
                            PsiElement element = reference.getElement();
                            PsiElement parent = PsiTreeUtil.skipParentsOfType(element, PsiParenthesizedExpression.class);
                            if (!(parent instanceof PsiReferenceExpression)) {
                                return true;
                            }
                            PsiElement grandParent = parent.getParent();
                            if (!(grandParent instanceof PsiMethodCallExpression)) {
                                return true;
                            }
                            PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) grandParent;
                            PsiExpressionList argumentList = methodCallExpression.getArgumentList();
                            PsiExpression[] arguments = argumentList.getExpressions();
                            for (PsiExpression argument : arguments) {
                                argument.accept(ReferenceFinder.this);
                            }
                            return true;
                        }
                    });
                    PsiExpression initializer = variable.getInitializer();
                    if (initializer != null) {
                        initializer.accept(this);
                    }
                }
                return;
            }
            if (ignoreGetMessage) {
                argumentsContainCatchParameter = true;
            }
            else {
                PsiElement parent = expression.getParent();
                if (parent instanceof PsiReferenceExpression) {
                    PsiElement grandParent = parent.getParent();
                    if (grandParent instanceof PsiMethodCallExpression) {
                        return;
                    }
                }
                argumentsContainCatchParameter = true;
            }
        }

        public boolean usesParameter() {
            return argumentsContainCatchParameter;
        }
    }
}