// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.codeInsight.completion;

import com.intellij.java.analysis.codeInsight.guess.GuessManager;
import com.intellij.java.impl.codeInsight.ExpectedTypeInfo;
import com.intellij.java.impl.codeInsight.ExpectedTypeInfoImpl;
import com.intellij.java.impl.codeInsight.ExpectedTypesProvider;
import com.intellij.java.impl.codeInsight.completion.simple.RParenthTailType;
import com.intellij.java.impl.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.java.language.impl.psi.impl.source.tree.java.PsiEmptyExpressionImpl;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.util.matcher.PrefixMatcher;
import consulo.codeEditor.Editor;
import consulo.codeEditor.ScrollType;
import consulo.document.Document;
import consulo.externalService.statistic.FeatureUsageTracker;
import consulo.language.codeStyle.CommonCodeStyleSettings;
import consulo.language.codeStyle.PostprocessReformattingAspect;
import consulo.language.editor.completion.*;
import consulo.language.editor.completion.lookup.InsertionContext;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementDecorator;
import consulo.language.editor.completion.lookup.TailType;
import consulo.language.pattern.ElementPattern;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiErrorElement;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ProcessingContext;
import consulo.util.collection.ContainerUtil;

import jakarta.annotation.Nonnull;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static consulo.language.pattern.PlatformPatterns.psiElement;

/**
 * @author peter
 */
class SmartCastProvider implements CompletionProvider {
    static final ElementPattern<PsiElement> TYPECAST_TYPE_CANDIDATE = psiElement().afterLeaf("(");

    static boolean shouldSuggestCast(CompletionParameters parameters) {
        PsiElement position = parameters.getPosition();
        PsiElement parent = getParenthesisOwner(position);
        if (parent instanceof PsiTypeCastExpression) {
            return true;
        }
        if (parent instanceof PsiParenthesizedExpression) {
            return parameters.getOffset() == position.getTextRange().getStartOffset();
        }
        return false;
    }

    private static PsiElement getParenthesisOwner(PsiElement position) {
        PsiElement lParen = PsiTreeUtil.prevVisibleLeaf(position);
        return lParen == null || !lParen.textMatches("(") ? null : lParen.getParent();
    }

    @RequiredReadAction
    @Override
    public void addCompletions(
        @Nonnull final CompletionParameters parameters,
        @Nonnull final ProcessingContext context,
        @Nonnull final CompletionResultSet result
    ) {
        addCastVariants(parameters, result.getPrefixMatcher(), result, false);
    }

    static void addCastVariants(
        @Nonnull CompletionParameters parameters,
        PrefixMatcher matcher,
        @Nonnull Consumer<? super LookupElement> result,
        boolean quick
    ) {
        if (!shouldSuggestCast(parameters)) {
            return;
        }

        PsiElement position = parameters.getPosition();
        PsiElement parenthesisOwner = getParenthesisOwner(position);
        final boolean insideCast = parenthesisOwner instanceof PsiTypeCastExpression;

        if (insideCast) {
            PsiElement parent = parenthesisOwner.getParent();
            if (parent instanceof PsiParenthesizedExpression) {
                if (parent.getParent() instanceof PsiReferenceExpression) {
                    for (ExpectedTypeInfo info : ExpectedTypesProvider.getExpectedTypes((PsiParenthesizedExpression)parent, false)) {
                        result.accept(PsiTypeLookupItem.createLookupItem(info.getType(), parent));
                    }
                }
                for (ExpectedTypeInfo info : getParenthesizedCastExpectationByOperandType(position)) {
                    addHierarchyTypes(
                        parameters,
                        matcher,
                        info,
                        type -> result.accept(PsiTypeLookupItem.createLookupItem(type, parent)),
                        quick
                    );
                }
                return;
            }
        }

        for (final ExpectedTypeInfo info : JavaSmartCompletionContributor.getExpectedTypes(parameters)) {
            PsiType type = info.getDefaultType();
            if (type instanceof PsiWildcardType) {
                type = ((PsiWildcardType)type).getBound();
            }

            if (type == null || PsiType.VOID.equals(type)) {
                continue;
            }

            if (type instanceof PsiPrimitiveType) {
                final PsiType castedType = getCastedExpressionType(parenthesisOwner);
                if (castedType != null && !(castedType instanceof PsiPrimitiveType)) {
                    final PsiClassType boxedType = ((PsiPrimitiveType)type).getBoxedType(position);
                    if (boxedType != null) {
                        type = boxedType;
                    }
                }
            }
            result.accept(createSmartCastElement(parameters, insideCast, type));
        }
    }

