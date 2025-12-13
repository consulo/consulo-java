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
package com.siyeh.ig;

import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.document.util.TextRange;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiWhiteSpace;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

public abstract class BaseInspectionVisitor extends JavaElementVisitor {
    private BaseInspection inspection = null;
    private boolean onTheFly = false;
    private ProblemsHolder holder = null;

    final void setInspection(BaseInspection inspection) {
        this.inspection = inspection;
    }

    final void setOnTheFly(boolean onTheFly) {
        this.onTheFly = onTheFly;
    }

    public final boolean isOnTheFly() {
        return onTheFly;
    }

    @RequiredReadAction
    protected final void registerNewExpressionError(@Nonnull PsiNewExpression expression, Object... infos) {
        PsiJavaCodeReferenceElement classReference = expression.getClassOrAnonymousClassReference();
        if (classReference == null) {
            registerError(expression, infos);
        }
        else {
            registerError(classReference, infos);
        }
    }

    @RequiredReadAction
    protected final void registerMethodCallError(@Nonnull PsiMethodCallExpression expression, Object... infos) {
        PsiReferenceExpression methodExpression = expression.getMethodExpression();
        PsiElement nameToken = methodExpression.getReferenceNameElement();
        if (nameToken == null) {
            registerError(expression, infos);
        }
        else {
            registerError(nameToken, infos);
        }
    }

    @RequiredReadAction
    protected final void registerStatementError(@Nonnull PsiStatement statement, Object... infos) {
        PsiElement statementToken = statement.getFirstChild();
        if (statementToken == null) {
            registerError(statement, infos);
        }
        else {
            registerError(statementToken, infos);
        }
    }

    @RequiredReadAction
    protected final void registerClassError(@Nonnull PsiClass aClass, Object... infos) {
        PsiElement nameIdentifier;
        if (aClass instanceof PsiEnumConstantInitializer enumConstantInitializer) {
            PsiEnumConstant enumConstant = enumConstantInitializer.getEnumConstant();
            nameIdentifier = enumConstant.getNameIdentifier();
        }
        else if (aClass instanceof PsiAnonymousClass anonymousClass) {
            nameIdentifier = anonymousClass.getBaseClassReference();
        }
        else {
            nameIdentifier = aClass.getNameIdentifier();
        }
        if (nameIdentifier != null && !nameIdentifier.isPhysical()) {
            nameIdentifier = nameIdentifier.getNavigationElement();
        }
        if (nameIdentifier == null || !nameIdentifier.isPhysical()) {
            registerError(aClass.getContainingFile(), infos);
        }
        else {
            registerError(nameIdentifier, infos);
        }
    }

    @RequiredReadAction
    protected final void registerMethodError(@Nonnull PsiMethod method, Object... infos) {
        PsiElement nameIdentifier = method.getNameIdentifier();
        if (nameIdentifier == null) {
            registerError(method.getContainingFile(), infos);
        }
        else {
            registerError(nameIdentifier, infos);
        }
    }

    @RequiredReadAction
    protected final void registerVariableError(@Nonnull PsiVariable variable, Object... infos) {
        PsiElement nameIdentifier = variable.getNameIdentifier();
        if (nameIdentifier == null) {
            registerError(variable, infos);
        }
        else {
            registerError(nameIdentifier, infos);
        }
    }

    @RequiredReadAction
    protected final void registerTypeParameterError(@Nonnull PsiTypeParameter typeParameter, Object... infos) {
        PsiElement nameIdentifier = typeParameter.getNameIdentifier();
        if (nameIdentifier == null) {
            registerError(typeParameter, infos);
        }
        else {
            registerError(nameIdentifier, infos);
        }
    }

    @RequiredReadAction
    protected final void registerFieldError(@Nonnull PsiField field, Object... infos) {
        PsiElement nameIdentifier = field.getNameIdentifier();
        registerError(nameIdentifier, infos);
    }

    @RequiredReadAction
    protected final void registerModifierError(@Nonnull String modifier, @Nonnull PsiModifierListOwner parameter, Object... infos) {
        PsiModifierList modifiers = parameter.getModifierList();
        if (modifiers == null) {
            return;
        }
        PsiElement[] children = modifiers.getChildren();
        for (PsiElement child : children) {
            String text = child.getText();
            if (modifier.equals(text)) {
                registerError(child, infos);
            }
        }
    }

    @RequiredReadAction
    protected final void registerClassInitializerError(@Nonnull PsiClassInitializer initializer, Object... infos) {
        PsiCodeBlock body = initializer.getBody();
        PsiJavaToken lBrace = body.getLBrace();
        if (lBrace == null) {
            registerError(initializer, infos);
        }
        else {
            registerError(lBrace, infos);
        }
    }

    @RequiredReadAction
    protected final void registerError(@Nonnull PsiElement location, Object... infos) {
        registerError(location, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, infos);
    }

    @RequiredReadAction
    protected final void registerError(@Nonnull PsiElement location, ProblemHighlightType highlightType, Object... infos) {
        if (location.getTextLength() == 0 && !(location instanceof PsiFile)) {
            return;
        }
        InspectionGadgetsFix[] fixes = createFixes(infos);
        for (InspectionGadgetsFix fix : fixes) {
            fix.setOnTheFly(onTheFly);
        }
        String description = inspection.buildErrorString(infos);
        holder.newProblem(LocalizeValue.of(description))
            .range(location)
            .withFixes(fixes)
            .highlightType(highlightType)
            .create();
    }

    @RequiredReadAction
    protected final void registerErrorAtOffset(@Nonnull PsiElement location, int offset, int length, Object... infos) {
        if (location.getTextLength() == 0 || length == 0) {
            return;
        }
        InspectionGadgetsFix[] fixes = createFixes(infos);
        for (InspectionGadgetsFix fix : fixes) {
            fix.setOnTheFly(onTheFly);
        }
        holder.newProblem(LocalizeValue.localizeTODO(inspection.buildErrorString(infos)))
            .range(location, new TextRange(offset, offset + length))
            .withFixes(fixes)
            .create();
    }

    @Nonnull
    private InspectionGadgetsFix[] createFixes(Object... infos) {
        if (!onTheFly && inspection.buildQuickFixesOnlyForOnTheFlyErrors()) {
            return InspectionGadgetsFix.EMPTY_ARRAY;
        }
        InspectionGadgetsFix[] fixes = inspection.buildFixes(infos);
        if (fixes.length > 0) {
            return fixes;
        }
        InspectionGadgetsFix fix = inspection.buildFix(infos);
        if (fix == null) {
            return InspectionGadgetsFix.EMPTY_ARRAY;
        }
        return new InspectionGadgetsFix[]{fix};
    }

    @Override
    public void visitReferenceExpression(@Nonnull PsiReferenceExpression expression) {
        visitExpression(expression);
    }

    @Override
    public final void visitWhiteSpace(PsiWhiteSpace space) {
        // none of our inspections need to do anything with white space,
        // so this is a performance optimization
    }

    public final void setProblemsHolder(ProblemsHolder holder) {
        this.holder = holder;
    }
}