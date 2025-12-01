/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.language.psi.infos;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.projectRoots.JavaSdkVersion;
import com.intellij.java.language.projectRoots.JavaVersionService;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.impl.source.resolve.DefaultParameterTypeInferencePolicy;
import com.intellij.java.language.psi.impl.source.resolve.ParameterTypeInferencePolicy;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.util.RecursionGuard;
import consulo.application.util.RecursionManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.util.collection.Maps;
import consulo.util.lang.ThreeState;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.intellij.lang.annotations.MagicConstant;

import java.util.Map;
import java.util.function.Supplier;

/**
 * @author ik, dsl
 */
public class MethodCandidateInfo extends CandidateInfo {
    public static final RecursionGuard<PsiElement> ourOverloadGuard = RecursionManager.createGuard("overload.guard");
    public static final ThreadLocal<Map<PsiElement, CurrentCandidateProperties>> CURRENT_CANDIDATE = new ThreadLocal<>();
    @ApplicabilityLevelConstant
    private volatile int myApplicabilityLevel;
    @ApplicabilityLevelConstant
    private volatile int myPertinentApplicabilityLevel;
    private final PsiElement myArgumentList;
    private final PsiType[] myArgumentTypes;
    private final PsiType[] myTypeArguments;
    private PsiSubstitutor myCalcedSubstitutor;

    private volatile String myInferenceError;
    private final LanguageLevel myLanguageLevel;

    @RequiredReadAction
    public MethodCandidateInfo(
        @Nonnull PsiElement candidate,
        PsiSubstitutor substitutor,
        boolean accessProblem,
        boolean staticsProblem,
        PsiElement argumentList,
        PsiElement currFileContext,
        @Nullable PsiType[] argumentTypes,
        PsiType[] typeArguments
    ) {
        this(
            candidate,
            substitutor,
            accessProblem,
            staticsProblem,
            argumentList,
            currFileContext,
            argumentTypes,
            typeArguments,
            PsiUtil.getLanguageLevel(argumentList)
        );
    }

    public MethodCandidateInfo(
        @Nonnull PsiElement candidate,
        @Nonnull PsiSubstitutor substitutor,
        boolean accessProblem,
        boolean staticsProblem,
        PsiElement argumentList,
        PsiElement currFileContext,
        @Nullable PsiType[] argumentTypes,
        PsiType[] typeArguments,
        @Nonnull LanguageLevel languageLevel
    ) {
        super(candidate, substitutor, accessProblem, staticsProblem, currFileContext);
        myArgumentList = argumentList;
        myArgumentTypes = argumentTypes;
        myTypeArguments = typeArguments;
        myLanguageLevel = languageLevel;
    }

    public boolean isVarargs() {
        return false;
    }

    public boolean isApplicable() {
        return getPertinentApplicabilityLevel() != ApplicabilityLevel.NOT_APPLICABLE;
    }

    @ApplicabilityLevelConstant
    @RequiredReadAction
    private int getApplicabilityLevelInner() {
        PsiType[] argumentTypes = getArgumentTypes();

        if (argumentTypes == null) {
            return ApplicabilityLevel.NOT_APPLICABLE;
        }

        int level = PsiUtil.getApplicabilityLevel(getElement(), getSubstitutor(), argumentTypes, myLanguageLevel);
        if (level > ApplicabilityLevel.NOT_APPLICABLE && !isTypeArgumentsApplicable()) {
            level = ApplicabilityLevel.NOT_APPLICABLE;
        }
        return level;
    }

    @ApplicabilityLevelConstant
    @RequiredReadAction
    public int getApplicabilityLevel() {
        int result = myApplicabilityLevel;
        if (result == 0) {
            result = getApplicabilityLevelInner();
            myApplicabilityLevel = result;
        }
        return result;
    }

    @ApplicabilityLevelConstant
    @RequiredReadAction
    public int getPertinentApplicabilityLevel() {
        int result = myPertinentApplicabilityLevel;
        if (result == 0) {
            myPertinentApplicabilityLevel = result = pullInferenceErrorMessagesFromSubexpressions(getPertinentApplicabilityLevelInner());
        }
        return result;
    }

