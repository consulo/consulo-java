// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.value;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.*;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfConstantType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfLongType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.codeInsight.ConcurrencyAnnotationsManager;
import com.intellij.java.language.codeInsight.Nullability;
import com.intellij.java.language.impl.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.java.language.impl.psi.impl.light.LightRecordMethod;
import com.intellij.java.language.impl.psi.impl.source.PsiImmediateClassType;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.*;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.lang.ObjectUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import one.util.streamex.LongStreamEx;
import org.jetbrains.annotations.Contract;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes.rangeClamped;

/**
 * @author peter
 */
public class DfaExpressionFactory {

    private final DfaValueFactory myFactory;
    private final Map<Integer, ArrayElementDescriptor> myArrayIndices = new HashMap<>();

    DfaExpressionFactory(DfaValueFactory factory) {
        myFactory = factory;
    }

    @Nullable
    @Contract("null -> null")
    @RequiredReadAction
    DfaValue getExpressionDfaValue(@Nullable PsiExpression expression) {
        if (expression == null) {
            return null;
        }

        if (expression instanceof PsiParenthesizedExpression parenthesized) {
            return getExpressionDfaValue(parenthesized.getExpression());
        }

        if (expression instanceof PsiArrayAccessExpression arrayAccess) {
            PsiExpression arrayExpression = arrayAccess.getArrayExpression();
            DfaValue qualifier = getQualifierValue(arrayExpression);
            if (qualifier != null
                && ExpressionUtils.computeConstantExpression(arrayAccess.getIndexExpression()) instanceof Integer index1) {
                DfaValue arrayElementValue = getArrayElementValue(qualifier, index1);
                if (arrayElementValue != null) {
                    return arrayElementValue;
                }
            }
            PsiType type = expression.getType();
            if (type != null) {
                return myFactory.getObjectType(type, DfaPsiUtil.getElementNullability(type, null));
            }
        }

        if (expression instanceof PsiMethodCallExpression methodCall) {
            return createReferenceValue(methodCall.getMethodExpression());
        }

        if (expression instanceof PsiReferenceExpression refExpr) {
            return createReferenceValue(refExpr);
        }

        if (expression instanceof PsiLiteralExpression literal) {
            return myFactory.fromDfType(DfaPsiUtil.fromLiteral(literal));
        }

        if (expression instanceof PsiNewExpression || expression instanceof PsiLambdaExpression) {
            return myFactory.getObjectType(expression.getType(), Nullability.NOT_NULL);
        }

        Object value = JavaConstantExpressionEvaluator.computeConstantExpression(expression, false);
        if (value != null) {
            PsiType type = expression.getType();
            if (type != null) {
                return myFactory.getConstant(value, type);
            }
        }

        if (expression instanceof PsiThisExpression || expression instanceof PsiSuperExpression) {
            PsiJavaCodeReferenceElement qualifier = ((PsiQualifiedExpression)expression).getQualifier();
            PsiClass target;
            if (qualifier != null) {
                target = ObjectUtil.tryCast(qualifier.resolve(), PsiClass.class);
            }
            else {
                target = ClassUtils.getContainingClass(expression);
            }
            return target == null
                ? myFactory.getObjectType(expression.getType(), Nullability.NOT_NULL)
                : myFactory.getVarFactory().createThisValue(target);
        }
        return null;
    }

    @RequiredReadAction
    private DfaValue createReferenceValue(@Nonnull PsiReferenceExpression refExpr) {
        PsiElement target = refExpr.resolve();
        if (target instanceof PsiVariable variable && !PsiUtil.isAccessedForWriting(refExpr)) {
            DfaValue constValue = myFactory.getConstantFromVariable(variable);
            if (constValue != null && !maybeUninitializedConstant(constValue, refExpr, variable)) {
                return constValue;
            }
        }
        VariableDescriptor var = getAccessedVariableOrGetter(target);
        if (var == null) {
            return null;
        }

        DfaValue qualifier = getQualifierOrThisValue(refExpr);
        DfaValue result = var.createValue(myFactory, qualifier, true);
        if (var instanceof SpecialField) {
            PsiType wantedType = refExpr.getType();
            result = DfaUtil.boxUnbox(result, wantedType);
        }
        return result;
    }

