/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis;

import com.intellij.java.analysis.codeInsight.intention.QuickFixFactory;
import com.intellij.java.analysis.impl.psi.util.PsiMatchers;
import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.java.language.impl.codeInsight.daemon.JavaErrorBundle;
import com.intellij.java.language.impl.psi.impl.PsiClassImplUtil;
import com.intellij.java.language.projectRoots.JavaSdkVersion;
import com.intellij.java.language.projectRoots.JavaVersionService;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.search.PsiShortNamesCache;
import com.intellij.java.language.psi.search.searches.SuperMethodsSearch;
import com.intellij.java.language.psi.util.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.dumb.IndexNotReadyException;
import consulo.document.util.TextRange;
import consulo.java.language.impl.localize.JavaErrorLocalize;
import consulo.java.language.module.util.JavaClassNames;
import consulo.language.content.FileIndexFacade;
import consulo.language.editor.intention.QuickFixAction;
import consulo.language.editor.intention.QuickFixActionRegistrar;
import consulo.language.editor.rawHighlight.HighlightInfo;
import consulo.language.editor.rawHighlight.HighlightInfoHolder;
import consulo.language.editor.rawHighlight.HighlightInfoType;
import consulo.language.psi.*;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiMatcherImpl;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.DumbService;
import consulo.project.Project;
import consulo.util.collection.*;
import consulo.util.lang.Comparing;
import consulo.util.lang.Pair;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;

/**
 * @author cdr
 */
public class GenericsHighlightUtil {
    private static final Logger LOG = Logger.getInstance(GenericsHighlightUtil.class);

