/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.analysis.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.language.impl.psi.impl.PsiDiamondTypeUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiExpressionTrimRenderer;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.codeEditor.Editor;
import consulo.codeEditor.ScrollType;
import consulo.document.util.TextRange;
import consulo.document.util.UnfairTextRange;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.editor.rawHighlight.HighlightInfo;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public class ChangeNewOperatorTypeFix implements SyntheticIntentionAction {
    private final PsiType myType;
    private final PsiNewExpression myExpression;

    private ChangeNewOperatorTypeFix(PsiType type, PsiNewExpression expression) {
        myType = type;
        myExpression = expression;
    }

    @Nonnull
    @Override
    public LocalizeValue getText() {
        return JavaQuickFixLocalize.changeNewOperatorTypeText(
            new PsiExpressionTrimRenderer.RenderFunction().apply(myExpression),
            myType.getPresentableText(),
            myType instanceof PsiArrayType ? "" : "()"
        );
    }

    @Override
    public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
        return myType.isValid()
            && myExpression.isValid()
            && myExpression.getManager().isInProject(myExpression)
            && !TypeConversionUtil.isPrimitiveAndNotNull(myType)
            && (myType instanceof PsiArrayType || myExpression.getArgumentList() != null);
    }

    @Override
    @RequiredUIAccess
    public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        if (!FileModificationService.getInstance().prepareFileForWrite(file)) {
            return;
        }
        changeNewOperatorType(myExpression, myType, editor);
    }

    @RequiredUIAccess
    private static void changeNewOperatorType(PsiNewExpression originalExpression, PsiType toType, Editor editor)
        throws IncorrectOperationException {
        PsiNewExpression newExpression;
        PsiElementFactory factory = JavaPsiFacade.getInstance(originalExpression.getProject()).getElementFactory();
        int caretOffset;
        TextRange selection;
        if (toType instanceof PsiArrayType) {
            final PsiExpression[] originalExpressionArrayDimensions = originalExpression.getArrayDimensions();
            caretOffset = 0;
            String text = "new " + toType.getDeepComponentType().getCanonicalText() + "[";
            if (originalExpressionArrayDimensions.length > 0) {
                text += originalExpressionArrayDimensions[0].getText();
            }
            else {
                text += "0";
                caretOffset = -2;
            }
            text += "]";
            for (int i = 1; i < toType.getArrayDimensions(); i++) {
                text += "[";
                String arrayDimension = "";
                if (originalExpressionArrayDimensions.length > i) {
                    arrayDimension = originalExpressionArrayDimensions[i].getText();
                    text += arrayDimension;
                }
                text += "]";
                if (caretOffset < 0) {
                    caretOffset -= arrayDimension.length() + 2;
                }
            }

            newExpression = (PsiNewExpression)factory.createExpressionFromText(text, originalExpression);
            if (caretOffset < 0) {
                selection = new UnfairTextRange(caretOffset, caretOffset + 1);
            }
            else {
                selection = null;
            }
        }
        else {
            PsiAnonymousClass anonymousClass = originalExpression.getAnonymousClass();
            newExpression = (PsiNewExpression)factory.createExpressionFromText(
                "new " + toType.getCanonicalText() + "()" + (anonymousClass != null ? "{}" : ""),
                originalExpression
            );
            PsiExpressionList argumentList = originalExpression.getArgumentList();
            if (argumentList == null) {
                return;
            }
            newExpression.getArgumentList().replace(argumentList);
            if (anonymousClass == null) { //just to prevent useless inference
                if (PsiDiamondTypeUtil.canCollapseToDiamond(newExpression, originalExpression, toType)) {
                    PsiElement paramList =
                        PsiDiamondTypeUtil.replaceExplicitWithDiamond(newExpression.getClassOrAnonymousClassReference().getParameterList());
                    newExpression = PsiTreeUtil.getParentOfType(paramList, PsiNewExpression.class);
                }
            }

            if (anonymousClass != null) {
                PsiAnonymousClass newAnonymousClass = newExpression.getAnonymousClass();
                PsiElement childInside = anonymousClass.getLBrace().getNextSibling();
                if (childInside != null) {
                    newAnonymousClass.addRange(childInside, anonymousClass.getRBrace().getPrevSibling());
                }
            }
            selection = null;
            caretOffset = -1;
        }
        PsiElement element = originalExpression.replace(newExpression);
        editor.getCaretModel().moveToOffset(element.getTextRange().getEndOffset() + caretOffset);
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
        if (selection != null) {
            selection = selection.shiftRight(element.getTextRange().getEndOffset());
            editor.getSelectionModel().setSelection(selection.getStartOffset(), selection.getEndOffset());
        }
    }

    @Override
    public boolean startInWriteAction() {
        return true;
    }

    @RequiredReadAction
    public static void register(@Nonnull HighlightInfo.Builder highlightInfo, PsiExpression expression, PsiType lType) {
        if (!(PsiUtil.deparenthesizeExpression(expression) instanceof PsiNewExpression newExpr)) {
            return;
        }
        PsiType newType = lType;
        if (newExpr.getType() instanceof PsiClassType rClassType && newType instanceof PsiClassType lClassType) {
            PsiClassType.ClassResolveResult rResolveResult = rClassType.resolveGenerics();
            PsiClass rClass = rResolveResult.getElement();
            if (rClass instanceof PsiAnonymousClass anonymousClass) {
                rClass = anonymousClass.getBaseClassType().resolve();
            }
            if (rClass != null) {
                PsiClassType.ClassResolveResult lResolveResult = lClassType.resolveGenerics();
                PsiClass lClass = lResolveResult.getElement();
                if (lClass != null) {
                    PsiSubstitutor substitutor =
                        getInheritorSubstitutorForNewExpression(lClass, rClass, lResolveResult.getSubstitutor(), newExpr);
                    if (substitutor != null) {
                        newType = JavaPsiFacade.getInstance(lClass.getProject()).getElementFactory().createType(rClass, substitutor);
                    }
                }
            }
        }
        highlightInfo.registerFix(new ChangeNewOperatorTypeFix(newType, newExpr));
    }

    /* Guesswork */
    @Nullable
    @RequiredReadAction
    private static PsiSubstitutor getInheritorSubstitutorForNewExpression(
        PsiClass baseClass,
        PsiClass inheritor,
        PsiSubstitutor baseSubstitutor,
        PsiElement context
    ) {
        Project project = baseClass.getProject();
        JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        PsiResolveHelper resolveHelper = facade.getResolveHelper();
        PsiSubstitutor superSubstitutor = TypeConversionUtil.getClassSubstitutor(baseClass, inheritor, PsiSubstitutor.EMPTY);
        if (superSubstitutor == null) {
            return null;
        }
        PsiSubstitutor inheritorSubstitutor = PsiSubstitutor.EMPTY;
        for (PsiTypeParameter inheritorParameter : PsiUtil.typeParametersIterable(inheritor)) {
            for (PsiTypeParameter baseParameter : PsiUtil.typeParametersIterable(baseClass)) {
                PsiType substituted = superSubstitutor.substitute(baseParameter);
                PsiType arg = baseSubstitutor.substitute(baseParameter);
                if (arg instanceof PsiWildcardType wildcardType) {
                    arg = wildcardType.getBound();
                }
                PsiType substitution = resolveHelper.getSubstitutionForTypeParameter(
                    inheritorParameter,
                    substituted,
                    arg,
                    true,
                    PsiUtil.getLanguageLevel(context)
                );
                if (PsiType.NULL.equals(substitution)) {
                    continue;
                }
                if (substitution == null) {
                    return facade.getElementFactory().createRawSubstitutor(inheritor);
                }
                inheritorSubstitutor = inheritorSubstitutor.put(inheritorParameter, substitution);
                break;
            }
        }

        return inheritorSubstitutor;
    }
}