    /**
     * Returns a DFA variable which represents the qualifier for given reference if possible. For unqualified reference
     * to a non-static member, a variable which represents the corresponding {@code this} may be returned
     *
     * @param refExpr reference to create a qualifier variable for
     * @return a qualifier variable or null if qualifier is unnecessary or cannot be represented as a variable
     */
    @Nullable
    @RequiredReadAction
    public DfaValue getQualifierOrThisValue(PsiReferenceExpression refExpr) {
        PsiExpression qualifierExpression = refExpr.getQualifierExpression();
        if (qualifierExpression == null) {
            PsiElement element = refExpr.resolve();
            if (element instanceof PsiMember member && !member.isStatic()) {
                PsiClass currentClass;
                currentClass = ClassUtils.getContainingClass(refExpr);
                PsiClass memberClass = member.getContainingClass();
                if (memberClass != null && currentClass != null) {
                    PsiClass target;
                    if (currentClass == memberClass || InheritanceUtil.isInheritorOrSelf(currentClass, memberClass, true)) {
                        target = currentClass;
                    }
                    else {
                        target = memberClass;
                    }
                    return myFactory.getVarFactory().createThisValue(target);
                }
            }
        }
        return getQualifierValue(qualifierExpression);
    }

    @Nullable
    @RequiredReadAction
    private DfaValue getQualifierValue(PsiExpression qualifierExpression) {
        DfaValue qualifierValue = getExpressionDfaValue(qualifierExpression);
        if (qualifierValue == null) {
            return null;
        }
        PsiVariable constVar = DfConstantType.getConstantOfType(qualifierValue.getDfType(), PsiVariable.class);
        if (constVar != null) {
            return myFactory.getVarFactory().createVariableValue(constVar);
        }
        return qualifierValue;
    }

    private static boolean maybeUninitializedConstant(
        DfaValue constValue,
        @Nonnull PsiReferenceExpression refExpr,
        PsiModifierListOwner var
    ) {
        // If static final field is referred from the same or inner/nested class,
        // we consider that it might be uninitialized yet as some class initializers may call its methods or
        // even instantiate objects of this class and call their methods
        if (!DfConstantType.isConst(constValue.getDfType(), var)) {
            return false;
        }
        //noinspection SimplifiableIfStatement
        if (!(var instanceof PsiField) || var instanceof PsiEnumConstant) {
            return false;
        }
        return PsiTreeUtil.getTopmostParentOfType(refExpr, PsiClass.class) == PsiTreeUtil.getTopmostParentOfType(var, PsiClass.class);
    }

    @Contract("null -> null")
    @Nullable
    public static VariableDescriptor getAccessedVariableOrGetter(PsiElement target) {
        SpecialField sf = SpecialField.findSpecialField(target);
        if (sf != null) {
            return sf;
        }
        if (target instanceof PsiVariable variable) {
            return new PlainDescriptor(variable);
        }
        if (target instanceof PsiMethod method) {
            if (method.getParameterList().isEmpty()
                && (
                    PropertyUtilBase.isSimplePropertyGetter(method)
                        || JavaMethodContractUtil.isPure(method)
                        || isClassAnnotatedImmutable(method)
                )
                && isContractAllowedForGetter(method)) {
                return new GetterDescriptor(method);
            }
        }
        return null;
    }

    private static boolean isClassAnnotatedImmutable(PsiMethod method) {
        List<String> annotations = ConcurrencyAnnotationsManager.getInstance(method.getProject()).getImmutableAnnotations();
        return AnnotationUtil.findAnnotation(method.getContainingClass(), annotations) != null;
    }

    private static boolean isContractAllowedForGetter(PsiMethod method) {
        List<? extends MethodContract> contracts = JavaMethodContractUtil.getMethodCallContracts(method, null);
        if (contracts.size() == 1) {
            MethodContract contract = contracts.get(0);
            return contract.isTrivial() && contract.getReturnValue().equals(ContractReturnValue.returnNew());
        }
        return contracts.isEmpty();
    }

