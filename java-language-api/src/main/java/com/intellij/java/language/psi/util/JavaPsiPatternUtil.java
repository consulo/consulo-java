// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.psi.util;

import com.intellij.java.language.JavaFeature;
import com.intellij.java.language.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.java.language.psi.*;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.ObjectUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.Contract;

import java.util.*;

public final class JavaPsiPatternUtil {
    public static @Nullable PsiType getDeconstructedImplicitPatternVariableType(@Nonnull PsiPatternVariable parameter) {
        return getDeconstructedImplicitPatternType(parameter.getPattern());
    }

    /**
     * @return type of variable in pattern, or null if pattern is incomplete
     */
    @Contract(value = "null -> null", pure = true)
    @Nullable
    public static PsiType getPatternType(@Nullable PsiCaseLabelElement pattern) {
        PsiTypeElement typeElement = getPatternTypeElement(pattern);
        if (typeElement == null) {
            return null;
        }
        return typeElement.getType();
    }

    /**
     * @param context  context element
     * @param whoType  type that should cover the overWhom type
     * @param overWhom type that needs to be covered
     * @return true if whoType overs overWhom type
     */
    public static boolean covers(@Nonnull PsiElement context, @Nonnull PsiType whoType, @Nonnull PsiType overWhom) {
        List<PsiType> whoTypes = deconstructSelectorType(whoType);
        List<PsiType> overWhomTypes = deconstructSelectorType(overWhom);
        for (PsiType currentWhoType : whoTypes) {
            if (!ContainerUtil.exists(overWhomTypes, currentOverWhomType -> {
                boolean unconditionallyExactForType =
                    isUnconditionallyExactForType(context, currentOverWhomType, currentWhoType);
                if (unconditionallyExactForType) {
                    return true;
                }
                PsiPrimitiveType unboxedOverWhomType = PsiPrimitiveType.getUnboxedType(currentOverWhomType);
                if (unboxedOverWhomType == null) {
                    return false;
                }
                return isUnconditionallyExactForType(context, unboxedOverWhomType, currentWhoType);
            })) {
                return false;
            }
        }
        return true;
    }

    /**
     * @param context     context PSI element (to check language level and resolve boxed type if necessary)
     * @param type        selector type
     * @param patternType pattern type
     * @return true if the supplied pattern type is unconditionally exact, that is cast from type to patternType
     * always succeeds without data loss
     */
    public static boolean isUnconditionallyExactForType(@Nonnull PsiElement context, @Nonnull PsiType type, PsiType patternType) {
        type = TypeConversionUtil.erasure(type);
        if ((type instanceof PsiPrimitiveType || patternType instanceof PsiPrimitiveType) &&
            PsiUtil.isAvailable(JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS, context)) {
            if (type.equals(patternType)) {
                return true;
            }
            if (type instanceof PsiPrimitiveType && patternType instanceof PsiPrimitiveType) {
                return isExactPrimitiveWideningConversion(type, patternType);
            }
            else if (!(type instanceof PsiPrimitiveType)) {
                return false;
            }
            else {
                PsiClassType boxedType = ((PsiPrimitiveType) type).getBoxedType(context);
                return dominates(patternType, boxedType);
            }
        }
        else {
            return dominates(patternType, type);
        }
    }

    /**
     * Checks if the given type is an exact primitive widening conversion of the pattern type according to 5.1.2
     *
     * @param type        the type to check
     * @param patternType the pattern type to compare with
     * @return true if the given type is an exact primitive widening conversion of the pattern type, false otherwise
     */
    public static boolean isExactPrimitiveWideningConversion(@Nonnull PsiType type, @Nonnull PsiType patternType) {
        if (type.equals(PsiTypes.byteType())) {
            return patternType.equals(PsiTypes.shortType()) ||
                patternType.equals(PsiTypes.intType()) ||
                patternType.equals(PsiTypes.longType()) ||
                patternType.equals(PsiTypes.floatType()) ||
                patternType.equals(PsiTypes.doubleType());
        }
        if (type.equals(PsiTypes.shortType())) {
            return patternType.equals(PsiTypes.intType()) ||
                patternType.equals(PsiTypes.longType()) ||
                patternType.equals(PsiTypes.floatType()) ||
                patternType.equals(PsiTypes.doubleType());
        }
        if (type.equals(PsiTypes.charType())) {
            return patternType.equals(PsiTypes.intType()) ||
                patternType.equals(PsiTypes.longType()) ||
                patternType.equals(PsiTypes.floatType()) ||
                patternType.equals(PsiTypes.doubleType());
        }
        if (type.equals(PsiTypes.intType())) {
            return patternType.equals(PsiTypes.longType()) ||
                patternType.equals(PsiTypes.doubleType());
        }
        if (type.equals(PsiTypes.floatType())) {
            return patternType.equals(PsiTypes.doubleType());
        }
        return false;
    }