    private GenericsHighlightUtil() {
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkInferredTypeArguments(
        PsiTypeParameterListOwner listOwner,
        PsiElement call,
        PsiSubstitutor substitutor
    ) {
        return checkInferredTypeArguments(listOwner.getTypeParameters(), call, substitutor);
    }

    @Nullable
    @RequiredReadAction
    private static HighlightInfo checkInferredTypeArguments(
        PsiTypeParameter[] typeParameters,
        PsiElement call,
        PsiSubstitutor substitutor
    ) {
        Pair<PsiTypeParameter, PsiType> inferredTypeArgument =
            GenericsUtil.findTypeParameterWithBoundError(typeParameters, substitutor, call, false);
        if (inferredTypeArgument != null) {
            PsiType extendsType = inferredTypeArgument.second;
            PsiTypeParameter typeParameter = inferredTypeArgument.first;
            PsiClass boundClass = extendsType instanceof PsiClassType extendsClassType ? extendsClassType.resolve() : null;

            String c = HighlightUtil.formatClass(typeParameter);
            String t1 = JavaHighlightUtil.formatType(extendsType);
            String t2 = JavaHighlightUtil.formatType(substitutor.substitute(typeParameter));
            LocalizeValue description = boundClass == null || typeParameter.isInterface() == boundClass.isInterface()
                ? JavaErrorLocalize.genericsInferredTypeForTypeParameterIsNotWithinItsBoundExtend(c, t1, t2)
                : JavaErrorLocalize.genericsInferredTypeForTypeParameterIsNotWithinItsBoundImplement(c, t1, t2);
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(call)
                .descriptionAndTooltip(description)
                .create();
        }

        return null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkParameterizedReferenceTypeArguments(
        PsiElement resolved,
        PsiJavaCodeReferenceElement referenceElement,
        PsiSubstitutor substitutor,
        @Nonnull JavaSdkVersion javaSdkVersion
    ) {
        if (!(resolved instanceof PsiTypeParameterListOwner typeParameterListOwner)) {
            return null;
        }
        return checkReferenceTypeArgumentList(
            typeParameterListOwner,
            referenceElement.getParameterList(),
            substitutor,
            true,
            javaSdkVersion
        );
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkReferenceTypeArgumentList(
        PsiTypeParameterListOwner typeParameterListOwner,
        PsiReferenceParameterList referenceParameterList,
        PsiSubstitutor substitutor,
        boolean registerIntentions,
        @Nonnull JavaSdkVersion javaSdkVersion
    ) {
        PsiDiamondType.DiamondInferenceResult inferenceResult = null;
        PsiTypeElement[] referenceElements = null;
        if (referenceParameterList != null) {
            referenceElements = referenceParameterList.getTypeParameterElements();
            if (referenceElements.length == 1 && referenceElements[0].getType() instanceof PsiDiamondType diamondType) {
                if (!typeParameterListOwner.hasTypeParameters()) {
                    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                        .range(referenceParameterList)
                        .descriptionAndTooltip(JavaErrorLocalize.genericsDiamondNotApplicable())
                        .create();
                }
                inferenceResult = diamondType.resolveInferredTypes();
                String errorMessage = inferenceResult.getErrorMessage();
                if (errorMessage != null
                    && !(inferenceResult.failedToInfer()
                    && detectExpectedType(referenceParameterList) instanceof PsiClassType expectedClassType
                    && expectedClassType.isRaw())) {
                    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                        .range(referenceParameterList)
                        .descriptionAndTooltip(errorMessage)
                        .create();
                }
            }
        }

        if (registerIntentions) {
            HighlightInfo wrongParamNumberHighlightInfo =
                checkForWrongNumberOfTypeParameters(typeParameterListOwner, referenceParameterList, javaSdkVersion);
            if (wrongParamNumberHighlightInfo != null) {
                return wrongParamNumberHighlightInfo;
            }
        }

        // bounds check
        PsiTypeParameter[] typeParameters = typeParameterListOwner.getTypeParameters();
        int targetParametersNum = typeParameters.length;
        int refParametersNum = referenceParameterList == null ? 0 : referenceParameterList.getTypeArguments().length;
        if (targetParametersNum > 0 && refParametersNum != 0) {
            if (inferenceResult != null) {
                PsiType[] types = inferenceResult.getTypes();
                for (int i = 0; i < typeParameters.length; i++) {
                    PsiType type = types[i];
                    HighlightInfo highlightInfo = checkTypeParameterWithinItsBound(
                        typeParameters[i],
                        substitutor,
                        type,
                        referenceElements[0],
                        referenceParameterList
                    );
                    if (highlightInfo != null) {
                        return highlightInfo;
                    }
                }
            }
            else {
                for (int i = 0; i < typeParameters.length; i++) {
                    PsiTypeElement typeElement = referenceElements[i];
                    HighlightInfo highlightInfo = checkTypeParameterWithinItsBound(
                        typeParameters[i],
                        substitutor,
                        typeElement.getType(),
                        typeElement,
                        referenceParameterList
                    );
                    if (highlightInfo != null) {
                        return highlightInfo;
                    }
                }
            }
        }

        return null;
    }

    @Nullable
    @RequiredReadAction
    private static HighlightInfo checkForWrongNumberOfTypeParameters(
        PsiTypeParameterListOwner typeParameterListOwner,
        PsiReferenceParameterList referenceParameterList,
        @Nonnull JavaSdkVersion javaSdkVersion
    ) {
        PsiTypeParameter[] typeParameters = typeParameterListOwner.getTypeParameters();
        int targetParametersNum = typeParameters.length;
        int refParametersNum = referenceParameterList == null ? 0 : referenceParameterList.getTypeArguments().length;
        if (targetParametersNum == refParametersNum || refParametersNum == 0) {
            return null;
        }

        LocalizeValue description;
        if (targetParametersNum == 0) {
            if (PsiTreeUtil.getParentOfType(referenceParameterList, PsiCall.class) != null
                && typeParameterListOwner instanceof PsiMethod method
                && (javaSdkVersion.isAtLeast(JavaSdkVersion.JDK_1_7) || hasSuperMethodsWithTypeParams(method))) {
                return null;
            }
            else {
                description = JavaErrorLocalize.genericsTypeOrMethodDoesNotHaveTypeParameters(
                    typeParameterListOwnerCategoryDescription(typeParameterListOwner),
                    typeParameterListOwnerDescription(typeParameterListOwner)
                );
            }
        }
        else {
            description = JavaErrorLocalize.genericsWrongNumberOfTypeArguments(refParametersNum, targetParametersNum);
        }

        HighlightInfo highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .range(referenceParameterList)
            .descriptionAndTooltip(description)
            .create();
        if (typeParameterListOwner instanceof PsiClass psiClass) {
            QuickFixAction.registerQuickFixAction(
                highlightInfo,
                QuickFixFactory.getInstance().createChangeClassSignatureFromUsageFix(psiClass, referenceParameterList)
            );
        }

        if (referenceParameterList.getParent().getParent() instanceof PsiTypeElement typeElem
            && typeElem.getParent() instanceof PsiVariable variable) {
            if (targetParametersNum == 0) {
                QuickFixAction.registerQuickFixAction(
                    highlightInfo,
                    QuickFixFactory.getInstance().createRemoveTypeArgumentsFix(variable)
                );
            }
            registerVariableParameterizedTypeFixes(highlightInfo, variable, referenceParameterList, javaSdkVersion);
        }
        return highlightInfo;
    }

    private static boolean hasSuperMethodsWithTypeParams(PsiMethod method) {
        for (PsiMethod superMethod : method.findDeepestSuperMethods()) {
            if (superMethod.hasTypeParameters()) {
                return true;
            }
        }
        return false;
    }

    private static PsiType detectExpectedType(PsiReferenceParameterList referenceParameterList) {
        PsiNewExpression newExpr = PsiTreeUtil.getParentOfType(referenceParameterList, PsiNewExpression.class);
        LOG.assertTrue(newExpr != null);
        PsiElement parent = newExpr.getParent();
        PsiType expectedType = null;
        if (parent instanceof PsiVariable variable && newExpr.equals(variable.getInitializer())) {
            expectedType = variable.getType();
        }
        else if (parent instanceof PsiAssignmentExpression assignment && newExpr.equals(assignment.getRExpression())) {
            expectedType = assignment.getLExpression().getType();
        }
        else if (parent instanceof PsiReturnStatement) {
            if (PsiTreeUtil.getParentOfType(parent, PsiMethod.class, PsiLambdaExpression.class) instanceof PsiMethod method) {
                expectedType = method.getReturnType();
            }
        }
        else if (parent instanceof PsiExpressionList) {
            if (parent.getParent() instanceof PsiCallExpression callExpr && parent.equals(callExpr.getArgumentList())) {
                PsiMethod method = callExpr.resolveMethod();
                if (method != null) {
                    PsiExpression[] expressions = callExpr.getArgumentList().getExpressions();
                    int idx = ArrayUtil.find(expressions, newExpr);
                    if (idx > -1) {
                        PsiParameterList parameterList = method.getParameterList();
                        if (idx < parameterList.getParametersCount()) {
                            expectedType = parameterList.getParameters()[idx].getType();
                        }
                    }
                }
            }
        }
        return expectedType;
    }

    @Nullable
    @RequiredReadAction
    private static HighlightInfo checkTypeParameterWithinItsBound(
        PsiTypeParameter classParameter,
        PsiSubstitutor substitutor,
        PsiType type,
        PsiElement typeElement2Highlight,
        PsiReferenceParameterList referenceParameterList
    ) {
        PsiClass referenceClass = type instanceof PsiClassType ? ((PsiClassType)type).resolve() : null;
        PsiType psiType = substitutor.substitute(classParameter);
        if (psiType instanceof PsiClassType && !(PsiUtil.resolveClassInType(psiType) instanceof PsiTypeParameter)
            && GenericsUtil.checkNotInBounds(type, psiType, referenceParameterList)) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(typeElement2Highlight)
                .descriptionAndTooltip(LocalizeValue.localizeTODO("Actual type argument and inferred type contradict each other"))
                .create();
        }

        PsiClassType[] bounds = classParameter.getSuperTypes();
        for (PsiClassType type1 : bounds) {
            PsiType bound = substitutor.substitute(type1);
            if (!bound.equalsToText(JavaClassNames.JAVA_LANG_OBJECT)
                && GenericsUtil.checkNotInBounds(type, bound, referenceParameterList)) {
                PsiClass boundClass = bound instanceof PsiClassType boundClassType ? boundClassType.resolve() : null;

                String c = referenceClass != null ? HighlightUtil.formatClass(referenceClass) : type.getPresentableText();
                String t = JavaHighlightUtil.formatType(bound);
                LocalizeValue description = boundClass == null || referenceClass == null
                    || referenceClass.isInterface() == boundClass.isInterface()
                    ? JavaErrorLocalize.genericsTypeParameterIsNotWithinItsBoundExtend(c, t)
                    : JavaErrorLocalize.genericsTypeParameterIsNotWithinItsBoundImplement(c, t);

                HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(typeElement2Highlight)
                    .descriptionAndTooltip(description)
                    .create();
                if (bound instanceof PsiClassType boundClassType && referenceClass != null && info != null) {
                    QuickFixAction.registerQuickFixAction(
                        info,
                        QuickFixFactory.getInstance().createExtendsListFix(referenceClass, boundClassType, true), null
                    );
                }
                return info;
            }
        }
        return null;
    }

    private static String typeParameterListOwnerDescription(PsiTypeParameterListOwner typeParameterListOwner) {
        return switch (typeParameterListOwner) {
            case PsiClass psiClass -> HighlightUtil.formatClass(psiClass);
            case PsiMethod method -> JavaHighlightUtil.formatMethod(method);
            default -> {
                LOG.error("Unknown " + typeParameterListOwner);
                yield "?";
            }
        };
    }

    private static LocalizeValue typeParameterListOwnerCategoryDescription(PsiTypeParameterListOwner typeParameterListOwner) {
        return switch (typeParameterListOwner) {
            case PsiClass psiClass -> JavaErrorLocalize.genericsHolderType();
            case PsiMethod method -> JavaErrorLocalize.genericsHolderMethod();
            default -> {
                LOG.error("Unknown " + typeParameterListOwner);
                yield LocalizeValue.of("?");
            }
        };
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkElementInTypeParameterExtendsList(
        @Nonnull PsiReferenceList referenceList,
        @Nonnull PsiClass aClass,
        @Nonnull JavaResolveResult resolveResult,
        @Nonnull PsiElement element
    ) {
        PsiJavaCodeReferenceElement[] referenceElements = referenceList.getReferenceElements();
        PsiClass extendFrom = (PsiClass)resolveResult.getElement();
        if (extendFrom == null) {
            return null;
        }
        if (!extendFrom.isInterface() && referenceElements.length != 0 && element != referenceElements[0]) {
            PsiClassType type = JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory()
                .createType(extendFrom, resolveResult.getSubstitutor());
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(element)
                .descriptionAndTooltip(JavaErrorLocalize.interfaceExpected())
                .registerFix(QuickFixFactory.getInstance().createMoveBoundClassToFrontFix(aClass, type), null, null, null, null)
                .create();
        }
        else if (referenceElements.length != 0 && element != referenceElements[0]
            && referenceElements[0].resolve() instanceof PsiTypeParameter) {
            PsiClassType type = JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory()
                .createType(extendFrom, resolveResult.getSubstitutor());
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(element)
                .descriptionAndTooltip(JavaErrorBundle.message("type.parameter.cannot.be.followed.by.other.bounds"))
                .registerFix(QuickFixFactory.getInstance().createExtendsListFix(aClass, type, false), null, null, null, null)
                .create();
        }
        return null;
    }

    public static HighlightInfo checkInterfaceMultipleInheritance(PsiClass aClass) {
        PsiClassType[] types = aClass.getSuperTypes();
        if (types.length < 2) {
            return null;
        }
        Map<PsiClass, PsiSubstitutor> inheritedClasses = new HashMap<>();
        TextRange textRange = HighlightNamesUtil.getClassDeclarationTextRange(aClass);
        return checkInterfaceMultipleInheritance(aClass, aClass, PsiSubstitutor.EMPTY, inheritedClasses, new HashSet<>(), textRange);
    }

    private static HighlightInfo checkInterfaceMultipleInheritance(
        PsiClass aClass,
        PsiElement place,
        PsiSubstitutor derivedSubstitutor,
        Map<PsiClass, PsiSubstitutor> inheritedClasses,
        Set<PsiClass> visited,
        TextRange textRange
    ) {
        List<PsiClassType.ClassResolveResult> superTypes =
            PsiClassImplUtil.getScopeCorrectedSuperTypes(aClass, place.getResolveScope());
        for (PsiClassType.ClassResolveResult result : superTypes) {
            PsiClass superClass = result.getElement();
            if (superClass == null || visited.contains(superClass)) {
                continue;
            }
            PsiSubstitutor superTypeSubstitutor = result.getSubstitutor();
            superTypeSubstitutor = MethodSignatureUtil.combineSubstitutors(superTypeSubstitutor, derivedSubstitutor);

            PsiSubstitutor inheritedSubstitutor = inheritedClasses.get(superClass);
            if (inheritedSubstitutor != null) {
                PsiTypeParameter[] typeParameters = superClass.getTypeParameters();
                for (PsiTypeParameter typeParameter : typeParameters) {
                    PsiType type1 = inheritedSubstitutor.substitute(typeParameter);
                    PsiType type2 = superTypeSubstitutor.substitute(typeParameter);

                    if (!Objects.equals(type1, type2)) {
                        LocalizeValue description = JavaErrorLocalize.genericsCannotBeInheritedWithDifferentTypeArguments(
                            HighlightUtil.formatClass(superClass),
                            JavaHighlightUtil.formatType(type1),
                            JavaHighlightUtil.formatType(type2)
                        );
                        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                            .range(textRange)
                            .descriptionAndTooltip(description)
                            .create();
                    }
                }
            }
            inheritedClasses.put(superClass, superTypeSubstitutor);
            visited.add(superClass);
            HighlightInfo highlightInfo =
                checkInterfaceMultipleInheritance(superClass, place, superTypeSubstitutor, inheritedClasses, visited, textRange);
            visited.remove(superClass);

            if (highlightInfo != null) {
                return highlightInfo;
            }
        }
        return null;
    }

    @Nonnull
    @RequiredReadAction
    public static Collection<HighlightInfo> checkOverrideEquivalentMethods(@Nonnull PsiClass aClass) {
        List<HighlightInfo> result = new ArrayList<>();
        Collection<HierarchicalMethodSignature> signaturesWithSupers = aClass.getVisibleSignatures();
        PsiManager manager = aClass.getManager();
        Map<MethodSignature, MethodSignatureBackedByPsiMethod> sameErasureMethods =
            Maps.newHashMap(MethodSignatureUtil.METHOD_PARAMETERS_ERASURE_EQUALITY);

        Set<MethodSignature> foundProblems = Sets.newHashSet(MethodSignatureUtil.METHOD_PARAMETERS_ERASURE_EQUALITY);
        for (HierarchicalMethodSignature signature : signaturesWithSupers) {
            HighlightInfo info = checkSameErasureNotSubSignatureInner(signature, manager, aClass, sameErasureMethods);
            if (info != null && foundProblems.add(signature)) {
                result.add(info);
            }
            if (aClass instanceof PsiTypeParameter) {
                info = HighlightMethodUtil.checkMethodIncompatibleReturnType(
                    signature,
                    signature.getSuperSignatures(),
                    true,
                    HighlightNamesUtil.getClassDeclarationTextRange(aClass)
                );
                if (info != null) {
                    result.add(info);
                }
            }
        }

        return result;
    }

    @RequiredReadAction
    public static HighlightInfo checkDefaultMethodOverrideEquivalentToObjectNonPrivate(
        @Nonnull LanguageLevel languageLevel,
        @Nonnull PsiClass aClass,
        @Nonnull PsiMethod method,
        @Nonnull PsiElement methodIdentifier
    ) {
        if (languageLevel.isAtLeast(LanguageLevel.JDK_1_8) && aClass.isInterface() && method.hasModifierProperty(PsiModifier.DEFAULT)) {
            HierarchicalMethodSignature sig = method.getHierarchicalMethodSignature();
            for (HierarchicalMethodSignature methodSignature : sig.getSuperSignatures()) {
                PsiMethod objectMethod = methodSignature.getMethod();
                PsiClass containingClass = objectMethod.getContainingClass();
                if (containingClass != null && JavaClassNames.JAVA_LANG_OBJECT.equals(containingClass.getQualifiedName())
                    && objectMethod.isPublic()) {
                    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                        .range(methodIdentifier)
                        .descriptionAndTooltip("Default method '" + sig.getName() + "' overrides a member of 'java.lang.Object'")
                        .create();
                }
            }
        }
        return null;
    }

    @RequiredReadAction
    public static HighlightInfo checkUnrelatedDefaultMethods(@Nonnull PsiClass aClass, @Nonnull PsiIdentifier classIdentifier) {
        Map<? extends MethodSignature, Set<PsiMethod>> overrideEquivalent = PsiSuperMethodUtil.collectOverrideEquivalents(aClass);

        boolean isInterface = aClass.isInterface();
        for (Set<PsiMethod> overrideEquivalentMethods : overrideEquivalent.values()) {
            if (overrideEquivalentMethods.size() <= 1) {
                continue;
            }
            List<PsiMethod> defaults = null;
            List<PsiMethod> abstracts = null;
            boolean hasConcrete = false;
            for (PsiMethod method : overrideEquivalentMethods) {
                boolean isDefault = method.hasModifierProperty(PsiModifier.DEFAULT);
                boolean isAbstract = method.isAbstract();
                if (isDefault) {
                    if (defaults == null) {
                        defaults = new ArrayList<>(2);
                    }
                    defaults.add(method);
                }
                if (isAbstract) {
                    if (abstracts == null) {
                        abstracts = new ArrayList<>(2);
                    }
                    abstracts.add(method);
                }
                hasConcrete |= !isDefault && !isAbstract;
            }

            if (!hasConcrete && defaults != null) {
                PsiMethod defaultMethod = defaults.get(0);
                if (MethodSignatureUtil.findMethodBySuperMethod(aClass, defaultMethod, false) != null) {
                    continue;
                }
                PsiClass defaultMethodContainingClass = defaultMethod.getContainingClass();
                if (defaultMethodContainingClass == null) {
                    continue;
                }
                PsiMethod unrelatedMethod = abstracts != null ? abstracts.get(0) : defaults.get(1);
                PsiClass unrelatedMethodContainingClass = unrelatedMethod.getContainingClass();
                if (unrelatedMethodContainingClass == null) {
                    continue;
                }
                if (!aClass.isAbstract() && !(aClass instanceof PsiTypeParameter)
                    && abstracts != null && unrelatedMethodContainingClass.isInterface()) {
                    if (defaultMethodContainingClass.isInheritor(unrelatedMethodContainingClass, true)
                        && MethodSignatureUtil.isSubsignature(
                        unrelatedMethod.getSignature(TypeConversionUtil.getSuperClassSubstitutor(
                            unrelatedMethodContainingClass,
                            defaultMethodContainingClass,
                            PsiSubstitutor.EMPTY
                        )),
                        defaultMethod.getSignature(PsiSubstitutor.EMPTY)
                    )
                    ) {
                        continue;
                    }
                    String c1 = HighlightUtil.formatClass(aClass, false);
                    String m = JavaHighlightUtil.formatMethod(abstracts.get(0));
                    String c2 = HighlightUtil.formatClass(unrelatedMethodContainingClass, false);
                    LocalizeValue message = aClass instanceof PsiEnumConstantInitializer
                        ? JavaErrorLocalize.enumConstantShouldImplementMethod(c1, m, c2)
                        : JavaErrorLocalize.classMustBeAbstract(c1, m, c2);
                    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                        .range(classIdentifier)
                        .descriptionAndTooltip(message)
                        .registerFix(QuickFixFactory.getInstance().createImplementMethodsFix(aClass), null, null, null, null)
                        .create();
                }
                if (isInterface || abstracts == null || unrelatedMethodContainingClass.isInterface()) {
                    List<PsiClass> defaultContainingClasses = ContainerUtil.mapNotNull(defaults, PsiMethod::getContainingClass);
                    String unrelatedDefaults = hasUnrelatedDefaults(defaultContainingClasses);
                    if (unrelatedDefaults == null
                        && (abstracts == null || !hasNotOverriddenAbstract(defaultContainingClasses, unrelatedMethodContainingClass))) {
                        continue;
                    }

                    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                        .range(classIdentifier)
                        .descriptionAndTooltip(LocalizeValue.localizeTODO(
                            HighlightUtil.formatClass(aClass) +
                                (unrelatedDefaults != null ? " inherits unrelated defaults for " : " inherits abstract and default for ") +
                                JavaHighlightUtil.formatMethod(defaultMethod) + " from types " + (
                                unrelatedDefaults != null
                                    ? unrelatedDefaults
                                    : HighlightUtil.formatClass(defaultMethodContainingClass) + " and " +
                                    HighlightUtil.formatClass(unrelatedMethodContainingClass)
                            )
                        ))
                        .registerFix(QuickFixFactory.getInstance().createImplementMethodsFix(aClass), null, null, null, null)
                        .create();
                }
            }
        }
        return null;
    }

    private static boolean belongToOneHierarchy(
        @Nonnull PsiClass defaultMethodContainingClass,
        @Nonnull PsiClass unrelatedMethodContainingClass
    ) {
        return defaultMethodContainingClass.isInheritor(unrelatedMethodContainingClass, true) || unrelatedMethodContainingClass.isInheritor(
            defaultMethodContainingClass,
            true
        );
    }

    private static boolean hasNotOverriddenAbstract(
        List<PsiClass> defaultContainingClasses,
        @Nonnull PsiClass abstractMethodContainingClass
    ) {
        return defaultContainingClasses.stream()
            .noneMatch(containingClass -> belongToOneHierarchy(containingClass, abstractMethodContainingClass));
    }

    private static String hasUnrelatedDefaults(List<PsiClass> defaults) {
        if (defaults.size() > 1) {
            PsiClass[] defaultClasses = defaults.toArray(PsiClass.EMPTY_ARRAY);
            ArrayList<PsiClass> classes = new ArrayList<>(defaults);
            for (PsiClass aClass1 : defaultClasses) {
                classes.removeIf(aClass2 -> aClass1.isInheritor(aClass2, true));
            }

            if (classes.size() > 1) {
                return HighlightUtil.formatClass(classes.get(0)) + " and " + HighlightUtil.formatClass(classes.get(1));
            }
        }

        return null;
    }

    @RequiredReadAction
    public static HighlightInfo checkUnrelatedConcrete(@Nonnull PsiClass psiClass, @Nonnull PsiIdentifier classIdentifier) {
        PsiClass superClass = psiClass.getSuperClass();
        if (superClass != null && superClass.hasTypeParameters()) {
            Collection<HierarchicalMethodSignature> visibleSignatures = superClass.getVisibleSignatures();
            Map<MethodSignature, PsiMethod> overrideEquivalent =
                Maps.newHashMap(MethodSignatureUtil.METHOD_PARAMETERS_ERASURE_EQUALITY);
            for (HierarchicalMethodSignature hms : visibleSignatures) {
                PsiMethod method = hms.getMethod();
                if (method.isConstructor()) {
                    continue;
                }
                if (method.isAbstract() || method.hasModifierProperty(PsiModifier.DEFAULT)) {
                    continue;
                }
                if (psiClass.findMethodsBySignature(method, false).length > 0) {
                    continue;
                }
                PsiClass containingClass = method.getContainingClass();
                if (containingClass == null) {
                    continue;
                }
                PsiSubstitutor containingClassSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(containingClass, psiClass, PsiSubstitutor.EMPTY);
                PsiSubstitutor finalSubstitutor =
                    PsiSuperMethodUtil.obtainFinalSubstitutor(containingClass, containingClassSubstitutor, hms.getSubstitutor(), false);
                MethodSignatureBackedByPsiMethod signature = MethodSignatureBackedByPsiMethod.create(method, finalSubstitutor, false);
                PsiMethod foundMethod = overrideEquivalent.get(signature);
                PsiClass foundMethodContainingClass;
                if (foundMethod != null
                    && !foundMethod.isAbstract()
                    && !foundMethod.hasModifierProperty(PsiModifier.DEFAULT)
                    && (foundMethodContainingClass = foundMethod.getContainingClass()) != null) {
                    String description = "Methods " +
                        JavaHighlightUtil.formatMethod(foundMethod) + " from " + HighlightUtil.formatClass(foundMethodContainingClass) +
                        " and " +
                        JavaHighlightUtil.formatMethod(method) + " from " + HighlightUtil.formatClass(containingClass) +
                        " are inherited with the same signature";

                    HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                        .range(classIdentifier)
                        .descriptionAndTooltip(description)
                        .create();
                    //todo override fix
                    return info;
                }
                overrideEquivalent.put(signature, method);
            }
        }
        return null;
    }

    @Nullable
    @RequiredReadAction
    private static HighlightInfo checkSameErasureNotSubSignatureInner(
        @Nonnull HierarchicalMethodSignature signature,
        @Nonnull PsiManager manager,
        @Nonnull PsiClass aClass,
        @Nonnull Map<MethodSignature, MethodSignatureBackedByPsiMethod> sameErasureMethods
    ) {
        PsiMethod method = signature.getMethod();
        JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());
        if (!facade.getResolveHelper().isAccessible(method, aClass, null)) {
            return null;
        }
        MethodSignature signatureToErase = method.getSignature(PsiSubstitutor.EMPTY);
        MethodSignatureBackedByPsiMethod sameErasure = sameErasureMethods.get(signatureToErase);
        HighlightInfo info;
        if (sameErasure != null) {
            if (aClass instanceof PsiTypeParameter
                || MethodSignatureUtil.findMethodBySuperMethod(aClass, sameErasure.getMethod(), false) != null
                || !(InheritanceUtil.isInheritorOrSelf(
                sameErasure.getMethod().getContainingClass(),
                method.getContainingClass(),
                true
            )
                || InheritanceUtil.isInheritorOrSelf(method.getContainingClass(), sameErasure.getMethod().getContainingClass(), true))) {
                info = checkSameErasureNotSubSignatureOrSameClass(sameErasure, signature, aClass, method);
                if (info != null) {
                    return info;
                }
            }
        }
        else {
            sameErasureMethods.put(signatureToErase, signature);
        }
        List<HierarchicalMethodSignature> supers = signature.getSuperSignatures();
        for (HierarchicalMethodSignature superSignature : supers) {
            info = checkSameErasureNotSubSignatureInner(superSignature, manager, aClass, sameErasureMethods);
            if (info != null) {
                return info;
            }

            if (superSignature.isRaw() && !signature.isRaw()) {
                PsiType[] parameterTypes = signature.getParameterTypes();
                PsiType[] erasedTypes = superSignature.getErasedParameterTypes();
                for (int i = 0; i < erasedTypes.length; i++) {
                    if (!Comparing.equal(parameterTypes[i], erasedTypes[i])) {
                        return getSameErasureMessage(
                            false,
                            method,
                            superSignature.getMethod(),
                            HighlightNamesUtil.getClassDeclarationTextRange(aClass)
                        );
                    }
                }
            }

        }
        return null;
    }

    @Nullable
    @RequiredReadAction
    private static HighlightInfo checkSameErasureNotSubSignatureOrSameClass(
        MethodSignatureBackedByPsiMethod signatureToCheck,
        HierarchicalMethodSignature superSignature,
        PsiClass aClass,
        PsiMethod superMethod
    ) {
        PsiMethod checkMethod = signatureToCheck.getMethod();
        if (superMethod.equals(checkMethod)) {
            return null;
        }
        PsiClass checkContainingClass = checkMethod.getContainingClass();
        LOG.assertTrue(checkContainingClass != null);
        PsiClass superContainingClass = superMethod.getContainingClass();
        boolean checkEqualsSuper = checkContainingClass.equals(superContainingClass);
        if (checkMethod.isConstructor()) {
            if (!superMethod.isConstructor() || !checkEqualsSuper) {
                return null;
            }
        }
        else if (superMethod.isConstructor()) {
            return null;
        }

        boolean atLeast17 = JavaVersionService.getInstance().isAtLeast(aClass, JavaSdkVersion.JDK_1_7);
        if (checkMethod.isStatic() && !checkEqualsSuper && !atLeast17) {
            return null;
        }

        if (superMethod.isStatic() && superContainingClass != null &&
            superContainingClass.isInterface() && !checkEqualsSuper && PsiUtil.isLanguageLevel8OrHigher(superContainingClass)) {
            return null;
        }

        PsiType retErasure1 = TypeConversionUtil.erasure(checkMethod.getReturnType());
        PsiType retErasure2 = TypeConversionUtil.erasure(superMethod.getReturnType());

        boolean differentReturnTypeErasure = !Comparing.equal(retErasure1, retErasure2);
        if (checkEqualsSuper && atLeast17) {
            if (retErasure1 != null && retErasure2 != null) {
                differentReturnTypeErasure = !TypeConversionUtil.isAssignable(retErasure1, retErasure2);
            }
            else {
                differentReturnTypeErasure = !(retErasure1 == null && retErasure2 == null);
            }
        }

        if (differentReturnTypeErasure
            && !TypeConversionUtil.isVoidType(retErasure1)
            && !TypeConversionUtil.isVoidType(retErasure2)
            && !(checkEqualsSuper && Arrays.equals(superSignature.getParameterTypes(), signatureToCheck.getParameterTypes()))
            && !atLeast17) {
            int idx = 0;
            PsiType[] erasedTypes = signatureToCheck.getErasedParameterTypes();
            boolean erasure = erasedTypes.length > 0;
            for (PsiType type : superSignature.getParameterTypes()) {
                erasure &= Comparing.equal(type, erasedTypes[idx]);
                idx++;
            }

            if (!erasure) {
                return null;
            }
        }

        if (!checkEqualsSuper && MethodSignatureUtil.isSubsignature(superSignature, signatureToCheck)) {
            return null;
        }
        if (superContainingClass != null && !superContainingClass.isInterface() && checkContainingClass.isInterface()
            && !aClass.equals(superContainingClass)) {
            return null;
        }
        if (aClass.equals(checkContainingClass)) {
            boolean sameClass = aClass.equals(superContainingClass);
            return getSameErasureMessage(
                sameClass,
                checkMethod,
                superMethod,
                HighlightNamesUtil.getMethodDeclarationTextRange(checkMethod)
            );
        }
        else {
            return getSameErasureMessage(false, checkMethod, superMethod, HighlightNamesUtil.getClassDeclarationTextRange(aClass));
        }
    }

    private static HighlightInfo getSameErasureMessage(
        boolean sameClass,
        @Nonnull PsiMethod method,
        @Nonnull PsiMethod superMethod,
        TextRange textRange
    ) {
        String clashMethodMessage = HighlightMethodUtil.createClashMethodMessage(method, superMethod, !sameClass);
        LocalizeValue description = sameClass
            ? JavaErrorLocalize.genericsMethodsHaveSameErasure(clashMethodMessage)
            : method.isStatic()
            ? JavaErrorLocalize.genericsMethodsHaveSameErasureHide(clashMethodMessage)
            : JavaErrorLocalize.genericsMethodsHaveSameErasureOverride(clashMethodMessage);
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .range(textRange)
            .descriptionAndTooltip(description)
            .create();
    }

    @RequiredReadAction
    public static HighlightInfo checkTypeParameterInstantiation(PsiNewExpression expression) {
        PsiJavaCodeReferenceElement classReference = expression.getClassOrAnonymousClassReference();
        if (classReference == null) {
            return null;
        }
        JavaResolveResult result = classReference.advancedResolve(false);
        PsiElement element = result.getElement();
        if (element instanceof PsiTypeParameter typeParam) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(classReference)
                .descriptionAndTooltip(JavaErrorLocalize.genericsTypeParameterCannotBeInstantiated(HighlightUtil.formatClass(typeParam)))
                .create();
        }
        return null;
    }

    @RequiredReadAction
    public static HighlightInfo checkWildcardUsage(PsiTypeElement typeElement) {
        PsiType type = typeElement.getType();
        if (type instanceof PsiWildcardType) {
            if (typeElement.getParent() instanceof PsiReferenceParameterList) {
                PsiElement parent = typeElement.getParent().getParent();
                LOG.assertTrue(parent instanceof PsiJavaCodeReferenceElement, parent);
                PsiElement refParent = parent.getParent();
                if (refParent instanceof PsiAnonymousClass) {
                    refParent = refParent.getParent();
                }
                if (refParent instanceof PsiNewExpression) {
                    PsiNewExpression newExpression = (PsiNewExpression)refParent;
                    if (!(newExpression.getType() instanceof PsiArrayType)) {
                        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                            .range(typeElement)
                            .descriptionAndTooltip(JavaErrorLocalize.wildcardTypeCannotBeInstantiated(JavaHighlightUtil.formatType(type)))
                            .create();
                    }
                }
                else if (refParent instanceof PsiReferenceList) {
                    if (!(refParent.getParent() instanceof PsiTypeParameter typeParam) || refParent != typeParam.getExtendsList()) {
                        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                            .range(typeElement)
                            .descriptionAndTooltip(JavaErrorLocalize.genericsWildcardNotExpected())
                            .create();
                    }
                }
            }
            else {
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(typeElement)
                    .descriptionAndTooltip(JavaErrorLocalize.genericsWildcardsMayBeUsedOnlyAsReferenceParameters())
                    .create();
            }
        }

        return null;
    }

    @RequiredReadAction
    public static HighlightInfo checkReferenceTypeUsedAsTypeArgument(PsiTypeElement typeElement, LanguageLevel level) {
        PsiType type = typeElement.getType();
        if (type != PsiType.NULL && type instanceof PsiPrimitiveType
            || type instanceof PsiWildcardType wildcardType && wildcardType.getBound() instanceof PsiPrimitiveType) {
            PsiElement element = new PsiMatcherImpl(typeElement)
                .parent(PsiMatchers.hasClass(PsiReferenceParameterList.class))
                .parent(PsiMatchers.hasClass(PsiJavaCodeReferenceElement.class, PsiNewExpression.class))
                .getElement();
            if (element == null) {
                return null;
            }

            if (level.isAtLeast(LanguageLevel.JDK_X)) {
                return null;
            }

            HighlightInfo highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(typeElement)
                .descriptionAndTooltip(JavaErrorLocalize.genericsTypeArgumentCannotBeOfPrimitiveType())
                .create();

            PsiType toConvert = type;
            if (type instanceof PsiWildcardType wildcardType) {
                toConvert = wildcardType.getBound();
            }
            if (toConvert instanceof PsiPrimitiveType primitiveType) {
                PsiClassType boxedType = primitiveType.getBoxedType(typeElement);
                if (boxedType != null) {
                    QuickFixAction.registerQuickFixAction(
                        highlightInfo,
                        QuickFixFactory.getInstance().createReplacePrimitiveWithBoxedTypeAction(
                            typeElement,
                            toConvert.getPresentableText(),
                            primitiveType.getBoxedTypeName()
                        )
                    );
                }
            }
            return highlightInfo;
        }

        return null;
    }

    @RequiredReadAction
    public static HighlightInfo checkForeachExpressionTypeIsIterable(PsiExpression expression) {
        if (expression == null || expression.getType() == null) {
            return null;
        }
        PsiType itemType = JavaGenericsUtil.getCollectionItemType(expression);
        if (itemType == null) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(expression)
                .descriptionAndTooltip(JavaErrorLocalize.foreachNotApplicable(JavaHighlightUtil.formatType(expression.getType())))
                .registerFix(QuickFixFactory.getInstance().createNotIterableForEachLoopFix(expression), null, null, null, null)
                .create();
        }
        return null;
    }