    @Nonnull
    @RequiredReadAction
    private DfaValue getAdvancedExpressionDfaValue(@Nullable PsiExpression expression, @Nullable PsiType targetType) {
        if (expression == null) {
            return myFactory.getUnknown();
        }
        DfaValue value = getExpressionDfaValue(expression);
        if (value != null) {
            return DfaUtil.boxUnbox(value, targetType);
        }
        if (expression instanceof PsiConditionalExpression conditionalExpr) {
            return getAdvancedExpressionDfaValue(conditionalExpr.getThenExpression(), targetType)
                .unite(getAdvancedExpressionDfaValue(conditionalExpr.getElseExpression(), targetType));
        }
        PsiType type = expression.getType();
        if (expression instanceof PsiArrayInitializerExpression arrayInitializer) {
            int length = arrayInitializer.getInitializers().length;
            return myFactory.fromDfType(SpecialField.ARRAY_LENGTH.asDfType(DfTypes.intValue(length), type));
        }
        DfType dfType = DfTypes.typedObject(type, NullabilityUtil.getExpressionNullability(expression));
        if (type instanceof PsiPrimitiveType primitiveType
            && targetType instanceof PsiPrimitiveType targetPrimitiveType
            && !primitiveType.equals(targetPrimitiveType)) {
            if (TypeConversionUtil.isIntegralNumberType(targetPrimitiveType)) {
                LongRangeSet range = DfLongType.extractRange(dfType);
                return myFactory.fromDfType(rangeClamped(range.castTo(targetPrimitiveType), PsiType.LONG.equals(targetPrimitiveType)));
            }
            return myFactory.fromDfType(DfTypes.typedObject(targetPrimitiveType, Nullability.UNKNOWN));
        }
        return DfaUtil.boxUnbox(myFactory.fromDfType(dfType), targetType);
    }

    @Nonnull
    public DfaValue getArrayElementValue(DfaValue array, LongRangeSet indexSet) {
        if (!(array instanceof DfaVariableValue arrayDfaVar)) {
            return myFactory.getUnknown();
        }
        if (indexSet.isEmpty()) {
            return myFactory.getUnknown();
        }
        long min = indexSet.min();
        long max = indexSet.max();
        if (min == max && min >= 0 && min < Integer.MAX_VALUE) {
            DfaValue value = getArrayElementValue(array, (int)min);
            return value == null ? myFactory.getUnknown() : value;
        }
        if (!(arrayDfaVar.getPsiVariable() instanceof PsiVariable arrayVar)) {
            return myFactory.getUnknown();
        }
        PsiType targetType = arrayVar.getType() instanceof PsiArrayType arrayType ? arrayType.getComponentType() : null;
        PsiExpression[] elements = ExpressionUtils.getConstantArrayElements(arrayVar);
        if (elements == null || elements.length == 0) {
            return myFactory.getUnknown();
        }
        indexSet = indexSet.intersect(LongRangeSet.range(0, elements.length - 1));
        if (indexSet.isEmpty() || indexSet.isCardinalityBigger(100)) {
            return myFactory.getUnknown();
        }
        return LongStreamEx.of(indexSet.stream())
            .mapToObj(idx -> getAdvancedExpressionDfaValue(elements[(int)idx], targetType))
            .prefix(DfaValue::unite)
            .takeWhileInclusive(value -> !DfaTypeValue.isUnknown(value))
            .reduce((a, b) -> b)
            .orElseGet(myFactory::getUnknown);
    }

    @Contract("null, _ -> null")
    @Nullable
    @RequiredReadAction
    public DfaValue getArrayElementValue(DfaValue array, int index) {
        if (!(array instanceof DfaVariableValue arrayDfaVar) || !(arrayDfaVar.getType() instanceof PsiArrayType arrayType)) {
            return null;
        }
        PsiModifierListOwner arrayPsiVar = arrayDfaVar.getPsiVariable();
        if (arrayPsiVar instanceof PsiVariable arrayVar) {
            PsiExpression constantArrayElement = ExpressionUtils.getConstantArrayElement(arrayVar, index);
            if (constantArrayElement != null) {
                return getAdvancedExpressionDfaValue(constantArrayElement, arrayType.getComponentType());
            }
        }
        ArrayElementDescriptor indexVariable = getArrayIndexVariable(index);
        if (indexVariable == null) {
            return null;
        }
        return indexVariable.createValue(myFactory, arrayDfaVar);
    }

