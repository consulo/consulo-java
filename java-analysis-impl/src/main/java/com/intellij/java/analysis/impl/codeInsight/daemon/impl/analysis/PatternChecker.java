// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis;

import com.intellij.java.language.JavaFeature;
import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.java.language.impl.psi.util.JavaPatternExhaustivenessUtil;
import com.intellij.java.language.impl.psi.util.JavaPsiPatternUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.document.util.TextRange;
import consulo.java.language.localize.JavaCompilationErrorLocalize;
import consulo.language.editor.rawHighlight.HighlightInfo;
import consulo.language.editor.rawHighlight.HighlightInfoType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.util.collection.ContainerUtil;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.function.Consumer;

import static com.intellij.java.language.psi.PsiModifier.SEALED;
import static consulo.util.lang.ObjectUtil.tryCast;

final class PatternChecker {
    private PatternChecker() {
    }

    @RequiredReadAction
    static HighlightInfo.@Nullable Builder checkDeconstructionVariable(PsiPatternVariable variable) {
        if (variable.getPattern() instanceof PsiDeconstructionPattern) {
            return error(variable.getTextRange(), JavaCompilationErrorLocalize.patternDeconstructionVariable());
        }
        return null;
    }

    @RequiredReadAction
    static HighlightInfo.@Nullable Builder checkPatternVariableRequired(
        PsiReferenceExpression expression,
        JavaResolveResult resultForIncompleteCode
    ) {
        if (!(expression.getParent() instanceof PsiCaseLabelElementList)) {
            return null;
        }
        PsiClass resolved = tryCast(resultForIncompleteCode.getElement(), PsiClass.class);
        if (resolved == null) {
            return null;
        }
        return error(expression.getTextRange(), JavaCompilationErrorLocalize.patternTypePatternExpected());
    }

    @RequiredReadAction
    static void checkDeconstructionPattern(
        PsiDeconstructionPattern deconstructionPattern,
        LanguageLevel languageLevel,
        PsiFile file,
        Consumer<HighlightInfo.Builder> sink
    ) {
        PsiTreeUtil.processElements(deconstructionPattern.getTypeElement(), PsiAnnotation.class, annotation -> {
            sink.accept(error(annotation.getTextRange(), JavaCompilationErrorLocalize.patternDeconstructionAnnotation()));
            return true;
        });
        PsiElement parent = deconstructionPattern.getParent();
        if (parent instanceof PsiForeachPatternStatement forEach) {
            HighlightInfo.Builder feature =
                HighlightUtil.checkFeature(deconstructionPattern, JavaFeature.RECORD_PATTERNS_IN_FOR_EACH, languageLevel, file);
            if (feature != null) {
                sink.accept(feature);
                return;
            }
            PsiTypeElement typeElement = JavaPsiPatternUtil.getPatternTypeElement(deconstructionPattern);
            if (typeElement == null) {
                return;
            }
            PsiType patternType = typeElement.getType();
            PsiExpression iteratedValue = forEach.getIteratedValue();
            PsiType itemType = iteratedValue == null ? null : JavaGenericsUtil.getCollectionItemType(iteratedValue);
            if (itemType == null) {
                return;
            }
            if (checkForEachPatternApplicable(deconstructionPattern, patternType, itemType, file, sink)) {
                return;
            }
            PsiClass selectorClass = PsiUtil.resolveClassInClassTypeOnly(TypeConversionUtil.erasure(itemType));
            if (selectorClass != null && (selectorClass.hasModifierProperty(SEALED) || selectorClass.isRecord())) {
                if (!JavaPatternExhaustivenessUtil.checkRecordExhaustiveness(
                    Collections.singletonList(deconstructionPattern),
                    patternType,
                    forEach
                ).isExhaustive()) {
                    sink.accept(patternNotExhaustive(deconstructionPattern, itemType, patternType));
                }
            }
            else {
                sink.accept(patternNotExhaustive(deconstructionPattern, itemType, patternType));
            }
        }
        else {
            HighlightInfo.Builder feature =
                HighlightUtil.checkFeature(deconstructionPattern, JavaFeature.PATTERN_GUARDS_AND_RECORD_PATTERNS, languageLevel, file);
            if (feature != null) {
                sink.accept(feature);
            }
        }
    }

