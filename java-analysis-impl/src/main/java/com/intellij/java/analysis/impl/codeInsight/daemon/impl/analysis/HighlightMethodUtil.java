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
package com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis;

import com.intellij.java.analysis.codeInsight.intention.QuickFixFactory;
import com.intellij.java.analysis.impl.codeInsight.daemon.impl.quickfix.*;
import com.intellij.java.analysis.impl.psi.util.PsiMatchers;
import com.intellij.java.language.JavaFeature;
import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.java.language.impl.codeInsight.ExceptionUtil;
import com.intellij.java.language.impl.codeInsight.daemon.JavaErrorBundle;
import com.intellij.java.language.impl.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.java.language.impl.refactoring.util.RefactoringChangeUtil;
import com.intellij.java.language.projectRoots.JavaSdkVersion;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.infos.CandidateInfo;
import com.intellij.java.language.psi.infos.MethodCandidateInfo;
import com.intellij.java.language.psi.util.*;
import com.intellij.java.language.util.VisibilityUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.dumb.IndexNotReadyException;
import consulo.document.util.TextRange;
import consulo.document.util.TextRangeUtil;
import consulo.java.language.impl.localize.JavaErrorLocalize;
import consulo.java.language.module.util.JavaClassNames;
import consulo.language.editor.inspection.LocalQuickFixOnPsiElementAsIntentionAdapter;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.editor.intention.QuickFixAction;
import consulo.language.editor.intention.QuickFixActionRegistrar;
import consulo.language.editor.intention.UnresolvedReferenceQuickFixProvider;
import consulo.language.editor.localize.DaemonLocalize;
import consulo.language.editor.rawHighlight.HighlightInfo;
import consulo.language.editor.rawHighlight.HighlightInfoHolder;
import consulo.language.editor.rawHighlight.HighlightInfoType;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiMatcherImpl;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.ui.ex.JBColor;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.awt.util.ColorUtil;
import consulo.util.collection.MostlySingularMultiMap;
import consulo.util.lang.Comparing;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.SimpleReference;
import consulo.util.lang.xml.XmlStringUtil;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.intellij.lang.annotations.Language;

import java.text.MessageFormat;
import java.util.*;

/**
 * Highlight method problems
 *
 * @author cdr
 * @since 2002-08-14
 */
public class HighlightMethodUtil {
    private static final Logger LOG = Logger.getInstance(HighlightMethodUtil.class);

    private HighlightMethodUtil() {
    }

    public static String createClashMethodMessage(PsiMethod method1, PsiMethod method2, boolean showContainingClasses) {
        String m1 = JavaHighlightUtil.formatMethod(method1);
        String m2 = JavaHighlightUtil.formatMethod(method2);
        return showContainingClasses
            ? JavaErrorLocalize.clashMethodsMessageShowClasses(
                m1,
                m2,
                HighlightUtil.formatClass(method1.getContainingClass()),
                HighlightUtil.formatClass(method2.getContainingClass())
            ).get()
            : JavaErrorLocalize.clashMethodsMessage(m1, m2).get();
    }

    @RequiredReadAction
    public static HighlightInfo checkMethodWeakerPrivileges(
        @Nonnull MethodSignatureBackedByPsiMethod methodSignature,
        @Nonnull List<HierarchicalMethodSignature> superMethodSignatures,
        boolean includeRealPositionInfo,
        @Nonnull PsiFile containingFile
    ) {
        PsiMethod method = methodSignature.getMethod();
        PsiModifierList modifierList = method.getModifierList();
        if (modifierList.hasModifierProperty(PsiModifier.PUBLIC)) {
            return null;
        }
        int accessLevel = PsiUtil.getAccessLevel(modifierList);
        String accessModifier = PsiUtil.getAccessModifier(accessLevel);
        for (MethodSignatureBackedByPsiMethod superMethodSignature : superMethodSignatures) {
            PsiMethod superMethod = superMethodSignature.getMethod();
            if (method.isAbstract() && !MethodSignatureUtil.isSuperMethod(superMethod, method)) {
                continue;
            }
            if (!PsiUtil.isAccessible(containingFile.getProject(), superMethod, method, null)) {
                continue;
            }
            if (!includeRealPositionInfo && MethodSignatureUtil.isSuperMethod(superMethod, method)) {
                continue;
            }
            HighlightInfo info = isWeaker(method, modifierList, accessModifier, accessLevel, superMethod, includeRealPositionInfo);
            if (info != null) {
                return info;
            }
        }
        return null;
    }

    @RequiredReadAction
    private static HighlightInfo isWeaker(
        PsiMethod method,
        PsiModifierList modifierList,
        String accessModifier,
        int accessLevel,
        PsiMethod superMethod,
        boolean includeRealPositionInfo
    ) {
        int superAccessLevel = PsiUtil.getAccessLevel(superMethod.getModifierList());
        if (accessLevel < superAccessLevel) {
            TextRange textRange;
            if (includeRealPositionInfo) {
                PsiElement keyword = PsiUtil.findModifierInList(modifierList, accessModifier);
                if (keyword == null) {
                    // in case of package-private or some crazy third-party plugin where some access modifier implied even if it's absent
                    textRange = method.getNameIdentifier().getTextRange();
                }
                else {
                    textRange = keyword.getTextRange();
                }
            }
            else {
                textRange = TextRange.EMPTY_RANGE;
            }
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(textRange)
                .descriptionAndTooltip(JavaErrorLocalize.weakerPrivileges(
                    createClashMethodMessage(method, superMethod, true),
                    VisibilityUtil.toPresentableText(accessModifier),
                    PsiUtil.getAccessModifier(superAccessLevel)
                ))
                .registerFix(QuickFixFactory.getInstance().createAddModifierFix(method, PsiUtil.getAccessModifier(superAccessLevel)))
                .create();
        }
        return null;
    }

    @RequiredReadAction
    public static HighlightInfo checkMethodIncompatibleReturnType(
        @Nonnull MethodSignatureBackedByPsiMethod methodSignature,
        @Nonnull List<HierarchicalMethodSignature> superMethodSignatures,
        boolean includeRealPositionInfo
    ) {
        return checkMethodIncompatibleReturnType(methodSignature, superMethodSignatures, includeRealPositionInfo, null);
    }

    @RequiredReadAction
    public static HighlightInfo checkMethodIncompatibleReturnType(
        @Nonnull MethodSignatureBackedByPsiMethod methodSignature,
        @Nonnull List<HierarchicalMethodSignature> superMethodSignatures,
        boolean includeRealPositionInfo,
        @Nullable TextRange textRange
    ) {
        PsiMethod method = methodSignature.getMethod();
        PsiType returnType = methodSignature.getSubstitutor().substitute(method.getReturnType());
        PsiClass aClass = method.getContainingClass();
        if (aClass == null) {
            return null;
        }
        for (MethodSignatureBackedByPsiMethod superMethodSignature : superMethodSignatures) {
            PsiMethod superMethod = superMethodSignature.getMethod();
            PsiType declaredReturnType = superMethod.getReturnType();
            PsiType superReturnType = declaredReturnType;
            if (superMethodSignature.isRaw()) {
                superReturnType = TypeConversionUtil.erasure(declaredReturnType);
            }
            if (returnType == null || superReturnType == null || method == superMethod) {
                continue;
            }
            PsiClass superClass = superMethod.getContainingClass();
            if (superClass == null) {
                continue;
            }
            TextRange toHighlight = textRange != null ? textRange
                : includeRealPositionInfo ? method.getReturnTypeElement().getTextRange() : TextRange.EMPTY_RANGE;
            HighlightInfo highlightInfo = checkSuperMethodSignature(
                superMethod,
                superMethodSignature,
                superReturnType,
                method,
                methodSignature,
                returnType,
                JavaErrorLocalize.incompatibleReturnType(),
                toHighlight,
                PsiUtil.getLanguageLevel(aClass)
            );
            if (highlightInfo != null) {
                return highlightInfo;
            }
        }

        return null;
    }

    private static HighlightInfo checkSuperMethodSignature(
        @Nonnull PsiMethod superMethod,
        @Nonnull MethodSignatureBackedByPsiMethod superMethodSignature,
        PsiType superReturnType,
        @Nonnull PsiMethod method,
        @Nonnull MethodSignatureBackedByPsiMethod methodSignature,
        @Nonnull PsiType returnType,
        @Nonnull LocalizeValue detailMessage,
        @Nonnull TextRange range,
        @Nonnull LanguageLevel languageLevel
    ) {
        if (superReturnType == null) {
            return null;
        }
        PsiClass superContainingClass = superMethod.getContainingClass();
        if (superContainingClass != null
            && JavaClassNames.JAVA_LANG_OBJECT.equals(superContainingClass.getQualifiedName())
            && !superMethod.isPublic()) {
            PsiClass containingClass = method.getContainingClass();
            if (containingClass != null && containingClass.isInterface() && !superContainingClass.isInterface()) {
                return null;
            }
        }

        PsiType substitutedSuperReturnType;
        boolean isJdk15 = languageLevel.isAtLeast(LanguageLevel.JDK_1_5);
        if (isJdk15 && !superMethodSignature.isRaw() && superMethodSignature.equals(methodSignature)) { //see 8.4.5
            PsiSubstitutor unifyingSubstitutor =
                MethodSignatureUtil.getSuperMethodSignatureSubstitutor(methodSignature, superMethodSignature);
            substitutedSuperReturnType = unifyingSubstitutor == null ? superReturnType : unifyingSubstitutor.substitute(superReturnType);
        }
        else {
            substitutedSuperReturnType = TypeConversionUtil.erasure(superMethodSignature.getSubstitutor().substitute(superReturnType));
        }

        if (returnType.equals(substitutedSuperReturnType)) {
            return null;
        }
        if (!(returnType instanceof PsiPrimitiveType) && substitutedSuperReturnType.getDeepComponentType() instanceof PsiClassType
            && isJdk15 && TypeConversionUtil.isAssignable(substitutedSuperReturnType, returnType)) {
            return null;
        }

        return createIncompatibleReturnTypeMessage(method, superMethod, substitutedSuperReturnType, returnType, detailMessage, range);
    }

    private static HighlightInfo createIncompatibleReturnTypeMessage(
        @Nonnull PsiMethod method,
        @Nonnull PsiMethod superMethod,
        @Nonnull PsiType substitutedSuperReturnType,
        @Nonnull PsiType returnType,
        @Nonnull LocalizeValue detailMessage,
        @Nonnull TextRange textRange
    ) {
        String description = MessageFormat.format("{0}; {1}", createClashMethodMessage(method, superMethod, true), detailMessage);
        QuickFixFactory factory = QuickFixFactory.getInstance();
        HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .range(textRange)
            .descriptionAndTooltip(description)
            .registerFix(factory.createMethodReturnFix(method, substitutedSuperReturnType, false))
            .registerFix(factory.createSuperMethodReturnFix(superMethod, returnType));
        PsiClass returnClass = PsiUtil.resolveClassInClassTypeOnly(returnType);
        if (returnClass != null && substitutedSuperReturnType instanceof PsiClassType classType) {
            builder.registerFix(factory.createChangeParameterClassFix(returnClass, classType));
        }

        return builder.create();
    }

    @RequiredReadAction
    public static HighlightInfo checkMethodOverridesFinal(
        MethodSignatureBackedByPsiMethod methodSignature,
        List<HierarchicalMethodSignature> superMethodSignatures
    ) {
        PsiMethod method = methodSignature.getMethod();
        for (MethodSignatureBackedByPsiMethod superMethodSignature : superMethodSignatures) {
            PsiMethod superMethod = superMethodSignature.getMethod();
            HighlightInfo info = checkSuperMethodIsFinal(method, superMethod);
            if (info != null) {
                return info;
            }
        }
        return null;
    }