    @Nonnull
    static List<ExpectedTypeInfo> getParenthesizedCastExpectationByOperandType(PsiElement position) {
        PsiElement parenthesisOwner = getParenthesisOwner(position);
        PsiExpression operand = getCastedExpression(parenthesisOwner);
        if (operand == null || !(parenthesisOwner.getParent() instanceof PsiParenthesizedExpression)) {
            return Collections.emptyList();
        }

        List<PsiType> dfaTypes = GuessManager.getInstance(operand.getProject()).getControlFlowExpressionTypeConjuncts(operand);
        if (!dfaTypes.isEmpty()) {
            return ContainerUtil.map(dfaTypes, dfaType ->
                new ExpectedTypeInfoImpl(dfaType, ExpectedTypeInfo.TYPE_OR_SUPERTYPE, dfaType, TailType.NONE, null, () -> null));
        }

        PsiType type = operand.getType();
        return type == null || type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)
            ? Collections.emptyList()
            : Collections.singletonList(new ExpectedTypeInfoImpl(
                type,
                ExpectedTypeInfo.TYPE_OR_SUBTYPE,
                type,
                TailType.NONE,
                null,
                () -> null
            ));
    }

    private static void addHierarchyTypes(
        CompletionParameters parameters,
        PrefixMatcher matcher,
        ExpectedTypeInfo info,
        Consumer<? super PsiType> result,
        boolean quick
    ) {
        PsiType infoType = info.getType();
        PsiClass infoClass = PsiUtil.resolveClassInClassTypeOnly(infoType);
        if (info.getKind() == ExpectedTypeInfo.TYPE_OR_SUPERTYPE) {
            InheritanceUtil.processSupers(infoClass, true, superClass -> {
                if (!CommonClassNames.JAVA_LANG_OBJECT.equals(superClass.getQualifiedName())) {
                    result.accept(JavaPsiFacade.getElementFactory(superClass.getProject())
                        .createType(CompletionUtilCore.getOriginalOrSelf(superClass)));
                }
                return true;
            });
        }
        else if (infoType instanceof PsiClassType && !quick) {
            JavaInheritorsGetter.processInheritors(parameters, Collections.singleton((PsiClassType)infoType), matcher, type -> {
                if (!infoType.equals(type)) {
                    result.accept(type);
                }
            });
        }
    }

    private static PsiType getCastedExpressionType(PsiElement parenthesisOwner) {
        PsiExpression operand = getCastedExpression(parenthesisOwner);
        return operand == null ? null : operand.getType();
    }

    private static PsiExpression getCastedExpression(PsiElement parenthesisOwner) {
        if (parenthesisOwner instanceof PsiTypeCastExpression) {
            return ((PsiTypeCastExpression)parenthesisOwner).getOperand();
        }

        if (parenthesisOwner instanceof PsiParenthesizedExpression) {
            PsiElement next = parenthesisOwner.getNextSibling();
            while ((next instanceof PsiEmptyExpressionImpl || next instanceof PsiErrorElement || next instanceof PsiWhiteSpace)) {
                next = next.getNextSibling();
            }
            if (next instanceof PsiExpression) {
                return (PsiExpression)next;
            }
        }
        return null;
    }

    private static LookupElement createSmartCastElement(
        final CompletionParameters parameters,
        final boolean overwrite,
        final PsiType type
    ) {
        return AutoCompletionPolicy.ALWAYS_AUTOCOMPLETE.applyPolicy(new LookupElementDecorator<PsiTypeLookupItem>(
            PsiTypeLookupItem.createLookupItem(type, parameters.getPosition())) {

            @Override
            public void handleInsert(@Nonnull InsertionContext context) {
                FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.smarttype.casting");

                final Editor editor = context.getEditor();
                final Document document = editor.getDocument();
                if (overwrite) {
                    document.deleteString(
                        context.getSelectionEndOffset(),
                        context.getOffsetMap().getOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET)
                    );
                }

                final CommonCodeStyleSettings csSettings = CompletionStyleUtil.getCodeStyleSettings(context);
                final int oldTail = context.getTailOffset();
                context.setTailOffset(RParenthTailType.addRParenth(editor, oldTail, csSettings.SPACE_WITHIN_CAST_PARENTHESES));

                getDelegate().handleInsert(CompletionUtilCore.newContext(context, getDelegate(), context.getStartOffset(), oldTail));

                PostprocessReformattingAspect.getInstance(context.getProject()).doPostponedFormatting();
                if (csSettings.SPACE_AFTER_TYPE_CAST) {
                    context.setTailOffset(TailType.insertChar(editor, context.getTailOffset(), ' '));
                }

                if (parameters.getCompletionType() == CompletionType.SMART) {
                    editor.getCaretModel().moveToOffset(context.getTailOffset());
                }
                editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
            }
        });
    }
}
