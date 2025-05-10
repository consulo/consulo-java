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
package com.intellij.java.language.impl.codeInsight;

import com.intellij.java.language.codeInsight.CustomExceptionHandler;
import com.intellij.java.language.impl.psi.controlFlow.*;
import com.intellij.java.language.impl.psi.impl.PsiClassImplUtil;
import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import com.intellij.java.language.impl.psi.scope.MethodProcessorSetupFailedException;
import com.intellij.java.language.impl.psi.scope.processor.MethodResolverProcessor;
import com.intellij.java.language.impl.psi.scope.util.PsiScopesUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.infos.MethodCandidateInfo;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.MethodSignatureUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.java.language.impl.codeInsight.ExtraExceptionHandler;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.SmartList;
import consulo.util.lang.BitUtil;
import consulo.util.lang.Pair;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;

/**
 * @author mike
 */
public class ExceptionUtil {
    private static final String CLONE_METHOD_NAME = "clone";

    private ExceptionUtil() {
    }

    @Nonnull
    public static List<PsiClassType> getThrownExceptions(@Nonnull PsiElement[] elements) {
        List<PsiClassType> array = new ArrayList<>();
        for (PsiElement element : elements) {
            List<PsiClassType> exceptions = getThrownExceptions(element);
            addExceptions(array, exceptions);
        }

        return array;
    }

    @Nonnull
    public static List<PsiClassType> getThrownCheckedExceptions(@Nonnull PsiElement... elements) {
        List<PsiClassType> exceptions = getThrownExceptions(elements);
        if (exceptions.isEmpty()) {
            return exceptions;
        }
        exceptions = filterOutUncheckedExceptions(exceptions);
        return exceptions;
    }

    @Nonnull
    private static List<PsiClassType> filterOutUncheckedExceptions(@Nonnull List<PsiClassType> exceptions) {
        List<PsiClassType> array = new ArrayList<>();
        for (PsiClassType exception : exceptions) {
            if (!isUncheckedException(exception)) {
                array.add(exception);
            }
        }
        return array;
    }