    private static HighlightInfo.Builder patternNotExhaustive(
        PsiDeconstructionPattern deconstructionPattern,
        PsiType contextType,
        PsiType patternType
    ) {
        return error(
            deconstructionPattern.getTextRange(),
            JavaCompilationErrorLocalize.patternNotExhaustive(
                JavaHighlightUtil.formatType(patternType),
                JavaHighlightUtil.formatType(contextType)
            )
        );
    }

    @RequiredReadAction
    static HighlightInfo.@Nullable Builder checkUncheckedPatternConversion(PsiPattern pattern) {
        PsiType patternType = JavaPsiPatternUtil.getPatternType(pattern);
        if (patternType == null) {
            return null;
        }
        if (pattern instanceof PsiDeconstructionPattern subPattern) {
            PsiJavaCodeReferenceElement element = subPattern.getTypeElement().getInnermostComponentReferenceElement();
            if (element != null && element.getTypeParameterCount() == 0 && patternType instanceof PsiClassType classType) {
                patternType = classType.rawType();
            }
        }
        PsiType contextType = JavaPsiPatternUtil.getContextType(pattern);
        if (contextType == null) {
            return null;
        }
        if (contextType instanceof PsiWildcardType wildcardType) {
            contextType = wildcardType.getExtendsBound();
        }
        if (!JavaGenericsUtil.isUncheckedCast(patternType, contextType)) {
            return null;
        }
        return error(
            pattern.getTextRange(),
            JavaCompilationErrorLocalize.patternUnsafeCast(contextType.getPresentableText(), patternType.getPresentableText())
        );
    }

    /**
     * @return true if an error was reported
     */
    @RequiredReadAction
    private static boolean checkForEachPatternApplicable(
        PsiDeconstructionPattern pattern,
        PsiType patternType,
        PsiType itemType,
        PsiFile file,
        Consumer<HighlightInfo.Builder> sink
    ) {
        if (!TypeConversionUtil.areTypesConvertible(itemType, patternType)) {
            HighlightInfo.Builder incompatible = reportIncompatibleType(itemType, patternType, pattern);
            if (incompatible != null) {
                sink.accept(incompatible);
            }
            return true;
        }
        HighlightInfo.Builder unchecked = checkUncheckedPatternConversion(pattern);
        if (unchecked != null) {
            sink.accept(unchecked);
            return true;
        }
        return checkDeconstructionErrors(pattern, file, sink);
    }

    /**
     * @return true if an error was reported
     */
    @RequiredReadAction
    static boolean checkDeconstructionErrors(
        @Nullable PsiDeconstructionPattern deconstructionPattern,
        PsiFile file,
        Consumer<HighlightInfo.Builder> sink
    ) {
        if (deconstructionPattern == null) {
            return false;
        }
        boolean[] reported = {false};
        Consumer<HighlightInfo.Builder> reportingSink = builder -> {
            reported[0] = true;
            sink.accept(builder);
        };
        doCheckDeconstructionErrors(deconstructionPattern, file, reportingSink);
        return reported[0];
    }