    /**
     * 15.12.2.2 Identify Matching Arity Methods Applicable by Strict Invocation
     */
    @ApplicabilityLevelConstant
    @RequiredReadAction
    public int getPertinentApplicabilityLevelInner() {
        if (myArgumentList == null || !PsiUtil.isLanguageLevel8OrHigher(myArgumentList)) {
            return getApplicabilityLevel();
        }

        PsiMethod method = getElement();

        if (isToInferApplicability()) {
            if (!isOverloadCheck()) {
                //ensure applicability check is performed
                getSubstitutor(false);
            }

            //already performed checks, so if inference failed, error message should be saved
            if (myInferenceError != null || isPotentiallyCompatible() != ThreeState.YES) {
                return ApplicabilityLevel.NOT_APPLICABLE;
            }
            return isVarargs() ? ApplicabilityLevel.VARARGS : ApplicabilityLevel.FIXED_ARITY;
        }

        PsiSubstitutor substitutor = getSubstitutor(false);
        @ApplicabilityLevelConstant
        int level = computeForOverloadedCandidate(
            () -> {
                //arg types are calculated here without additional constraints:
                //non-pertinent to applicability arguments of arguments would be skipped
                PsiType[] argumentTypes = getArgumentTypes();
                if (argumentTypes == null) {
                    return ApplicabilityLevel.NOT_APPLICABLE;
                }

                int level1 = PsiUtil.getApplicabilityLevel(method, substitutor, argumentTypes, myLanguageLevel);
                if (!isVarargs() && level1 < ApplicabilityLevel.FIXED_ARITY) {
                    return ApplicabilityLevel.NOT_APPLICABLE;
                }
                return level1;
            },
            substitutor,
            isVarargs(),
            true
        );
        if (level > ApplicabilityLevel.NOT_APPLICABLE && !isTypeArgumentsApplicable(() -> substitutor)) {
            level = ApplicabilityLevel.NOT_APPLICABLE;
        }
        return level;
    }

    //If m is a generic method and the method invocation does not provide explicit type
    //arguments, then the applicability of the method is inferred as specified in §18.5.1
    public boolean isToInferApplicability() {
        return myTypeArguments == null && getElement().hasTypeParameters() && !isRawSubstitution();
    }

    /**
     * 15.12.2.1 Identify Potentially Applicable Methods
     */
    public ThreeState isPotentiallyCompatible() {
        if (myArgumentList instanceof PsiExpressionList exprList) {
            PsiMethod method = getElement();
            PsiParameter[] parameters = method.getParameterList().getParameters();
            PsiExpression[] expressions = exprList.getExpressions();

            if (!isVarargs() && myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
                if (expressions.length != parameters.length) {
                    return ThreeState.NO;
                }
            }
            else {
                if (expressions.length < parameters.length - 1) {
                    return ThreeState.NO;
                }

                if (parameters.length == 0 && expressions.length != parameters.length) {
                    return ThreeState.NO;
                }
            }

            boolean unsure = false;

            for (int i = 0; i < expressions.length; i++) {
                PsiExpression expression = expressions[i];
                PsiType formalParameterType = i < parameters.length ? parameters[i].getType() : parameters[parameters.length - 1].getType();

                if (formalParameterType instanceof PsiEllipsisType && isVarargs()) {
                    formalParameterType = ((PsiEllipsisType) formalParameterType).getComponentType();
                }

                ThreeState compatible = isPotentialCompatible(expression, getSiteSubstitutor().substitute(formalParameterType), method);
                if (compatible == ThreeState.NO) {
                    return ThreeState.NO;
                }

                if (compatible == ThreeState.UNSURE) {
                    unsure = true;
                }
            }

            if (unsure) {
                return ThreeState.UNSURE;
            }

            if (method.hasTypeParameters() && myTypeArguments != null) {
                return ThreeState.fromBoolean(method.getTypeParameters().length == myTypeArguments.length); //todo
            }
        }
        return ThreeState.YES;
    }

