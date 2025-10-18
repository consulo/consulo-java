// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.codeInsight.template.postfix.templates;

import com.intellij.java.impl.codeInsight.generation.surroundWith.JavaExpressionSurrounder;
import com.intellij.java.impl.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils;
import com.intellij.java.language.JavaFeature;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.dumb.DumbAware;
import consulo.codeEditor.Editor;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.java.localize.JavaLocalize;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.CodeInsightUtilCore;
import consulo.language.editor.refactoring.postfixTemplate.PostfixTemplateExpressionSelector;
import consulo.language.editor.refactoring.postfixTemplate.PostfixTemplateExpressionSelectorBase;
import consulo.language.editor.refactoring.postfixTemplate.SurroundPostfixTemplateBase;
import consulo.language.editor.surroundWith.Surrounder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.DumbService;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public class SwitchStatementPostfixTemplate extends SurroundPostfixTemplateBase implements DumbAware {
    private static final Predicate<PsiElement> SWITCH_TYPE = e -> {
        if (!(e instanceof PsiExpression expression)) {
            return false;
        }

        return DumbService.getInstance(expression.getProject()).computeWithAlternativeResolveEnabled(() -> {
            PsiType type = expression.getType();

            if (type == null) {
                return false;
            }
            if (PsiTypes.intType().isAssignableFrom(type)) {
                return true;
            }
            if (type instanceof PsiClassType classType) {
                if (PsiUtil.isAvailable(JavaFeature.PATTERNS_IN_SWITCH, expression)) {
                    return true;
                }

                PsiClass psiClass = classType.resolve();
                if (psiClass != null && psiClass.isEnum()) {
                    return true;
                }
            }

            if (type.equalsToText(CommonClassNames.JAVA_LANG_STRING) && expression.getContainingFile() instanceof PsiJavaFile javaFile) {
                if (PsiUtil.isAvailable(JavaFeature.STRING_SWITCH, javaFile)) {
                    return true;
                }
            }

            return false;
        });
    };

    public SwitchStatementPostfixTemplate() {
        super("switch", "switch(expr)", JavaPostfixTemplatesUtils.JAVA_PSI_INFO, selectorTopmost(SWITCH_TYPE));
    }

    @Nonnull
    @Override
    protected Surrounder getSurrounder() {
        return new JavaExpressionSurrounder() {
            @Override
            public boolean isApplicable(PsiExpression expr) {
                return expr.isPhysical() && SWITCH_TYPE.test(expr);
            }

            @Override
            @RequiredReadAction
            public TextRange surroundExpression(Project project, Editor editor, PsiExpression expr) throws IncorrectOperationException {
                PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
                CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);

                PsiElement parent = expr.getParent();
                if (parent instanceof PsiExpressionStatement) {
                    PsiSwitchStatement switchStatement = (PsiSwitchStatement)factory.createStatementFromText("switch(1){case 1:}", null);
                    return postprocessSwitch(editor, expr, codeStyleManager, parent, switchStatement);
                }
                else if (PsiUtil.isAvailable(JavaFeature.ENHANCED_SWITCH, expr)) {
                    PsiSwitchExpression switchExpression =
                        (PsiSwitchExpression)factory.createExpressionFromText("switch(1){case 1->1;}", null);
                    return postprocessSwitch(editor, expr, codeStyleManager, expr, switchExpression);
                }

                return TextRange.from(editor.getCaretModel().getOffset(), 0);
            }

            @Nonnull
            @RequiredReadAction
            private static TextRange postprocessSwitch(
                Editor editor,
                PsiExpression expr,
                CodeStyleManager codeStyleManager,
                PsiElement toReplace,
                PsiSwitchBlock switchBlock
            ) {
                switchBlock = (PsiSwitchBlock)codeStyleManager.reformat(switchBlock);
                PsiExpression selectorExpression = switchBlock.getExpression();
                if (selectorExpression != null) {
                    selectorExpression.replace(expr);
                }

                switchBlock = (PsiSwitchBlock)toReplace.replace(switchBlock);

                PsiCodeBlock body = switchBlock.getBody();
                if (body != null) {
                    body = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(body);
                    if (body != null) {
                        TextRange range = body.getStatements()[0].getTextRange();
                        editor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());
                        return TextRange.from(range.getStartOffset(), 0);
                    }
                }
                return TextRange.from(editor.getCaretModel().getOffset(), 0);
            }

            @Nonnull
            @Override
            public LocalizeValue getTemplateDescription() {
                return JavaLocalize.switchStmtTemplateDescription();
            }
        };
    }

    public static PostfixTemplateExpressionSelector selectorTopmost(Predicate<? super PsiElement> additionalFilter) {
        return new PostfixTemplateExpressionSelectorBase(additionalFilter) {
            @Override
            @RequiredReadAction
            protected List<PsiElement> getNonFilteredExpressions(@Nonnull PsiElement context, @Nonnull Document document, int offset) {
                boolean isEnhancedSwitchAvailable = PsiUtil.isAvailable(JavaFeature.ENHANCED_SWITCH, context);
                List<PsiElement> result = new ArrayList<>();

                for (PsiElement element = PsiTreeUtil.getNonStrictParentOfType(context, PsiExpression.class, PsiStatement.class);
                     element instanceof PsiExpression; element = element.getParent()) {
                    PsiElement parent = element.getParent();
                    if (parent instanceof PsiExpressionStatement) {
                        result.add(element);
                    }
                    else if (isEnhancedSwitchAvailable
                        && (isVariableInitializer(element, parent)
                        || isRightSideOfAssignment(element, parent)
                        || isReturnValue(element, parent)
                        || isArgumentList(parent))) {
                        result.add(element);
                    }
                }
                return result;
            }

            @Override
            protected Predicate<PsiElement> getFilters(int offset) {
                return super.getFilters(offset).and(getPsiErrorFilter());
            }

            @Nonnull
            @Override
            public Function<PsiElement, String> getRenderer() {
                return JavaPostfixTemplatesUtils.getRenderer();
            }

            private static boolean isVariableInitializer(PsiElement element, PsiElement parent) {
                return parent instanceof PsiVariable variable && variable.getInitializer() == element;
            }

            private static boolean isRightSideOfAssignment(PsiElement element, PsiElement parent) {
                return parent instanceof PsiAssignmentExpression assignment && assignment.getRExpression() == element;
            }

            private static boolean isReturnValue(PsiElement element, PsiElement parent) {
                return parent instanceof PsiReturnStatement returnStmt && returnStmt.getReturnValue() == element;
            }

            private static boolean isArgumentList(PsiElement parent) {
                return parent instanceof PsiExpressionList && parent.getParent() instanceof PsiCall;
            }
        };
    }
}