    @RequiredReadAction
    private static void doCheckDeconstructionErrors(
        PsiDeconstructionPattern deconstructionPattern,
        PsiFile file,
        Consumer<HighlightInfo.Builder> sink
    ) {
        PsiTypeElement typeElement = deconstructionPattern.getTypeElement();
        PsiType recordType = typeElement.getType();
        PsiClassType.ClassResolveResult resolveResult =
            recordType instanceof PsiClassType classType ? classType.resolveGenerics() : PsiClassType.ClassResolveResult.EMPTY;
        PsiClass recordClass = resolveResult.getElement();
        if (recordClass == null || !recordClass.isRecord()) {
            sink.accept(error(
                typeElement.getTextRange(),
                JavaCompilationErrorLocalize.patternDeconstructionRequiresRecord(JavaHighlightUtil.formatType(typeElement.getType()))
            ));
            return;
        }
        if (resolveResult.getInferenceError() != null) {
            sink.accept(error(
                typeElement.getTextRange(),
                JavaCompilationErrorLocalize.patternCannotInferType(resolveResult.getInferenceError())
            ));
            return;
        }
        PsiSubstitutor substitutor = resolveResult.getSubstitutor();
        PsiRecordComponent[] recordComponents = recordClass.getRecordComponents();
        PsiPattern[] deconstructionComponents = deconstructionPattern.getDeconstructionList().getDeconstructionComponents();
        boolean hasMismatchedPattern = false;
        for (int i = 0; i < Math.min(recordComponents.length, deconstructionComponents.length); i++) {
            PsiPattern deconstructionComponent = deconstructionComponents[i];
            PsiType recordComponentType = recordComponents[i].getType();
            PsiType substitutedRecordComponentType = substitutor.substitute(recordComponentType);
            PsiType deconstructionComponentType = JavaPsiPatternUtil.getPatternType(deconstructionComponent);
            LanguageLevel languageLevel = PsiUtil.getLanguageLevel(deconstructionPattern);
            if (!isApplicableForRecordComponent(substitutedRecordComponentType, deconstructionComponentType, languageLevel)) {
                hasMismatchedPattern = true;
                if (recordComponents.length == deconstructionComponents.length) {
                    if (isApplicableForRecordComponent(
                        substitutedRecordComponentType,
                        deconstructionComponentType,
                        JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS.getMinimumLevel()
                    )) {
                        HighlightInfo.Builder feature = HighlightUtil.checkFeature(
                            deconstructionComponent,
                            JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS,
                            languageLevel,
                            file
                        );
                        if (feature != null) {
                            sink.accept(feature);
                        }
                        continue;
                    }
                    else if ((substitutedRecordComponentType instanceof PsiPrimitiveType
                        || deconstructionComponentType instanceof PsiPrimitiveType)
                        && JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS.isSufficient(languageLevel)) {
                        sink.accept(error(
                            deconstructionComponent.getTextRange(),
                            JavaCompilationErrorLocalize.castInconvertible(
                                JavaHighlightUtil.formatType(substitutedRecordComponentType),
                                JavaHighlightUtil.formatType(deconstructionComponentType)
                            )
                        ));
                        continue;
                    }

                    HighlightInfo.Builder incompatible =
                        reportIncompatibleType(substitutedRecordComponentType, deconstructionComponentType, deconstructionComponent);
                    if (incompatible != null) {
                        sink.accept(incompatible);
                    }
                }
            }
            else {
                HighlightInfo.Builder unchecked = checkUncheckedPatternConversion(deconstructionComponent);
                if (unchecked != null) {
                    sink.accept(unchecked);
                    hasMismatchedPattern = true;
                }
            }
            if (recordComponents.length != deconstructionComponents.length && hasMismatchedPattern) {
                break;
            }
            if (deconstructionComponent instanceof PsiDeconstructionPattern deconstructionComponentPattern) {
                doCheckDeconstructionErrors(deconstructionComponentPattern, file, sink);
            }
        }
        if (recordComponents.length != deconstructionComponents.length) {
            PsiDeconstructionList list = deconstructionPattern.getDeconstructionList();
            TextRange range;
            int navigationShift;
            if (recordComponents.length < deconstructionComponents.length && !hasMismatchedPattern) {
                int startOffset = deconstructionComponents[recordComponents.length].getTextRange().getStartOffset();
                range = TextRange.create(startOffset, list.getTextRange().getEndOffset());
                navigationShift = 0;
            }
            else {
                range = list.getTextRange();
                navigationShift = 1;
            }
            sink.accept(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(range)
                .navigationShift(navigationShift)
                .descriptionAndTooltip(JavaCompilationErrorLocalize.patternDeconstructionCountMismatch(
                    recordComponents.length,
                    deconstructionComponents.length
                )));
        }
    }