    private static ThreeState isPotentialCompatible(PsiExpression expression, PsiType formalType, PsiMethod method) {
        if (expression instanceof PsiFunctionalExpression funcExpr) {
            PsiClass targetTypeParameter = PsiUtil.resolveClassInClassTypeOnly(formalType);
            if (targetTypeParameter instanceof PsiTypeParameter typeParam && method.equals(typeParam.getOwner())) {
                return ThreeState.YES;
            }

            if (!LambdaUtil.isFunctionalType(formalType)) {
                return ThreeState.NO;
            }

            if (!funcExpr.isPotentiallyCompatible(formalType)) {
                return ThreeState.UNSURE;
            }
        }
        else if (expression instanceof PsiParenthesizedExpression parenExpr) {
            return isPotentialCompatible(parenExpr.getExpression(), formalType, method);
        }
        else if (expression instanceof PsiConditionalExpression conditional) {
            ThreeState thenCompatible = isPotentialCompatible(conditional.getThenExpression(), formalType, method);
            ThreeState elseCompatible = isPotentialCompatible(conditional.getElseExpression(), formalType, method);
            if (thenCompatible == ThreeState.NO || elseCompatible == ThreeState.NO) {
                return ThreeState.NO;
            }
            if (thenCompatible == ThreeState.UNSURE || elseCompatible == ThreeState.UNSURE) {
                return ThreeState.UNSURE;
            }
        }
        return ThreeState.YES;
    }

    private <T> T computeForOverloadedCandidate(
        Supplier<T> computable,
        PsiSubstitutor substitutor,
        boolean varargs,
        boolean applicabilityCheck
    ) {
        Map<PsiElement, CurrentCandidateProperties> map = CURRENT_CANDIDATE.get();
        if (map == null) {
            map = Maps.newConcurrentWeakHashMap();
            CURRENT_CANDIDATE.set(map);
        }
        PsiElement argumentList = getMarkerList();
        CurrentCandidateProperties alreadyThere =
            map.put(argumentList, new CurrentCandidateProperties(this, substitutor, varargs, applicabilityCheck));
        try {
            return computable.get();
        }
        finally {
            if (alreadyThere == null) {
                map.remove(argumentList);
            }
            else {
                map.put(argumentList, alreadyThere);
            }
        }
    }

    @Nonnull
    public PsiSubstitutor getSiteSubstitutor() {
        PsiSubstitutor incompleteSubstitutor = super.getSubstitutor();
        if (myTypeArguments != null) {
            PsiMethod method = getElement();
            PsiTypeParameter[] typeParams = method.getTypeParameters();
            for (int i = 0; i < myTypeArguments.length && i < typeParams.length; i++) {
                incompleteSubstitutor = incompleteSubstitutor.put(typeParams[i], myTypeArguments[i]);
            }
        }
        return incompleteSubstitutor;
    }

    @Nonnull
    @Override
    public PsiSubstitutor getSubstitutor() {
        return getSubstitutor(true);
    }