    /**
     * 14.11.1 Switch Blocks
     *
     * @param overWhom - type of constant
     */
    @Contract(value = "_,null -> false", pure = true)
    public static boolean dominatesOverConstant(@Nonnull PsiCaseLabelElement who, @Nullable PsiType overWhom) {
        if (overWhom == null) {
            return false;
        }
        who = findUnconditionalPattern(who);
        PsiType whoType = TypeConversionUtil.erasure(getPatternType(who));
        if (whoType == null) {
            return false;
        }
        PsiType overWhomType = null;
        if (overWhom instanceof PsiPrimitiveType) {
            overWhomType = ((PsiPrimitiveType) overWhom).getBoxedType(who);
        }
        else if (overWhom instanceof PsiClassType) {
            overWhomType = overWhom;
        }
        return overWhomType != null && TypeConversionUtil.areTypesConvertible(overWhomType, whoType);
    }

    /**
     * @param pattern deconstruction pattern to find a context type for
     * @return a context type for the pattern; null, if it cannot be determined. This method can perform
     * the inference for outer patterns if necessary.
     */
    @Nullable
    public static PsiType getContextType(@Nonnull PsiPattern pattern) {
        PsiElement parent = pattern.getParent();
        while (parent instanceof PsiParenthesizedPattern) {
            parent = parent.getParent();
        }
        if (parent instanceof PsiInstanceOfExpression) {
            return ((PsiInstanceOfExpression) parent).getOperand().getType();
        }
        if (parent instanceof PsiForeachPatternStatement) {
            PsiExpression iteratedValue = ((PsiForeachPatternStatement) parent).getIteratedValue();
            if (iteratedValue == null) {
                return null;
            }
            return JavaGenericsUtil.getCollectionItemType(iteratedValue);
        }
        if (parent instanceof PsiCaseLabelElementList) {
            PsiSwitchLabelStatementBase label = ObjectUtil.tryCast(parent.getParent(), PsiSwitchLabelStatementBase.class);
            if (label != null) {
                PsiSwitchBlock block = label.getEnclosingSwitchBlock();
                if (block != null) {
                    PsiExpression expression = block.getExpression();
                    if (expression != null) {
                        return expression.getType();
                    }
                }
            }
        }
        if (parent instanceof PsiDeconstructionList) {
            PsiDeconstructionPattern parentPattern = ObjectUtil.tryCast(parent.getParent(), PsiDeconstructionPattern.class);
            if (parentPattern != null) {
                int index = ArrayUtil.indexOf(((PsiDeconstructionList) parent).getDeconstructionComponents(), pattern);
                if (index < 0) {
                    return null;
                }
                PsiType patternType = parentPattern.getTypeElement().getType();
                if (!(patternType instanceof PsiClassType)) {
                    return null;
                }
                PsiSubstitutor parentSubstitutor = ((PsiClassType) patternType).resolveGenerics().getSubstitutor();
                PsiClass parentRecord = PsiUtil.resolveClassInClassTypeOnly(parentPattern.getTypeElement().getType());
                if (parentRecord == null) {
                    return null;
                }
                PsiRecordComponent[] components = parentRecord.getRecordComponents();
                if (index >= components.length) {
                    return null;
                }
                return parentSubstitutor.substitute(components[index].getType());
            }
        }
        return null;
    }

    @Contract(value = "null, _ -> false", pure = true)
    public static boolean isUnconditionalForType(@Nullable PsiCaseLabelElement pattern, @Nonnull PsiType type) {
        return isUnconditionalForType(pattern, type, false) && !isGuarded(pattern);
    }

