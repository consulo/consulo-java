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

package com.intellij.java.language.impl.psi.scope.util;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.impl.psi.impl.PsiClassImplUtil;
import com.intellij.java.language.impl.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.java.language.impl.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.java.language.impl.psi.scope.MethodProcessorSetupFailedException;
import com.intellij.java.language.impl.psi.scope.processor.MethodsProcessor;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.infos.ClassCandidateInfo;
import com.intellij.java.language.psi.scope.JavaScopeProcessorEvent;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.progress.ProgressIndicatorProvider;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiInvalidElementAccessException;
import consulo.language.psi.PsiManager;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.util.lang.Comparing;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PsiScopesUtil {
    private static final Logger LOG = Logger.getInstance(PsiScopesUtil.class);

    private PsiScopesUtil() {
    }

    public static boolean treeWalkUp(@Nonnull PsiScopeProcessor processor, @Nonnull PsiElement entrance, @Nullable PsiElement maxScope) {
        return treeWalkUp(processor, entrance, maxScope, ResolveState.initial());
    }

    public static boolean treeWalkUp(
        @Nonnull PsiScopeProcessor processor,
        @Nonnull PsiElement entrance,
        @Nullable PsiElement maxScope,
        @Nonnull ResolveState state
    ) {
        if (!entrance.isValid()) {
            LOG.error(new PsiInvalidElementAccessException(entrance));
        }
        PsiElement prevParent = entrance;
        PsiElement scope = entrance;

        while (scope != null) {
            ProgressIndicatorProvider.checkCanceled();
            if (scope instanceof PsiClass) {
                processor.handleEvent(JavaScopeProcessorEvent.SET_CURRENT_FILE_CONTEXT, scope);
            }
            if (!scope.processDeclarations(processor, state, prevParent, entrance)) {
                return false; // resolved
            }

            if (scope instanceof PsiModifierListOwner modifierListOwner
                && !(scope instanceof PsiParameter/* important for not loading tree! */)) {
                PsiModifierList modifierList = modifierListOwner.getModifierList();
                if (modifierList != null && modifierList.hasModifierProperty(PsiModifier.STATIC)) {
                    processor.handleEvent(JavaScopeProcessorEvent.START_STATIC, null);
                }
            }
            if (scope == maxScope) {
                break;
            }
            prevParent = scope;
            scope = prevParent.getContext();
            processor.handleEvent(JavaScopeProcessorEvent.CHANGE_LEVEL, null);
        }

        return true;
    }

    @RequiredReadAction
    public static boolean walkChildrenScopes(
        @Nonnull PsiElement thisElement,
        @Nonnull PsiScopeProcessor processor,
        @Nonnull ResolveState state,
        PsiElement lastParent,
        PsiElement place
    ) {
        PsiElement child = null;
        if (lastParent != null && lastParent.getParent() == thisElement) {
            child = lastParent.getPrevSibling();
            if (child == null) {
                return true; // first element
            }
        }

        if (child == null) {
            child = thisElement.getLastChild();
        }

        while (child != null) {
            if (!child.processDeclarations(processor, state, null, place)) {
                return false;
            }
            child = child.getPrevSibling();
        }

        return true;
    }

    @RequiredReadAction
    public static void processTypeDeclarations(PsiType type, PsiElement place, PsiScopeProcessor processor) {
        if (type instanceof PsiArrayType arrayType) {
            LanguageLevel languageLevel = PsiUtil.getLanguageLevel(place);
            PsiClass arrayClass = JavaPsiFacade.getElementFactory(place.getProject()).getArrayClass(languageLevel);
            PsiTypeParameter[] arrayTypeParameters = arrayClass.getTypeParameters();
            PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
            if (arrayTypeParameters.length > 0) {
                substitutor = substitutor.put(arrayTypeParameters[0], arrayType.getComponentType());
            }
            arrayClass.processDeclarations(processor, ResolveState.initial().put(PsiSubstitutor.KEY, substitutor), arrayClass, place);
        }
        else if (type instanceof PsiIntersectionType intersectionType) {
            for (PsiType psiType : intersectionType.getConjuncts()) {
                processTypeDeclarations(psiType, place, processor);
            }
        }
        else if (type instanceof PsiDisjunctionType disjunctionType) {
            PsiType lub = disjunctionType.getLeastUpperBound();
            processTypeDeclarations(lub, place, processor);
        }
        else if (type instanceof PsiCapturedWildcardType capturedWildcardType) {
            PsiType classType = convertToTypeParameter(capturedWildcardType, place);
            if (classType != null) {
                processTypeDeclarations(classType, place, processor);
            }
        }
        else {
            JavaResolveResult result = PsiUtil.resolveGenericsClassInType(type);
            PsiClass clazz = (PsiClass)result.getElement();
            if (clazz != null) {
                clazz.processDeclarations(processor, ResolveState.initial().put(PsiSubstitutor.KEY, result.getSubstitutor()), clazz, place);
            }
        }
    }

    @RequiredReadAction
    public static boolean resolveAndWalk(
        @Nonnull PsiScopeProcessor processor,
        @Nonnull PsiJavaCodeReferenceElement ref,
        @Nullable PsiElement maxScope
    ) {
        return resolveAndWalk(processor, ref, maxScope, false);
    }

    @RequiredReadAction
    public static boolean resolveAndWalk(
        @Nonnull PsiScopeProcessor processor,
        @Nonnull PsiJavaCodeReferenceElement ref,
        @Nullable PsiElement maxScope,
        boolean incompleteCode
    ) {
        PsiElement qualifier = ref.getQualifier();
        PsiElement classNameElement = ref.getReferenceNameElement();
        if (classNameElement == null) {
            return true;
        }
        if (qualifier != null) {
            // Composite expression
            PsiElement target = null;
            PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
            if (qualifier instanceof PsiExpression || qualifier instanceof PsiJavaCodeReferenceElement) {
                PsiType type = null;
                if (qualifier instanceof PsiExpression expression) {
                    type = expression.getType();
                    if (type != null) {
                        assert type.isValid() : type.getClass() + "; " + qualifier;
                    }
                    processTypeDeclarations(type, ref, processor);
                }

                if (type == null && qualifier instanceof PsiJavaCodeReferenceElement referenceElement) {
                    // In case of class qualifier
                    JavaResolveResult result = referenceElement.advancedResolve(incompleteCode);
                    target = result.getElement();
                    substitutor = result.getSubstitutor();

                    if (target instanceof PsiVariable variable) {
                        type = substitutor.substitute(variable.getType());
                        if (type instanceof PsiClassType classType) {
                            JavaResolveResult typeResult = classType.resolveGenerics();
                            target = typeResult.getElement();
                            substitutor = substitutor.putAll(typeResult.getSubstitutor());
                        }
                        else {
                            target = null;
                        }
                    }
                    else if (target instanceof PsiMethod method) {
                        if (substitutor.substitute(method.getReturnType()) instanceof PsiClassType classType) {
                            JavaResolveResult typeResult = classType.resolveGenerics();
                            target = typeResult.getElement();
                            substitutor = substitutor.putAll(typeResult.getSubstitutor());
                        }
                        else {
                            target = null;
                        }
                        PsiType[] types = referenceElement.getTypeParameters();
                        if (target instanceof PsiClass psiClass) {
                            substitutor = substitutor.putAll(psiClass, types);
                        }
                    }
                    else if (target instanceof PsiClass) {
                        processor.handleEvent(JavaScopeProcessorEvent.START_STATIC, null);
                    }
                }
            }

            if (target != null) {
                return target.processDeclarations(processor, ResolveState.initial().put(PsiSubstitutor.KEY, substitutor), target, ref);
            }
        }
        else {
            // simple expression -> trying to resolve variable or method
            return treeWalkUp(processor, ref, maxScope);
        }

        return true;
    }

    @RequiredReadAction
    public static void setupAndRunProcessor(
        @Nonnull MethodsProcessor processor,
        @Nonnull PsiCallExpression call,
        boolean dummyImplicitConstructor
    )
        throws MethodProcessorSetupFailedException {
        if (call instanceof PsiMethodCallExpression methodCall) {
            PsiJavaCodeReferenceElement ref = methodCall.getMethodExpression();

            processor.setArgumentList(methodCall.getArgumentList());
            processor.obtainTypeArguments(methodCall);
            if (!ref.isQualified() || ref.getReferenceNameElement() instanceof PsiKeyword) {
                PsiElement referenceNameElement = ref.getReferenceNameElement();
                if (referenceNameElement == null) {
                    return;
                }
                if (referenceNameElement instanceof PsiKeyword keyword) {
                    if (keyword.getTokenType() == JavaTokenType.THIS_KEYWORD) {
                        PsiClass aClass = JavaResolveUtil.getContextClass(methodCall);
                        if (aClass == null) {
                            throw new MethodProcessorSetupFailedException("Can't resolve class for this expression");
                        }

                        processor.setIsConstructor(true);
                        processor.setAccessClass(aClass);
                        aClass.processDeclarations(processor, ResolveState.initial(), null, call);

                        if (dummyImplicitConstructor) {
                            processDummyConstructor(processor, aClass);
                        }
                    }
                    else if (keyword.getTokenType() == JavaTokenType.SUPER_KEYWORD) {
                        PsiClass aClass = JavaResolveUtil.getContextClass(methodCall);
                        if (aClass == null) {
                            throw new MethodProcessorSetupFailedException("Can't resolve class for super expression");
                        }

                        PsiClass superClass = aClass.getSuperClass();
                        if (superClass != null) {
                            PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
                            PsiClass runSuper = superClass;
                            List<PsiSubstitutor> contextSubstitutors = new ArrayList<>();
                            do {
                                if (runSuper != null) {
                                    PsiSubstitutor superSubstitutor =
                                        TypeConversionUtil.getSuperClassSubstitutor(runSuper, aClass, PsiSubstitutor.EMPTY);
                                    contextSubstitutors.add(superSubstitutor);
                                }
                                if (aClass.isStatic()) {
                                    break;
                                }
                                aClass = JavaResolveUtil.getContextClass(aClass);
                                if (aClass != null) {
                                    runSuper = aClass.getSuperClass();
                                }
                            }
                            while (aClass != null);
                            //apply substitutors in 'outer classes down to inner classes' order because inner class subst take precedence
                            for (int i = contextSubstitutors.size() - 1; i >= 0; i--) {
                                PsiSubstitutor contextSubstitutor = contextSubstitutors.get(i);
                                substitutor = substitutor.putAll(contextSubstitutor);
                            }

                            processor.setIsConstructor(true);
                            processor.setAccessClass(null);
                            PsiMethod[] constructors = superClass.getConstructors();
                            ResolveState state = ResolveState.initial().put(PsiSubstitutor.KEY, substitutor);
                            for (PsiMethod constructor : constructors) {
                                if (!processor.execute(constructor, state)) {
                                    return;
                                }
                            }

                            if (dummyImplicitConstructor) {
                                processDummyConstructor(processor, superClass);
                            }
                        }
                    }
                    else {
                        LOG.error("Unknown name element " + referenceNameElement + " in reference " + ref.getText() + "(" + ref + ")");
                    }
                }
                else if (referenceNameElement instanceof PsiIdentifier) {
                    processor.setIsConstructor(false);
                    processor.setName(referenceNameElement.getText());
                    processor.setAccessClass(null);
                    resolveAndWalk(processor, ref, null);
                }
                else {
                    LOG.error("Unknown name element " + referenceNameElement + " in reference " + ref.getText() + "(" + ref + ")");
                }
            }
            else {
                // Complex expression
                PsiElement referenceName = methodCall.getMethodExpression().getReferenceNameElement();
                PsiManager manager = call.getManager();
                if (referenceName == null) {
                    // e.g. "manager.(beginTransaction)"
                    throw new MethodProcessorSetupFailedException("Can't resolve method name for this expression");
                }
                if (referenceName instanceof PsiIdentifier && ref.getQualifier() instanceof PsiExpression qualifier) {
                    PsiType type = qualifier.getType();
                    if (type != null && qualifier instanceof PsiReferenceExpression qRfExpr) {
                        PsiElement resolve = qRfExpr.resolve();
                        if (resolve instanceof PsiEnumConstant enumConst) {
                            PsiEnumConstantInitializer initializingClass = enumConst.getInitializingClass();
                            if (hasDesiredMethod(methodCall, type, initializingClass)) {
                                processQualifierResult(
                                    new ClassCandidateInfo(initializingClass, PsiSubstitutor.EMPTY),
                                    processor,
                                    methodCall
                                );
                                return;
                            }
                        }
                        else if (resolve instanceof PsiVariable variable
                            && variable.hasModifierProperty(PsiModifier.FINAL)
                            && variable.hasInitializer()) {
                            PsiExpression initializer = variable.getInitializer();
                            if (initializer instanceof PsiNewExpression newExpr
                                && hasDesiredMethod(methodCall, type, newExpr.getAnonymousClass())) {
                                type = initializer.getType();
                            }
                        }
                    }
                    if (type == null) {
                        if (qualifier instanceof PsiJavaCodeReferenceElement javaCodeRef) {
                            JavaResolveResult result = javaCodeRef.advancedResolve(false);
                            if (result.getElement() instanceof PsiClass) {
                                processor.handleEvent(JavaScopeProcessorEvent.START_STATIC, null);
                                processQualifierResult(result, processor, methodCall);
                            }
                        }
                        else {
                            throw new MethodProcessorSetupFailedException("Cant determine qualifier type!");
                        }
                    }
                    else if (type instanceof PsiDisjunctionType disjunctionType) {
                        processQualifierType(disjunctionType.getLeastUpperBound(), processor, manager, methodCall);
                    }
                    else if (type instanceof PsiCapturedWildcardType capturedWildcardType) {
                        PsiType psiType = convertToTypeParameter(capturedWildcardType, methodCall);
                        if (psiType != null) {
                            processQualifierType(psiType, processor, manager, methodCall);
                        }
                    }
                    else {
                        processQualifierType(type, processor, manager, methodCall);
                    }
                }
                else {
                    LOG.error(
                        "ref: " + ref + " (" + ref.getClass() + ")," +
                        " ref.getReferenceNameElement()=" + ref.getReferenceNameElement() +
                        "; methodCall.getMethodExpression().getReferenceNameElement()=" +
                        methodCall.getMethodExpression().getReferenceNameElement() +
                        "; qualifier=" + ref.getQualifier()
                    );
                }
            }
        }
        else {
            LOG.assertTrue(call instanceof PsiNewExpression);
            PsiNewExpression newExpr = (PsiNewExpression)call;
            PsiJavaCodeReferenceElement classRef = newExpr.getClassOrAnonymousClassReference();
            if (classRef == null) {
                throw new MethodProcessorSetupFailedException("Cant get reference to class in new expression");
            }

            JavaResolveResult result = classRef.advancedResolve(false);
            PsiClass aClass = (PsiClass)result.getElement();
            if (aClass == null) {
                throw new MethodProcessorSetupFailedException("Cant resolve class in new expression");
            }
            processor.setIsConstructor(true);
            processor.setAccessClass(aClass);
            processor.setArgumentList(newExpr.getArgumentList());
            processor.obtainTypeArguments(newExpr);
            aClass.processDeclarations(processor, ResolveState.initial().put(PsiSubstitutor.KEY, result.getSubstitutor()), null, call);

            if (dummyImplicitConstructor) {
                processDummyConstructor(processor, aClass);
            }
        }
    }

    private static PsiType convertToTypeParameter(PsiCapturedWildcardType type, PsiElement methodCall) {
        GlobalSearchScope placeResolveScope = methodCall.getResolveScope();
        PsiType upperBound = PsiClassImplUtil.correctType(type.getUpperBound(), placeResolveScope);
        while (upperBound instanceof PsiCapturedWildcardType capturedWildcardType) {
            upperBound = PsiClassImplUtil.correctType(capturedWildcardType.getUpperBound(), placeResolveScope);
        }

        //arrays can't participate in extends list
        if (upperBound instanceof PsiArrayType) {
            return upperBound;
        }

        if (upperBound != null) {
            return InferenceSession.createTypeParameterTypeWithUpperBound(upperBound, methodCall);
        }
        return null;
    }

    private static boolean hasDesiredMethod(PsiMethodCallExpression methodCall, PsiType type, PsiAnonymousClass anonymousClass) {
        if (anonymousClass != null && type.equals(anonymousClass.getBaseClassType())) {
            PsiMethod[] refMethods = anonymousClass.findMethodsByName(methodCall.getMethodExpression().getReferenceName(), false);
            if (refMethods.length > 0) {
                PsiClass baseClass = PsiUtil.resolveClassInType(type);
                if (baseClass != null && !hasCovariantOverridingOrNotPublic(baseClass, refMethods)) {
                    for (PsiMethod method : refMethods) {
                        if (method.findSuperMethods(baseClass).length > 0) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static boolean hasCovariantOverridingOrNotPublic(PsiClass baseClass, PsiMethod[] refMethods) {
        for (PsiMethod method : refMethods) {
            PsiType methodReturnType = method.getReturnType();
            for (PsiMethod superMethod : method.findSuperMethods(baseClass)) {
                if (!Comparing.equal(methodReturnType, superMethod.getReturnType())) {
                    return true;
                }

                if (!superMethod.isPublic()) {
                    return true;
                }
            }
        }
        return false;
    }

    @RequiredReadAction
    private static boolean processQualifierType(
        @Nonnull PsiType type,
        MethodsProcessor processor,
        PsiManager manager,
        PsiMethodCallExpression call
    ) throws MethodProcessorSetupFailedException {
        LOG.assertTrue(type.isValid());
        if (type instanceof PsiClassType classType) {
            JavaResolveResult qualifierResult = classType.resolveGenerics();
            return processQualifierResult(qualifierResult, processor, call);
        }
        if (type instanceof PsiArrayType arrayType) {
            LanguageLevel languageLevel = PsiUtil.getLanguageLevel(call);
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(manager.getProject());
            JavaResolveResult qualifierResult =
                factory.getArrayClassType(arrayType.getComponentType(), languageLevel).resolveGenerics();
            return processQualifierResult(qualifierResult, processor, call);
        }
        if (type instanceof PsiIntersectionType intersectionType) {
            for (PsiType conjunct : intersectionType.getConjuncts()) {
                if (!processQualifierType(conjunct, processor, manager, call)) {
                    return false;
                }
            }
        }

        return true;
    }

    @RequiredReadAction
    private static boolean processQualifierResult(
        @Nonnull JavaResolveResult qualifierResult,
        @Nonnull MethodsProcessor processor,
        @Nonnull PsiMethodCallExpression methodCall
    ) throws MethodProcessorSetupFailedException {
        PsiElement resolve = qualifierResult.getElement();

        if (resolve == null) {
            throw new MethodProcessorSetupFailedException("Cant determine qualifier class!");
        }

        if (resolve instanceof PsiTypeParameter typeParam) {
            processor.setAccessClass(typeParam);
        }
        else if (resolve instanceof PsiClass psiClass) {
            PsiExpression qualifier = methodCall.getMethodExpression().getQualifierExpression();
            if (!(qualifier instanceof PsiSuperExpression superExpr)) {
                processor.setAccessClass((PsiClass)PsiUtil.getAccessObjectClass(qualifier).getElement());
            }
            else if (superExpr.getQualifier() != null
                && PsiUtil.isLanguageLevel8OrHigher(qualifier)
                && CommonClassNames.JAVA_LANG_CLONEABLE.equals(psiClass.getQualifiedName())
                && psiClass.isInterface()) {
                processor.setAccessClass(psiClass);
            }
        }

        processor.setIsConstructor(false);
        processor.setName(methodCall.getMethodExpression().getReferenceName());
        ResolveState state = ResolveState.initial().put(PsiSubstitutor.KEY, qualifierResult.getSubstitutor());
        return resolve.processDeclarations(processor, state, methodCall, methodCall);
    }

    private static void processDummyConstructor(MethodsProcessor processor, PsiClass aClass) {
        if (aClass instanceof PsiAnonymousClass) {
            return;
        }
        try {
            PsiMethod[] constructors = aClass.getConstructors();
            if (constructors.length != 0) {
                return;
            }
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(aClass.getProject());
            PsiMethod dummyConstructor = factory.createConstructor();
            PsiIdentifier nameIdentifier = aClass.getNameIdentifier();
            if (nameIdentifier != null) {
                dummyConstructor.getNameIdentifier().replace(nameIdentifier);
            }
            processor.forceAddResult(dummyConstructor);
        }
        catch (IncorrectOperationException e) {
            LOG.error(e);
        }
    }
}