    @Nonnull
    public PsiSubstitutor getSubstitutor(boolean includeReturnConstraint) {
        PsiSubstitutor substitutor = myCalcedSubstitutor;
        if (substitutor == null || !includeReturnConstraint && myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_8) || isOverloadCheck()) {

            PsiSubstitutor incompleteSubstitutor = super.getSubstitutor();
            PsiMethod method = getElement();
            if (myTypeArguments == null) {
                RecursionGuard.StackStamp stackStamp = RecursionManager.markStack();

                PsiSubstitutor inferredSubstitutor =
                    inferTypeArguments(DefaultParameterTypeInferencePolicy.INSTANCE, includeReturnConstraint);

                if (!stackStamp.mayCacheNow()
                    || isOverloadCheck()
                    || !includeReturnConstraint && myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_8)
                    || getMarkerList() != null && PsiResolveHelper.ourGraphGuard.currentStack().contains(getMarkerList().getParent())
                    || LambdaUtil.isLambdaParameterCheck()) {
                    return inferredSubstitutor;
                }

                myCalcedSubstitutor = substitutor = inferredSubstitutor;
            }
            else {
                PsiTypeParameter[] typeParams = method.getTypeParameters();
                for (int i = 0; i < myTypeArguments.length && i < typeParams.length; i++) {
                    incompleteSubstitutor = incompleteSubstitutor.put(typeParams[i], myTypeArguments[i]);
                }
                myCalcedSubstitutor = substitutor = incompleteSubstitutor;
            }
        }

        return substitutor;
    }

    public static boolean isOverloadCheck() {
        return !ourOverloadGuard.currentStack().isEmpty();
    }

    public static boolean isOverloadCheck(PsiElement argumentList) {
        return ourOverloadGuard.currentStack().contains(argumentList);
    }

    @RequiredReadAction
    public boolean isTypeArgumentsApplicable() {
        return isTypeArgumentsApplicable(() -> getSubstitutor(false));
    }

    @RequiredReadAction
    private boolean isTypeArgumentsApplicable(Supplier<PsiSubstitutor> computable) {
        PsiMethod psiMethod = getElement();
        PsiTypeParameter[] typeParams = psiMethod.getTypeParameters();
        if (myTypeArguments != null && typeParams.length != myTypeArguments.length && !PsiUtil.isLanguageLevel7OrHigher(psiMethod)) {
            return typeParams.length == 0 && JavaVersionService.getInstance().isAtLeast(psiMethod, JavaSdkVersion.JDK_1_7);
        }
        return GenericsUtil.isTypeArgumentsApplicable(typeParams, computable.get(), getParent());
    }

    protected PsiElement getParent() {
        return myArgumentList != null ? myArgumentList.getParent() : null;
    }

    @Override
    public boolean isValidResult() {
        return super.isValidResult() && isApplicable();
    }

    @Nonnull
    @Override
    public PsiMethod getElement() {
        return (PsiMethod) super.getElement();
    }

    @Nonnull
    public PsiSubstitutor inferTypeArguments(@Nonnull ParameterTypeInferencePolicy policy, boolean includeReturnConstraint) {
        return inferTypeArguments(
            policy,
            myArgumentList instanceof PsiExpressionList ? ((PsiExpressionList) myArgumentList).getExpressions() : PsiExpression.EMPTY_ARRAY,
            includeReturnConstraint
        );
    }

    public PsiSubstitutor inferSubstitutorFromArgs(@Nonnull ParameterTypeInferencePolicy policy, PsiExpression[] arguments) {
        if (myTypeArguments == null) {
            return inferTypeArguments(policy, arguments, true);
        }
        else {
            return getSiteSubstitutor();
        }
    }

    /**
     * If iterated through all candidates, should be called under {@link #ourOverloadGuard} guard so results won't be cached on the top level call
     */
    @Nonnull
    public PsiSubstitutor inferTypeArguments(
        @Nonnull ParameterTypeInferencePolicy policy,
        @Nonnull PsiExpression[] arguments,
        boolean includeReturnConstraint
    ) {
        return computeForOverloadedCandidate(
            () -> {
                PsiMethod method = this.getElement();
                PsiTypeParameter[] typeParameters = method.getTypeParameters();

                if (this.isRawSubstitution()) {
                    return JavaPsiFacade.getInstance(method.getProject())
                        .getElementFactory()
                        .createRawSubstitutor(mySubstitutor, typeParameters);
                }

                PsiElement parent = this.getParent();
                if (parent == null) {
                    return PsiSubstitutor.EMPTY;
                }
                Project project = method.getProject();
                JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(project);
                return javaPsiFacade.getResolveHelper().inferTypeArguments(
                    typeParameters,
                    method.getParameterList().getParameters(),
                    arguments,
                    mySubstitutor,
                    parent,
                    policy,
                    myLanguageLevel
                );
            },
            super.getSubstitutor(),
            policy.isVarargsIgnored() || isVarargs(),
            !includeReturnConstraint
        );
    }

    public boolean isRawSubstitution() {
        PsiMethod method = getElement();
        if (!method.isStatic()) {
            PsiClass containingClass = method.getContainingClass();
            if (containingClass != null && PsiUtil.isRawSubstitutor(containingClass, mySubstitutor)) {
                return true;
            }
        }
        return false;
    }

    protected PsiElement getMarkerList() {
        return myArgumentList;
    }

    @RequiredReadAction
	public boolean isInferencePossible() {
        return myArgumentList != null && myArgumentList.isValid();
    }

    public static CurrentCandidateProperties getCurrentMethod(PsiElement context) {
        Map<PsiElement, CurrentCandidateProperties> currentMethodCandidates = CURRENT_CANDIDATE.get();
        return currentMethodCandidates != null ? currentMethodCandidates.get(context) : null;
    }

    public static void updateSubstitutor(PsiElement context, PsiSubstitutor newSubstitutor) {
        CurrentCandidateProperties candidateProperties = getCurrentMethod(context);
        if (candidateProperties != null) {
            candidateProperties.setSubstitutor(newSubstitutor);
        }
    }

    @Nullable
    public PsiType[] getArgumentTypes() {
        return myArgumentTypes;
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o) && isVarargs() == ((MethodCandidateInfo) o).isVarargs();
    }

    @Override
    public int hashCode() {
        return 31 * super.hashCode() + (isVarargs() ? 1 : 0);
    }

    public void setInferenceError(String inferenceError) {
        myInferenceError = inferenceError;
    }

    public String getInferenceErrorMessage() {
        return myInferenceError;
    }

    public String getParentInferenceErrorMessage(PsiExpressionList list) {
        String errorMessage = getInferenceErrorMessage();
        while (errorMessage == null) {
            list = PsiTreeUtil.getParentOfType(list, PsiExpressionList.class, true, PsiCodeBlock.class);
            if (list == null) {
                break;
            }
            if (!(list.getParent() instanceof PsiCallExpression call)) {
                break;
            }
            if (call.resolveMethodGenerics() instanceof MethodCandidateInfo methodCandidateInfo) {
                errorMessage = methodCandidateInfo.getInferenceErrorMessage();
            }
        }
        return errorMessage;
    }

    @ApplicabilityLevelConstant
    private int pullInferenceErrorMessagesFromSubexpressions(@ApplicabilityLevelConstant int level) {
        if (myArgumentList instanceof PsiExpressionList exprList && level == ApplicabilityLevel.NOT_APPLICABLE) {
            String errorMessage = null;
            for (PsiExpression expression : exprList.getExpressions()) {
                String message = clearErrorMessageInSubexpressions(expression);
                if (message != null) {
                    errorMessage = message;
                }
            }
            if (errorMessage != null) {
                setInferenceError(errorMessage);
            }
        }
        return level;
    }

    private static String clearErrorMessageInSubexpressions(PsiExpression expression) {
        expression = PsiUtil.skipParenthesizedExprDown(expression);
        if (expression instanceof PsiConditionalExpression conditional) {
            String thenErrorMessage = clearErrorMessageInSubexpressions(conditional.getThenExpression());
            String elseErrorMessage = clearErrorMessageInSubexpressions(conditional.getElseExpression());
            if (thenErrorMessage != null) {
                return thenErrorMessage;
            }
            return elseErrorMessage;
        }
        else if (expression instanceof PsiCallExpression callExpr) {
            JavaResolveResult result;
            if (callExpr instanceof PsiNewExpression newExpr) {
                PsiDiamondType diamondType = PsiDiamondType.getDiamondType(newExpr);
                result = diamondType != null ? diamondType.getStaticFactory() : newExpr.resolveMethodGenerics();
            }
            else {
                result = callExpr.resolveMethodGenerics();
            }
            if (result instanceof MethodCandidateInfo methodCandidateInfo) {
                String message = methodCandidateInfo.getInferenceErrorMessage();
                methodCandidateInfo.setInferenceError(null);
                return message;
            }
        }
        return null;
    }

    public CurrentCandidateProperties createProperties() {
        return new CurrentCandidateProperties(this, getSiteSubstitutor(), isVarargs(), false);
    }

    public static class CurrentCandidateProperties {
        private final MethodCandidateInfo myMethod;
        private PsiSubstitutor mySubstitutor;
        private boolean myVarargs;
        private boolean myApplicabilityCheck;

        private CurrentCandidateProperties(
            MethodCandidateInfo info,
            PsiSubstitutor substitutor,
            boolean varargs,
            boolean applicabilityCheck
        ) {
            myMethod = info;
            mySubstitutor = substitutor;
            myVarargs = varargs;
            myApplicabilityCheck = applicabilityCheck;
        }

        public PsiMethod getMethod() {
            return myMethod.getElement();
        }

        public MethodCandidateInfo getInfo() {
            return myMethod;
        }

        public PsiSubstitutor getSubstitutor() {
            return mySubstitutor;
        }

        public void setSubstitutor(PsiSubstitutor substitutor) {
            mySubstitutor = substitutor;
        }

        public boolean isVarargs() {
            return myVarargs;
        }

        public void setVarargs(boolean varargs) {
            myVarargs = varargs;
        }

        public boolean isApplicabilityCheck() {
            return myApplicabilityCheck;
        }

        public void setApplicabilityCheck(boolean applicabilityCheck) {
            myApplicabilityCheck = applicabilityCheck;
        }
    }

    public static class ApplicabilityLevel {
        public static final int NOT_APPLICABLE = 1;
        public static final int VARARGS = 2;
        public static final int FIXED_ARITY = 3;
    }

    @MagicConstant(intValues = {
        ApplicabilityLevel.NOT_APPLICABLE,
        ApplicabilityLevel.VARARGS,
        ApplicabilityLevel.FIXED_ARITY
    })
    public @interface ApplicabilityLevelConstant {
    }
}