    @RequiredReadAction
    public static HighlightInfo checkForEachParameterType(@Nonnull PsiForeachStatement statement, @Nonnull PsiParameter parameter) {
        PsiExpression expression = statement.getIteratedValue();
        PsiType itemType = expression == null ? null : JavaGenericsUtil.getCollectionItemType(expression);
        if (itemType == null) {
            return null;
        }

        PsiType parameterType = parameter.getType();
        if (TypeConversionUtil.isAssignable(parameterType, itemType)) {
            return null;
        }
        HighlightInfo highlightInfo =
            HighlightUtil.createIncompatibleTypeHighlightInfo(itemType, parameterType, parameter.getTextRange(), 0);
        HighlightUtil.registerChangeVariableTypeFixes(parameter, itemType, expression, highlightInfo);
        return highlightInfo;
    }

    //http://docs.oracle.com/javase/specs/jls/se7/html/jls-8.html#jls-8.9.2
    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkAccessStaticFieldFromEnumConstructor(
        @Nonnull PsiReferenceExpression expr,
        @Nonnull JavaResolveResult result
    ) {
        PsiElement resolved = result.getElement();

        if (!(resolved instanceof PsiField field) || !field.isStatic()) {
            return null;
        }
        if (expr.getParent() instanceof PsiSwitchLabelStatement) {
            return null;
        }
        PsiMember constructorOrInitializer = PsiUtil.findEnclosingConstructorOrInitializer(expr);
        if (constructorOrInitializer == null) {
            return null;
        }
        if (constructorOrInitializer.isStatic()) {
            return null;
        }
        PsiClass aClass = constructorOrInitializer instanceof PsiEnumConstantInitializer enumConstantInitializer
            ? enumConstantInitializer
            : constructorOrInitializer.getContainingClass();
        if (aClass == null || !(aClass.isEnum() || aClass instanceof PsiEnumConstantInitializer)) {
            return null;
        }
        if (aClass instanceof PsiEnumConstantInitializer) {
            if (field.getContainingClass() != aClass.getSuperClass()) {
                return null;
            }
        }
        else if (field.getContainingClass() != aClass) {
            return null;
        }

        if (!JavaVersionService.getInstance().isAtLeast(field, JavaSdkVersion.JDK_1_6)) {
            PsiType type = field.getType();
            if (type instanceof PsiClassType && ((PsiClassType)type).resolve() == aClass) {
                return null;
            }
        }

        if (PsiUtil.isCompileTimeConstant((PsiVariable)field)) {
            return null;
        }

        LocalizeValue description = JavaErrorLocalize.illegalToAccessStaticMemberFromEnumConstructorOrInstanceInitializer(
            HighlightMessageUtil.getSymbolName(resolved, result.getSubstitutor())
        );

        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .range(expr)
            .descriptionAndTooltip(description)
            .create();
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkEnumInstantiation(PsiElement expression, PsiClass aClass) {
        if (aClass != null && aClass.isEnum() && (!(expression instanceof PsiNewExpression newExpr)
            || newExpr.getArrayDimensions().length == 0 && newExpr.getArrayInitializer() == null)) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(expression)
                .descriptionAndTooltip(JavaErrorLocalize.enumTypesCannotBeInstantiated())
                .create();
        }
        return null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkGenericArrayCreation(PsiElement element, PsiType type) {
        if (type instanceof PsiArrayType arrayType && !JavaGenericsUtil.isReifiableType(arrayType.getComponentType())) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(element)
                .descriptionAndTooltip(JavaErrorLocalize.genericArrayCreation())
                .create();
        }
        return null;
    }