    @RequiredReadAction
    static HighlightInfo.@Nullable Builder checkMalformedDeconstructionPatternInCase(PsiDeconstructionPattern pattern) {
        // We are checking the case when the pattern looks similar to method call in switch and want to show user-friendly message that here
        // only constant expressions are expected.
        // it is required to do it in deconstruction list because unresolved reference won't let any parents show any highlighting,
        // so we need element which is not parent
        PsiElement grandParent = pattern.getParent();
        if (!(grandParent instanceof PsiCaseLabelElementList)) {
            return null;
        }
        PsiTypeElement typeElement = pattern.getTypeElement();
        PsiJavaCodeReferenceElement ref = PsiTreeUtil.getChildOfType(typeElement, PsiJavaCodeReferenceElement.class);
        if (ref == null) {
            return null;
        }
        if (ref.multiResolve(true).length == 0) {
            PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(pattern.getProject());
            if (pattern.getPatternVariable() == null && pattern.getDeconstructionList().getDeconstructionComponents().length == 0) {
                PsiClassType type = tryCast(pattern.getTypeElement().getType(), PsiClassType.class);
                if (type != null && ContainerUtil.exists(type.getParameters(), PsiWildcardType.class::isInstance)) {
                    return null;
                }
                PsiExpression expression = elementFactory.createExpressionFromText(pattern.getText(), grandParent);
                PsiMethodCallExpression call = tryCast(expression, PsiMethodCallExpression.class);
                if (call == null) {
                    return null;
                }
                if (call.getMethodExpression().resolve() != null) {
                    return error(pattern.getTextRange(), JavaCompilationErrorLocalize.callParsedAsDeconstructionPattern());
                }
            }
        }
        return null;
    }

    @RequiredReadAction
    static HighlightInfo.@Nullable Builder checkInstanceOfPatternSupertype(PsiInstanceOfExpression expression) {
        PsiPattern expressionPattern = expression.getPattern();
        PsiTypeTestPattern pattern = tryCast(expressionPattern, PsiTypeTestPattern.class);
        if (pattern == null) {
            return null;
        }
        PsiPatternVariable variable = pattern.getPatternVariable();
        if (variable == null) {
            return null;
        }
        PsiTypeElement typeElement = pattern.getCheckType();
        if (typeElement == null) {
            return null;
        }
        PsiType checkType = typeElement.getType();
        PsiType expressionType = expression.getOperand().getType();
        if (expressionType != null && checkType.isAssignableFrom(expressionType)) {
            if (checkType.equals(expressionType)) {
                return error(
                    typeElement.getTextRange(),
                    JavaCompilationErrorLocalize.patternInstanceofEquals(checkType.getPresentableText())
                );
            }
            else {
                return error(
                    typeElement.getTextRange(),
                    JavaCompilationErrorLocalize.patternInstanceofSupertype(
                        checkType.getPresentableText(),
                        expressionType.getPresentableText()
                    )
                );
            }
        }
        return null;
    }

    /**
     * Checks if the given record component type is applicable for the pattern type based on the specified language level.
     * For example:
     * <pre><code>
     *  record SomeClass(RecordComponentType component)
     *  (a instanceof SomeClass(PatternType obj))
     * </code></pre>
     *
     * @param recordComponentType the type of the record component
     * @param patternType         the type of the pattern
     * @param languageLevel       the language level to consider
     * @return true if the record component type is applicable for the pattern type, false otherwise
     */
    private static boolean isApplicableForRecordComponent(
        PsiType recordComponentType,
        @Nullable PsiType patternType,
        LanguageLevel languageLevel
    ) {
        if ((recordComponentType instanceof PsiPrimitiveType || patternType instanceof PsiPrimitiveType)
            && !JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS.isSufficient(languageLevel)) {
            return recordComponentType.equals(patternType);
        }
        return patternType != null && TypeConversionUtil.areTypesConvertible(recordComponentType, patternType);
    }

    @RequiredReadAction
    private static HighlightInfo.@Nullable Builder reportIncompatibleType(
        PsiType lType,
        @Nullable PsiType rType,
        PsiElement elementToHighlight
    ) {
        if (rType instanceof PsiLambdaParameterType || lType instanceof PsiLambdaParameterType) {
            // Do not report an incompatible type if the lambda parameter type is not known;
            // this problem is induced by another problem, which is more useful to report
            return null;
        }
        return HighlightUtil.createIncompatibleTypeHighlightInfo(lType, rType, elementToHighlight.getTextRange(), 0);
    }

    private static HighlightInfo.Builder error(TextRange range, LocalizeValue message) {
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range).descriptionAndTooltip(message);
    }
}