    @Nonnull
    public static List<PsiClassType> getThrownExceptions(@Nonnull PsiElement element) {
        List<PsiClassType> result = new ArrayList<>();
        element.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitAnonymousClass(@Nonnull PsiAnonymousClass aClass) {
                PsiExpressionList argumentList = aClass.getArgumentList();
                if (argumentList != null) {
                    super.visitExpressionList(argumentList);
                }
                super.visitAnonymousClass(aClass);
            }

            @Override
            public void visitClass(@Nonnull PsiClass aClass) {
                // do not go inside class declaration
            }

            @Override
            public void visitMethodCallExpression(@Nonnull PsiMethodCallExpression expression) {
                PsiReferenceExpression methodRef = expression.getMethodExpression();
                JavaResolveResult resolveResult = methodRef.advancedResolve(false);
                PsiMethod method = (PsiMethod)resolveResult.getElement();
                if (method != null) {
                    addExceptions(result, getExceptionsByMethod(method, resolveResult.getSubstitutor(), element));
                }
                super.visitMethodCallExpression(expression);
            }

            @Override
            public void visitNewExpression(@Nonnull PsiNewExpression expression) {
                JavaResolveResult resolveResult = expression.resolveMethodGenerics();
                PsiMethod method = (PsiMethod)resolveResult.getElement();
                if (method != null) {
                    addExceptions(result, getExceptionsByMethod(method, resolveResult.getSubstitutor(), element));
                }
                super.visitNewExpression(expression);
            }

            @Override
            @RequiredReadAction
            public void visitThrowStatement(@Nonnull PsiThrowStatement statement) {
                PsiExpression expr = statement.getException();
                if (expr != null) {
                    addExceptions(
                        result,
                        ContainerUtil.mapNotNull(getPreciseThrowTypes(expr), it -> it instanceof PsiClassType classType ? classType : null)
                    );
                }
                super.visitThrowStatement(statement);
            }

            @Override
            public void visitLambdaExpression(@Nonnull PsiLambdaExpression expression) {
                // do not go inside lambda
            }

            @Override
            public void visitResourceList(@Nonnull PsiResourceList resourceList) {
                for (PsiResourceListElement listElement : resourceList) {
                    addExceptions(result, getCloserExceptions(listElement));
                }
                super.visitResourceList(resourceList);
            }

            @Override
            public void visitTryStatement(@Nonnull PsiTryStatement statement) {
                addExceptions(result, getTryExceptions(statement));
                // do not call super: try exception goes into try body recursively
            }
        });
        return result;
    }

    @Nonnull
    private static List<PsiClassType> getTryExceptions(@Nonnull PsiTryStatement tryStatement) {
        List<PsiClassType> array = new ArrayList<>();

        PsiResourceList resourceList = tryStatement.getResourceList();
        if (resourceList != null) {
            for (PsiResourceListElement resource : resourceList) {
                addExceptions(array, getUnhandledCloserExceptions(resource, resourceList));
            }
        }

        PsiCodeBlock tryBlock = tryStatement.getTryBlock();
        if (tryBlock != null) {
            addExceptions(array, getThrownExceptions(tryBlock));
        }

        for (PsiParameter parameter : tryStatement.getCatchBlockParameters()) {
            PsiType exception = parameter.getType();
            for (int j = array.size() - 1; j >= 0; j--) {
                PsiClassType exception1 = array.get(j);
                if (exception.isAssignableFrom(exception1)) {
                    array.remove(exception1);
                }
            }
        }

        for (PsiCodeBlock catchBlock : tryStatement.getCatchBlocks()) {
            addExceptions(array, getThrownExceptions(catchBlock));
        }

        PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
        if (finallyBlock != null) {
            // if finally block completes normally, exception not caught
            // if finally block completes abruptly, exception gets lost
            try {
                ControlFlow flow = ControlFlowFactory.getInstance(finallyBlock.getProject())
                    .getControlFlow(finallyBlock, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance(), false);
                int completionReasons = ControlFlowUtil.getCompletionReasons(flow, 0, flow.getSize());
                List<PsiClassType> thrownExceptions = getThrownExceptions(finallyBlock);
                if (!BitUtil.isSet(completionReasons, ControlFlowUtil.NORMAL_COMPLETION_REASON)) {
                    array = new ArrayList<>(thrownExceptions);
                }
                else {
                    addExceptions(array, thrownExceptions);
                }
            }
            catch (AnalysisCanceledException e) {
                // incomplete code
            }
        }

        return array;
    }

    @Nonnull
    @RequiredReadAction
    private static List<PsiClassType> getExceptionsByMethodAndChildren(
        @Nonnull PsiElement element,
        @Nonnull JavaResolveResult resolveResult
    ) {
        List<PsiClassType> result = new ArrayList<>();

        PsiMethod method = (PsiMethod)resolveResult.getElement();
        if (method != null) {
            addExceptions(result, getExceptionsByMethod(method, resolveResult.getSubstitutor(), element));
        }

        addExceptions(result, getThrownExceptions(element.getChildren()));

        return result;
    }

    @Nonnull
    private static List<PsiClassType> getExceptionsByMethod(
        @Nonnull PsiMethod method,
        @Nonnull PsiSubstitutor substitutor,
        @Nonnull PsiElement place
    ) {
        PsiClassType[] referenceTypes = method.getThrowsList().getReferencedTypes();
        if (referenceTypes.length == 0) {
            return Collections.emptyList();
        }

        GlobalSearchScope scope = place.getResolveScope();

        List<PsiClassType> result = new ArrayList<>();
        for (PsiType type : referenceTypes) {
            type = PsiClassImplUtil.correctType(substitutor.substitute(type), scope);
            if (type instanceof PsiClassType classType) {
                result.add(classType);
            }
        }

        return result;
    }

    private static void addExceptions(@Nonnull List<PsiClassType> array, @Nonnull Collection<PsiClassType> exceptions) {
        for (PsiClassType exception : exceptions) {
            addException(array, exception);
        }
    }

    private static void addException(@Nonnull List<PsiClassType> array, @Nullable PsiClassType exception) {
        if (exception == null) {
            return;
        }
        for (int i = array.size() - 1; i >= 0; i--) {
            PsiClassType exception1 = array.get(i);
            if (exception1.isAssignableFrom(exception)) {
                return;
            }
            if (exception.isAssignableFrom(exception1)) {
                array.remove(i);
            }
        }
        array.add(exception);
    }

    @Nonnull
    @RequiredReadAction
    public static Collection<PsiClassType> collectUnhandledExceptions(@Nonnull PsiElement element, @Nullable PsiElement topElement) {
        return collectUnhandledExceptions(element, topElement, true);
    }

    @Nonnull
    @RequiredReadAction
    public static Collection<PsiClassType> collectUnhandledExceptions(
        @Nonnull PsiElement element,
        @Nullable PsiElement topElement,
        boolean includeSelfCalls
    ) {
        Set<PsiClassType> set = collectUnhandledExceptions(element, topElement, null, includeSelfCalls);
        return set == null ? Collections.emptyList() : set;
    }

    @Nullable
    @RequiredReadAction
    private static Set<PsiClassType> collectUnhandledExceptions(
        @Nonnull PsiElement element,
        @Nullable PsiElement topElement,
        @Nullable Set<PsiClassType> foundExceptions,
        boolean includeSelfCalls
    ) {
        Collection<PsiClassType> unhandledExceptions = null;
        if (element instanceof PsiCallExpression call) {
            unhandledExceptions = getUnhandledExceptions(call, topElement, includeSelfCalls);
        }
        else if (element instanceof PsiMethodReferenceExpression methodRef) {
            PsiExpression qualifierExpression = methodRef.getQualifierExpression();
            return qualifierExpression != null
                ? collectUnhandledExceptions(qualifierExpression, topElement, null, false)
                : null;
        }
        else if (element instanceof PsiLambdaExpression) {
            return null;
        }
        else if (element instanceof PsiThrowStatement throwStmt) {
            unhandledExceptions = getUnhandledExceptions(throwStmt, topElement);
        }
        else if (element instanceof PsiCodeBlock codeBlock && codeBlock.getParent() instanceof PsiMethod constructor
            && constructor.isConstructor() && !firstStatementIsConstructorCall(codeBlock)) {
            // there is implicit parent constructor call
            PsiClass aClass = constructor.getContainingClass();
            PsiClass superClass = aClass == null ? null : aClass.getSuperClass();
            PsiMethod[] superConstructors = superClass == null ? PsiMethod.EMPTY_ARRAY : superClass.getConstructors();
            Set<PsiClassType> unhandled = new HashSet<>();
            for (PsiMethod superConstructor : superConstructors) {
                if (!superConstructor.isPrivate() && superConstructor.getParameterList().getParametersCount() == 0) {
                    PsiClassType[] exceptionTypes = superConstructor.getThrowsList().getReferencedTypes();
                    for (PsiClassType exceptionType : exceptionTypes) {
                        if (!isUncheckedException(exceptionType) && !isHandled(element, exceptionType, topElement)) {
                            unhandled.add(exceptionType);
                        }
                    }
                    break;
                }
            }

            // plus all exceptions thrown in instance class initializers
            if (aClass != null) {
                PsiClassInitializer[] initializers = aClass.getInitializers();
                Set<PsiClassType> thrownByInitializer = new HashSet<>();
                for (PsiClassInitializer initializer : initializers) {
                    if (initializer.isStatic()) {
                        continue;
                    }
                    thrownByInitializer.clear();
                    collectUnhandledExceptions(initializer.getBody(), initializer, thrownByInitializer, includeSelfCalls);
                    for (PsiClassType thrown : thrownByInitializer) {
                        if (!isHandled(constructor.getBody(), thrown, topElement)) {
                            unhandled.add(thrown);
                        }
                    }
                }
            }
            unhandledExceptions = unhandled;
        }
        else if (element instanceof PsiResourceListElement resourceListElement) {
            List<PsiClassType> unhandled = getUnhandledCloserExceptions(resourceListElement, topElement);
            if (!unhandled.isEmpty()) {
                unhandledExceptions = new ArrayList<>(unhandled);
            }
        }

        if (unhandledExceptions != null) {
            if (foundExceptions == null) {
                foundExceptions = new HashSet<>();
            }
            foundExceptions.addAll(unhandledExceptions);
        }

        for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            Set<PsiClassType> foundInChild = collectUnhandledExceptions(child, topElement, foundExceptions, includeSelfCalls);
            if (foundExceptions == null) {
                foundExceptions = foundInChild;
            }
            else if (foundInChild != null) {
                foundExceptions.addAll(foundInChild);
            }
        }

        return foundExceptions;
    }

    @Nonnull
    private static Collection<PsiClassType> getUnhandledExceptions(
        @Nonnull PsiMethodReferenceExpression methodReferenceExpression,
        PsiElement topElement
    ) {
        JavaResolveResult resolveResult = methodReferenceExpression.advancedResolve(false);
        if (resolveResult.getElement() instanceof PsiMethod method) {
            PsiElement referenceNameElement = methodReferenceExpression.getReferenceNameElement();
            return getUnhandledExceptions(method, referenceNameElement, topElement, resolveResult.getSubstitutor());
        }
        return Collections.emptyList();
    }

    @RequiredReadAction
    private static boolean firstStatementIsConstructorCall(@Nonnull PsiCodeBlock constructorBody) {
        PsiStatement[] statements = constructorBody.getStatements();
        if (statements.length == 0) {
            return false;
        }
        if (!(statements[0] instanceof PsiExpressionStatement exprStmt)
            || !(exprStmt.getExpression() instanceof PsiMethodCallExpression methodCall)) {
            return false;
        }

        PsiMethod method = (PsiMethod)methodCall.getMethodExpression().resolve();
        return method != null && method.isConstructor();
    }

    @Nonnull
    public static List<PsiClassType> getUnhandledExceptions(final @Nonnull PsiElement[] elements) {
        final List<PsiClassType> array = new ArrayList<>();

        PsiElementVisitor visitor = new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitEnumConstant(PsiEnumConstant enumConstant) {
                PsiMethod method = enumConstant.resolveMethod();
                if (method != null) {
                    addExceptions(array, getUnhandledExceptions(method, enumConstant, null, PsiSubstitutor.EMPTY));
                }
                visitElement(enumConstant);
            }

            @Override
            public void visitCallExpression(@Nonnull PsiCallExpression expression) {
                addExceptions(array, getUnhandledExceptions(expression, null));
                visitElement(expression);
            }

            @Override
            @RequiredReadAction
            public void visitThrowStatement(@Nonnull PsiThrowStatement statement) {
                addExceptions(array, getUnhandledExceptions(statement, null));
                visitElement(statement);
            }

            @Override
            public void visitLambdaExpression(@Nonnull PsiLambdaExpression expression) {
                if (ArrayUtil.find(elements, expression) >= 0) {
                    visitElement(expression);
                }
            }

            @Override
            public void visitMethodReferenceExpression(@Nonnull PsiMethodReferenceExpression expression) {
                if (ArrayUtil.find(elements, expression) >= 0) {
                    addExceptions(array, getUnhandledExceptions(expression, null));
                    visitElement(expression);
                }
            }

            @Override
            public void visitResourceVariable(@Nonnull PsiResourceVariable resource) {
                addExceptions(array, getUnhandledCloserExceptions(resource, null));
                visitElement(resource);
            }

            @Override
            public void visitResourceExpression(@Nonnull PsiResourceExpression resource) {
                addExceptions(array, getUnhandledCloserExceptions(resource, null));
                visitElement(resource);
            }

            @Override
            public void visitClass(@Nonnull PsiClass aClass) {
            }
        };

        for (PsiElement element : elements) {
            element.accept(visitor);
        }

        return array;
    }

    @Nonnull
    public static List<PsiClassType> getUnhandledExceptions(@Nonnull PsiElement element) {
        return getUnhandledExceptions(new PsiElement[]{element});
    }

    @Nonnull
    public static List<PsiClassType> getUnhandledExceptions(
        @Nonnull PsiCallExpression methodCall,
        @Nullable PsiElement topElement
    ) {
        return getUnhandledExceptions(methodCall, topElement, true);
    }

    @Nonnull
    public static List<PsiClassType> getUnhandledExceptions(
        @Nonnull PsiCallExpression methodCall,
        @Nullable PsiElement topElement,
        boolean includeSelfCalls
    ) {
        //exceptions only influence the invocation type after overload resolution is complete
        if (MethodCandidateInfo.isOverloadCheck()) {
            return Collections.emptyList();
        }
        MethodCandidateInfo.CurrentCandidateProperties properties =
            MethodCandidateInfo.getCurrentMethod(methodCall.getArgumentList());
        JavaResolveResult result =
            properties != null ? properties.getInfo() : PsiDiamondType.getDiamondsAwareResolveResult(methodCall);
        if (!(result.getElement() instanceof PsiMethod method)) {
            return Collections.emptyList();
        }
        PsiMethod containingMethod = PsiTreeUtil.getParentOfType(methodCall, PsiMethod.class);
        if (!includeSelfCalls && method == containingMethod) {
            return Collections.emptyList();
        }

        if (properties != null) {
            PsiUtilCore.ensureValid(method);
        }

        PsiClassType[] thrownExceptions = method.getThrowsList().getReferencedTypes();
        if (thrownExceptions.length == 0) {
            return Collections.emptyList();
        }

        PsiSubstitutor substitutor = result.getSubstitutor();
        if (!isArrayClone(method, methodCall) && methodCall instanceof PsiMethodCallExpression methodCallExpr) {
            PsiFile containingFile = (containingMethod == null ? methodCallExpr : containingMethod).getContainingFile();
            MethodResolverProcessor processor = new MethodResolverProcessor(methodCallExpr, containingFile);
            try {
                PsiScopesUtil.setupAndRunProcessor(processor, methodCallExpr, false);
                List<Pair<PsiMethod, PsiSubstitutor>> candidates = ContainerUtil.mapNotNull(
                    processor.getResults(),
                    info -> {
                        if (info instanceof MethodCandidateInfo candidate
                            && info.getElement() instanceof PsiMethod otherMethod
                            && otherMethod != method /* don't check self */
                            && MethodSignatureUtil.areSignaturesEqual(method, otherMethod)
                            && !MethodSignatureUtil.isSuperMethod(otherMethod, method)
                            && !(candidate.isToInferApplicability() && !candidate.isApplicable())) {
                            return Pair.create(otherMethod, candidate.getSubstitutor(false));
                        }
                        return null;
                    }
                );
                if (!candidates.isEmpty()) {
                    GlobalSearchScope scope = methodCallExpr.getResolveScope();
                    List<PsiClassType> ex = collectSubstituted(substitutor, thrownExceptions, scope);
                    for (Pair<PsiMethod, PsiSubstitutor> pair : candidates) {
                        PsiClassType[] exceptions = pair.first.getThrowsList().getReferencedTypes();
                        if (exceptions.length == 0) {
                            return getUnhandledExceptions(methodCallExpr, topElement, PsiSubstitutor.EMPTY, PsiClassType.EMPTY_ARRAY);
                        }
                        retainExceptions(ex, collectSubstituted(pair.second, exceptions, scope));
                    }
                    return getUnhandledExceptions(methodCallExpr, topElement, PsiSubstitutor.EMPTY, ex.toArray(new PsiClassType[ex.size()]));
                }
            }
            catch (MethodProcessorSetupFailedException ignore) {
                return Collections.emptyList();
            }
        }

        return getUnhandledExceptions(method, methodCall, topElement, substitutor);
    }

    public static void retainExceptions(List<PsiClassType> ex, List<PsiClassType> thrownEx) {
        List<PsiClassType> replacement = new ArrayList<>();
        for (Iterator<PsiClassType> iterator = ex.iterator(); iterator.hasNext(); ) {
            PsiClassType classType = iterator.next();
            boolean found = false;
            for (PsiClassType psiClassType : thrownEx) {
                if (psiClassType.isAssignableFrom(classType)) {
                    found = true;
                    break;
                }
                else if (classType.isAssignableFrom(psiClassType)) {
                    if (isUncheckedException(classType) == isUncheckedException(psiClassType)) {
                        replacement.add(psiClassType);
                    }
                }
            }
            if (!found) {
                iterator.remove();
            }
        }
        ex.removeAll(replacement);
        ex.addAll(replacement);
    }

    public static List<PsiClassType> collectSubstituted(
        PsiSubstitutor substitutor,
        PsiClassType[] thrownExceptions,
        GlobalSearchScope scope
    ) {
        List<PsiClassType> ex = new ArrayList<>();
        for (PsiClassType thrownException : thrownExceptions) {
            PsiType psiType = PsiClassImplUtil.correctType(substitutor.substitute(thrownException), scope);
            if (psiType instanceof PsiClassType classType) {
                ex.add(classType);
            }
            else if (psiType instanceof PsiCapturedWildcardType capturedWildcardType) {
                if (capturedWildcardType.getUpperBound() instanceof PsiClassType upperBoundClassType) {
                    ex.add(upperBoundClassType);
                }
            }
        }
        return ex;
    }

    @Nonnull
    public static List<PsiClassType> getCloserExceptions(@Nonnull PsiResourceListElement resource) {
        List<PsiClassType> ex = getExceptionsFromClose(resource);
        return ex != null ? ex : Collections.emptyList();
    }

    @Nonnull
    public static List<PsiClassType> getUnhandledCloserExceptions(
        @Nonnull PsiResourceListElement resource,
        @Nullable PsiElement topElement
    ) {
        PsiType type = resource.getType();
        return getUnhandledCloserExceptions(resource, topElement, type);
    }

    @Nonnull
    public static List<PsiClassType> getUnhandledCloserExceptions(PsiElement place, @Nullable PsiElement topElement, PsiType type) {
        List<PsiClassType> ex = type instanceof PsiClassType ? getExceptionsFromClose(type, place.getResolveScope()) : null;
        return ex != null
            ? getUnhandledExceptions(place, topElement, PsiSubstitutor.EMPTY, ex.toArray(new PsiClassType[ex.size()]))
            : Collections.emptyList();
    }

    private static List<PsiClassType> getExceptionsFromClose(PsiResourceListElement resource) {
        return resource.getType() instanceof PsiClassType classType ? getExceptionsFromClose(classType, resource.getResolveScope()) : null;
    }

    private static List<PsiClassType> getExceptionsFromClose(PsiType type, GlobalSearchScope scope) {
        PsiClassType.ClassResolveResult resourceType = PsiUtil.resolveGenericsClassInType(type);
        PsiClass resourceClass = resourceType.getElement();
        if (resourceClass == null) {
            return null;
        }

        PsiMethod[] methods = PsiUtil.getResourceCloserMethodsForType((PsiClassType)type);
        if (methods != null) {
            List<PsiClassType> ex = null;
            for (PsiMethod method : methods) {
                PsiClass closerClass = method.getContainingClass();
                if (closerClass != null) {
                    PsiSubstitutor substitutor =
                        TypeConversionUtil.getClassSubstitutor(closerClass, resourceClass, resourceType.getSubstitutor());
                    if (substitutor != null) {
                        PsiClassType[] exceptionTypes = method.getThrowsList().getReferencedTypes();
                        if (exceptionTypes.length == 0) {
                            return Collections.emptyList();
                        }

                        if (ex == null) {
                            ex = collectSubstituted(substitutor, exceptionTypes, scope);
                        }
                        else {
                            retainExceptions(ex, collectSubstituted(substitutor, exceptionTypes, scope));
                        }
                    }
                }
            }
            return ex;
        }

        return null;
    }

    @Nonnull
    @RequiredReadAction
    public static List<PsiClassType> getUnhandledExceptions(@Nonnull PsiThrowStatement throwStatement, @Nullable PsiElement topElement) {
        List<PsiClassType> unhandled = new SmartList<>();
        for (PsiType type : getPreciseThrowTypes(throwStatement.getException())) {
            List<PsiType> types =
                type instanceof PsiDisjunctionType disjunctionType ? disjunctionType.getDisjunctions() : Collections.singletonList(type);
            for (PsiType subType : types) {
                if (subType instanceof PsiClassType classType
                    && !isUncheckedException(classType)
                    && !isHandled(throwStatement, classType, topElement)) {
                    unhandled.add(classType);
                }
            }
        }
        return unhandled;
    }

    @Nonnull
    @RequiredReadAction
    private static List<PsiType> getPreciseThrowTypes(@Nullable PsiExpression expression) {
        expression = PsiUtil.skipParenthesizedExprDown(expression);
        if (expression instanceof PsiReferenceExpression refExpr) {
            PsiElement target = refExpr.resolve();
            if (target != null && PsiUtil.isCatchParameter(target)) {
                return ((PsiCatchSection)target.getParent()).getPreciseCatchTypes();
            }
        }

        if (expression != null) {
            PsiType type = expression.getType();
            if (type != null) {
                return Collections.singletonList(type);
            }
        }

        return Collections.emptyList();
    }

    @Nonnull
    public static List<PsiClassType> getUnhandledExceptions(
        @Nonnull PsiMethod method,
        PsiElement element,
        PsiElement topElement,
        @Nonnull PsiSubstitutor substitutor
    ) {
        if (isArrayClone(method, element)) {
            return Collections.emptyList();
        }
        PsiClassType[] referencedTypes = method.getThrowsList().getReferencedTypes();
        return getUnhandledExceptions(element, topElement, substitutor, referencedTypes);
    }

    private static List<PsiClassType> getUnhandledExceptions(
        PsiElement element,
        PsiElement topElement,
        PsiSubstitutor substitutor,
        PsiClassType[] referencedTypes
    ) {
        if (referencedTypes.length > 0) {
            List<PsiClassType> result = new ArrayList<>();

            for (PsiClassType referencedType : referencedTypes) {
                PsiType type = PsiClassImplUtil.correctType(
                    GenericsUtil.eliminateWildcards(substitutor.substitute(referencedType), false),
                    element.getResolveScope()
                );
                if (type instanceof PsiClassType exceptionClassType
                    && exceptionClassType.resolve() != null
                    && !isUncheckedException(exceptionClassType)
                    && !isHandled(element, exceptionClassType, topElement)) {

                    result.add(exceptionClassType);
                }
            }

            return result;
        }
        return Collections.emptyList();
    }

    private static boolean isArrayClone(@Nonnull PsiMethod method, PsiElement element) {
        if (!method.getName().equals(CLONE_METHOD_NAME)) {
            return false;
        }
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null || !CommonClassNames.JAVA_LANG_OBJECT.equals(containingClass.getQualifiedName())) {
            return false;
        }
        if (element instanceof PsiMethodReferenceExpression methodRef) {
            PsiExpression qualifierExpression = methodRef.getQualifierExpression();
            return qualifierExpression != null && qualifierExpression.getType() instanceof PsiArrayType;
        }
        if (!(element instanceof PsiMethodCallExpression methodCall)) {
            return false;
        }

        PsiExpression qualifierExpression = methodCall.getMethodExpression().getQualifierExpression();
        return qualifierExpression != null && qualifierExpression.getType() instanceof PsiArrayType;
    }

    public static boolean isUncheckedException(@Nonnull PsiClassType type) {
        return InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION)
            || InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_LANG_ERROR);
    }

    public static boolean isUncheckedException(@Nonnull PsiClass psiClass) {
        return InheritanceUtil.isInheritor(psiClass, CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION)
            || InheritanceUtil.isInheritor(psiClass, CommonClassNames.JAVA_LANG_ERROR);
    }

    public static boolean isUncheckedExceptionOrSuperclass(@Nonnull PsiClassType type) {
        return isGeneralExceptionType(type) || isUncheckedException(type);
    }

    public static boolean isGeneralExceptionType(@Nonnull PsiType type) {
        String canonicalText = type.getCanonicalText();
        return CommonClassNames.JAVA_LANG_THROWABLE.equals(canonicalText) || CommonClassNames.JAVA_LANG_EXCEPTION.equals(canonicalText);
    }

    public static boolean isHandled(@Nonnull PsiClassType exceptionType, @Nonnull PsiElement throwPlace) {
        return isHandled(throwPlace, exceptionType, throwPlace.getContainingFile());
    }

    private static boolean isHandled(@Nullable PsiElement element, @Nonnull PsiClassType exceptionType, PsiElement topElement) {
        if (element == null || element.getParent() == topElement || element.getParent() == null) {
            return false;
        }

        Project project = element.getProject();

        CustomExceptionHandler handler =
            project.getExtensionPoint(CustomExceptionHandler.class).findFirstSafe(it -> it.isHandled(element, exceptionType, topElement));
        if (handler != null) {
            return true;
        }

        PsiElement parent = element.getParent();

        if (parent instanceof PsiMethod method) {
            return isHandledByMethodThrowsClause(method, exceptionType);
        }
        else if (parent instanceof PsiClass psiClass) {
            // arguments to anon class constructor should be handled higher
            // like in void f() throws XXX { new AA(methodThrowingXXX()) { ... }; }
            return psiClass instanceof PsiAnonymousClass && isHandled(psiClass, exceptionType, topElement);
        }
        else if (parent instanceof PsiLambdaExpression lambda
            || parent instanceof PsiMethodReferenceExpression methodRef && element == methodRef.getReferenceNameElement()) {
            PsiType interfaceType = ((PsiFunctionalExpression)parent).getFunctionalInterfaceType();
            return isDeclaredBySAMMethod(exceptionType, interfaceType);
        }
        else if (parent instanceof PsiClassInitializer classInitializer) {
            if (classInitializer.isStatic()) {
                return false;
            }
            // anonymous class initializers can throw any exceptions
            if (!(parent.getParent() instanceof PsiAnonymousClass)) {
                // exception thrown from within class instance initializer must be handled in every class constructor
                // check each constructor throws exception or superclass (there must be at least one)
                PsiClass aClass = classInitializer.getContainingClass();
                return areAllConstructorsThrow(aClass, exceptionType);
            }
        }
        else if (parent instanceof PsiTryStatement tryStmt) {
            if (tryStmt.getTryBlock() == element && isCaught(tryStmt, exceptionType)) {
                return true;
            }
            if (tryStmt.getResourceList() == element && isCaught(tryStmt, exceptionType)) {
                return true;
            }
            PsiCodeBlock finallyBlock = tryStmt.getFinallyBlock();
            if (element instanceof PsiCatchSection && finallyBlock != null && blockCompletesAbruptly(finallyBlock)) {
                // exception swallowed
                return true;
            }
        }
        else if (parent instanceof JavaCodeFragment codeFragment) {
            JavaCodeFragment.ExceptionHandler exceptionHandler = codeFragment.getExceptionHandler();
            return exceptionHandler != null && exceptionHandler.isHandledException(exceptionType);
        }
        else if (PsiImplUtil.isInServerPage(parent) && parent instanceof PsiFile) {
            return true;
        }
        else if (parent instanceof PsiFile) {
            return false;
        }
        else if (parent instanceof PsiField field && field.getInitializer() == element) {
            PsiClass aClass = field.getContainingClass();
            if (aClass != null && !(aClass instanceof PsiAnonymousClass) && !field.isStatic()) {
                // exceptions thrown in field initializers should be thrown in all class constructors
                return areAllConstructorsThrow(aClass, exceptionType);
            }
        }
        else {
            for (ExtraExceptionHandler exceptionHandler : ExtraExceptionHandler.EP_NAME.getExtensionList()) {
                if (exceptionHandler.isHandled(exceptionType, element)) {
                    return true;
                }
            }
        }
        return isHandled(parent, exceptionType, topElement);
    }

    private static boolean isDeclaredBySAMMethod(@Nonnull PsiClassType exceptionType, @Nullable PsiType interfaceType) {
        if (interfaceType != null) {
            PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(interfaceType);
            PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(resolveResult);
            if (interfaceMethod != null) {
                return isHandledByMethodThrowsClause(
                    interfaceMethod,
                    exceptionType,
                    LambdaUtil.getSubstitutor(interfaceMethod, resolveResult)
                );
            }
        }
        return true;
    }

    private static boolean areAllConstructorsThrow(@Nullable PsiClass aClass, @Nonnull PsiClassType exceptionType) {
        if (aClass == null) {
            return false;
        }
        PsiMethod[] constructors = aClass.getConstructors();
        boolean thrown = constructors.length != 0;
        for (PsiMethod constructor : constructors) {
            if (!isHandledByMethodThrowsClause(constructor, exceptionType)) {
                thrown = false;
                break;
            }
        }
        return thrown;
    }

    private static boolean isCaught(@Nonnull PsiTryStatement tryStatement, @Nonnull PsiClassType exceptionType) {
        // if finally block completes abruptly, exception gets lost
        PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
        if (finallyBlock != null && blockCompletesAbruptly(finallyBlock)) {
            return true;
        }

        PsiParameter[] catchBlockParameters = tryStatement.getCatchBlockParameters();
        for (PsiParameter parameter : catchBlockParameters) {
            PsiType paramType = parameter.getType();
            if (paramType.isAssignableFrom(exceptionType)) {
                return true;
            }
        }

        return false;
    }

    private static boolean blockCompletesAbruptly(@Nonnull PsiCodeBlock finallyBlock) {
        try {
            ControlFlow flow = ControlFlowFactory.getInstance(finallyBlock.getProject())
                .getControlFlow(finallyBlock, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance(), false);
            int completionReasons = ControlFlowUtil.getCompletionReasons(flow, 0, flow.getSize());
            if (!BitUtil.isSet(completionReasons, ControlFlowUtil.NORMAL_COMPLETION_REASON)) {
                return true;
            }
        }
        catch (AnalysisCanceledException e) {
            return true;
        }
        return false;
    }

    private static boolean isHandledByMethodThrowsClause(@Nonnull PsiMethod method, @Nonnull PsiClassType exceptionType) {
        return isHandledByMethodThrowsClause(method, exceptionType, PsiSubstitutor.EMPTY);
    }

    private static boolean isHandledByMethodThrowsClause(
        @Nonnull PsiMethod method,
        @Nonnull PsiClassType exceptionType,
        PsiSubstitutor substitutor
    ) {
        PsiClassType[] referencedTypes = method.getThrowsList().getReferencedTypes();
        return isHandledBy(exceptionType, referencedTypes, substitutor);
    }

    public static boolean isHandledBy(@Nonnull PsiClassType exceptionType, @Nonnull PsiClassType[] referencedTypes) {
        return isHandledBy(exceptionType, referencedTypes, PsiSubstitutor.EMPTY);
    }

    public static boolean isHandledBy(
        @Nonnull PsiClassType exceptionType,
        @Nonnull PsiClassType[] referencedTypes,
        PsiSubstitutor substitutor
    ) {
        for (PsiClassType classType : referencedTypes) {
            PsiType psiType = substitutor.substitute(classType);
            if (psiType != null && psiType.isAssignableFrom(exceptionType)) {
                return true;
            }
        }
        return false;
    }

    public static void sortExceptionsByHierarchy(@Nonnull List<PsiClassType> exceptions) {
        if (exceptions.size() <= 1) {
            return;
        }
        sortExceptionsByHierarchy(exceptions.subList(1, exceptions.size()));
        for (int i = 0; i < exceptions.size() - 1; i++) {
            if (TypeConversionUtil.isAssignable(exceptions.get(i), exceptions.get(i + 1))) {
                Collections.swap(exceptions, i, i + 1);
            }
        }
    }
}
