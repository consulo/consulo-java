/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.jdk;

import com.intellij.java.impl.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.intellij.java.analysis.codeInspection.ParenthesesUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class ForeachStatementInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.extendedForStatementDisplayName();
    }

    @Nonnull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.extendedForStatementProblemDescriptor().get();
    }

    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new ForEachFix();
    }

    private static class ForEachFix extends InspectionGadgetsFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.extendedForStatementReplaceQuickfix();
        }

        public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            PsiElement element = descriptor.getPsiElement();
            PsiForeachStatement statement = (PsiForeachStatement) element.getParent();
            JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
            assert statement != null;
            PsiExpression iteratedValue = statement.getIteratedValue();
            if (iteratedValue == null) {
                return;
            }
            @NonNls StringBuilder newStatement = new StringBuilder();
            PsiParameter iterationParameter = statement.getIterationParameter();
            JavaCodeStyleSettings codeStyleSettings =
                CodeStyleSettingsManager.getSettings(project).getCustomSettings(JavaCodeStyleSettings.class);
            if (iteratedValue.getType() instanceof PsiArrayType) {
                PsiType type = iterationParameter.getType();
                String index = codeStyleManager.suggestUniqueVariableName("i", statement, true);
                newStatement.append("for(int ").append(index).append(" = 0;");
                newStatement.append(index).append('<').append(iteratedValue.getText()).append(".length;");
                newStatement.append(index).append("++)").append("{ ");
                if (codeStyleSettings.GENERATE_FINAL_LOCALS) {
                    newStatement.append("final ");
                }
                newStatement.append(type.getCanonicalText()).append(' ').append(iterationParameter.getName());
                newStatement.append(" = ").append(iteratedValue.getText()).append('[').append(index).append("];");
            }
            else {
                @NonNls StringBuilder methodCall = new StringBuilder();
                if (ParenthesesUtils.getPrecedence(iteratedValue) > ParenthesesUtils.METHOD_CALL_PRECEDENCE) {
                    methodCall.append('(').append(iteratedValue.getText()).append(')');
                }
                else {
                    methodCall.append(iteratedValue.getText());
                }
                methodCall.append(".iterator()");
                PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
                PsiExpression iteratorCall = factory.createExpressionFromText(methodCall.toString(), iteratedValue);
                PsiType variableType = GenericsUtil.getVariableTypeByExpressionType(iteratorCall.getType());
                if (variableType == null) {
                    return;
                }
                PsiType parameterType = iterationParameter.getType();
                String typeText = parameterType.getCanonicalText();
                newStatement.append("for(").append(variableType.getCanonicalText()).append(' ');
                String iterator = codeStyleManager.suggestUniqueVariableName("iterator", statement, true);
                newStatement.append(iterator).append("=").append(iteratorCall.getText()).append(';');
                newStatement.append(iterator).append(".hasNext();){");
                if (codeStyleSettings.GENERATE_FINAL_LOCALS) {
                    newStatement.append("final ");
                }
                newStatement.append(typeText)
                    .append(' ')
                    .append(iterationParameter.getName())
                    .append(" = ")
                    .append(iterator)
                    .append(".next();");
            }
            PsiStatement body = statement.getBody();
            if (body instanceof PsiBlockStatement) {
                PsiBlockStatement blockStatement = (PsiBlockStatement) body;
                PsiCodeBlock block = blockStatement.getCodeBlock();
                PsiElement[] children = block.getChildren();
                for (int i = 1; i < children.length - 1; i++) {
                    //skip the braces
                    newStatement.append(children[i].getText());
                }
            }
            else {
                String bodyText;
                if (body == null) {
                    bodyText = "";
                }
                else {
                    bodyText = body.getText();
                }
                newStatement.append(bodyText);
            }
            newStatement.append('}');
            replaceStatementAndShortenClassNames(statement, newStatement.toString());
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ForeachStatementVisitor();
    }

    private static class ForeachStatementVisitor extends BaseInspectionVisitor {

        @Override
        public void visitForeachStatement(@Nonnull PsiForeachStatement statement) {
            super.visitForeachStatement(statement);
            PsiExpression iteratedValue = statement.getIteratedValue();
            if (iteratedValue == null || !InheritanceUtil.isInheritor(iteratedValue.getType(), CommonClassNames.JAVA_LANG_ITERABLE)) {
                return;
            }
            registerStatementError(statement);
        }
    }
}