    @Nullable
    public static PsiPrimaryPattern findUnconditionalPattern(@Nullable PsiCaseLabelElement pattern) {
        if (pattern == null || isGuarded(pattern)) {
            return null;
        }
        if (pattern instanceof PsiParenthesizedPattern) {
            return findUnconditionalPattern(((PsiParenthesizedPattern) pattern).getPattern());
        }
        else if (pattern instanceof PsiDeconstructionPattern || pattern instanceof PsiTypeTestPattern || pattern instanceof PsiUnnamedPattern) {
            return (PsiPrimaryPattern) pattern;
        }
        return null;
    }

    @Contract("null,_,_ -> false")
    public static boolean isUnconditionalForType(@Nullable PsiCaseLabelElement pattern, @Nonnull PsiType type, boolean forDomination) {
        PsiPrimaryPattern unconditionalPattern = findUnconditionalPattern(pattern);
        if (unconditionalPattern == null) {
            return false;
        }
        if (unconditionalPattern instanceof PsiDeconstructionPattern) {
            return forDomination && dominates(getPatternType(unconditionalPattern), type);
        }
        else if (unconditionalPattern instanceof PsiTypeTestPattern || unconditionalPattern instanceof PsiUnnamedPattern) {
            return dominates(getPatternType(unconditionalPattern), type);
        }
        return false;
    }

    public static @Nullable PsiDeconstructionPattern findDeconstructionPattern(@Nullable PsiCaseLabelElement element) {
        return ObjectUtil.tryCast(element, PsiDeconstructionPattern.class);
    }

    /**
     * 14.30.3 Pattern Totality and Dominance
     */
    @Contract(value = "null, _ -> false", pure = true)
    public static boolean dominates(@Nullable PsiCaseLabelElement who, @Nonnull PsiCaseLabelElement overWhom) {
        if (who == null) {
            return false;
        }
        PsiType overWhomType = getPatternType(overWhom);
        if (overWhomType == null || !isUnconditionalForType(who, overWhomType, true)) {
            return false;
        }
        PsiDeconstructionPattern whoDeconstruction = findDeconstructionPattern(who);
        if (whoDeconstruction != null) {
            PsiDeconstructionPattern overWhomDeconstruction = findDeconstructionPattern(overWhom);
            return dominatesComponents(whoDeconstruction, overWhomDeconstruction);
        }
        return true;
    }

    private static boolean dominatesComponents(@Nonnull PsiDeconstructionPattern who, @Nullable PsiDeconstructionPattern overWhom) {
        if (overWhom == null) {
            return false;
        }
        PsiPattern[] whoComponents = who.getDeconstructionList().getDeconstructionComponents();
        PsiPattern[] overWhomComponents = overWhom.getDeconstructionList().getDeconstructionComponents();
        if (whoComponents.length != overWhomComponents.length) {
            return false;
        }
        for (int i = 0; i < whoComponents.length; i++) {
            PsiPattern whoComponent = whoComponents[i];
            PsiPattern overWhomComponent = overWhomComponents[i];
            if (!dominates(whoComponent, overWhomComponent)) {
                return false;
            }
        }
        return true;
    }

    public static boolean dominates(@Nullable PsiType who, @Nullable PsiType overWhom) {
        if (who == null || overWhom == null) {
            return false;
        }
        if (who.getCanonicalText().equals(overWhom.getCanonicalText())) {
            return true;
        }
        overWhom = TypeConversionUtil.erasure(overWhom);
        PsiType baseType = TypeConversionUtil.erasure(who);
        if (overWhom.equals(PsiTypes.nullType())) {
            return who instanceof PsiClassType || who instanceof PsiArrayType;
        }
        if (overWhom instanceof PsiArrayType || baseType instanceof PsiArrayType) {
            return baseType != null && TypeConversionUtil.isAssignable(baseType, overWhom);
        }
        PsiClass typeClass = PsiTypesUtil.getPsiClass(overWhom);
        PsiClass baseTypeClass = PsiTypesUtil.getPsiClass(baseType);
        return typeClass != null && baseTypeClass != null && InheritanceUtil.isInheritorOrSelf(typeClass, baseTypeClass, true);
    }

    @Nullable
    private static Object evaluateConstant(@Nullable PsiExpression expression) {
        if (expression == null) {
            return null;
        }
        return JavaPsiFacade.getInstance(expression.getProject()).getConstantEvaluationHelper()
            .computeConstantExpression(expression, false);
    }