    @RequiredReadAction
    private static HighlightInfo checkSuperMethodIsFinal(PsiMethod method, PsiMethod superMethod) {
        // strange things happen when super method is from Object and method from interface
        if (superMethod.isFinal()) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(HighlightNamesUtil.getMethodDeclarationTextRange(method))
                .descriptionAndTooltip(JavaErrorLocalize.finalMethodOverride(
                    JavaHighlightUtil.formatMethod(method),
                    JavaHighlightUtil.formatMethod(superMethod),
                    HighlightUtil.formatClass(superMethod.getContainingClass())
                ))
                .registerFix(QuickFixFactory.getInstance().createModifierListFix(
                    superMethod,
                    PsiModifier.FINAL,
                    false,
                    true
                ))
                .create();
        }
        return null;
    }

    @RequiredReadAction
    public static HighlightInfo checkMethodIncompatibleThrows(
        MethodSignatureBackedByPsiMethod methodSignature,
        List<HierarchicalMethodSignature> superMethodSignatures,
        boolean includeRealPositionInfo,
        PsiClass analyzedClass
    ) {
        PsiMethod method = methodSignature.getMethod();
        PsiClass aClass = method.getContainingClass();
        if (aClass == null) {
            return null;
        }
        PsiSubstitutor superSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(aClass, analyzedClass, PsiSubstitutor.EMPTY);
        PsiClassType[] exceptions = method.getThrowsList().getReferencedTypes();
        PsiJavaCodeReferenceElement[] referenceElements;
        List<PsiElement> exceptionContexts;
        if (includeRealPositionInfo) {
            exceptionContexts = new ArrayList<>();
            referenceElements = method.getThrowsList().getReferenceElements();
        }
        else {
            exceptionContexts = null;
            referenceElements = null;
        }
        List<PsiClassType> checkedExceptions = new ArrayList<>();
        for (int i = 0; i < exceptions.length; i++) {
            PsiClassType exception = exceptions[i];
            if (exception == null) {
                LOG.error("throws: " + method.getThrowsList().getText() + "; method: " + method);
            }
            if (!ExceptionUtil.isUncheckedException(exception)) {
                checkedExceptions.add(exception);
                if (includeRealPositionInfo && i < referenceElements.length) {
                    PsiJavaCodeReferenceElement exceptionRef = referenceElements[i];
                    exceptionContexts.add(exceptionRef);
                }
            }
        }
        for (MethodSignatureBackedByPsiMethod superMethodSignature : superMethodSignatures) {
            PsiMethod superMethod = superMethodSignature.getMethod();
            int index = getExtraExceptionNum(methodSignature, superMethodSignature, checkedExceptions, superSubstitutor);
            if (index != -1) {
                if (aClass.isInterface()) {
                    PsiClass superContainingClass = superMethod.getContainingClass();
                    if (superContainingClass != null && !superContainingClass.isInterface()) {
                        continue;
                    }
                    if (superContainingClass != null && !aClass.isInheritor(superContainingClass, true)) {
                        continue;
                    }
                }
                PsiClassType exception = checkedExceptions.get(index);
                String description = JavaErrorLocalize.overriddenMethodDoesNotThrow(
                    createClashMethodMessage(method, superMethod, true),
                    JavaHighlightUtil.formatType(exception)
                ).get();
                TextRange textRange;
                if (includeRealPositionInfo) {
                    PsiElement exceptionContext = exceptionContexts.get(index);
                    textRange = exceptionContext.getTextRange();
                }
                else {
                    textRange = TextRange.EMPTY_RANGE;
                }
                QuickFixFactory factory = QuickFixFactory.getInstance();
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(textRange)
                    .descriptionAndTooltip(description)
                    .registerFix(new LocalQuickFixOnPsiElementAsIntentionAdapter(factory.createMethodThrowsFix(
                        method,
                        exception,
                        false,
                        false
                    )))
                    .registerFix(new LocalQuickFixOnPsiElementAsIntentionAdapter(factory.createMethodThrowsFix(
                        superMethod,
                        exception,
                        true,
                        true
                    )))
                    .create();
            }
        }
        return null;
    }

    // return number of exception  which was not declared in super method or -1
    private static int getExtraExceptionNum(
        MethodSignature methodSignature,
        MethodSignatureBackedByPsiMethod superSignature,
        List<PsiClassType> checkedExceptions,
        PsiSubstitutor substitutorForDerivedClass
    ) {
        PsiMethod superMethod = superSignature.getMethod();
        PsiSubstitutor substitutorForMethod = MethodSignatureUtil.getSuperMethodSignatureSubstitutor(methodSignature, superSignature);
        for (int i = 0; i < checkedExceptions.size(); i++) {
            PsiClassType checkedEx = checkedExceptions.get(i);
            PsiType substituted =
                substitutorForMethod != null ? substitutorForMethod.substitute(checkedEx) : TypeConversionUtil.erasure(checkedEx);
            PsiType exception = substitutorForDerivedClass.substitute(substituted);
            if (!isMethodThrows(superMethod, substitutorForMethod, exception, substitutorForDerivedClass)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isMethodThrows(
        PsiMethod method,
        @Nullable PsiSubstitutor substitutorForMethod,
        PsiType exception,
        PsiSubstitutor substitutorForDerivedClass
    ) {
        PsiClassType[] thrownExceptions = method.getThrowsList().getReferencedTypes();
        for (PsiClassType thrownException1 : thrownExceptions) {
            PsiType thrownException = substitutorForMethod != null
                ? substitutorForMethod.substitute(thrownException1)
                : TypeConversionUtil.erasure(thrownException1);
            thrownException = substitutorForDerivedClass.substitute(thrownException);
            if (TypeConversionUtil.isAssignable(thrownException, exception)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkMethodCall(
        @Nonnull PsiMethodCallExpression methodCall,
        @Nonnull PsiResolveHelper resolveHelper,
        @Nonnull LanguageLevel languageLevel,
        @Nonnull JavaSdkVersion javaSdkVersion,
        @Nonnull PsiFile file
    ) {
        PsiExpressionList list = methodCall.getArgumentList();
        PsiReferenceExpression referenceToMethod = methodCall.getMethodExpression();
        JavaResolveResult[] results = referenceToMethod.multiResolve(true);
        JavaResolveResult resolveResult = results.length == 1 ? results[0] : JavaResolveResult.EMPTY;
        PsiElement resolved = resolveResult.getElement();

        boolean isDummy = isDummyConstructorCall(methodCall, resolveHelper, list, referenceToMethod);
        if (isDummy) {
            return null;
        }
        HighlightInfo highlightInfo;

        PsiSubstitutor substitutor = resolveResult.getSubstitutor();
        if (resolved instanceof PsiMethod method && resolveResult.isValidResult()) {
            TextRange fixRange = getFixRange(methodCall);
            highlightInfo = HighlightUtil.checkUnhandledExceptions(methodCall, fixRange);

            if (highlightInfo == null && method.isStatic()) {
                PsiClass containingClass = method.getContainingClass();
                if (containingClass != null && containingClass.isInterface()) {
                    PsiReferenceExpression methodRef = methodCall.getMethodExpression();
                    PsiElement element = ObjectUtil.notNull(methodRef.getReferenceNameElement(), methodRef);
                    highlightInfo = HighlightUtil.checkFeature(element, JavaFeature.STATIC_INTERFACE_CALLS, languageLevel, file);
                    if (highlightInfo == null) {
                        LocalizeValue message =
                            checkStaticInterfaceMethodCallQualifier(methodRef, resolveResult.getCurrentFileResolveScope(), containingClass);
                        if (message != null) {
                            highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                                .descriptionAndTooltip(message)
                                .range(fixRange)
                                .create();
                        }
                    }
                }
            }

            if (highlightInfo == null) {
                highlightInfo = GenericsHighlightUtil.checkInferredIntersections(substitutor, fixRange);
            }

            if (highlightInfo == null) {
                highlightInfo = checkVarargParameterErasureToBeAccessible((MethodCandidateInfo)resolveResult, methodCall);
            }

            if (highlightInfo == null) {
                String errorMessage = ((MethodCandidateInfo)resolveResult).getInferenceErrorMessage();
                if (errorMessage != null) {
                    highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                        .descriptionAndTooltip(errorMessage)
                        .range(fixRange)
                        .create();
                    if (highlightInfo != null) {
                        registerMethodCallIntentions(highlightInfo, methodCall, list, resolveHelper);
                        registerMethodReturnFixAction(highlightInfo, (MethodCandidateInfo)resolveResult, methodCall);
                        registerTargetTypeFixesBasedOnApplicabilityInference(
                            methodCall,
                            (MethodCandidateInfo)resolveResult,
                            (PsiMethod)resolved,
                            highlightInfo
                        );
                    }
                }
            }
        }
        else {
            PsiMethod resolvedMethod = null;
            MethodCandidateInfo candidateInfo = null;
            if (resolveResult instanceof MethodCandidateInfo mci) {
                candidateInfo = mci;
                resolvedMethod = candidateInfo.getElement();
            }

            if (!resolveResult.isAccessible() || !resolveResult.isStaticsScopeCorrect()) {
                highlightInfo = null;
            }
            else if (candidateInfo != null && !candidateInfo.isApplicable()) {
                if (candidateInfo.isTypeArgumentsApplicable()) {
                    LocalizeValue methodName = HighlightMessageUtil.getSymbolName(resolved, substitutor);
                    PsiElement parent = resolved.getParent();
                    LocalizeValue containerName =
                        parent == null ? LocalizeValue.empty() : HighlightMessageUtil.getSymbolName(parent, substitutor);
                    String argTypes = buildArgTypesList(list);
                    String description = JavaErrorLocalize.wrongMethodArguments(methodName, containerName, argTypes).get();
                    SimpleReference<PsiElement> elementToHighlight = SimpleReference.create(list);
                    String toolTip;
                    if (parent instanceof PsiClass) {
                        toolTip = buildOneLineMismatchDescription(list, candidateInfo, elementToHighlight);
                        if (toolTip == null) {
                            toolTip = createMismatchedArgumentsHtmlTooltip(candidateInfo, list);
                        }
                    }
                    else {
                        toolTip = description;
                    }
                    PsiElement element = elementToHighlight.get();
                    int navigationShift =
                        element instanceof PsiExpressionList ? +1 : 0; // argument list starts with paren which there is no need to highlight
                    highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                        .range(element)
                        .description(description)
                        .escapedToolTip(toolTip)
                        .navigationShift(navigationShift)
                        .create();
                    if (highlightInfo != null) {
                        registerMethodCallIntentions(highlightInfo, methodCall, list, resolveHelper);
                        registerMethodReturnFixAction(highlightInfo, candidateInfo, methodCall);
                        registerTargetTypeFixesBasedOnApplicabilityInference(methodCall, candidateInfo, resolvedMethod, highlightInfo);
                    }
                }
                else {
                    PsiReferenceExpression methodExpression = methodCall.getMethodExpression();
                    PsiReferenceParameterList typeArgumentList = methodCall.getTypeArgumentList();
                    PsiSubstitutor applicabilitySubstitutor = candidateInfo.getSubstitutor(false);
                    if (typeArgumentList.getTypeArguments().length == 0 && resolvedMethod.hasTypeParameters()) {
                        highlightInfo =
                            GenericsHighlightUtil.checkInferredTypeArguments(resolvedMethod, methodCall, applicabilitySubstitutor);
                    }
                    else {
                        highlightInfo = GenericsHighlightUtil.checkParameterizedReferenceTypeArguments(
                            resolved,
                            methodExpression,
                            applicabilitySubstitutor,
                            javaSdkVersion
                        );
                    }
                }
            }
            else {
                highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(methodCall)
                    .descriptionAndTooltip(JavaErrorLocalize.methodCallExpected())
                    .create();
                if (resolved instanceof PsiClass psiClass) {
                    QuickFixAction.registerQuickFixAction(
                        highlightInfo,
                        QuickFixFactory.getInstance().createInsertNewFix(methodCall, psiClass)
                    );
                }
                else {
                    TextRange range = getFixRange(methodCall);
                    QuickFixAction.registerQuickFixAction(
                        highlightInfo,
                        range,
                        QuickFixFactory.getInstance().createCreateMethodFromUsageFix(methodCall)
                    );
                    QuickFixAction.registerQuickFixAction(
                        highlightInfo,
                        range,
                        QuickFixFactory.getInstance().createCreateAbstractMethodFromUsageFix(methodCall)
                    );
                    QuickFixAction.registerQuickFixAction(
                        highlightInfo,
                        range,
                        QuickFixFactory.getInstance().createCreatePropertyFromUsageFix(methodCall)
                    );
                    QuickFixAction.registerQuickFixAction(
                        highlightInfo,
                        range,
                        QuickFixFactory.getInstance().createStaticImportMethodFix(methodCall)
                    );
                    if (resolved instanceof PsiVariable variable && languageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
                        PsiMethod method = LambdaUtil.getFunctionalInterfaceMethod(variable.getType());
                        if (method != null) {
                            QuickFixAction.registerQuickFixAction(
                                highlightInfo,
                                range,
                                QuickFixFactory.getInstance().createInsertMethodCallFix(methodCall, method)
                            );
                        }
                    }
                }
            }
        }
        if (highlightInfo == null) {
            highlightInfo =
                GenericsHighlightUtil.checkParameterizedReferenceTypeArguments(resolved, referenceToMethod, substitutor, javaSdkVersion);
        }
        return highlightInfo;
    }

    @RequiredReadAction
    private static void registerTargetTypeFixesBasedOnApplicabilityInference(
        @Nonnull PsiMethodCallExpression methodCall,
        MethodCandidateInfo resolveResult,
        PsiMethod resolved,
        HighlightInfo highlightInfo
    ) {
        PsiElement parent = PsiUtil.skipParenthesizedExprUp(methodCall.getParent());
        PsiVariable variable = null;
        if (parent instanceof PsiVariable var) {
            variable = var;
        }
        else if (parent instanceof PsiAssignmentExpression assignment
            && assignment.getLExpression() instanceof PsiReferenceExpression lRefExpr
            && lRefExpr.resolve() instanceof PsiVariable lVar) {
            variable = lVar;
        }

        if (variable != null) {
            PsiType rType = methodCall.getType();
            if (rType != null && !variable.getType().isAssignableFrom(rType)) {
                PsiType expectedTypeByApplicabilityConstraints = resolveResult.getSubstitutor(false).substitute(resolved.getReturnType());
                if (expectedTypeByApplicabilityConstraints != null && !expectedTypeByApplicabilityConstraints.equals(rType)) {
                    HighlightUtil.registerChangeVariableTypeFixes(
                        variable,
                        expectedTypeByApplicabilityConstraints,
                        methodCall,
                        highlightInfo
                    );
                }
            }
        }
    }

    /* see also PsiReferenceExpressionImpl.hasValidQualifier() */
    @Nullable
    @RequiredReadAction
    private static LocalizeValue checkStaticInterfaceMethodCallQualifier(
        PsiReferenceExpression ref,
        PsiElement scope,
        PsiClass containingClass
    ) {
        PsiExpression qualifierExpression = ref.getQualifierExpression();
        if (qualifierExpression == null && (scope instanceof PsiImportStaticStatement
            || PsiTreeUtil.isAncestor(containingClass, ref, true))) {
            return null;
        }

        if (qualifierExpression instanceof PsiReferenceExpression qRefExpr) {
            PsiElement resolve = qRefExpr.resolve();
            if (resolve == containingClass) {
                return null;
            }

            if (resolve instanceof PsiTypeParameter typeParam) {
                Set<PsiClass> classes = new HashSet<>();
                for (PsiClassType type : typeParam.getExtendsListTypes()) {
                    PsiClass aClass = type.resolve();
                    if (aClass != null) {
                        classes.add(aClass);
                    }
                }

                if (classes.size() == 1 && classes.contains(containingClass)) {
                    return null;
                }
            }
        }

        return JavaErrorLocalize.staticInterfaceMethodCallQualifier();
    }

    @RequiredReadAction
    private static void registerMethodReturnFixAction(HighlightInfo highlightInfo, MethodCandidateInfo candidate, PsiCall methodCall) {
        if (methodCall.getParent() instanceof PsiReturnStatement) {
            PsiMethod containerMethod = PsiTreeUtil.getParentOfType(methodCall, PsiMethod.class, true, PsiLambdaExpression.class);
            if (containerMethod != null) {
                PsiMethod method = candidate.getElement();
                PsiExpression methodCallCopy =
                    JavaPsiFacade.getElementFactory(method.getProject()).createExpressionFromText(methodCall.getText(), methodCall);
                PsiType methodCallTypeByArgs = methodCallCopy.getType();
                //ensure type params are not included
                methodCallTypeByArgs = JavaPsiFacade.getElementFactory(method.getProject())
                    .createRawSubstitutor(method)
                    .substitute(methodCallTypeByArgs);
                if (methodCallTypeByArgs != null) {
                    QuickFixAction.registerQuickFixAction(
                        highlightInfo,
                        getFixRange(methodCall),
                        QuickFixFactory.getInstance().createMethodReturnFix(
                            containerMethod,
                            methodCallTypeByArgs,
                            true
                        )
                    );
                }
            }
        }
    }

    @RequiredReadAction
    private static String buildOneLineMismatchDescription(
        @Nonnull PsiExpressionList list,
        @Nonnull MethodCandidateInfo candidateInfo,
        @Nonnull SimpleReference<PsiElement> elementToHighlight
    ) {
        PsiExpression[] expressions = list.getExpressions();
        PsiMethod resolvedMethod = candidateInfo.getElement();
        PsiSubstitutor substitutor = candidateInfo.getSubstitutor();
        PsiParameter[] parameters = resolvedMethod.getParameterList().getParameters();
        if (expressions.length == parameters.length && parameters.length > 1) {
            int idx = -1;
            for (int i = 0; i < expressions.length; i++) {
                PsiExpression expression = expressions[i];
                if (expression instanceof PsiMethodCallExpression methodCall
                    && methodCall.resolveMethodGenerics() instanceof MethodCandidateInfo methodCandidateInfo
                    && PsiUtil.isLanguageLevel8OrHigher(list)
                    && methodCandidateInfo.isToInferApplicability()
                    && methodCandidateInfo.getInferenceErrorMessage() == null) {
                    continue;
                }
                if (!TypeConversionUtil.areTypesAssignmentCompatible(substitutor.substitute(parameters[i].getType()), expression)) {
                    if (idx != -1) {
                        idx = -1;
                        break;
                    }
                    else {
                        idx = i;
                    }
                }
            }

            if (idx > -1) {
                PsiExpression wrongArg = expressions[idx];
                PsiType argType = wrongArg.getType();
                if (argType != null) {
                    elementToHighlight.set(wrongArg);
                    LocalizeValue message = JavaErrorLocalize.incompatibleCallTypes(
                        idx + 1,
                        substitutor.substitute(parameters[idx].getType()).getCanonicalText(),
                        argType.getCanonicalText()
                    );

                    return XmlStringUtil.wrapInHtml("<body>" + XmlStringUtil.escapeString(message.get()) + " <a href=\"#assignment/" +
                        XmlStringUtil.escapeString(createMismatchedArgumentsHtmlTooltip(
                            candidateInfo,
                            list
                        )) + "\"" + (UIUtil.isUnderDarcula() ? " color=\"7AB4C9\" " : "") + ">" +
                        DaemonLocalize.inspectionExtendedDescription() + "</a></body>");
                }
            }
        }
        return null;
    }

    public static boolean isDummyConstructorCall(
        PsiMethodCallExpression methodCall,
        PsiResolveHelper resolveHelper,
        PsiExpressionList list,
        PsiReferenceExpression referenceToMethod
    ) {
        boolean isDummy = false;
        boolean isThisOrSuper = referenceToMethod.getReferenceNameElement() instanceof PsiKeyword;
        if (isThisOrSuper) {
            // super(..) or this(..)
            if (list.getExpressions().length == 0) { // implicit ctr call
                CandidateInfo[] candidates = resolveHelper.getReferencedMethodCandidates(methodCall, true);
                if (candidates.length == 1 && !candidates[0].getElement().isPhysical()) {
                    isDummy = true;// dummy constructor
                }
            }
        }
        return isDummy;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkAmbiguousMethodCallIdentifier(
        @Nonnull PsiReferenceExpression referenceToMethod,
        @Nonnull JavaResolveResult[] resolveResults,
        @Nonnull PsiExpressionList list,
        PsiElement element,
        @Nonnull JavaResolveResult resolveResult,
        @Nonnull PsiMethodCallExpression methodCall,
        @Nonnull PsiResolveHelper resolveHelper,
        @Nonnull LanguageLevel languageLevel,
        @Nonnull PsiFile file
    ) {
        MethodCandidateInfo methodCandidate1 = null;
        MethodCandidateInfo methodCandidate2 = null;
        for (JavaResolveResult result : resolveResults) {
            if (!(result instanceof MethodCandidateInfo candidate)) {
                continue;
            }
            if (candidate.isApplicable() && !candidate.getElement().isConstructor()) {
                if (methodCandidate1 == null) {
                    methodCandidate1 = candidate;
                }
                else {
                    methodCandidate2 = candidate;
                    break;
                }
            }
        }
        MethodCandidateInfo[] candidates = toMethodCandidates(resolveResults);

        HighlightInfoType highlightInfoType = HighlightInfoType.ERROR;
        if (methodCandidate2 != null) {
            return null;
        }
        LocalizeValue description;
        PsiElement elementToHighlight = ObjectUtil.notNull(referenceToMethod.getReferenceNameElement(), referenceToMethod);
        if (element != null && !resolveResult.isAccessible()) {
            description = HighlightUtil.buildProblemWithAccessDescription(referenceToMethod, resolveResult);
        }
        else if (element != null && !resolveResult.isStaticsScopeCorrect()) {
            if (element instanceof PsiMethod method && method.isStatic()) {
                PsiClass containingClass = method.getContainingClass();
                if (containingClass != null && containingClass.isInterface()) {
                    HighlightInfo info =
                        HighlightUtil.checkFeature(elementToHighlight, JavaFeature.STATIC_INTERFACE_CALLS, languageLevel, file);
                    if (info != null) {
                        return info;
                    }
                    description = checkStaticInterfaceMethodCallQualifier(
                        referenceToMethod,
                        resolveResult.getCurrentFileResolveScope(),
                        containingClass
                    );
                    if (description != null) {
                        return HighlightInfo.newHighlightInfo(highlightInfoType)
                            .range(elementToHighlight)
                            .description(description.get())
                            .escapedToolTip(XmlStringUtil.escapeString(description.get()))
                            .registerFix(QuickFixFactory.getInstance().createAccessStaticViaInstanceFix(referenceToMethod, resolveResult))
                            .create();
                    }
                }
            }

            description = HighlightUtil.buildProblemWithStaticDescription(element);
        }
        else {
            String methodName = referenceToMethod.getReferenceName() + buildArgTypesList(list);
            description = JavaErrorLocalize.cannotResolveMethod(methodName);
            if (candidates.length == 0) {
                highlightInfoType = HighlightInfoType.WRONG_REF;
            }
            else {
                return null;
            }
        }

        String toolTip = XmlStringUtil.escapeString(description.get());
        HighlightInfo info = HighlightInfo.newHighlightInfo(highlightInfoType)
            .range(elementToHighlight)
            .description(description.get())
            .escapedToolTip(toolTip)
            .create();
        registerMethodCallIntentions(info, methodCall, list, resolveHelper);
        if (element != null && !resolveResult.isStaticsScopeCorrect()) {
            HighlightUtil.registerStaticProblemQuickFixAction(element, info, referenceToMethod);
        }

        TextRange fixRange = getFixRange(elementToHighlight);
        CastMethodArgumentFix.REGISTRAR.registerCastActions(candidates, methodCall, info, fixRange);
        WrapArrayToArraysAsListFix.REGISTAR.registerCastActions(candidates, methodCall, info, fixRange);
        WrapLongWithMathToIntExactFix.REGISTAR.registerCastActions(candidates, methodCall, info, fixRange);
        WrapObjectWithOptionalOfNullableFix.REGISTAR.registerCastActions(candidates, methodCall, info, fixRange);
        WrapLongWithMathToIntExactFix.REGISTAR.registerCastActions(candidates, methodCall, info, fixRange);
        PermuteArgumentsFix.registerFix(info, methodCall, candidates, fixRange);
        WrapExpressionFix.registerWrapAction(candidates, list.getExpressions(), info);
        registerChangeParameterClassFix(methodCall, list, info);
        if (candidates.length == 0 && info != null) {
            UnresolvedReferenceQuickFixProvider.registerReferenceFixes(
                methodCall.getMethodExpression(),
                QuickFixActionRegistrar.create(info)
            );
        }
        return info;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkAmbiguousMethodCallArguments(
        @Nonnull PsiReferenceExpression referenceToMethod,
        @Nonnull JavaResolveResult[] resolveResults,
        @Nonnull PsiExpressionList list,
        PsiElement element,
        @Nonnull JavaResolveResult resolveResult,
        @Nonnull PsiMethodCallExpression methodCall,
        @Nonnull PsiResolveHelper resolveHelper,
        @Nonnull PsiElement elementToHighlight
    ) {
        MethodCandidateInfo methodCandidate1 = null;
        MethodCandidateInfo methodCandidate2 = null;
        for (JavaResolveResult result : resolveResults) {
            if (!(result instanceof MethodCandidateInfo candidate)) {
                continue;
            }
            if (candidate.isApplicable() && !candidate.getElement().isConstructor()) {
                if (methodCandidate1 == null) {
                    methodCandidate1 = candidate;
                }
                else {
                    methodCandidate2 = candidate;
                    break;
                }
            }
        }
        MethodCandidateInfo[] candidates = toMethodCandidates(resolveResults);

        String description;
        String toolTip;
        HighlightInfoType highlightInfoType = HighlightInfoType.ERROR;
        if (methodCandidate2 != null) {
            PsiMethod element1 = methodCandidate1.getElement();
            String m1 = PsiFormatUtil.formatMethod(
                element1,
                methodCandidate1.getSubstitutor(false),
                PsiFormatUtilBase.SHOW_CONTAINING_CLASS | PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
                PsiFormatUtilBase.SHOW_TYPE
            );
            PsiMethod element2 = methodCandidate2.getElement();
            String m2 = PsiFormatUtil.formatMethod(
                element2,
                methodCandidate2.getSubstitutor(false),
                PsiFormatUtilBase.SHOW_CONTAINING_CLASS | PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
                PsiFormatUtilBase.SHOW_TYPE
            );
            VirtualFile virtualFile1 = PsiUtilCore.getVirtualFile(element1);
            VirtualFile virtualFile2 = PsiUtilCore.getVirtualFile(element2);
            if (!Comparing.equal(virtualFile1, virtualFile2)) {
                if (virtualFile1 != null) {
                    m1 += " (In " + virtualFile1.getPresentableUrl() + ")";
                }
                if (virtualFile2 != null) {
                    m2 += " (In " + virtualFile2.getPresentableUrl() + ")";
                }
            }
            description = JavaErrorLocalize.ambiguousMethodCall(m1, m2).get();
            toolTip = createAmbiguousMethodHtmlTooltip(new MethodCandidateInfo[]{
                methodCandidate1,
                methodCandidate2
            }).get();
        }
        else {
            if (element != null && !resolveResult.isAccessible()) {
                return null;
            }
            if (element != null && !resolveResult.isStaticsScopeCorrect()) {
                return null;
            }
            String methodName = referenceToMethod.getReferenceName() + buildArgTypesList(list);
            description = JavaErrorLocalize.cannotResolveMethod(methodName).get();
            if (candidates.length == 0) {
                return null;
            }
            toolTip = XmlStringUtil.escapeString(description);
        }
        HighlightInfo info = HighlightInfo.newHighlightInfo(highlightInfoType)
            .range(elementToHighlight)
            .description(description)
            .escapedToolTip(toolTip)
            .create();
        if (methodCandidate2 == null) {
            registerMethodCallIntentions(info, methodCall, list, resolveHelper);
        }
        if (!resolveResult.isAccessible() && resolveResult.isStaticsScopeCorrect() && methodCandidate2 != null) {
            HighlightUtil.registerAccessQuickFixAction(
                (PsiMember)element,
                referenceToMethod,
                info,
                resolveResult.getCurrentFileResolveScope()
            );
        }
        if (element != null && !resolveResult.isStaticsScopeCorrect()) {
            HighlightUtil.registerStaticProblemQuickFixAction(element, info, referenceToMethod);
        }

        TextRange fixRange = getFixRange(elementToHighlight);
        CastMethodArgumentFix.REGISTRAR.registerCastActions(candidates, methodCall, info, fixRange);
        WrapArrayToArraysAsListFix.REGISTAR.registerCastActions(candidates, methodCall, info, fixRange);
        WrapLongWithMathToIntExactFix.REGISTAR.registerCastActions(candidates, methodCall, info, fixRange);
        WrapObjectWithOptionalOfNullableFix.REGISTAR.registerCastActions(candidates, methodCall, info, fixRange);
        WrapStringWithFileFix.REGISTAR.registerCastActions(candidates, methodCall, info, fixRange);
        PermuteArgumentsFix.registerFix(info, methodCall, candidates, fixRange);
        WrapExpressionFix.registerWrapAction(candidates, list.getExpressions(), info);
        registerChangeParameterClassFix(methodCall, list, info);
        return info;
    }

    @Nonnull
    private static MethodCandidateInfo[] toMethodCandidates(@Nonnull JavaResolveResult[] resolveResults) {
        List<MethodCandidateInfo> candidateList = new ArrayList<>(resolveResults.length);

        for (JavaResolveResult result : resolveResults) {
            if (result instanceof MethodCandidateInfo candidate && candidate.isAccessible()) {
                candidateList.add(candidate);
            }
        }
        return candidateList.toArray(new MethodCandidateInfo[candidateList.size()]);
    }

    @RequiredReadAction
    private static void registerMethodCallIntentions(
        @Nullable HighlightInfo highlightInfo,
        PsiMethodCallExpression methodCall,
        PsiExpressionList list,
        PsiResolveHelper resolveHelper
    ) {
        TextRange fixRange = getFixRange(methodCall);
        if (methodCall.getMethodExpression().getQualifierExpression() instanceof PsiReferenceExpression qualifierExpr
            && qualifierExpr.resolve() instanceof PsiClass psiClass && psiClass.getContainingClass() != null && !psiClass.isStatic()) {
            QuickFixAction.registerQuickFixAction(
                highlightInfo,
                QuickFixFactory.getInstance().createAddModifierFix(psiClass, PsiModifier.STATIC)
            );
        }

        QuickFixAction.registerQuickFixAction(
            highlightInfo,
            fixRange,
            QuickFixFactory.getInstance().createCreateMethodFromUsageFix(methodCall)
        );
        QuickFixAction.registerQuickFixAction(
            highlightInfo,
            fixRange,
            QuickFixFactory.getInstance().createCreateAbstractMethodFromUsageFix(methodCall)
        );
        QuickFixAction.registerQuickFixAction(
            highlightInfo,
            fixRange,
            QuickFixFactory.getInstance().createCreateConstructorFromSuperFix(methodCall)
        );
        QuickFixAction.registerQuickFixAction(
            highlightInfo,
            fixRange,
            QuickFixFactory.getInstance().createCreateConstructorFromThisFix(methodCall)
        );
        QuickFixAction.registerQuickFixAction(
            highlightInfo,
            fixRange,
            QuickFixFactory.getInstance().createCreatePropertyFromUsageFix(methodCall)
        );
        QuickFixAction.registerQuickFixAction(
            highlightInfo,
            fixRange,
            QuickFixFactory.getInstance().createCreateGetterSetterPropertyFromUsageFix(methodCall)
        );
        CandidateInfo[] methodCandidates = resolveHelper.getReferencedMethodCandidates(methodCall, false);
        CastMethodArgumentFix.REGISTRAR.registerCastActions(methodCandidates, methodCall, highlightInfo, fixRange);
        PermuteArgumentsFix.registerFix(highlightInfo, methodCall, methodCandidates, fixRange);
        AddTypeArgumentsFix.REGISTRAR.registerCastActions(methodCandidates, methodCall, highlightInfo, fixRange);
        WrapArrayToArraysAsListFix.REGISTAR.registerCastActions(methodCandidates, methodCall, highlightInfo, fixRange);
        WrapLongWithMathToIntExactFix.REGISTAR.registerCastActions(methodCandidates, methodCall, highlightInfo, fixRange);
        WrapObjectWithOptionalOfNullableFix.REGISTAR.registerCastActions(methodCandidates, methodCall, highlightInfo, fixRange);
        WrapStringWithFileFix.REGISTAR.registerCastActions(methodCandidates, methodCall, highlightInfo, fixRange);
        registerMethodAccessLevelIntentions(methodCandidates, methodCall, list, highlightInfo);
        registerChangeMethodSignatureFromUsageIntentions(methodCandidates, list, highlightInfo, fixRange);
        RemoveRedundantArgumentsFix.registerIntentions(methodCandidates, list, highlightInfo, fixRange);
        ConvertDoubleToFloatFix.registerIntentions(methodCandidates, list, highlightInfo, fixRange);
        WrapExpressionFix.registerWrapAction(methodCandidates, list.getExpressions(), highlightInfo);
        registerChangeParameterClassFix(methodCall, list, highlightInfo);
        if (methodCandidates.length == 0) {
            QuickFixAction.registerQuickFixAction(
                highlightInfo,
                fixRange,
                QuickFixFactory.getInstance().createStaticImportMethodFix(methodCall)
            );
            QuickFixAction.registerQuickFixAction(highlightInfo, fixRange, QuickFixFactory.getInstance().addMethodQualifierFix(methodCall));
        }
        for (IntentionAction action : QuickFixFactory.getInstance().getVariableTypeFromCallFixes(methodCall, list)) {
            QuickFixAction.registerQuickFixAction(highlightInfo, fixRange, action);
        }
        QuickFixAction.registerQuickFixAction(
            highlightInfo,
            fixRange,
            QuickFixFactory.getInstance().createReplaceAddAllArrayToCollectionFix(methodCall)
        );
        QuickFixAction.registerQuickFixAction(
            highlightInfo,
            fixRange,
            QuickFixFactory.getInstance().createSurroundWithArrayFix(methodCall, null)
        );
        QualifyThisArgumentFix.registerQuickFixAction(methodCandidates, methodCall, highlightInfo, fixRange);

        CandidateInfo[] candidates = resolveHelper.getReferencedMethodCandidates(methodCall, true);
        ChangeStringLiteralToCharInMethodCallFix.registerFixes(candidates, methodCall, highlightInfo);
    }

    @RequiredReadAction
    private static void registerMethodAccessLevelIntentions(
        CandidateInfo[] methodCandidates,
        PsiMethodCallExpression methodCall,
        PsiExpressionList exprList,
        HighlightInfo highlightInfo
    ) {
        for (CandidateInfo methodCandidate : methodCandidates) {
            PsiMethod method = (PsiMethod)methodCandidate.getElement();
            if (!methodCandidate.isAccessible() && PsiUtil.isApplicable(method, methodCandidate.getSubstitutor(), exprList)) {
                HighlightUtil.registerAccessQuickFixAction(
                    method,
                    methodCall.getMethodExpression(),
                    highlightInfo,
                    methodCandidate.getCurrentFileResolveScope()
                );
            }
        }
    }

    @Nonnull
    @RequiredReadAction
    private static LocalizeValue createAmbiguousMethodHtmlTooltip(MethodCandidateInfo[] methodCandidates) {
        return JavaErrorLocalize.ambiguousMethodHtmlTooltip(
            methodCandidates[0].getElement().getParameterList().getParametersCount() + 2,
            createAmbiguousMethodHtmlTooltipMethodRow
                (methodCandidates[0]),
            getContainingClassName(methodCandidates[0]),
            createAmbiguousMethodHtmlTooltipMethodRow(methodCandidates[1]),
            getContainingClassName(methodCandidates[1])
        );
    }

    @RequiredReadAction
    private static String getContainingClassName(MethodCandidateInfo methodCandidate) {
        PsiMethod method = methodCandidate.getElement();
        PsiClass containingClass = method.getContainingClass();
        return containingClass == null ? method.getContainingFile().getName() : HighlightUtil.formatClass(containingClass, false);
    }

    @Language("HTML")
    private static String createAmbiguousMethodHtmlTooltipMethodRow(MethodCandidateInfo methodCandidate) {
        PsiMethod method = methodCandidate.getElement();
        PsiParameter[] parameters = method.getParameterList().getParameters();
        PsiSubstitutor substitutor = methodCandidate.getSubstitutor();
        @Language("HTML") String ms = "<td><b>" + method.getName() + "</b></td>";

        for (int j = 0; j < parameters.length; j++) {
            PsiParameter parameter = parameters[j];
            PsiType type = substitutor.substitute(parameter.getType());
            ms +=
                "<td><b>" + (j == 0 ? "(" : "") + XmlStringUtil.escapeString(type.getPresentableText()) + (j == parameters.length - 1 ? ")" : ",") + "</b></td>";
        }
        if (parameters.length == 0) {
            ms += "<td><b>()</b></td>";
        }
        return ms;
    }

    @RequiredReadAction
    private static String createMismatchedArgumentsHtmlTooltip(MethodCandidateInfo info, PsiExpressionList list) {
        PsiMethod method = info.getElement();
        PsiSubstitutor substitutor = info.getSubstitutor();
        PsiClass aClass = method.getContainingClass();
        PsiParameter[] parameters = method.getParameterList().getParameters();
        String methodName = method.getName();
        return createMismatchedArgumentsHtmlTooltip(list, info, parameters, methodName, substitutor, aClass);
    }

    private static String createShortMismatchedArgumentsHtmlTooltip(
        PsiExpressionList list,
        @Nullable MethodCandidateInfo info,
        PsiParameter[] parameters,
        String methodName,
        PsiSubstitutor substitutor,
        PsiClass aClass
    ) {
        PsiExpression[] expressions = list.getExpressions();
        int cols = Math.max(parameters.length, expressions.length);

        @Language("HTML") String parensizedName = methodName + (parameters.length == 0 ? "(&nbsp;)&nbsp;" : "");
        String errorMessage = info != null ? info.getParentInferenceErrorMessage(list) : null;
        return JavaErrorBundle.message(
            "argument.mismatch.html.tooltip",
            cols - parameters.length + 1,
            parensizedName,
            HighlightUtil.formatClass(aClass, false),
            createMismatchedArgsHtmlTooltipParamsRow(parameters, substitutor, expressions),
            createMismatchedArgsHtmlTooltipArgumentsRow(expressions, parameters, substitutor, cols),
            errorMessage != null
                ? "<br/>reason: " + XmlStringUtil.escapeString(errorMessage).replaceAll("\n", "<br/>")
                : ""
        );
    }

    private static String esctrim(@Nonnull String s) {
        return XmlStringUtil.escapeString(trimNicely(s));
    }

    private static String trimNicely(String s) {
        if (s.length() <= 40) {
            return s;
        }

        List<TextRange> wordIndices = TextRangeUtil.getWordIndicesIn(s);
        if (wordIndices.size() > 2) {
            int firstWordEnd = wordIndices.get(0).getEndOffset();

            // try firstWord...remainder
            for (int i = 1; i < wordIndices.size(); i++) {
                int stringLength = firstWordEnd + s.length() - wordIndices.get(i).getStartOffset();
                if (stringLength <= 40) {
                    return s.substring(0, firstWordEnd) + "..." + s.substring(wordIndices.get(i).getStartOffset());
                }
            }
        }
        // maybe one last word will fit?
        if (!wordIndices.isEmpty() && s.length() - wordIndices.get(wordIndices.size() - 1).getStartOffset() <= 40) {
            return "..." + s.substring(wordIndices.get(wordIndices.size() - 1).getStartOffset());
        }

        return StringUtil.last(s, 40, true).toString();
    }

    @RequiredReadAction
    private static String createMismatchedArgumentsHtmlTooltip(
        PsiExpressionList list,
        MethodCandidateInfo info,
        PsiParameter[] parameters,
        String methodName,
        PsiSubstitutor substitutor,
        PsiClass aClass
    ) {
        return Math.max(parameters.length, list.getExpressions().length) <= 2
            ? createShortMismatchedArgumentsHtmlTooltip(list, info, parameters, methodName, substitutor, aClass)
            : createLongMismatchedArgumentsHtmlTooltip(list, info, parameters, methodName, substitutor, aClass);
    }

    @RequiredReadAction
    @SuppressWarnings("StringContatenationInLoop")
    @Language("HTML")
    private static String createLongMismatchedArgumentsHtmlTooltip(
        PsiExpressionList list,
        @Nullable MethodCandidateInfo info,
        PsiParameter[] parameters,
        String methodName,
        PsiSubstitutor substitutor,
        PsiClass aClass
    ) {
        PsiExpression[] expressions = list.getExpressions();

        @SuppressWarnings("NonConstantStringShouldBeStringBuffer") String s =
            "<html><body><table border=0>" + "<tr><td colspan=3>" + "<nobr><b>" + methodName + "()</b> in <b>" +
                HighlightUtil.formatClass(
                    aClass,
                    false
                ) + "</b> cannot be applied to:</nobr>" + "</td></tr>" + "<tr><td colspan=2 align=left>Expected<br>Parameters:</td><td " +
                "align=left>Actual<br>Arguments:</td></tr>" + "<tr><td colspan=3><hr></td></tr>";

        for (int i = 0; i < Math.max(parameters.length, expressions.length); i++) {
            PsiParameter parameter = i < parameters.length ? parameters[i] : null;
            PsiExpression expression = i < expressions.length ? expressions[i] : null;
            boolean showShort = showShortType(i, parameters, expressions, substitutor);
            String mismatchColor = showShort ? null : UIUtil.isUnderDarcula() ? "FF6B68" : "red";

            s += "<tr" + (i % 2 == 0 ? " style='background-color: #" +
                (UIUtil.isUnderDarcula() ? ColorUtil.toHex(ColorUtil.shift(UIUtil.getToolTipBackground(), 1.1)) : "eeeeee") +
                "'" : "") + ">";
            s += "<td><b><nobr>";
            if (parameter != null) {
                String name = parameter.getName();
                if (name != null) {
                    s += esctrim(name) + ":";
                }
            }
            s += "</nobr></b></td>";

            s += "<td><b><nobr>";
            if (parameter != null) {
                PsiType type = substitutor.substitute(parameter.getType());
                s += "<font " + (mismatchColor == null ? "" : "color=" + mismatchColor) + ">" +
                    esctrim(showShort ? type.getPresentableText() : JavaHighlightUtil.formatType(type)) + "</font>";
            }
            s += "</nobr></b></td>";

            s += "<td><b><nobr>";
            if (expression != null) {
                PsiType type = expression.getType();
                s += "<font " + (mismatchColor == null ? "" : "color='" + mismatchColor + "'") + ">" + esctrim(expression.getText()) +
                    "&nbsp;&nbsp;" + (
                    mismatchColor == null || type == null || type == PsiType.NULL
                        ? ""
                        : "(" + esctrim(JavaHighlightUtil.formatType(type)) + ")"
                ) + "</font>";

            }
            s += "</nobr></b></td>";

            s += "</tr>";
        }

        s += "</table>";
        String errorMessage = info != null ? info.getParentInferenceErrorMessage(list) : null;
        if (errorMessage != null) {
            s += "reason: ";
            s += XmlStringUtil.escapeString(errorMessage).replaceAll("\n", "<br/>");
        }
        s += "</body></html>";
        return s;
    }

    @SuppressWarnings("StringContatenationInLoop")
    @Language("HTML")
    private static String createMismatchedArgsHtmlTooltipArgumentsRow(
        PsiExpression[] expressions,
        PsiParameter[] parameters,
        PsiSubstitutor substitutor,
        int cols
    ) {
        @Language("HTML")

        String ms = "";
        for (int i = 0; i < expressions.length; i++) {
            PsiExpression expression = expressions[i];
            PsiType type = expression.getType();

            boolean showShort = showShortType(i, parameters, expressions, substitutor);
            String mismatchColor = showShort ? null : ColorUtil.toHex(JBColor.RED);
            ms += "<td> " + "<b><nobr>" + (i == 0 ? "(" : "") + "<font " + (showShort ? "" : "color=" + mismatchColor) + ">" +
                XmlStringUtil.escapeString(
                    showShort
                        ? type.getPresentableText()
                        : JavaHighlightUtil.formatType(type)
                ) + "</font>" + (i == expressions.length - 1 ? ")" : ",") + "</nobr></b></td>";
        }
        for (int i = expressions.length; i < cols + 1; i++) {
            ms += "<td>" + (i == 0 ? "<b>()</b>" : "") + "&nbsp;</td>";
        }
        return ms;
    }

    @SuppressWarnings("StringContatenationInLoop")
    @Language("HTML")
    private static String createMismatchedArgsHtmlTooltipParamsRow(
        PsiParameter[] parameters,
        PsiSubstitutor substitutor,
        PsiExpression[] expressions
    ) {
        String ms = "";
        for (int i = 0; i < parameters.length; i++) {
            PsiParameter parameter = parameters[i];
            PsiType type = substitutor.substitute(parameter.getType());
            ms += "<td><b><nobr>" + (i == 0 ? "(" : "") + XmlStringUtil.escapeString(
                showShortType(i, parameters, expressions, substitutor)
                    ? type.getPresentableText()
                    : JavaHighlightUtil.formatType(type)
            ) + (i == parameters.length - 1 ? ")" : ",") + "</nobr></b></td>";
        }
        return ms;
    }

    private static boolean showShortType(int i, PsiParameter[] parameters, PsiExpression[] expressions, PsiSubstitutor substitutor) {
        PsiExpression expression = i < expressions.length ? expressions[i] : null;
        if (expression == null) {
            return true;
        }
        PsiType paramType = i < parameters.length && parameters[i] != null ? substitutor.substitute(parameters[i].getType()) : null;
        PsiType expressionType = expression.getType();
        return paramType != null && expressionType != null && TypeConversionUtil.isAssignable(paramType, expressionType);
    }

    @RequiredReadAction
    public static HighlightInfo checkMethodMustHaveBody(PsiMethod method, PsiClass aClass) {
        if (method.getBody() == null && !method.isAbstract()
            && !method.hasModifierProperty(PsiModifier.NATIVE) && aClass != null && !aClass.isInterface()
            && !PsiUtilCore.hasErrorElementChild(method)) {
            int start = method.getModifierList().getTextRange().getStartOffset();
            int end = method.getTextRange().getEndOffset();

            QuickFixFactory factory = QuickFixFactory.getInstance();
            HighlightInfo.Builder errorResult = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(start, end)
                .descriptionAndTooltip(JavaErrorLocalize.missingMethodBody());
            if (HighlightUtil.getIncompatibleModifier(PsiModifier.ABSTRACT, method.getModifierList()) == null) {
                errorResult.registerFix(factory.createAddModifierFix(method, PsiModifier.ABSTRACT));
            }
            return errorResult.registerFix(factory.createAddMethodBodyFix(method))
                .create();
        }
        return null;
    }

    @RequiredReadAction
    public static HighlightInfo checkAbstractMethodInConcreteClass(PsiMethod method, PsiElement elementToHighlight) {
        PsiClass aClass = method.getContainingClass();
        if (method.isAbstract() && aClass != null
            && !aClass.isAbstract() && !aClass.isEnum() && !PsiUtilCore.hasErrorElementChild(method)) {
            QuickFixFactory factory = QuickFixFactory.getInstance();
            HighlightInfo.Builder errorResult = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(elementToHighlight)
                .descriptionAndTooltip(JavaErrorLocalize.abstractMethodInNonAbstractClass());
            if (method.getBody() != null) {
                errorResult.registerFix(factory.createRemoveModifierFix(method, PsiModifier.ABSTRACT));
            }
            return errorResult.registerFix(factory.createAddMethodBodyFix(method))
                .registerFix(factory.createAddModifierFix(aClass, PsiModifier.ABSTRACT))
                .create();
        }
        return null;
    }

    @RequiredReadAction
    public static HighlightInfo checkConstructorName(PsiMethod method) {
        String methodName = method.getName();
        PsiClass aClass = method.getContainingClass();

        if (aClass != null) {
            String className = aClass instanceof PsiAnonymousClass ? null : aClass.getName();
            if (className == null || !Comparing.strEqual(methodName, className)) {
                HighlightInfo.Builder errorResult = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(method.getNameIdentifier())
                    .descriptionAndTooltip(JavaErrorLocalize.missingReturnType());
                if (className != null) {
                    errorResult.registerFix(QuickFixFactory.getInstance().createRenameElementFix(method, className));
                }
                return errorResult.create();
            }
        }
        return null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkDuplicateMethod(
        PsiClass aClass,
        @Nonnull PsiMethod method,
        @Nonnull MostlySingularMultiMap<MethodSignature, PsiMethod> duplicateMethods
    ) {
        if (aClass == null || method instanceof ExternallyDefinedPsiElement) {
            return null;
        }
        MethodSignature methodSignature = method.getSignature(PsiSubstitutor.EMPTY);
        int methodCount = 1;
        List<PsiMethod> methods = (List<PsiMethod>)duplicateMethods.get(methodSignature);
        if (methods.size() > 1) {
            methodCount++;
        }

        if (methodCount == 1 && aClass.isEnum() && GenericsHighlightUtil.isEnumSyntheticMethod(methodSignature, aClass.getProject())) {
            methodCount++;
        }
        if (methodCount > 1) {
            TextRange textRange = HighlightNamesUtil.getMethodDeclarationTextRange(method);
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(method, textRange.getStartOffset(), textRange.getEndOffset())
                .descriptionAndTooltip(JavaErrorLocalize.duplicateMethod(
                    JavaHighlightUtil.formatMethod(method),
                    HighlightUtil.formatClass(aClass)
                ))
                .create();
        }
        return null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkMethodCanHaveBody(@Nonnull PsiMethod method, @Nonnull LanguageLevel languageLevel) {
        PsiClass aClass = method.getContainingClass();
        boolean hasNoBody = method.getBody() == null;
        boolean isInterface = aClass != null && aClass.isInterface();
        boolean isExtension = method.hasModifierProperty(PsiModifier.DEFAULT);
        boolean isStatic = method.isStatic();
        boolean isPrivate = method.isPrivate();

        List<IntentionAction> additionalFixes = new ArrayList<>();
        LocalizeValue description;
        QuickFixFactory factory = QuickFixFactory.getInstance();
        if (hasNoBody) {
            if (isExtension) {
                description = JavaErrorLocalize.extensionMethodShouldHaveABody();
                additionalFixes.add(factory.createAddMethodBodyFix(method));
            }
            else if (isInterface) {
                if (isStatic && languageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
                    description = LocalizeValue.localizeTODO("Static methods in interfaces should have a body");
                }
                else if (isPrivate && languageLevel.isAtLeast(LanguageLevel.JDK_1_9)) {
                    description = LocalizeValue.localizeTODO("Private methods in interfaces should have a body");
                }
                else {
                    return null;
                }
            }
            else {
                return null;
            }
        }
        else if (isInterface) {
            if (!isExtension && !isStatic && !isPrivate) {
                description = JavaErrorLocalize.interfaceMethodsCannotHaveBody();
                if (languageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
                    additionalFixes.add(factory.createAddModifierFix(method, PsiModifier.DEFAULT));
                    additionalFixes.add(factory.createAddModifierFix(method, PsiModifier.STATIC));
                }
            }
            else {
                return null;
            }
        }
        else if (isExtension) {
            description = JavaErrorLocalize.extensionMethodInClass();
        }
        else if (method.isAbstract()) {
            description = JavaErrorLocalize.abstractMethodsCannotHaveABody();
        }
        else if (method.hasModifierProperty(PsiModifier.NATIVE)) {
            description = JavaErrorLocalize.nativeMethodsCannotHaveABody();
        }
        else {
            return null;
        }

        HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .range(HighlightNamesUtil.getMethodDeclarationTextRange(method))
            .descriptionAndTooltip(description);
        if (!hasNoBody) {
            info.registerFix(factory.createDeleteMethodBodyFix(method));
        }
        if (method.isAbstract() && !isInterface) {
            info.registerFix(factory.createRemoveModifierFix(method, PsiModifier.ABSTRACT));
        }
        for (IntentionAction intentionAction : additionalFixes) {
            info.registerFix(intentionAction);
        }
        return info.create();
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkConstructorCallMustBeFirstStatement(@Nonnull PsiMethodCallExpression methodCall) {
        if (!RefactoringChangeUtil.isSuperOrThisMethodCall(methodCall)) {
            return null;
        }
        if (methodCall.getParent().getParent() instanceof PsiCodeBlock codeBlock
            && codeBlock.getParent() instanceof PsiMethod method && method.isConstructor()) {
            PsiElement prevSibling = methodCall.getParent().getPrevSibling();
            while (true) {
                if (prevSibling == null) {
                    return null;
                }
                if (prevSibling instanceof PsiStatement) {
                    break;
                }
                prevSibling = prevSibling.getPrevSibling();
            }
        }
        PsiReferenceExpression expression = methodCall.getMethodExpression();
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .range(methodCall)
            .descriptionAndTooltip(JavaErrorLocalize.constructorCallMustBeFirstStatement(expression.getText() + "()"))
            .create();
    }


    @RequiredReadAction
    public static HighlightInfo checkSuperAbstractMethodDirectCall(@Nonnull PsiMethodCallExpression methodCallExpression) {
        PsiReferenceExpression expression = methodCallExpression.getMethodExpression();
        if (!(expression.getQualifierExpression() instanceof PsiSuperExpression)) {
            return null;
        }
        PsiMethod method = methodCallExpression.resolveMethod();
        if (method != null && method.isAbstract()) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(methodCallExpression)
                .descriptionAndTooltip(JavaErrorLocalize.directAbstractMethodAccess(JavaHighlightUtil.formatMethod(method)))
                .create();
        }
        return null;
    }

    public static HighlightInfo checkConstructorCallsBaseClassConstructor(
        PsiMethod constructor,
        RefCountHolder refCountHolder,
        PsiResolveHelper resolveHelper
    ) {
        if (!constructor.isConstructor()) {
            return null;
        }
        PsiClass aClass = constructor.getContainingClass();
        if (aClass == null) {
            return null;
        }
        if (aClass.isEnum()) {
            return null;
        }
        PsiCodeBlock body = constructor.getBody();
        if (body == null) {
            return null;
        }

        // check whether constructor call super(...) or this(...)
        PsiElement element = new PsiMatcherImpl(body).firstChild(PsiMatchers.hasClass(PsiExpressionStatement.class))
            .firstChild(PsiMatchers.hasClass(PsiMethodCallExpression.class))
            .firstChild
                (PsiMatchers.hasClass(PsiReferenceExpression.class))
            .firstChild(PsiMatchers.hasClass(PsiKeyword.class))
            .getElement();
        if (element != null) {
            return null;
        }
        TextRange textRange = HighlightNamesUtil.getMethodDeclarationTextRange(constructor);
        PsiClassType[] handledExceptions = constructor.getThrowsList().getReferencedTypes();
        HighlightInfo info =
            HighlightClassUtil.checkBaseClassDefaultConstructorProblem(aClass, refCountHolder, resolveHelper, textRange, handledExceptions);
        if (info != null) {
            QuickFixAction.registerQuickFixAction(info, QuickFixFactory.getInstance().createInsertSuperFix(constructor));
            QuickFixAction.registerQuickFixAction(
                info,
                QuickFixFactory.getInstance().createAddDefaultConstructorFix(aClass.getSuperClass())
            );
        }
        return info;
    }

    /**
     * @return error if static method overrides instance method or
     * instance method overrides static. see JLS 8.4.6.1, 8.4.6.2
     */
    @RequiredReadAction
    public static HighlightInfo checkStaticMethodOverride(@Nonnull PsiMethod method, @Nonnull PsiFile containingFile) {
        // constructors are not members and therefor don't override class methods
        if (method.isConstructor()) {
            return null;
        }

        PsiClass aClass = method.getContainingClass();
        if (aClass == null) {
            return null;
        }
        HierarchicalMethodSignature methodSignature = PsiSuperMethodImplUtil.getHierarchicalMethodSignature(method);
        List<HierarchicalMethodSignature> superSignatures = methodSignature.getSuperSignatures();
        if (superSignatures.isEmpty()) {
            return null;
        }

        boolean isStatic = method.isStatic();
        for (HierarchicalMethodSignature signature : superSignatures) {
            PsiMethod superMethod = signature.getMethod();
            PsiClass superClass = superMethod.getContainingClass();
            if (superClass == null) {
                continue;
            }
            HighlightInfo highlightInfo =
                checkStaticMethodOverride(aClass, method, isStatic, superClass, superMethod, containingFile);
            if (highlightInfo != null) {
                return highlightInfo;
            }
        }
        return null;
    }

    @RequiredReadAction
    private static HighlightInfo checkStaticMethodOverride(
        PsiClass aClass,
        PsiMethod method,
        boolean isMethodStatic,
        PsiClass superClass,
        PsiMethod superMethod,
        @Nonnull PsiFile containingFile
    ) {
        if (superMethod == null) {
            return null;
        }
        PsiManager manager = containingFile.getManager();
        PsiModifierList superModifierList = superMethod.getModifierList();
        PsiModifierList modifierList = method.getModifierList();
        if (superModifierList.hasModifierProperty(PsiModifier.PRIVATE)) {
            return null;
        }
        if (superModifierList.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) && !JavaPsiFacade.getInstance(manager.getProject())
            .arePackagesTheSame(aClass, superClass)) {
            return null;
        }
        boolean isSuperMethodStatic = superModifierList.hasModifierProperty(PsiModifier.STATIC);
        if (isMethodStatic != isSuperMethodStatic) {
            String m1 = JavaHighlightUtil.formatMethod(method);
            String m2 = JavaHighlightUtil.formatMethod(superMethod);
            String c1 = HighlightUtil.formatClass(aClass);
            String c2 = HighlightUtil.formatClass(superClass);
            LocalizeValue description = isMethodStatic
                ? JavaErrorLocalize.staticMethodCannotOverrideInstanceMethod(m1, c1, m2, c2)
                : JavaErrorLocalize.instanceMethodCannotOverrideStaticMethod(m1, c1, m2, c2);

            HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(HighlightNamesUtil.getMethodDeclarationTextRange(method))
                .descriptionAndTooltip(description)
                .create();
            if (!isSuperMethodStatic || HighlightUtil.getIncompatibleModifier(PsiModifier.STATIC, modifierList) == null) {
                QuickFixAction.registerQuickFixAction(
                    info,
                    QuickFixFactory.getInstance().createModifierListFix(
                        method,
                        PsiModifier.STATIC,
                        isSuperMethodStatic,
                        false
                    )
                );
            }
            if (manager.isInProject(superMethod)
                && (!isMethodStatic || HighlightUtil.getIncompatibleModifier(PsiModifier.STATIC, superModifierList) == null)) {
                QuickFixAction.registerQuickFixAction(
                    info,
                    QuickFixFactory.getInstance().createModifierListFix(
                        superMethod,
                        PsiModifier.STATIC,
                        isMethodStatic,
                        true
                    )
                );
            }
            return info;
        }

        if (isMethodStatic) {
            if (superClass.isInterface()) {
                return null;
            }
            int accessLevel = PsiUtil.getAccessLevel(modifierList);
            String accessModifier = PsiUtil.getAccessModifier(accessLevel);
            HighlightInfo info = isWeaker(method, modifierList, accessModifier, accessLevel, superMethod, true);
            if (info != null) {
                return info;
            }
            info = checkSuperMethodIsFinal(method, superMethod);
            if (info != null) {
                return info;
            }
        }
        return null;
    }

    private static HighlightInfo checkInterfaceInheritedMethodsReturnTypes(
        @Nonnull List<? extends MethodSignatureBackedByPsiMethod> superMethodSignatures,
        @Nonnull LanguageLevel languageLevel
    ) {
        if (superMethodSignatures.size() < 2) {
            return null;
        }
        MethodSignatureBackedByPsiMethod[] returnTypeSubstitutable = {superMethodSignatures.get(0)};
        for (int i = 1; i < superMethodSignatures.size(); i++) {
            PsiMethod currentMethod = returnTypeSubstitutable[0].getMethod();
            PsiType currentType = returnTypeSubstitutable[0].getSubstitutor().substitute(currentMethod.getReturnType());

            MethodSignatureBackedByPsiMethod otherSuperSignature = superMethodSignatures.get(i);
            PsiMethod otherSuperMethod = otherSuperSignature.getMethod();
            PsiSubstitutor otherSubstitutor = otherSuperSignature.getSubstitutor();
            PsiType otherSuperReturnType = otherSubstitutor.substitute(otherSuperMethod.getReturnType());
            PsiSubstitutor unifyingSubstitutor =
                MethodSignatureUtil.getSuperMethodSignatureSubstitutor(returnTypeSubstitutable[0], otherSuperSignature);
            if (unifyingSubstitutor != null) {
                otherSuperReturnType = unifyingSubstitutor.substitute(otherSuperReturnType);
                currentType = unifyingSubstitutor.substitute(currentType);
            }

            if (otherSuperReturnType == null || currentType == null || otherSuperReturnType.equals(currentType)) {
                continue;
            }
            PsiType otherReturnType = otherSuperReturnType;
            PsiType curType = currentType;
            HighlightInfo info =
                LambdaUtil.performWithSubstitutedParameterBounds(otherSuperMethod.getTypeParameters(), otherSubstitutor, () ->
                {
                    if (languageLevel.isAtLeast(LanguageLevel.JDK_1_5)) {
                        //http://docs.oracle.com/javase/specs/jls/se7/html/jls-8.html#jls-8.4.8 Example 8.1.5-3
                        if (!(otherReturnType instanceof PsiPrimitiveType || curType instanceof PsiPrimitiveType)) {
                            if (otherReturnType.isAssignableFrom(curType)) {
                                return null;
                            }
                            if (curType.isAssignableFrom(otherReturnType)) {
                                returnTypeSubstitutable[0] = otherSuperSignature;
                                return null;
                            }
                        }
                        if (otherSuperMethod.getTypeParameters().length > 0 && JavaGenericsUtil.isRawToGeneric(otherReturnType, curType)) {
                            return null;
                        }
                    }
                    return createIncompatibleReturnTypeMessage(
                        otherSuperMethod,
                        currentMethod,
                        curType,
                        otherReturnType,
                        JavaErrorLocalize.unrelatedOverridingMethodsReturnTypes(),
                        TextRange.EMPTY_RANGE
                    );
                });
            if (info != null) {
                return info;
            }
        }
        return null;
    }

    @RequiredReadAction
    public static HighlightInfo checkOverrideEquivalentInheritedMethods(
        PsiClass aClass,
        PsiFile containingFile,
        @Nonnull LanguageLevel languageLevel
    ) {
        String description = null;
        boolean appendImplementMethodFix = true;
        Collection<HierarchicalMethodSignature> visibleSignatures = aClass.getVisibleSignatures();
        PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(aClass.getProject()).getResolveHelper();
        Ultimate:
        for (HierarchicalMethodSignature signature : visibleSignatures) {
            PsiMethod method = signature.getMethod();
            if (!resolveHelper.isAccessible(method, aClass, null)) {
                continue;
            }
            List<HierarchicalMethodSignature> superSignatures = signature.getSuperSignatures();

            boolean allAbstracts = method.isAbstract();
            PsiClass containingClass = method.getContainingClass();
            if (aClass.equals(containingClass)) {
                continue; //to be checked at method level
            }

            if (aClass.isInterface() && !containingClass.isInterface()) {
                continue;
            }
            HighlightInfo highlightInfo;
            if (allAbstracts) {
                superSignatures = new ArrayList<>(superSignatures);
                superSignatures.add(0, signature);
                highlightInfo = checkInterfaceInheritedMethodsReturnTypes(superSignatures, languageLevel);
            }
            else {
                highlightInfo = checkMethodIncompatibleReturnType(signature, superSignatures, false);
            }
            if (highlightInfo != null) {
                description = highlightInfo.getDescription();
            }

            if (method.isStatic()) {
                for (HierarchicalMethodSignature superSignature : superSignatures) {
                    PsiMethod superMethod = superSignature.getMethod();
                    if (!superMethod.isStatic()) {
                        description = JavaErrorLocalize.staticMethodCannotOverrideInstanceMethod(JavaHighlightUtil.formatMethod(method),
                            HighlightUtil.formatClass(containingClass),
                            JavaHighlightUtil.formatMethod(superMethod),
                            HighlightUtil.formatClass(superMethod.getContainingClass())
                        ).get();
                        appendImplementMethodFix = false;
                        break Ultimate;
                    }
                }
                continue;
            }

            if (description == null) {
                highlightInfo = checkMethodIncompatibleThrows(signature, superSignatures, false, aClass);
                if (highlightInfo != null) {
                    description = highlightInfo.getDescription();
                }
            }

            if (description == null) {
                highlightInfo = checkMethodWeakerPrivileges(signature, superSignatures, false, containingFile);
                if (highlightInfo != null) {
                    description = highlightInfo.getDescription();
                }
            }

            if (description != null) {
                break;
            }
        }

        if (description != null) {
            // show error info at the class level
            TextRange textRange = HighlightNamesUtil.getClassDeclarationTextRange(aClass);
            HighlightInfo highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(textRange)
                .descriptionAndTooltip(description)
                .create();
            if (appendImplementMethodFix) {
                QuickFixAction.registerQuickFixAction(highlightInfo, QuickFixFactory.getInstance().createImplementMethodsFix(aClass));
            }
            return highlightInfo;
        }
        return null;
    }

    @RequiredReadAction
    public static HighlightInfo checkConstructorHandleSuperClassExceptions(PsiMethod method) {
        if (!method.isConstructor()) {
            return null;
        }
        PsiCodeBlock body = method.getBody();
        PsiStatement[] statements = body == null ? null : body.getStatements();
        if (statements == null) {
            return null;
        }

        // if we have unhandled exception inside method body, we could not have been called here,
        // so the only problem it can catch here is with super ctr only
        Collection<PsiClassType> unhandled = ExceptionUtil.collectUnhandledExceptions(method, method.getContainingClass());
        if (unhandled.isEmpty()) {
            return null;
        }
        TextRange textRange = HighlightNamesUtil.getMethodDeclarationTextRange(method);
        HighlightInfo.Builder highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .range(textRange)
            .descriptionAndTooltip(HighlightUtil.getUnhandledExceptionsDescriptor(unhandled));
        for (PsiClassType exception : unhandled) {
            highlightInfo.registerFix(new LocalQuickFixOnPsiElementAsIntentionAdapter(
                QuickFixFactory.getInstance().createMethodThrowsFix(
                    method,
                    exception,
                    true,
                    false
                )
            ));
        }
        return highlightInfo.create();
    }

    @RequiredReadAction
    public static HighlightInfo checkRecursiveConstructorInvocation(@Nonnull PsiMethod method) {
        if (!HighlightControlFlowUtil.isRecursivelyCalledConstructor(method)) {
            return null;
        }
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .range(HighlightNamesUtil.getMethodDeclarationTextRange(method))
            .descriptionAndTooltip(JavaErrorLocalize.recursiveConstructorInvocation())
            .create();
    }

    @Nonnull
    @RequiredReadAction
    public static TextRange getFixRange(@Nonnull PsiElement element) {
        TextRange range = element.getTextRange();
        int start = range.getStartOffset();
        int end = range.getEndOffset();

        PsiElement nextSibling = element.getNextSibling();
        if (nextSibling instanceof PsiJavaToken javaToken && javaToken.getTokenType() == JavaTokenType.SEMICOLON) {
            return new TextRange(start, end + 1);
        }
        return range;
    }

    @RequiredReadAction
    public static void checkNewExpression(
        @Nonnull PsiNewExpression expression,
        PsiType type,
        @Nonnull HighlightInfoHolder holder,
        @Nonnull JavaSdkVersion javaSdkVersion
    ) {
        if (!(type instanceof PsiClassType classType)) {
            return;
        }
        PsiClassType.ClassResolveResult typeResult = classType.resolveGenerics();
        PsiClass aClass = typeResult.getElement();
        if (aClass == null) {
            return;
        }
        if (aClass instanceof PsiAnonymousClass anonymousClass) {
            type = anonymousClass.getBaseClassType();
            typeResult = ((PsiClassType)type).resolveGenerics();
            aClass = typeResult.getElement();
            if (aClass == null) {
                return;
            }
        }

        PsiJavaCodeReferenceElement classReference = expression.getClassOrAnonymousClassReference();
        checkConstructorCall(typeResult, expression, type, classReference, holder, javaSdkVersion);
    }

    @RequiredReadAction
    public static void checkConstructorCall(
        @Nonnull PsiClassType.ClassResolveResult typeResolveResult,
        @Nonnull PsiConstructorCall constructorCall,
        @Nonnull PsiType type,
        PsiJavaCodeReferenceElement classReference,
        @Nonnull HighlightInfoHolder holder,
        @Nonnull JavaSdkVersion javaSdkVersion
    ) {
        PsiExpressionList list = constructorCall.getArgumentList();
        if (list == null) {
            return;
        }
        PsiClass aClass = typeResolveResult.getElement();
        if (aClass == null) {
            return;
        }
        PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(holder.getProject()).getResolveHelper();
        PsiClass accessObjectClass = null;
        if (constructorCall instanceof PsiNewExpression newExpr) {
            PsiExpression qualifier = newExpr.getQualifier();
            if (qualifier != null) {
                accessObjectClass = (PsiClass)PsiUtil.getAccessObjectClass(qualifier).getElement();
            }
        }
        if (classReference != null && !resolveHelper.isAccessible(aClass, constructorCall, accessObjectClass)) {
            HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(classReference.getReferenceNameElement())
                .descriptionAndTooltip(HighlightUtil.buildProblemWithAccessDescription(classReference, typeResolveResult))
                .create();
            HighlightUtil.registerAccessQuickFixAction(aClass, classReference, info, null);
            holder.add(info);
            return;
        }
        PsiMethod[] constructors = aClass.getConstructors();

        if (constructors.length == 0) {
            if (list.getExpressions().length != 0) {
                String constructorName = aClass.getName();
                String argTypes = buildArgTypesList(list);
                String tooltip = createMismatchedArgumentsHtmlTooltip(
                    list,
                    null,
                    PsiParameter.EMPTY_ARRAY,
                    constructorName,
                    PsiSubstitutor.EMPTY,
                    aClass
                );
                HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(list)
                    .description(JavaErrorLocalize.wrongConstructorArguments(constructorName + "()", argTypes).get())
                    .escapedToolTip(tooltip)
                    .navigationShift(+1)
                    .create();
                QuickFixAction.registerQuickFixAction(
                    info,
                    constructorCall.getTextRange(),
                    QuickFixFactory.getInstance().createCreateConstructorFromCallFix(constructorCall)
                );
                if (classReference != null) {
                    ConstructorParametersFixer.registerFixActions(classReference, constructorCall, info, getFixRange(list));
                }
                holder.add(info);
                return;
            }
            if (classReference != null && aClass.isProtected()
                && callingProtectedConstructorFromDerivedClass(constructorCall, aClass)) {
                holder.add(buildAccessProblem(classReference, typeResolveResult, aClass));
            }
            else if (aClass.isInterface() && constructorCall instanceof PsiNewExpression newExpr) {
                PsiReferenceParameterList typeArgumentList = newExpr.getTypeArgumentList();
                if (typeArgumentList.getTypeArguments().length > 0) {
                    holder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                        .range(typeArgumentList)
                        .descriptionAndTooltip("Anonymous class implements interface; cannot have type arguments")
                        .create());
                }
            }
        }
        else {
            PsiElement place = list;
            if (constructorCall instanceof PsiNewExpression newExpr) {
                PsiAnonymousClass anonymousClass = newExpr.getAnonymousClass();
                if (anonymousClass != null) {
                    place = anonymousClass;
                }
            }

            JavaResolveResult[] results = resolveHelper.multiResolveConstructor((PsiClassType)type, list, place);
            MethodCandidateInfo result = null;
            if (results.length == 1) {
                result = (MethodCandidateInfo)results[0];
            }

            PsiMethod constructor = result == null ? null : result.getElement();

            boolean applicable = true;
            try {
                PsiDiamondType diamondType =
                    constructorCall instanceof PsiNewExpression newExpr ? PsiDiamondType.getDiamondType(newExpr) : null;
                JavaResolveResult staticFactory = diamondType != null ? diamondType.getStaticFactory() : null;
                applicable = staticFactory instanceof MethodCandidateInfo info
                    ? info.isApplicable()
                    : result != null && result.isApplicable();
            }
            catch (IndexNotReadyException e) {
                // ignore
            }

            PsiElement infoElement = list.getTextLength() > 0 ? list : constructorCall;
            if (constructor == null) {
                String name = aClass.getName();
                name += buildArgTypesList(list);
                HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(list)
                    .descriptionAndTooltip(JavaErrorLocalize.cannotResolveConstructor(name))
                    .navigationShift(+1)
                    .create();
                if (info != null) {
                    WrapExpressionFix.registerWrapAction(results, list.getExpressions(), info);
                    registerFixesOnInvalidConstructorCall(
                        constructorCall,
                        classReference,
                        list,
                        aClass,
                        constructors,
                        results,
                        infoElement,
                        info
                    );
                    holder.add(info);
                }
            }
            else if (classReference != null && (!result.isAccessible()
                || constructor.isProtected() && callingProtectedConstructorFromDerivedClass(constructorCall, aClass))) {
                holder.add(buildAccessProblem(classReference, result, constructor));
            }
            else if (!applicable) {
                LocalizeValue constructorName = HighlightMessageUtil.getSymbolName(constructor, result.getSubstitutor());
                LocalizeValue containerName = HighlightMessageUtil.getSymbolName(constructor.getContainingClass(), result.getSubstitutor());
                String argTypes = buildArgTypesList(list);
                String toolTip = createMismatchedArgumentsHtmlTooltip(result, list);

                HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(infoElement)
                    .description(JavaErrorLocalize.wrongMethodArguments(constructorName, containerName, argTypes).get())
                    .escapedToolTip(toolTip)
                    .navigationShift(+1)
                    .create();
                if (info != null) {
                    JavaResolveResult[] methodCandidates = results;
                    if (constructorCall instanceof PsiNewExpression newExpr) {
                        methodCandidates = resolveHelper.getReferencedMethodCandidates(newExpr, true);
                    }
                    registerFixesOnInvalidConstructorCall(
                        constructorCall,
                        classReference,
                        list,
                        aClass,
                        constructors,
                        methodCandidates,
                        infoElement,
                        info
                    );
                    registerMethodReturnFixAction(info, result, constructorCall);
                    holder.add(info);
                }
            }
            else if (constructorCall instanceof PsiNewExpression newExpr) {
                HighlightInfo info = GenericsHighlightUtil.checkReferenceTypeArgumentList(
                    constructor,
                    newExpr.getTypeArgumentList(),
                    result.getSubstitutor(),
                    false,
                    javaSdkVersion
                );
                if (info != null) {
                    holder.add(info);
                }
            }

            if (result != null && !holder.hasErrorResults()) {
                holder.add(checkVarargParameterErasureToBeAccessible(result, constructorCall));
            }
        }
    }

    /**
     * If the compile-time declaration is applicable by variable arity invocation,
     * then where the last formal parameter type of the invocation type of the method is Fn[],
     * it is a compile-time error if the type which is the erasure of Fn is not accessible at the point of invocation.
     */
    @RequiredReadAction
    private static HighlightInfo checkVarargParameterErasureToBeAccessible(MethodCandidateInfo info, PsiCall place) {
        PsiMethod method = info.getElement();
        if (info.isVarargs() || method.isVarArgs() && !PsiUtil.isLanguageLevel8OrHigher(place)) {
            PsiParameter[] parameters = method.getParameterList().getParameters();
            PsiType componentType = ((PsiEllipsisType)parameters[parameters.length - 1].getType()).getComponentType();
            PsiType substitutedTypeErasure = TypeConversionUtil.erasure(info.getSubstitutor().substitute(componentType));
            PsiClass targetClass = PsiUtil.resolveClassInClassTypeOnly(substitutedTypeErasure);
            if (targetClass != null && !PsiUtil.isAccessible(targetClass, place, null)) {
                PsiExpressionList argumentList = place.getArgumentList();
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .descriptionAndTooltip(LocalizeValue.localizeTODO(
                        "Formal varargs element type " + PsiFormatUtil.formatClass(targetClass, PsiFormatUtilBase.SHOW_FQ_NAME) +
                            " is inaccessible here"
                    ))
                    .range(argumentList != null ? argumentList : place)
                    .create();
            }
        }
        return null;
    }

    @RequiredReadAction
    private static void registerFixesOnInvalidConstructorCall(
        PsiConstructorCall constructorCall,
        PsiJavaCodeReferenceElement classReference,
        PsiExpressionList list,
        PsiClass aClass,
        PsiMethod[] constructors,
        JavaResolveResult[] results,
        PsiElement infoElement,
        @Nonnull HighlightInfo info
    ) {
        QuickFixAction.registerQuickFixAction(
            info,
            constructorCall.getTextRange(),
            QuickFixFactory.getInstance().createCreateConstructorFromCallFix(constructorCall)
        );
        if (classReference != null) {
            ConstructorParametersFixer.registerFixActions(classReference, constructorCall, info, getFixRange(infoElement));
            ChangeTypeArgumentsFix.registerIntentions(results, list, info, aClass);
            ConvertDoubleToFloatFix.registerIntentions(results, list, info, null);
        }
        registerChangeMethodSignatureFromUsageIntentions(results, list, info, null);
        PermuteArgumentsFix.registerFix(info, constructorCall, toMethodCandidates(results), getFixRange(list));
        registerChangeParameterClassFix(constructorCall, list, info);
        QuickFixAction.registerQuickFixAction(
            info,
            getFixRange(list),
            QuickFixFactory.getInstance().createSurroundWithArrayFix(constructorCall, null)
        );
        ChangeStringLiteralToCharInMethodCallFix.registerFixes(constructors, constructorCall, info);
    }

    @RequiredReadAction
    private static HighlightInfo buildAccessProblem(
        @Nonnull PsiJavaCodeReferenceElement classReference,
        JavaResolveResult result,
        PsiMember elementToFix
    ) {
        HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .range(classReference)
            .descriptionAndTooltip(HighlightUtil.buildProblemWithAccessDescription(classReference, result))
            .navigationShift(+1)
            .create();
        if (result.isStaticsScopeCorrect()) {
            HighlightUtil.registerAccessQuickFixAction(elementToFix, classReference, info, result.getCurrentFileResolveScope());
        }
        return info;
    }

    private static boolean callingProtectedConstructorFromDerivedClass(PsiConstructorCall place, PsiClass constructorClass) {
        if (constructorClass == null) {
            return false;
        }
        // indirect instantiation via anonymous class is ok
        if (place instanceof PsiNewExpression newExpr && newExpr.getAnonymousClass() != null) {
            return false;
        }
        PsiElement curElement = place;
        PsiClass containingClass = constructorClass.getContainingClass();
        while (true) {
            PsiClass aClass = PsiTreeUtil.getParentOfType(curElement, PsiClass.class);
            if (aClass == null) {
                return false;
            }
            curElement = aClass;
            if ((aClass.isInheritor(constructorClass, true) || containingClass != null
                && aClass.isInheritor(containingClass, true))
                && !JavaPsiFacade.getInstance(aClass.getProject()).arePackagesTheSame(aClass, constructorClass)) {
                return true;
            }
        }
    }

    private static String buildArgTypesList(PsiExpressionList list) {
        StringBuilder builder = new StringBuilder();
        builder.append("(");
        PsiExpression[] args = list.getExpressions();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            PsiType argType = args[i].getType();
            builder.append(argType != null ? JavaHighlightUtil.formatType(argType) : "?");
        }
        builder.append(")");
        return builder.toString();
    }

    private static void registerChangeParameterClassFix(
        @Nonnull PsiCall methodCall,
        @Nonnull PsiExpressionList list,
        HighlightInfo highlightInfo
    ) {
        JavaResolveResult result = methodCall.resolveMethodGenerics();
        PsiMethod method = (PsiMethod)result.getElement();
        PsiSubstitutor substitutor = result.getSubstitutor();
        PsiExpression[] expressions = list.getExpressions();
        if (method == null) {
            return;
        }
        PsiParameter[] parameters = method.getParameterList().getParameters();
        if (parameters.length != expressions.length) {
            return;
        }
        for (int i = 0; i < expressions.length; i++) {
            PsiExpression expression = expressions[i];
            PsiParameter parameter = parameters[i];
            PsiType expressionType = expression.getType();
            PsiType parameterType = substitutor.substitute(parameter.getType());
            if (expressionType == null
                || expressionType instanceof PsiPrimitiveType
                || TypeConversionUtil.isNullType(expressionType)
                || expressionType instanceof PsiArrayType) {
                continue;
            }
            if (parameterType instanceof PsiPrimitiveType
                || TypeConversionUtil.isNullType(parameterType)
                || parameterType instanceof PsiArrayType) {
                continue;
            }
            if (parameterType.isAssignableFrom(expressionType)) {
                continue;
            }
            PsiClass parameterClass = PsiUtil.resolveClassInType(parameterType);
            PsiClass expressionClass = PsiUtil.resolveClassInType(expressionType);
            if (parameterClass == null
                || expressionClass == null
                || expressionClass instanceof PsiAnonymousClass
                || parameterClass.isInheritor(expressionClass, true)) {
                continue;
            }
            QuickFixAction.registerQuickFixAction(
                highlightInfo,
                QuickFixFactory.getInstance().createChangeParameterClassFix(expressionClass, (PsiClassType)parameterType)
            );
        }
    }

    private static void registerChangeMethodSignatureFromUsageIntentions(
        @Nonnull JavaResolveResult[] candidates,
        @Nonnull PsiExpressionList list,
        @Nullable HighlightInfo highlightInfo,
        TextRange fixRange
    ) {
        if (candidates.length == 0) {
            return;
        }
        PsiExpression[] expressions = list.getExpressions();
        for (JavaResolveResult candidate : candidates) {
            registerChangeMethodSignatureFromUsageIntention(expressions, highlightInfo, fixRange, candidate, list);
        }
    }

    private static void registerChangeMethodSignatureFromUsageIntention(
        @Nonnull PsiExpression[] expressions,
        @Nullable HighlightInfo highlightInfo,
        TextRange fixRange,
        @Nonnull JavaResolveResult candidate,
        @Nonnull PsiElement context
    ) {
        if (!candidate.isStaticsScopeCorrect()) {
            return;
        }
        PsiMethod method = (PsiMethod)candidate.getElement();
        PsiSubstitutor substitutor = candidate.getSubstitutor();
        if (method != null && context.getManager().isInProject(method)) {
            IntentionAction fix = QuickFixFactory.getInstance()
                .createChangeMethodSignatureFromUsageFix(method, expressions, substitutor, context, false, 2);
            QuickFixAction.registerQuickFixAction(highlightInfo, fixRange, fix);
            IntentionAction f2 = QuickFixFactory.getInstance().createChangeMethodSignatureFromUsageReverseOrderFix(
                method,
                expressions,
                substitutor,
                context,
                false,
                2
            );
            QuickFixAction.registerQuickFixAction(highlightInfo, fixRange, f2);
        }
    }
}