    @Nullable
    private ArrayElementDescriptor getArrayIndexVariable(int index) {
        if (index >= 0) {
            return myArrayIndices.computeIfAbsent(index, ArrayElementDescriptor::new);
        }
        return null;
    }

    @Nonnull
    private static PsiSubstitutor getSubstitutor(PsiElement member, @Nullable DfaVariableValue qualifier) {
        if (member instanceof PsiMember psiMember && qualifier != null) {
            PsiClass fieldClass = psiMember.getContainingClass();
            PsiClassType classType = ObjectUtil.tryCast(qualifier.getType(), PsiClassType.class);
            if (classType != null && InheritanceUtil.isInheritorOrSelf(classType.resolve(), fieldClass, true)) {
                return TypeConversionUtil.getSuperClassSubstitutor(fieldClass, classType);
            }
        }
        return PsiSubstitutor.EMPTY;
    }

    public DfaVariableValue getAssertionsDisabledVariable() {
        return myFactory.getVarFactory().createVariableValue(AssertionDisabledDescriptor.INSTANCE);
    }

    public static final class AssertionDisabledDescriptor implements VariableDescriptor {
        static final AssertionDisabledDescriptor INSTANCE = new AssertionDisabledDescriptor();

        private AssertionDisabledDescriptor() {
        }

        @Override
        public boolean isStable() {
            return true;
        }

        @Nonnull
        @Override
        public PsiType getType(@Nullable DfaVariableValue qualifier) {
            return PsiType.BOOLEAN;
        }

        @Override
        public String toString() {
            return "$assertionsDisabled";
        }
    }

    static final class PlainDescriptor implements VariableDescriptor {
        private final
        @Nonnull
        PsiVariable myVariable;

        PlainDescriptor(@Nonnull PsiVariable variable) {
            myVariable = variable;
        }

        @Nonnull
        @Override
        @RequiredReadAction
        public String toString() {
            return String.valueOf(myVariable.getName());
        }

        @Override
        public PsiType getType(@Nullable DfaVariableValue qualifier) {
            PsiType type = myVariable.getType();
            if (type instanceof PsiEllipsisType ellipsisType) {
                type = ellipsisType.toArrayType();
            }
            return getSubstitutor(myVariable, qualifier).substitute(type);
        }

        @Override
        public PsiVariable getPsiElement() {
            return myVariable;
        }

        @Override
        public boolean isStable() {
            return PsiUtil.isJvmLocalVariable(myVariable) || myVariable.hasModifierProperty(PsiModifier.FINAL);
        }

        @Nonnull
        @Override
        public DfaValue createValue(@Nonnull DfaValueFactory factory, @Nullable DfaValue qualifier, boolean forAccessor) {
            if (myVariable.hasModifierProperty(PsiModifier.VOLATILE)) {
                PsiType type = getType(ObjectUtil.tryCast(qualifier, DfaVariableValue.class));
                return factory.getObjectType(type, DfaPsiUtil.getElementNullability(type, myVariable));
            }
            if (PsiUtil.isJvmLocalVariable(myVariable)
                || (myVariable instanceof PsiField field && field.isStatic()
                && (!field.isFinal() || !DfaUtil.hasInitializationHacks(field)))) {
                return factory.getVarFactory().createVariableValue(this);
            }
            return VariableDescriptor.super.createValue(factory, qualifier, forAccessor);
        }

        @Override
        @RequiredReadAction
        public int hashCode() {
            return Objects.hashCode(myVariable.getName());
        }

        @Override
        public boolean equals(Object obj) {
            return obj == this || obj instanceof PlainDescriptor plainDescriptor && plainDescriptor.myVariable == myVariable;
        }
    }