    public static boolean isGuarded(@Nonnull PsiCaseLabelElement pattern) {
        PsiElement parent = pattern.getParent();
        if (parent instanceof PsiCaseLabelElementList) {
            PsiElement gParent = parent.getParent();
            if (gParent instanceof PsiSwitchLabelStatementBase) {
                PsiExpression guardExpression = ((PsiSwitchLabelStatementBase) gParent).getGuardExpression();
                if (guardExpression != null && !Boolean.TRUE.equals(evaluateConstant(guardExpression))) {
                    return true;
                }
            }
        }
        return false;
    }

    public static @Nullable PsiTypeElement getPatternTypeElement(@Nullable PsiCaseLabelElement pattern) {
        if (pattern == null) {
            return null;
        }
        if (pattern instanceof PsiParenthesizedPattern) {
            return getPatternTypeElement(((PsiParenthesizedPattern) pattern).getPattern());
        }
        else if (pattern instanceof PsiDeconstructionPattern) {
            return ((PsiDeconstructionPattern) pattern).getTypeElement();
        }
        else if (pattern instanceof PsiTypeTestPattern) {
            return ((PsiTypeTestPattern) pattern).getCheckType();
        }
        else if (pattern instanceof PsiUnnamedPattern) {
            return ((PsiUnnamedPattern) pattern).getTypeElement();
        }
        return null;
    }

    public static @Nullable PsiType getDeconstructedImplicitPatternType(@Nonnull PsiPattern pattern) {
        PsiRecordComponent recordComponent = getRecordComponentForPattern(pattern);
        if (recordComponent != null) {
            PsiDeconstructionList deconstructionList = ObjectUtil.tryCast(pattern.getParent(), PsiDeconstructionList.class);
            if (deconstructionList == null) {
                return null;
            }
            PsiDeconstructionPattern deconstructionPattern = (PsiDeconstructionPattern) deconstructionList.getParent();
            PsiType patternType = deconstructionPattern.getTypeElement().getType();
            if (patternType instanceof PsiClassType) {
                patternType = PsiUtil.captureToplevelWildcards(patternType, pattern);
                PsiSubstitutor substitutor = ((PsiClassType) patternType).resolveGenerics().getSubstitutor();
                PsiType recordComponentType = recordComponent.getType();
                return JavaVarTypeUtil.getUpwardProjection(substitutor.substitute(recordComponentType));
            }
        }
        return null;
    }

    @Contract(pure = true)
    @Nullable
    public static PsiRecordComponent getRecordComponentForPattern(@Nonnull PsiPattern pattern) {
        PsiDeconstructionList deconstructionList = ObjectUtil.tryCast(pattern.getParent(), PsiDeconstructionList.class);
        if (deconstructionList == null) {
            return null;
        }
        @Nonnull PsiPattern[] patterns = deconstructionList.getDeconstructionComponents();
        int index = ArrayUtil.indexOf(patterns, pattern);
        PsiDeconstructionPattern deconstructionPattern = ObjectUtil.tryCast(deconstructionList.getParent(), PsiDeconstructionPattern.class);
        if (deconstructionPattern == null) {
            return null;
        }
        PsiClassType classType = ObjectUtil.tryCast(deconstructionPattern.getTypeElement().getType(), PsiClassType.class);
        if (classType == null) {
            return null;
        }
        PsiClass aClass = classType.resolve();
        if (aClass == null) {
            return null;
        }
        PsiRecordComponent[] components = aClass.getRecordComponents();
        if (components.length <= index) {
            return null;
        }
        return components[index];
    }

    /**
     * @param expression expression to search pattern variables in
     * @return list of pattern variables declared within an expression that could be visible outside of given expression.
     */
    @Contract(pure = true)
    public static
    @Nonnull
    List<PsiPatternVariable> getExposedPatternVariables(@Nonnull PsiExpression expression) {
        PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
        boolean parentMayAccept =
            parent instanceof PsiPrefixExpression && ((PsiPrefixExpression) parent).getOperationTokenType().equals(JavaTokenType.EXCL) ||
                parent instanceof PsiPolyadicExpression && ((PsiPolyadicExpression) parent).getOperationTokenType().equals(JavaTokenType.ANDAND) ||
                parent instanceof PsiPolyadicExpression && ((PsiPolyadicExpression) parent).getOperationTokenType().equals(JavaTokenType.OROR) ||
                parent instanceof PsiConditionalExpression || parent instanceof PsiIfStatement || parent instanceof PsiConditionalLoopStatement;
        if (!parentMayAccept) {
            return Collections.emptyList();
        }
        List<PsiPatternVariable> list = new ArrayList<>();
        collectPatternVariableCandidates(expression, expression, list, false);
        return list;
    }