    private static final MethodSignature ourValuesEnumSyntheticMethod =
        MethodSignatureUtil.createMethodSignature("values", PsiType.EMPTY_ARRAY, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);

    public static boolean isEnumSyntheticMethod(MethodSignature methodSignature, Project project) {
        if (methodSignature.equals(ourValuesEnumSyntheticMethod)) {
            return true;
        }
        PsiType javaLangString = PsiType.getJavaLangString(PsiManager.getInstance(project), GlobalSearchScope.allScope(project));
        MethodSignature valueOfMethod = MethodSignatureUtil.createMethodSignature(
            "valueOf",
            new PsiType[]{javaLangString},
            PsiTypeParameter.EMPTY_ARRAY,
            PsiSubstitutor.EMPTY
        );
        return valueOfMethod.equals(methodSignature);
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkTypeParametersList(
        PsiTypeParameterList list,
        PsiTypeParameter[] parameters,
        @Nonnull LanguageLevel level
    ) {
        PsiElement parent = list.getParent();
        if (parent instanceof PsiClass psiClass && psiClass.isEnum()) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(list)
                .descriptionAndTooltip(JavaErrorLocalize.genericsEnumMayNotHaveTypeParameters())
                .create();
        }
        if (PsiUtil.isAnnotationMethod(parent)) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(list)
                .descriptionAndTooltip(JavaErrorLocalize.genericsAnnotationMembersMayNotHaveTypeParameters())
                .create();
        }
        if (parent instanceof PsiClass psiClass && psiClass.isAnnotationType()) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(list)
                .descriptionAndTooltip(JavaErrorLocalize.annotationMayNotHaveTypeParameters())
                .create();
        }

        for (int i = 0; i < parameters.length; i++) {
            PsiTypeParameter typeParameter1 = parameters[i];
            HighlightInfo cyclicInheritance = HighlightClassUtil.checkCyclicInheritance(typeParameter1);
            if (cyclicInheritance != null) {
                return cyclicInheritance;
            }
            String name1 = typeParameter1.getName();
            for (int j = i + 1; j < parameters.length; j++) {
                PsiTypeParameter typeParameter2 = parameters[j];
                String name2 = typeParameter2.getName();
                if (Comparing.strEqual(name1, name2)) {
                    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                        .range(typeParameter2)
                        .descriptionAndTooltip(JavaErrorLocalize.genericsDuplicateTypeParameter(name1))
                        .create();
                }
            }
            if (!level.isAtLeast(LanguageLevel.JDK_1_7)) {
                for (PsiJavaCodeReferenceElement referenceElement : typeParameter1.getExtendsList().getReferenceElements()) {
                    PsiElement resolve = referenceElement.resolve();
                    if (resolve instanceof PsiTypeParameter && ArrayUtil.find(parameters, resolve) > i) {
                        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                            .range(referenceElement.getTextRange())
                            .descriptionAndTooltip(LocalizeValue.localizeTODO("Illegal forward reference"))
                            .create();
                    }
                }
            }
        }
        return null;
    }

    @Nonnull
    @RequiredReadAction
    public static Collection<HighlightInfo> checkCatchParameterIsClass(PsiParameter parameter) {
        if (!(parameter.getDeclarationScope() instanceof PsiCatchSection)) {
            return Collections.emptyList();
        }
        Collection<HighlightInfo> result = new ArrayList<>();

        List<PsiTypeElement> typeElements = PsiUtil.getParameterTypeElements(parameter);
        for (PsiTypeElement typeElement : typeElements) {
            PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(typeElement.getType());
            if (aClass instanceof PsiTypeParameter) {
                result.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(typeElement)
                    .descriptionAndTooltip(JavaErrorLocalize.genericsCannotCatchTypeParameters())
                    .create());
            }
        }

        return result;
    }

    @RequiredReadAction
    public static HighlightInfo checkInstanceOfGenericType(PsiInstanceOfExpression expression) {
        PsiTypeElement checkTypeElement = expression.getCheckType();
        if (checkTypeElement == null) {
            return null;
        }
        return isIllegalForInstanceOf(checkTypeElement.getType(), checkTypeElement);
    }

    /**
     * 15.20.2 Type Comparison Operator instanceof
     * ReferenceType mentioned after the instanceof operator is reifiable
     */
    @RequiredReadAction
    private static HighlightInfo isIllegalForInstanceOf(PsiType type, PsiTypeElement typeElement) {
        PsiClass resolved = PsiUtil.resolveClassInClassTypeOnly(type);
        if (resolved instanceof PsiTypeParameter) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(typeElement)
                .descriptionAndTooltip(JavaErrorLocalize.genericsCannotInstanceofTypeParameters())
                .create();
        }

        if (!JavaGenericsUtil.isReifiableType(type)) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(typeElement)
                .descriptionAndTooltip(JavaErrorLocalize.illegalGenericTypeForInstanceof())
                .create();
        }

        return null;
    }

    @RequiredReadAction
    public static HighlightInfo checkClassObjectAccessExpression(PsiClassObjectAccessExpression expression) {
        PsiType type = expression.getOperand().getType();
        if (type instanceof PsiClassType classType) {
            return canSelectFrom(classType, expression.getOperand());
        }
        if (type instanceof PsiArrayType && type.getDeepComponentType() instanceof PsiClassType arrayComponentType) {
            return canSelectFrom(arrayComponentType, expression.getOperand());
        }

        return null;
    }

    @Nullable
    @RequiredReadAction
    private static HighlightInfo canSelectFrom(PsiClassType type, PsiTypeElement operand) {
        PsiClass aClass = type.resolve();
        if (aClass instanceof PsiTypeParameter) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(operand)
                .descriptionAndTooltip(JavaErrorLocalize.cannotSelectDotClassFromTypeVariable())
                .create();
        }
        if (type.getParameters().length > 0) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(operand)
                .descriptionAndTooltip("Cannot select from parameterized type")
                .create();
        }
        return null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkOverrideAnnotation(
        @Nonnull PsiMethod method,
        @Nonnull PsiAnnotation overrideAnnotation,
        @Nonnull LanguageLevel languageLevel
    ) {
        try {
            MethodSignatureBackedByPsiMethod superMethod = SuperMethodsSearch.search(method, null, true, false).findFirst();
            if (superMethod != null && method.getContainingClass().isInterface()) {
                PsiMethod psiMethod = superMethod.getMethod();
                PsiClass containingClass = psiMethod.getContainingClass();
                if (containingClass != null
                    && JavaClassNames.JAVA_LANG_OBJECT.equals(containingClass.getQualifiedName())
                    && psiMethod.isProtected()) {
                    superMethod = null;
                }
            }
            if (superMethod == null) {
                HighlightInfo highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(overrideAnnotation)
                    .descriptionAndTooltip(JavaErrorLocalize.methodDoesNotOverrideSuper())
                    .create();
                if (highlightInfo != null) {
                    QuickFixFactory.getInstance().registerPullAsAbstractUpFixes(method, QuickFixActionRegistrar.create(highlightInfo));
                }
                return highlightInfo;
            }
            PsiClass superClass = superMethod.getMethod().getContainingClass();
            if (languageLevel.equals(LanguageLevel.JDK_1_5)
                && superClass != null
                && superClass.isInterface()) {
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(overrideAnnotation)
                    .descriptionAndTooltip(JavaErrorBundle.message("override.not.allowed.in.interfaces"))
                    .registerFix(
                        QuickFixFactory.getInstance().createIncreaseLanguageLevelFix(LanguageLevel.JDK_1_6),
                        null,
                        null,
                        null,
                        null
                    )
                    .create();
            }
            return null;
        }
        catch (IndexNotReadyException e) {
            return null;
        }
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkSafeVarargsAnnotation(PsiMethod method, LanguageLevel languageLevel) {
        PsiModifierList list = method.getModifierList();
        PsiAnnotation safeVarargsAnnotation = list.findAnnotation("java.lang.SafeVarargs");
        if (safeVarargsAnnotation == null) {
            return null;
        }
        try {
            if (!method.isVarArgs()) {
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(safeVarargsAnnotation)
                    .descriptionAndTooltip("@SafeVarargs is not allowed on methods with fixed arity")
                    .create();
            }
            if (!isSafeVarargsNoOverridingCondition(method, languageLevel)) {
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(safeVarargsAnnotation)
                    .descriptionAndTooltip("@SafeVarargs is not allowed on non-final instance methods")
                    .create();
            }

            PsiParameter varParameter = method.getParameterList().getParameters()[method.getParameterList().getParametersCount() - 1];

            for (PsiReference reference : ReferencesSearch.search(varParameter)) {
                PsiElement element = reference.getElement();
                if (element instanceof PsiExpression && !PsiUtil.isAccessedForReading((PsiExpression)element)) {
                    return HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING)
                        .range(element)
                        .descriptionAndTooltip("@SafeVarargs do not suppress potentially unsafe operations")
                        .create();
                }
            }


            LOG.assertTrue(varParameter.isVarArgs());
            PsiEllipsisType ellipsisType = (PsiEllipsisType)varParameter.getType();
            PsiType componentType = ellipsisType.getComponentType();
            if (JavaGenericsUtil.isReifiableType(componentType)) {
                return HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING)
                    .range(varParameter.getTypeElement())
                    .descriptionAndTooltip("@SafeVarargs is not applicable for reifiable types")
                    .create();
            }
            return null;
        }
        catch (IndexNotReadyException e) {
            return null;
        }
    }

    public static boolean isSafeVarargsNoOverridingCondition(PsiMethod method, LanguageLevel languageLevel) {
        return method.isFinal()
            || method.isStatic()
            || method.isConstructor()
            || method.isPrivate() && languageLevel.isAtLeast(LanguageLevel.JDK_1_9);
    }

    @RequiredReadAction
    public static void checkEnumConstantForConstructorProblems(
        @Nonnull PsiEnumConstant enumConstant,
        @Nonnull HighlightInfoHolder holder,
        @Nonnull JavaSdkVersion javaSdkVersion
    ) {
        PsiClass containingClass = enumConstant.getContainingClass();
        if (enumConstant.getInitializingClass() == null) {
            HighlightInfo highlightInfo =
                HighlightClassUtil.checkInstantiationOfAbstractClass(containingClass, enumConstant.getNameIdentifier());
            if (highlightInfo != null) {
                QuickFixAction.registerQuickFixAction(highlightInfo, QuickFixFactory.getInstance().createImplementMethodsFix(enumConstant));
                holder.add(highlightInfo);
                return;
            }
            highlightInfo = HighlightClassUtil.checkClassWithAbstractMethods(
                enumConstant.getContainingClass(),
                enumConstant,
                enumConstant.getNameIdentifier().getTextRange()
            );
            if (highlightInfo != null) {
                holder.add(highlightInfo);
                return;
            }
        }
        PsiClassType type = JavaPsiFacade.getInstance(holder.getProject()).getElementFactory()
            .createType(containingClass);

        HighlightMethodUtil.checkConstructorCall(type.resolveGenerics(), enumConstant, type, null, holder, javaSdkVersion);
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkEnumSuperConstructorCall(PsiMethodCallExpression expr) {
        PsiReferenceExpression methodExpression = expr.getMethodExpression();
        PsiElement refNameElement = methodExpression.getReferenceNameElement();
        if (refNameElement != null && PsiKeyword.SUPER.equals(refNameElement.getText())) {
            if (PsiUtil.findEnclosingConstructorOrInitializer(expr) instanceof PsiMethod constructor) {
                PsiClass aClass = constructor.getContainingClass();
                if (aClass != null && aClass.isEnum()) {
                    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                        .range(expr)
                        .descriptionAndTooltip(JavaErrorLocalize.callToSuperIsNotAllowedInEnumConstructor())
                        .create();
                }
            }
        }
        return null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkVarArgParameterIsLast(@Nonnull PsiParameter parameter) {
        if (parameter.getDeclarationScope() instanceof PsiMethod method) {
            PsiParameter[] params = method.getParameterList().getParameters();
            if (params[params.length - 1] != parameter) {
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(parameter)
                    .descriptionAndTooltip(JavaErrorLocalize.varargNotLastParameter())
                    .registerFix(QuickFixFactory.getInstance().createMakeVarargParameterLastFix(parameter), null, null, null, null)
                    .create();
            }
        }
        return null;
    }

    @Nonnull
    @RequiredReadAction
    public static List<HighlightInfo> checkEnumConstantModifierList(PsiModifierList modifierList) {
        List<HighlightInfo> list = null;
        PsiElement[] children = modifierList.getChildren();
        for (PsiElement child : children) {
            if (child instanceof PsiKeyword) {
                if (list == null) {
                    list = new ArrayList<>();
                }
                list.add(
                    HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                        .range(child)
                        .descriptionAndTooltip(JavaErrorLocalize.modifiersForEnumConstants())
                        .create()
                );
            }
        }
        return Lists.notNullize(list);
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkParametersAllowed(PsiReferenceParameterList refParamList) {
        if (refParamList.getParent() instanceof PsiReferenceExpression refExpr
            && !(refExpr.getParent() instanceof PsiMethodCallExpression)
            && !(refExpr instanceof PsiMethodReferenceExpression)) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(refParamList)
                .descriptionAndTooltip(JavaErrorLocalize.genericsReferenceParametersNotAllowed())
                .create();
        }

        return null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkParametersOnRaw(PsiReferenceParameterList refParamList) {
        JavaResolveResult resolveResult = null;
        PsiElement parent = refParamList.getParent();
        PsiElement qualifier = null;
        if (parent instanceof PsiJavaCodeReferenceElement javaCodeRef) {
            resolveResult = javaCodeRef.advancedResolve(false);
            qualifier = javaCodeRef.getQualifier();
        }
        else if (parent instanceof PsiCallExpression call) {
            resolveResult = call.resolveMethodGenerics();
            if (call instanceof PsiMethodCallExpression methodCall) {
                PsiReferenceExpression methodExpression = methodCall.getMethodExpression();
                qualifier = methodExpression.getQualifier();
            }
        }
        if (resolveResult != null) {
            PsiElement element = resolveResult.getElement();
            if (!(element instanceof PsiTypeParameterListOwner typeParamListOwner)) {
                return null;
            }
            if (typeParamListOwner.isStatic()) {
                return null;
            }
            if (qualifier instanceof PsiJavaCodeReferenceElement javaCodeRef && javaCodeRef.resolve() instanceof PsiTypeParameter) {
                return null;
            }
            PsiClass containingClass = ((PsiMember)element).getContainingClass();
            if (containingClass != null && PsiUtil.isRawSubstitutor(containingClass, resolveResult.getSubstitutor())) {
                if ((parent instanceof PsiCallExpression || parent instanceof PsiMethodReferenceExpression)
                    && PsiUtil.isLanguageLevel7OrHigher(parent)) {
                    return null;
                }

                if (element instanceof PsiMethod method) {
                    if (method.findSuperMethods().length > 0) {
                        return null;
                    }
                    if (qualifier instanceof PsiReferenceExpression qualifierRefExpr) {
                        PsiType type = qualifierRefExpr.getType();
                        boolean isJavac7 = JavaVersionService.getInstance().isAtLeast(containingClass, JavaSdkVersion.JDK_1_7);
                        if (type instanceof PsiClassType classType && isJavac7 && classType.isRaw()) {
                            return null;
                        }
                        PsiClass typeParameter = PsiUtil.resolveClassInType(type);
                        if (typeParameter instanceof PsiTypeParameter) {
                            if (isJavac7) {
                                return null;
                            }
                            for (PsiClassType classType : typeParameter.getExtendsListTypes()) {
                                PsiClass resolve = classType.resolve();
                                if (resolve != null) {
                                    PsiMethod[] superMethods = resolve.findMethodsBySignature((PsiMethod)element, true);
                                    for (PsiMethod superMethod : superMethods) {
                                        if (!PsiUtil.isRawSubstitutor(superMethod, resolveResult.getSubstitutor())) {
                                            return null;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                LocalizeValue message = element instanceof PsiClass
                    ? JavaErrorLocalize.genericsTypeArgumentsOnRawType()
                    : JavaErrorLocalize.genericsTypeArgumentsOnRawMethod();

                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(refParamList)
                    .descriptionAndTooltip(message)
                    .create();
            }
        }
        return null;
    }

    @RequiredReadAction
    public static HighlightInfo checkCannotInheritFromEnum(PsiClass superClass, PsiElement elementToHighlight) {
        if (JavaClassNames.JAVA_LANG_ENUM.equals(superClass.getQualifiedName())) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(elementToHighlight)
                .descriptionAndTooltip(JavaErrorLocalize.classesExtendsEnum())
                .create();
        }
        return null;
    }

    @RequiredReadAction
    public static HighlightInfo checkGenericCannotExtendException(PsiReferenceList list) {
        PsiElement parent = list.getParent();
        if (!(parent instanceof PsiClass aClass)) {
            return null;
        }

        if (!aClass.hasTypeParameters() || aClass.getExtendsList() != list) {
            return null;
        }
        PsiJavaCodeReferenceElement[] referenceElements = list.getReferenceElements();
        PsiClass throwableClass = null;
        for (PsiJavaCodeReferenceElement referenceElement : referenceElements) {
            PsiElement resolved = referenceElement.resolve();
            if (!(resolved instanceof PsiClass)) {
                continue;
            }
            if (throwableClass == null) {
                throwableClass = JavaPsiFacade.getInstance(aClass.getProject()).findClass("java.lang.Throwable", aClass.getResolveScope());
            }
            if (InheritanceUtil.isInheritorOrSelf((PsiClass)resolved, throwableClass, true)) {
                HighlightInfo highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(referenceElement)
                    .descriptionAndTooltip(JavaErrorBundle.message("generic.extend.exception"))
                    .create();
                PsiClassType classType = JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory().createType((PsiClass)resolved);
                QuickFixAction.registerQuickFixAction(
                    highlightInfo,
                    QuickFixFactory.getInstance().createExtendsListFix(aClass, classType, false)
                );
                return highlightInfo;
            }
        }
        return null;
    }

    public static HighlightInfo checkEnumMustNotBeLocal(PsiClass aClass) {
        if (!aClass.isEnum()) {
            return null;
        }
        PsiElement parent = aClass.getParent();
        if (!(parent instanceof PsiClass || parent instanceof PsiFile || parent instanceof PsiClassLevelDeclarationStatement)) {
            TextRange textRange = HighlightNamesUtil.getClassDeclarationTextRange(aClass);
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(textRange)
                .descriptionAndTooltip(JavaErrorLocalize.localEnum())
                .create();
        }
        return null;
    }

    public static HighlightInfo checkEnumWithoutConstantsCantHaveAbstractMethods(PsiClass aClass) {
        if (!aClass.isEnum()) {
            return null;
        }
        for (PsiField field : aClass.getFields()) {
            if (field instanceof PsiEnumConstant) {
                return null;
            }
        }
        for (PsiMethod method : aClass.getMethods()) {
            if (method.isAbstract()) {
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(HighlightNamesUtil.getClassDeclarationTextRange(aClass))
                    .descriptionAndTooltip(
                        LocalizeValue.localizeTODO("Enum declaration without enum constants cannot have abstract methods")
                    )
                    .create();
            }
        }
        return null;
    }

    @RequiredReadAction
    public static HighlightInfo checkSelectStaticClassFromParameterizedType(PsiElement resolved, PsiJavaCodeReferenceElement ref) {
        if (resolved instanceof PsiClass psiClass && psiClass.isStatic()
            && ref.getQualifier() instanceof PsiJavaCodeReferenceElement javaCodeRef) {
            PsiReferenceParameterList parameterList = javaCodeRef.getParameterList();
            if (parameterList != null && parameterList.getTypeArguments().length > 0) {
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(parameterList)
                    .descriptionAndTooltip(JavaErrorLocalize.genericsSelectStaticClassFromParameterizedType(
                        HighlightUtil.formatClass(psiClass)
                    ))
                    .create();
            }
        }
        return null;
    }

    @RequiredReadAction
    public static HighlightInfo checkCannotInheritFromTypeParameter(
        PsiClass superClass,
        PsiJavaCodeReferenceElement toHighlight
    ) {
        if (superClass instanceof PsiTypeParameter) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(toHighlight)
                .descriptionAndTooltip(JavaErrorBundle.message("class.cannot.inherit.from.its.type.parameter"))
                .create();
        }
        return null;
    }

    /**
     * http://docs.oracle.com/javase/specs/jls/se7/html/jls-4.html#jls-4.8
     */
    @RequiredReadAction
    public static HighlightInfo checkRawOnParameterizedType(@Nonnull PsiJavaCodeReferenceElement parent, PsiElement resolved) {
        PsiReferenceParameterList list = parent.getParameterList();
        if (list == null || list.getTypeArguments().length > 0) {
            return null;
        }
        if (parent.getQualifier() instanceof PsiJavaCodeReferenceElement javaCodeRef
            && javaCodeRef.getTypeParameters().length > 0
            && resolved instanceof PsiTypeParameterListOwner typeParamListOwner
            && typeParamListOwner.hasTypeParameters()
            && !typeParamListOwner.isStatic()) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(parent)
                .descriptionAndTooltip(LocalizeValue.localizeTODO("Improper formed type; some type parameters are missing"))
                .create();
        }
        return null;
    }

    @RequiredReadAction
    public static HighlightInfo checkCannotPassInner(PsiJavaCodeReferenceElement ref) {
        if (!(ref.getParent() instanceof PsiTypeElement)) {
            return null;
        }
        PsiClass psiClass = PsiTreeUtil.getParentOfType(ref, PsiClass.class);
        if (psiClass == null) {
            return null;
        }
        if (PsiTreeUtil.isAncestor(psiClass.getExtendsList(), ref, false)
            || PsiTreeUtil.isAncestor(psiClass.getImplementsList(), ref, false)) {
            PsiElement qualifier = ref.getQualifier();
            if (qualifier instanceof PsiJavaCodeReferenceElement javaCodeRef && javaCodeRef.resolve() == psiClass) {
                PsiJavaCodeReferenceElement referenceElement = PsiTreeUtil.getParentOfType(ref, PsiJavaCodeReferenceElement.class);
                if (referenceElement == null) {
                    return null;
                }
                if (!(referenceElement.resolve() instanceof PsiClass typeClass)) {
                    return null;
                }
                PsiClass resolvedClass = (PsiClass)ref.resolve();
                PsiClass containingClass = resolvedClass != null ? resolvedClass.getContainingClass() : null;
                if (containingClass == null) {
                    return null;
                }
                if (psiClass.isInheritor(containingClass, true)
                    || unqualifiedNestedClassReferenceAccessedViaContainingClassInheritance(typeClass, resolvedClass.getExtendsList())
                    || unqualifiedNestedClassReferenceAccessedViaContainingClassInheritance(typeClass, resolvedClass.getImplementsList())) {
                    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                        .descriptionAndTooltip(LocalizeValue.localizeTODO(resolvedClass.getName() + " is not accessible in current context"))
                        .range(ref)
                        .create();
                }
            }
        }
        return null;
    }

    @RequiredReadAction
    private static boolean unqualifiedNestedClassReferenceAccessedViaContainingClassInheritance(
        PsiClass containingClass,
        PsiReferenceList referenceList
    ) {
        if (referenceList != null) {
            for (PsiJavaCodeReferenceElement referenceElement : referenceList.getReferenceElements()) {
                if (!referenceElement.isQualified() && referenceElement.resolve() instanceof PsiClass superClass) {
                    PsiClass superContainingClass = superClass.getContainingClass();
                    if (superContainingClass != null
                        && InheritanceUtil.isInheritorOrSelf(containingClass, superContainingClass, true)
                        && !PsiTreeUtil.isAncestor(superContainingClass, containingClass, true)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @RequiredReadAction
    private static void registerVariableParameterizedTypeFixes(
        @Nullable HighlightInfo highlightInfo,
        @Nonnull PsiVariable variable,
        @Nonnull PsiReferenceParameterList parameterList,
        @Nonnull JavaSdkVersion version
    ) {
        PsiType type = variable.getType();
        if (!(type instanceof PsiClassType) || highlightInfo == null) {
            return;
        }

        if (DumbService.getInstance(variable.getProject()).isDumb()) {
            return;
        }

        String shortName = ((PsiClassType)type).getClassName();
        PsiManager manager = parameterList.getManager();
        JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());
        PsiShortNamesCache shortNamesCache = PsiShortNamesCache.getInstance(parameterList.getProject());
        PsiClass[] classes = shortNamesCache.getClassesByName(shortName, GlobalSearchScope.allScope(manager.getProject()));
        PsiElementFactory factory = facade.getElementFactory();
        for (PsiClass aClass : classes) {
            if (checkReferenceTypeArgumentList(aClass, parameterList, PsiSubstitutor.EMPTY, false, version) == null) {
                PsiType[] actualTypeParameters = parameterList.getTypeArguments();
                PsiTypeParameter[] classTypeParameters = aClass.getTypeParameters();
                Map<PsiTypeParameter, PsiType> map = new HashMap<>();
                for (int j = 0; j < classTypeParameters.length; j++) {
                    PsiTypeParameter classTypeParameter = classTypeParameters[j];
                    PsiType actualTypeParameter = actualTypeParameters[j];
                    map.put(classTypeParameter, actualTypeParameter);
                }
                PsiSubstitutor substitutor = factory.createSubstitutor(map);
                PsiType suggestedType = factory.createType(aClass, substitutor);
                HighlightUtil.registerChangeVariableTypeFixes(variable, suggestedType, variable.getInitializer(), highlightInfo);
            }
        }
    }

    @RequiredReadAction
    public static HighlightInfo checkInferredIntersections(PsiSubstitutor substitutor, TextRange ref) {
        for (Map.Entry<PsiTypeParameter, PsiType> typeEntry : substitutor.getSubstitutionMap().entrySet()) {
            String parameterName = typeEntry.getKey().getName();
            PsiType type = typeEntry.getValue();
            if (type instanceof PsiIntersectionType) {
                String conflictingConjunctsMessage = ((PsiIntersectionType)type).getConflictingConjunctsMessage();
                if (conflictingConjunctsMessage != null) {
                    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                        .descriptionAndTooltip(LocalizeValue.localizeTODO(
                            "Type parameter " + parameterName + " has incompatible upper bounds: " + conflictingConjunctsMessage
                        ))
                        .range(ref)
                        .create();
                }
            }
        }
        return null;
    }

    public static HighlightInfo areSupersAccessible(@Nonnull PsiClass aClass) {
        return areSupersAccessible(aClass, aClass.getResolveScope(), HighlightNamesUtil.getClassDeclarationTextRange(aClass), true);
    }

    @RequiredReadAction
    public static HighlightInfo areSupersAccessible(@Nonnull PsiClass aClass, PsiReferenceExpression ref) {
        GlobalSearchScope resolveScope = ref.getResolveScope();
        HighlightInfo info = areSupersAccessible(aClass, resolveScope, ref.getTextRange(), false);
        if (info != null) {
            return info;
        }

        String message = null;
        PsiElement parent = ref.getParent();
        if (parent instanceof PsiMethodCallExpression) {
            JavaResolveResult resolveResult = ((PsiMethodCallExpression)parent).resolveMethodGenerics();
            PsiMethod method = (PsiMethod)resolveResult.getElement();
            if (method != null) {
                HashSet<PsiClass> classes = new HashSet<>();
                JavaPsiFacade facade = JavaPsiFacade.getInstance(aClass.getProject());
                PsiSubstitutor substitutor = resolveResult.getSubstitutor();

                message = isSuperTypeAccessible(substitutor.substitute(method.getReturnType()), classes, false, resolveScope, facade);
                if (message == null) {
                    for (PsiType type : method.getSignature(substitutor).getParameterTypes()) {

                        message = isSuperTypeAccessible(type, classes, false, resolveScope, facade);
                        if (message != null) {
                            break;
                        }
                    }
                }
            }
        }
        else if (ref.resolve() instanceof PsiField field) {
            message = isSuperTypeAccessible(
                field.getType(),
                new HashSet<>(),
                false,
                resolveScope,
                JavaPsiFacade.getInstance(aClass.getProject())
            );
        }

        if (message != null) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .descriptionAndTooltip(message)
                .range(ref.getTextRange())
                .create();
        }

        return null;
    }

    private static HighlightInfo areSupersAccessible(
        @Nonnull PsiClass aClass,
        GlobalSearchScope resolveScope,
        TextRange range,
        boolean checkParameters
    ) {
        JavaPsiFacade factory = JavaPsiFacade.getInstance(aClass.getProject());
        for (PsiClassType superType : aClass.getSuperTypes()) {
            String notAccessibleErrorMessage = isSuperTypeAccessible(superType, new HashSet<>(), checkParameters, resolveScope, factory);
            if (notAccessibleErrorMessage != null) {
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .descriptionAndTooltip(notAccessibleErrorMessage)
                    .range(range)
                    .create();
            }
        }
        return null;
    }

    @Nullable
    private static String isSuperTypeAccessible(
        PsiType superType,
        HashSet<PsiClass> classes,
        boolean checkParameters,
        GlobalSearchScope resolveScope,
        JavaPsiFacade factory
    ) {
        PsiClass aClass = PsiUtil.resolveClassInType(superType);
        if (aClass != null && classes.add(aClass)) {
            VirtualFile vFile = PsiUtilCore.getVirtualFile(aClass);
            if (vFile == null) {
                return null;
            }
            FileIndexFacade index = FileIndexFacade.getInstance(aClass.getProject());
            if (!index.isInSource(vFile) && !index.isInLibraryClasses(vFile)) {
                return null;
            }

            String qualifiedName = aClass.getQualifiedName();
            if (qualifiedName != null && factory.findClass(qualifiedName, resolveScope) == null) {
                return "Cannot access " + HighlightUtil.formatClass(aClass);
            }

            if (checkParameters) {
                boolean isInLibrary = !index.isInContent(vFile);
                if (superType instanceof PsiClassType) {
                    for (PsiType psiType : ((PsiClassType)superType).getParameters()) {
                        String notAccessibleMessage = isSuperTypeAccessible(psiType, classes, true, resolveScope, factory);
                        if (notAccessibleMessage != null) {
                            return notAccessibleMessage;
                        }
                    }
                }

                for (PsiClassType type : aClass.getSuperTypes()) {
                    String notAccessibleMessage = isSuperTypeAccessible(type, classes, !isInLibrary, resolveScope, factory);
                    if (notAccessibleMessage != null) {
                        return notAccessibleMessage;
                    }
                }
            }
        }
        return null;
    }

    @RequiredReadAction
    public static HighlightInfo checkTypeParameterOverrideEquivalentMethods(PsiClass aClass, LanguageLevel level) {
        if (aClass instanceof PsiTypeParameter && level.isAtLeast(LanguageLevel.JDK_1_7)) {
            PsiReferenceList extendsList = aClass.getExtendsList();
            if (extendsList != null && extendsList.getReferenceElements().length > 1) {
                //todo suppress erased methods which come from the same class
                Collection<HighlightInfo> result = checkOverrideEquivalentMethods(aClass);
                if (result != null && !result.isEmpty()) {
                    return result.iterator().next();
                }
            }
        }
        return null;
    }
}