    public static final class GetterDescriptor implements VariableDescriptor {
        private static final CallMatcher STABLE_METHODS = CallMatcher.anyOf(
            CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_OBJECT, "getClass").parameterCount(0),
            CallMatcher.instanceCall("java.lang.reflect.Member", "getName", "getModifiers", "getDeclaringClass", "isSynthetic"),
            CallMatcher.instanceCall("java.lang.reflect.Executable", "getParameterCount", "isVarArgs"),
            CallMatcher.instanceCall("java.lang.reflect.Field", "getType"),
            CallMatcher.instanceCall("java.lang.reflect.Method", "getReturnType"),
            CallMatcher.instanceCall(
                CommonClassNames.JAVA_LANG_CLASS,
                "getName",
                "isInterface",
                "isArray",
                "isPrimitive",
                "isSynthetic",
                "isAnonymousClass",
                "isLocalClass",
                "isMemberClass",
                "getDeclaringClass",
                "getEnclosingClass",
                "getSimpleName",
                "getCanonicalName"
            )
        );
        private final
        @Nonnull
        PsiMethod myGetter;
        private final boolean myStable;

        public GetterDescriptor(@Nonnull PsiMethod getter) {
            myGetter = getter;
            if (STABLE_METHODS.methodMatches(getter) || getter instanceof LightRecordMethod) {
                myStable = true;
            }
            else {
                PsiField field = PsiUtil.canBeOverridden(getter) ? null : PropertyUtil.getFieldOfGetter(getter);
                myStable = field != null && field.isFinal();
            }
        }

        @Nonnull
        @Override
        public String toString() {
            return myGetter.getName();
        }

        @Nullable
        @Override
        public PsiType getType(@Nullable DfaVariableValue qualifier) {
            return getSubstitutor(myGetter, qualifier).substitute(myGetter.getReturnType());
        }

        @Nonnull
        @Override
        public PsiMethod getPsiElement() {
            return myGetter;
        }

        @Override
        public boolean isStable() {
            return myStable;
        }

        @Override
        public boolean isCall() {
            return true;
        }

        @Nonnull
        @Override
        public DfaValue createValue(@Nonnull DfaValueFactory factory, @Nullable DfaValue qualifier, boolean forAccessor) {
            if (myGetter.isStatic()) {
                return factory.getVarFactory().createVariableValue(this);
            }
            return VariableDescriptor.super.createValue(factory, qualifier, forAccessor);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(myGetter.getName());
        }

        @Override
        public boolean equals(Object obj) {
            return obj == this || (obj instanceof GetterDescriptor getterDescr && getterDescr.myGetter == myGetter);
        }
    }

    public static final class ArrayElementDescriptor implements VariableDescriptor {
        private final int myIndex;

        ArrayElementDescriptor(int index) {
            myIndex = index;
        }

        public int getIndex() {
            return myIndex;
        }

        @Nullable
        @Override
        public PsiType getType(@Nullable DfaVariableValue qualifier) {
            if (qualifier == null) {
                return null;
            }
            return qualifier.getType() instanceof PsiArrayType arrayType ? arrayType.getComponentType() : null;
        }

        @Nonnull
        @Override
        public String toString() {
            return "[" + myIndex + "]";
        }

        @Override
        public boolean isStable() {
            return false;
        }
    }

    public static final class ThisDescriptor implements VariableDescriptor {
        @Nonnull
        private final PsiClass myQualifier;

        ThisDescriptor(@Nonnull PsiClass qualifier) {
            myQualifier = qualifier;
        }

        @Nonnull
        @Override
        @RequiredReadAction
        public String toString() {
            return myQualifier.getName() + ".this";
        }

        @Nonnull
        @Override
        public PsiType getType(@Nullable DfaVariableValue qualifier) {
            return new PsiImmediateClassType(myQualifier, PsiSubstitutor.EMPTY);
        }

        @Override
        public PsiClass getPsiElement() {
            return myQualifier;
        }

        @Override
        public boolean isStable() {
            return true;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(myQualifier.getQualifiedName());
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj || obj instanceof ThisDescriptor thisDescr && thisDescr.myQualifier == myQualifier;
        }
    }
}