    /**
     * @param expression expression to search pattern variables in
     * @return list of pattern variables declared within an expression that could be visible outside of given expression
     * under some other parent (e.g. under PsiIfStatement).
     */
    @Contract(pure = true)
    @Nonnull
    public static List<PsiPatternVariable> getExposedPatternVariablesIgnoreParent(@Nonnull PsiExpression expression) {
        List<PsiPatternVariable> list = new ArrayList<>();
        collectPatternVariableCandidates(expression, expression, list, true);
        return list;
    }

    /**
     * @param variable pattern variable
     * @return effective initializer expression for the variable; null if cannot be determined
     */
    @Nullable
    public static String getEffectiveInitializerText(@Nonnull PsiPatternVariable variable) {
        PsiPattern pattern = variable.getPattern();
        PsiInstanceOfExpression instanceOf = ObjectUtil.tryCast(pattern.getParent(), PsiInstanceOfExpression.class);
        if (instanceOf == null) {
            return null;
        }
        if (pattern instanceof PsiTypeTestPattern) {
            PsiExpression operand = instanceOf.getOperand();
            PsiTypeElement checkType = ((PsiTypeTestPattern) pattern).getCheckType();
            if (checkType.getType().equals(operand.getType())) {
                return operand.getText();
            }
            return "(" + checkType.getText() + ")" + operand.getText();
        }
        return null;
    }

    private static void collectPatternVariableCandidates(@Nonnull PsiExpression scope, @Nonnull PsiExpression expression,
                                                         Collection<PsiPatternVariable> candidates, boolean strict) {
        while (true) {
            if (expression instanceof PsiParenthesizedExpression) {
                expression = ((PsiParenthesizedExpression) expression).getExpression();
            }
            else if (expression instanceof PsiPrefixExpression &&
                ((PsiPrefixExpression) expression).getOperationTokenType().equals(JavaTokenType.EXCL)) {
                expression = ((PsiPrefixExpression) expression).getOperand();
            }
            else {
                break;
            }
        }
        if (expression instanceof PsiInstanceOfExpression) {
            PsiPattern pattern = ((PsiInstanceOfExpression) expression).getPattern();
            if (pattern instanceof PsiTypeTestPattern) {
                PsiPatternVariable variable = ((PsiTypeTestPattern) pattern).getPatternVariable();
                if (variable != null && !PsiTreeUtil.isAncestor(scope, variable.getDeclarationScope(), strict)) {
                    candidates.add(variable);
                }
            }
        }
        if (expression instanceof PsiPolyadicExpression) {
            PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression) expression;
            IElementType tokenType = polyadicExpression.getOperationTokenType();
            if (tokenType.equals(JavaTokenType.ANDAND) || tokenType.equals(JavaTokenType.OROR)) {
                for (PsiExpression operand : polyadicExpression.getOperands()) {
                    collectPatternVariableCandidates(scope, operand, candidates, strict);
                }
            }
        }
    }

    /**
     * @param selectorType pattern selector type
     * @return list of basic types that contain no intersections or type parameters
     */
    public static List<PsiType> deconstructSelectorType(@Nonnull PsiType selectorType) {
        List<PsiType> selectorTypes = new ArrayList<>();
        PsiClass resolvedClass = PsiUtil.resolveClassInClassTypeOnly(selectorType);
        //T is an intersection type T1& ... &Tn and P covers Ti, for one of the types Ti (1≤i≤n)
        if (resolvedClass instanceof PsiTypeParameter) {
            PsiClassType[] types = resolvedClass.getExtendsListTypes();
            Arrays.stream(types)
                .filter(t -> t != null)
                .forEach(t -> selectorTypes.add(t));
        }
        if (selectorType instanceof PsiIntersectionType) {
            for (PsiType conjunct : ((PsiIntersectionType) selectorType).getConjuncts()) {
                selectorTypes.addAll(deconstructSelectorType(conjunct));
            }
        }
        if (selectorTypes.isEmpty()) {
            selectorTypes.add(selectorType);
        }
        return selectorTypes;
    }
}
