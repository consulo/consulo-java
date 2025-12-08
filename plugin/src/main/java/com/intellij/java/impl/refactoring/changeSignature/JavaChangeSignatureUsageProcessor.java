/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.changeSignature;

import com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.java.impl.refactoring.util.CanonicalTypes;
import com.intellij.java.impl.refactoring.util.ConflictsUtil;
import com.intellij.java.impl.refactoring.util.FieldConflictsResolver;
import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.impl.refactoring.util.usageInfo.DefaultConstructorImplicitUsageInfo;
import com.intellij.java.impl.refactoring.util.usageInfo.NoConstructorClassUsageInfo;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.impl.codeInsight.ExceptionUtil;
import com.intellij.java.language.impl.psi.scope.processor.VariablesProcessor;
import com.intellij.java.language.impl.psi.scope.util.PsiScopesUtil;
import com.intellij.java.language.impl.refactoring.util.RefactoringChangeUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.codeStyle.VariableKind;
import com.intellij.java.language.psi.util.MethodSignatureUtil;
import com.intellij.java.language.psi.util.PsiTypesUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import com.intellij.java.language.util.VisibilityUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.java.impl.refactoring.changeSignature.ChangeSignatureUsageProcessorEx;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.refactoring.ResolveSnapshotProvider;
import consulo.language.editor.refactoring.changeSignature.ChangeInfo;
import consulo.language.editor.refactoring.changeSignature.DefaultValueChooser;
import consulo.language.editor.refactoring.changeSignature.ParameterInfo;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.rename.RenameUtil;
import consulo.language.editor.refactoring.ui.RefactoringUIUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiQualifiedReference;
import consulo.language.psi.PsiReference;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.usage.MoveRenameUsageInfo;
import consulo.usage.UsageInfo;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.MultiMap;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;

/**
 * @author Maxim.Medvedev
 */
@ExtensionImpl(id = "javaProcessor")
public class JavaChangeSignatureUsageProcessor implements ChangeSignatureUsageProcessorEx {
    private static final Logger LOG = Logger.getInstance(JavaChangeSignatureUsageProcessor.class);

    @RequiredReadAction
    private static boolean isJavaUsage(UsageInfo info) {
        PsiElement element = info.getElement();
        return element != null && element.getLanguage() == JavaLanguage.INSTANCE;
    }

    @Nonnull
    @Override
    public UsageInfo[] findUsages(@Nonnull ChangeInfo info) {
        if (info instanceof JavaChangeInfo javaChangeInfo) {
            return new JavaChangeSignatureUsageSearcher(javaChangeInfo).findUsages();
        }
        else {
            return UsageInfo.EMPTY_ARRAY;
        }
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public MultiMap<PsiElement, LocalizeValue> findConflicts(@Nonnull ChangeInfo info, SimpleReference<UsageInfo[]> refUsages) {
        if (info instanceof JavaChangeInfo javaChangeInfo) {
            return new ConflictSearcher(javaChangeInfo).findConflicts(refUsages);
        }
        else {
            return new MultiMap<>();
        }
    }

    @Override
    @RequiredWriteAction
    public boolean processUsage(
        @Nonnull ChangeInfo changeInfo,
        @Nonnull UsageInfo usage,
        boolean beforeMethodChange,
        @Nonnull UsageInfo[] usages
    ) {
        if (!isJavaUsage(usage)) {
            return false;
        }
        if (!(changeInfo instanceof JavaChangeInfo javaChangeInfo)) {
            return false;
        }

        if (beforeMethodChange) {
            if (usage instanceof CallerUsageInfo callerUsageInfo) {
                processCallerMethod(
                    javaChangeInfo,
                    callerUsageInfo.getMethod(),
                    null,
                    callerUsageInfo.isToInsertParameter(),
                    callerUsageInfo.isToInsertException()
                );
                return true;
            }
            else if (usage instanceof OverriderUsageInfo info) {
                PsiMethod method = info.getElement();
                PsiMethod baseMethod = info.getBaseMethod();
                if (info.isOriginalOverrider()) {
                    processPrimaryMethod(javaChangeInfo, method, baseMethod, false);
                }
                else {
                    processCallerMethod(javaChangeInfo, method, baseMethod, info.isToInsertArgs(), info.isToCatchExceptions());
                }
                return true;
            }
        }
        else {
            PsiElement element = usage.getElement();
            LOG.assertTrue(element != null);

            if (usage instanceof DefaultConstructorImplicitUsageInfo defConstructorUsage) {
                PsiMethod constructor = defConstructorUsage.getConstructor();
                if (!constructor.isPhysical()) {
                    boolean toPropagate = javaChangeInfo instanceof JavaChangeInfoImpl changeInfoImpl
                        && changeInfoImpl.propagateParametersMethods.remove(constructor);
                    PsiClass containingClass = defConstructorUsage.getContainingClass();
                    constructor = (PsiMethod) containingClass.add(constructor);
                    PsiUtil.setModifierProperty(constructor, VisibilityUtil.getVisibilityModifier(containingClass.getModifierList()), true);
                    if (toPropagate) {
                        ((JavaChangeInfoImpl) javaChangeInfo).propagateParametersMethods.add(constructor);
                    }
                }
                addSuperCall(javaChangeInfo, constructor, defConstructorUsage.getBaseConstructor(), usages);
                return true;
            }
            else if (usage instanceof NoConstructorClassUsageInfo noConstructorClassUsageInfo) {
                addDefaultConstructor(javaChangeInfo, noConstructorClassUsageInfo.getPsiClass(), usages);
                return true;
            }
            else if (usage instanceof MethodCallUsageInfo methodCallInfo) {
                processMethodUsage(
                    methodCallInfo.getElement(),
                    javaChangeInfo,
                    methodCallInfo.isToChangeArguments(),
                    methodCallInfo.isToCatchExceptions(),
                    methodCallInfo.getReferencedMethod(),
                    methodCallInfo.getSubstitutor(),
                    usages
                );
                return true;
            }
            else if (usage instanceof ChangeSignatureParameterUsageInfo parameterUsageInfo) {
                String newName = parameterUsageInfo.newParameterName;
                String oldName = parameterUsageInfo.oldParameterName;
                processParameterUsage((PsiReferenceExpression) element, oldName, newName);
                return true;
            }
            else if (usage instanceof CallReferenceUsageInfo callUsageInfo) {
                callUsageInfo.getReference().handleChangeSignature(javaChangeInfo);
                return true;
            }
            else if (element instanceof PsiEnumConstant enumConstant) {
                fixActualArgumentsList(
                    enumConstant.getArgumentList(),
                    javaChangeInfo,
                    true,
                    PsiSubstitutor.EMPTY
                );
                return true;
            }
            else if (!(usage instanceof OverriderUsageInfo)) {
                PsiReference reference = usage instanceof MoveRenameUsageInfo ? usage.getReference() : element.getReference();
                if (reference != null) {
                    PsiElement target = javaChangeInfo.getMethod();
                    if (target != null) {
                        reference.bindToElement(target);
                    }
                }
            }
        }
        return false;
    }

    private static void processParameterUsage(
        PsiReferenceExpression ref,
        String oldName,
        String newName
    ) throws IncorrectOperationException {
        PsiElement last = ref.getReferenceNameElement();
        if (last instanceof PsiIdentifier identifier && identifier.getText().equals(oldName)) {
            PsiElementFactory factory = JavaPsiFacade.getInstance(ref.getProject()).getElementFactory();
            PsiIdentifier newNameIdentifier = factory.createIdentifier(newName);
            last.replace(newNameIdentifier);
        }
    }

    @RequiredReadAction
    private static void addDefaultConstructor(
        JavaChangeInfo changeInfo,
        PsiClass aClass,
        UsageInfo[] usages
    ) throws IncorrectOperationException {
        if (!(aClass instanceof PsiAnonymousClass)) {
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(aClass.getProject());
            PsiMethod defaultConstructor = factory.createMethodFromText(aClass.getName() + "(){}", aClass);
            defaultConstructor = (PsiMethod) CodeStyleManager.getInstance(aClass.getProject()).reformat(defaultConstructor);
            defaultConstructor = (PsiMethod) aClass.add(defaultConstructor);
            PsiUtil.setModifierProperty(defaultConstructor, VisibilityUtil.getVisibilityModifier(aClass.getModifierList()), true);
            addSuperCall(changeInfo, defaultConstructor, null, usages);
        }
        else if (aClass.getParent() instanceof PsiNewExpression newExpr) {
            PsiClass baseClass = changeInfo.getMethod().getContainingClass();
            PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(baseClass, aClass, PsiSubstitutor.EMPTY);
            fixActualArgumentsList(newExpr.getArgumentList(), changeInfo, true, substitutor);
        }
    }

    @RequiredReadAction
    private static void addSuperCall(
        JavaChangeInfo changeInfo,
        PsiMethod constructor,
        PsiMethod callee,
        UsageInfo[] usages
    ) throws IncorrectOperationException {
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(constructor.getProject());
        PsiExpressionStatement superCall = (PsiExpressionStatement) factory.createStatementFromText("super();", constructor);
        PsiCodeBlock body = constructor.getBody();
        assert body != null;
        PsiStatement[] statements = body.getStatements();
        if (statements.length > 0) {
            superCall = (PsiExpressionStatement) body.addBefore(superCall, statements[0]);
        }
        else {
            superCall = (PsiExpressionStatement) body.add(superCall);
        }
        PsiMethodCallExpression callExpression = (PsiMethodCallExpression) superCall.getExpression();
        PsiClass aClass = constructor.getContainingClass();
        PsiClass baseClass = changeInfo.getMethod().getContainingClass();
        PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(baseClass, aClass, PsiSubstitutor.EMPTY);
        processMethodUsage(callExpression.getMethodExpression(), changeInfo, true, false, callee, substitutor, usages);
    }

    @RequiredReadAction
    private static void processMethodUsage(
        PsiElement ref,
        JavaChangeInfo changeInfo,
        boolean toChangeArguments,
        boolean toCatchExceptions,
        PsiMethod callee,
        PsiSubstitutor substitutor,
        UsageInfo[] usages
    ) throws IncorrectOperationException {
        if (changeInfo.isNameChanged()
            && ref instanceof PsiJavaCodeReferenceElement javaCodeRef
            && javaCodeRef.getReferenceNameElement() instanceof PsiIdentifier identifier
            && identifier.getText().equals(changeInfo.getOldName())) {
            identifier.replace(changeInfo.getNewNameIdentifier());
        }

        PsiMethod caller = RefactoringUtil.getEnclosingMethod(ref);
        if (toChangeArguments) {
            PsiExpressionList list = RefactoringUtil.getArgumentListByMethodReference(ref);
            LOG.assertTrue(list != null);
            boolean toInsertDefaultValue = needDefaultValue(changeInfo, caller);
            if (toInsertDefaultValue && ref instanceof PsiReferenceExpression refExpr) {
                PsiExpression qualifierExpression = refExpr.getQualifierExpression();
                if (qualifierExpression instanceof PsiSuperExpression && callerSignatureIsAboutToChangeToo(caller, usages)) {
                    toInsertDefaultValue = false;
                }
            }

            fixActualArgumentsList(list, changeInfo, toInsertDefaultValue, substitutor);
        }

        if (toCatchExceptions) {
            if (!(ref instanceof PsiReferenceExpression
                && JavaHighlightUtil.isSuperOrThisCall(PsiTreeUtil.getParentOfType(ref, PsiStatement.class), true, false))) {
                if (needToCatchExceptions(changeInfo, caller)) {
                    PsiClassType[] newExceptions =
                        callee != null ? getCalleeChangedExceptionInfo(callee) : getPrimaryChangedExceptionInfo(changeInfo);
                    fixExceptions(ref, newExceptions);
                }
            }
        }
    }

    private static boolean callerSignatureIsAboutToChangeToo(PsiMethod caller, UsageInfo[] usages) {
        for (UsageInfo usage : usages) {
            if (usage instanceof MethodCallUsageInfo callUsageInfo
                && MethodSignatureUtil.isSuperMethod(callUsageInfo.getReferencedMethod(), caller)) {
                return true;
            }
        }
        return false;
    }

    private static PsiClassType[] getCalleeChangedExceptionInfo(PsiMethod callee) {
        return callee.getThrowsList().getReferencedTypes(); //Callee method's throws list is already modified!
    }

    private static void fixExceptions(PsiElement ref, PsiClassType[] newExceptions) throws IncorrectOperationException {
        //methods' throws lists are already modified, may use ExceptionUtil.collectUnhandledExceptions
        newExceptions = filterCheckedExceptions(newExceptions);

        if (PsiTreeUtil.getParentOfType(ref, PsiTryStatement.class, PsiMethod.class) instanceof PsiTryStatement tryStmt) {
            PsiCodeBlock tryBlock = tryStmt.getTryBlock();

            //Remove unused catches
            Collection<PsiClassType> classes = ExceptionUtil.collectUnhandledExceptions(tryBlock, tryBlock);
            PsiParameter[] catchParameters = tryStmt.getCatchBlockParameters();
            for (PsiParameter parameter : catchParameters) {
                PsiType caughtType = parameter.getType();

                if (!(caughtType instanceof PsiClassType)) {
                    continue;
                }
                if (ExceptionUtil.isUncheckedExceptionOrSuperclass((PsiClassType) caughtType)) {
                    continue;
                }

                if (!isCatchParameterRedundant((PsiClassType) caughtType, classes)) {
                    continue;
                }
                parameter.getParent().delete(); //delete catch section
            }

            PsiClassType[] exceptionsToAdd = filterUnhandledExceptions(newExceptions, tryBlock);
            addExceptions(exceptionsToAdd, tryStmt);

            adjustPossibleEmptyTryStatement(tryStmt);
        }
        else {
            newExceptions = filterUnhandledExceptions(newExceptions, ref);
            if (newExceptions.length > 0) {
                //Add new try statement
                PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(ref.getProject());
                PsiTryStatement tryStatement =
                    (PsiTryStatement) elementFactory.createStatementFromText("try {} catch (Exception e) {}", null);
                PsiStatement anchor = PsiTreeUtil.getParentOfType(ref, PsiStatement.class);
                LOG.assertTrue(anchor != null);
                tryStatement.getTryBlock().add(anchor);
                tryStatement = (PsiTryStatement) anchor.getParent().addAfter(tryStatement, anchor);

                addExceptions(newExceptions, tryStatement);
                anchor.delete();
                tryStatement.getCatchSections()[0].delete(); //Delete dummy catch section
            }
        }
    }

    private static PsiClassType[] filterCheckedExceptions(PsiClassType[] exceptions) {
        List<PsiClassType> result = new ArrayList<PsiClassType>();
        for (PsiClassType exceptionType : exceptions) {
            if (!ExceptionUtil.isUncheckedException(exceptionType)) {
                result.add(exceptionType);
            }
        }
        return result.toArray(new PsiClassType[result.size()]);
    }

    private static void adjustPossibleEmptyTryStatement(PsiTryStatement tryStatement) throws IncorrectOperationException {
        PsiCodeBlock tryBlock = tryStatement.getTryBlock();
        if (tryBlock != null) {
            if (tryStatement.getCatchSections().length == 0 && tryStatement.getFinallyBlock() == null) {
                PsiElement firstBodyElement = tryBlock.getFirstBodyElement();
                if (firstBodyElement != null) {
                    tryStatement.getParent().addRangeAfter(firstBodyElement, tryBlock.getLastBodyElement(), tryStatement);
                }
                tryStatement.delete();
            }
        }
    }

    private static void addExceptions(PsiClassType[] exceptionsToAdd, PsiTryStatement tryStatement) throws IncorrectOperationException {
        for (PsiClassType type : exceptionsToAdd) {
            JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(tryStatement.getProject());
            String name = styleManager.suggestVariableName(VariableKind.PARAMETER, null, null, type).names[0];
            name = styleManager.suggestUniqueVariableName(name, tryStatement, false);

            PsiCatchSection catchSection =
                JavaPsiFacade.getInstance(tryStatement.getProject()).getElementFactory().createCatchSection(type, name, tryStatement);
            tryStatement.add(catchSection);
        }
    }

    private static PsiClassType[] filterUnhandledExceptions(PsiClassType[] exceptions, PsiElement place) {
        List<PsiClassType> result = new ArrayList<>();
        for (PsiClassType exception : exceptions) {
            if (!ExceptionUtil.isHandled(exception, place)) {
                result.add(exception);
            }
        }
        return result.toArray(new PsiClassType[result.size()]);
    }

    private static boolean isCatchParameterRedundant(PsiClassType catchParamType, Collection<PsiClassType> thrownTypes) {
        for (PsiType exceptionType : thrownTypes) {
            if (exceptionType.isConvertibleFrom(catchParamType)) {
                return false;
            }
        }
        return true;
    }

    //This methods works equally well for primary usages as well as for propagated callers' usages
    @RequiredUIAccess
    private static void fixActualArgumentsList(
        PsiExpressionList list,
        JavaChangeInfo changeInfo,
        boolean toInsertDefaultValue,
        PsiSubstitutor substitutor
    ) throws IncorrectOperationException {
        PsiElementFactory factory = JavaPsiFacade.getInstance(list.getProject()).getElementFactory();
        if (changeInfo.isParameterSetOrOrderChanged()) {
            if (changeInfo instanceof JavaChangeInfoImpl changeInfoImpl && changeInfoImpl.isPropagationEnabled) {
                ParameterInfoImpl[] createdParamsInfo = changeInfoImpl.getCreatedParmsInfoWithoutVarargs();
                for (ParameterInfoImpl info : createdParamsInfo) {
                    PsiExpression newArg;
                    if (toInsertDefaultValue) {
                        newArg = createDefaultValue(changeInfoImpl, factory, info, list);
                    }
                    else {
                        newArg = factory.createExpressionFromText(info.getName(), list);
                    }
                    if (newArg != null) {
                        JavaCodeStyleManager.getInstance(list.getProject()).shortenClassReferences(list.add(newArg));
                    }
                }
            }
            else {
                PsiExpression[] args = list.getExpressions();
                int nonVarargCount = getNonVarargCount(changeInfo, args);
                int varargCount = args.length - nonVarargCount;
                if (varargCount < 0) {
                    return;
                }
                PsiExpression[] newVarargInitializers = null;

                int newArgsLength;
                int newNonVarargCount;
                JavaParameterInfo[] newParams = changeInfo.getNewParameters();
                if (changeInfo.isArrayToVarargs()) {
                    newNonVarargCount = newParams.length - 1;
                    JavaParameterInfo lastNewParam = newParams[newParams.length - 1];
                    PsiExpression arrayToConvert = args[lastNewParam.getOldIndex()];
                    if (arrayToConvert instanceof PsiNewExpression newExpr) {
                        PsiArrayInitializerExpression arrayInitializer = newExpr.getArrayInitializer();
                        if (arrayInitializer != null) {
                            newVarargInitializers = arrayInitializer.getInitializers();
                        }
                    }
                    newArgsLength = newVarargInitializers == null ? newParams.length : newNonVarargCount + newVarargInitializers.length;
                }
                else if (changeInfo.isRetainsVarargs()) {
                    newNonVarargCount = newParams.length - 1;
                    newArgsLength = newNonVarargCount + varargCount;
                }
                else if (changeInfo.isObtainsVarargs()) {
                    newNonVarargCount = newParams.length - 1;
                    newArgsLength = newNonVarargCount;
                }
                else {
                    newNonVarargCount = newParams.length;
                    newArgsLength = newParams.length;
                }

                String[] oldVarargs = null;
                if (changeInfo.wasVararg() && !changeInfo.isRetainsVarargs()) {
                    oldVarargs = new String[varargCount];
                    for (int i = nonVarargCount; i < args.length; i++) {
                        oldVarargs[i - nonVarargCount] = args[i].getText();
                    }
                }

                PsiExpression[] newArgs = new PsiExpression[newArgsLength];
                for (int i = 0; i < newNonVarargCount; i++) {
                    if (newParams[i].getOldIndex() == nonVarargCount && oldVarargs != null) {
                        PsiType type = newParams[i].createType(changeInfo.getMethod(), list.getManager());
                        if (type instanceof PsiArrayType) {
                            type = substitutor.substitute(type);
                            type = TypeConversionUtil.erasure(type);
                            String typeText = type.getCanonicalText();
                            if (type instanceof PsiEllipsisType) {
                                typeText = typeText.replace("...", "[]");
                            }
                            String text = "new " + typeText + "{" + StringUtil.join(oldVarargs, ",") + "}";
                            newArgs[i] = factory.createExpressionFromText(text, changeInfo.getMethod());
                            continue;
                        }
                    }
                    newArgs[i] = createActualArgument(changeInfo, list, newParams[i], toInsertDefaultValue, args);
                }
                if (changeInfo.isArrayToVarargs()) {
                    if (newVarargInitializers == null) {
                        newArgs[newNonVarargCount] =
                            createActualArgument(changeInfo, list, newParams[newNonVarargCount], toInsertDefaultValue, args);
                    }
                    else {
                        System.arraycopy(newVarargInitializers, 0, newArgs, newNonVarargCount, newVarargInitializers.length);
                    }
                }
                else {
                    int newVarargCount = newArgsLength - newNonVarargCount;
                    LOG.assertTrue(newVarargCount == 0 || newVarargCount == varargCount);
                    for (int i = newNonVarargCount; i < newArgsLength; i++) {
                        int oldIndex = newParams[newNonVarargCount].getOldIndex();
                        if (oldIndex >= 0 && oldIndex != nonVarargCount) {
                            newArgs[i] = createActualArgument(changeInfo, list, newParams[newNonVarargCount], toInsertDefaultValue, args);
                        }
                        else {
                            System.arraycopy(args, nonVarargCount, newArgs, newNonVarargCount, newVarargCount);
                            break;
                        }
                    }
                }
                ChangeSignatureUtil.synchronizeList(list, Arrays.asList(newArgs), ExpressionList.INSTANCE, changeInfo.toRemoveParm());
            }
        }
    }

    private static int getNonVarargCount(JavaChangeInfo changeInfo, PsiExpression[] args) {
        if (!changeInfo.wasVararg()) {
            return args.length;
        }
        return changeInfo.getOldParameterTypes().length - 1;
    }


    @Nullable
    @RequiredReadAction
    private static PsiExpression createActualArgument(
        JavaChangeInfo changeInfo,
        PsiExpressionList list,
        JavaParameterInfo info,
        boolean toInsertDefaultValue,
        PsiExpression[] args
    ) throws IncorrectOperationException {
        PsiElementFactory factory = JavaPsiFacade.getInstance(list.getProject()).getElementFactory();
        int index = info.getOldIndex();
        if (index >= 0) {
            return args[index];
        }
        else if (toInsertDefaultValue) {
            return createDefaultValue(changeInfo, factory, info, list);
        }
        else {
            return factory.createExpressionFromText(info.getName(), list);
        }
    }

    @Nullable
    @RequiredReadAction
    private static PsiExpression createDefaultValue(
        JavaChangeInfo changeInfo,
        PsiElementFactory factory,
        JavaParameterInfo info,
        PsiExpressionList list
    ) throws IncorrectOperationException {
        if (info.isUseAnySingleVariable()) {
            PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(list.getProject()).getResolveHelper();
            PsiType type = info.getTypeWrapper().getType(changeInfo.getMethod(), list.getManager());
            VariablesProcessor processor = new VariablesProcessor(false) {
                @Override
                @RequiredReadAction
                protected boolean check(PsiVariable var, ResolveState state) {
                    if (var instanceof PsiField field && !resolveHelper.isAccessible(field, list, null)) {
                        return false;
                    }
                    if (var instanceof PsiLocalVariable && list.getTextRange().getStartOffset() <= var.getTextRange().getStartOffset()) {
                        return false;
                    }
                    if (PsiTreeUtil.isAncestor(var, list, false)) {
                        return false;
                    }
                    PsiType varType = state.get(PsiSubstitutor.KEY).substitute(var.getType());
                    return type.isAssignableFrom(varType);
                }

                @Override
                public boolean execute(@Nonnull PsiElement pe, ResolveState state) {
                    super.execute(pe, state);
                    return size() < 2;
                }
            };
            PsiScopesUtil.treeWalkUp(processor, list, null);
            if (processor.size() == 1) {
                PsiVariable result = processor.getResult(0);
                return factory.createExpressionFromText(result.getName(), list);
            }
            if (processor.size() == 0) {
                PsiClass parentClass = PsiTreeUtil.getParentOfType(list, PsiClass.class);
                if (parentClass != null) {
                    PsiClass containingClass = parentClass;
                    Set<PsiClass> containingClasses = new HashSet<>();
                    while (containingClass != null) {
                        if (type.isAssignableFrom(factory.createType(containingClass, PsiSubstitutor.EMPTY))) {
                            containingClasses.add(containingClass);
                        }
                        containingClass = PsiTreeUtil.getParentOfType(containingClass, PsiClass.class);
                    }
                    if (containingClasses.size() == 1) {
                        return RefactoringChangeUtil.createThisExpression(
                            parentClass.getManager(),
                            containingClasses.contains(parentClass) ? null : containingClasses.iterator().next()
                        );
                    }
                }
            }
        }
        PsiCallExpression callExpression = PsiTreeUtil.getParentOfType(list, PsiCallExpression.class);
        String defaultValue = info.getDefaultValue();
        return callExpression != null
            ? info.getValue(callExpression)
            : defaultValue.length() > 0
            ? factory.createExpressionFromText(defaultValue, list)
            : null;
    }


    @Override
    @RequiredWriteAction
    public boolean processPrimaryMethod(ChangeInfo changeInfo) {
        if (!JavaLanguage.INSTANCE.equals(changeInfo.getLanguage()) || !(changeInfo instanceof JavaChangeInfo)) {
            return false;
        }
        PsiElement element = changeInfo.getMethod();
        LOG.assertTrue(element instanceof PsiMethod);
        if (changeInfo.isGenerateDelegate()) {
            generateDelegate((JavaChangeInfo) changeInfo);
        }
        processPrimaryMethod((JavaChangeInfo) changeInfo, (PsiMethod) element, null, true);
        return true;
    }

    @Override
    public boolean shouldPreviewUsages(@Nonnull ChangeInfo changeInfo, @Nonnull UsageInfo[] usages) {
        return false;
    }

    @Override
    @RequiredUIAccess
    public boolean setupDefaultValues(ChangeInfo changeInfo, SimpleReference<UsageInfo[]> refUsages, Project project) {
        if (!(changeInfo instanceof JavaChangeInfo)) {
            return false;
        }
        for (UsageInfo usageInfo : refUsages.get()) {
            if (usageInfo instanceof MethodCallUsageInfo methodCallUsageInfo && methodCallUsageInfo.isToChangeArguments()) {
                PsiElement element = methodCallUsageInfo.getElement();
                if (element == null) {
                    continue;
                }
                PsiMethod caller = RefactoringUtil.getEnclosingMethod(element);
                boolean needDefaultValue = needDefaultValue(changeInfo, caller);
                if (needDefaultValue
                    && (caller == null || !MethodSignatureUtil.isSuperMethod(methodCallUsageInfo.getReferencedMethod(), caller))) {
                    ParameterInfo[] parameters = changeInfo.getNewParameters();
                    for (ParameterInfo parameter : parameters) {
                        String defaultValue = parameter.getDefaultValue();
                        if (defaultValue == null && parameter.getOldIndex() == -1) {
                            ((ParameterInfoImpl) parameter).setDefaultValue("");
                            if (!Application.get().isUnitTestMode()) {
                                PsiType type = ((ParameterInfoImpl) parameter).getTypeWrapper().getType(element, element.getManager());
                                DefaultValueChooser chooser =
                                    new DefaultValueChooser(project, parameter.getName(), PsiTypesUtil.getDefaultValueOfType(type));
                                chooser.show();
                                if (chooser.isOK()) {
                                    if (chooser.feelLucky()) {
                                        parameter.setUseAnySingleVariable(true);
                                    }
                                    else {
                                        ((ParameterInfoImpl) parameter).setDefaultValue(chooser.getDefaultValue());
                                    }
                                }
                                else {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    @Override
    public void registerConflictResolvers(
        List<ResolveSnapshotProvider.ResolveSnapshot> snapshots,
        @Nonnull ResolveSnapshotProvider resolveSnapshotProvider,
        UsageInfo[] usages,
        ChangeInfo changeInfo
    ) {
        snapshots.add(resolveSnapshotProvider.createSnapshot(changeInfo.getMethod()));
        for (UsageInfo usage : usages) {
            if (usage instanceof OverriderUsageInfo) {
                snapshots.add(resolveSnapshotProvider.createSnapshot(usage.getElement()));
            }
        }
    }

    private static boolean needDefaultValue(ChangeInfo changeInfo, PsiMethod method) {
        return !(changeInfo instanceof JavaChangeInfoImpl changeInfoImpl && changeInfoImpl.propagateParametersMethods.contains(method));
    }

    public static void generateDelegate(JavaChangeInfo changeInfo) throws IncorrectOperationException {
        PsiMethod delegate = generateDelegatePrototype(changeInfo);
        PsiClass targetClass = changeInfo.getMethod().getContainingClass();
        LOG.assertTrue(targetClass != null);
        targetClass.addBefore(delegate, changeInfo.getMethod());
    }

    public static PsiMethod generateDelegatePrototype(JavaChangeInfo changeInfo) {
        PsiMethod delegate = (PsiMethod) changeInfo.getMethod().copy();
        PsiClass targetClass = changeInfo.getMethod().getContainingClass();
        LOG.assertTrue(targetClass != null);
        if (targetClass.isInterface() && delegate.getBody() == null) {
            delegate.getModifierList().setModifierProperty(PsiModifier.DEFAULT, true);
        }
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(targetClass.getProject());
        ChangeSignatureProcessor.makeEmptyBody(factory, delegate);
        PsiCallExpression callExpression = ChangeSignatureProcessor.addDelegatingCallTemplate(delegate, changeInfo.getNewName());
        addDelegateArguments(changeInfo, factory, callExpression);
        return delegate;
    }

    private static void addDelegateArguments(
        JavaChangeInfo changeInfo,
        PsiElementFactory factory,
        PsiCallExpression callExpression
    ) throws IncorrectOperationException {
        JavaParameterInfo[] newParams = changeInfo.getNewParameters();
        String[] oldParameterNames = changeInfo.getOldParameterNames();
        for (int i = 0; i < newParams.length; i++) {
            JavaParameterInfo newParam = newParams[i];
            PsiExpression actualArg;
            if (newParam.getOldIndex() >= 0) {
                actualArg = factory.createExpressionFromText(oldParameterNames[newParam.getOldIndex()], callExpression);
            }
            else {
                actualArg = changeInfo.getValue(i, callExpression);
            }
            PsiExpressionList argumentList = callExpression.getArgumentList();
            if (actualArg != null && argumentList != null) {
                JavaCodeStyleManager.getInstance(callExpression.getProject()).shortenClassReferences(argumentList.add(actualArg));
            }
        }
    }

    @RequiredWriteAction
    private static void processPrimaryMethod(
        JavaChangeInfo changeInfo,
        PsiMethod method,
        PsiMethod baseMethod,
        boolean isOriginal
    ) throws IncorrectOperationException {
        PsiElementFactory factory = JavaPsiFacade.getInstance(method.getProject()).getElementFactory();

        if (changeInfo.isVisibilityChanged()) {
            PsiModifierList modifierList = method.getModifierList();
            String highestVisibility = isOriginal
                ? changeInfo.getNewVisibility()
                : VisibilityUtil.getHighestVisibility(changeInfo.getNewVisibility(), VisibilityUtil.getVisibilityModifier(modifierList));
            VisibilityUtil.setVisibility(modifierList, highestVisibility);
        }

        if (changeInfo.isNameChanged()) {
            String newName = baseMethod == null
                ? changeInfo.getNewName()
                : RefactoringUtil.suggestNewOverriderName(method.getName(), baseMethod.getName(), changeInfo.getNewName());

            if (newName != null && !newName.equals(method.getName())) {
                PsiIdentifier nameId = method.getNameIdentifier();
                assert nameId != null;
                nameId.replace(JavaPsiFacade.getInstance(method.getProject()).getElementFactory().createIdentifier(newName));
            }
        }

        PsiSubstitutor substitutor =
            baseMethod == null ? PsiSubstitutor.EMPTY : ChangeSignatureProcessor.calculateSubstitutor(method, baseMethod);

        if (changeInfo.isReturnTypeChanged()) {
            PsiType newTypeElement = changeInfo.getNewReturnType().getType(changeInfo.getMethod().getParameterList(), method.getManager());
            PsiType returnType = substitutor.substitute(newTypeElement);
            // don't modify return type for non-Java overriders (EJB)
            if (method.getName().equals(changeInfo.getNewName())) {
                PsiTypeElement typeElement = method.getReturnTypeElement();
                if (typeElement != null) {
                    typeElement.replace(factory.createTypeElement(returnType));
                }
            }
        }

        PsiParameterList list = method.getParameterList();
        PsiParameter[] parameters = list.getParameters();

        JavaParameterInfo[] parameterInfos = changeInfo.getNewParameters();
        int delta =
            baseMethod != null ? baseMethod.getParameterList().getParametersCount() - method.getParameterList().getParametersCount() : 0;
        PsiParameter[] newParams = new PsiParameter[Math.max(parameterInfos.length - delta, 0)];
        String[] oldParameterNames = changeInfo.getOldParameterNames();
        String[] oldParameterTypes = changeInfo.getOldParameterTypes();
        for (int i = 0; i < newParams.length; i++) {
            JavaParameterInfo info = parameterInfos[i];
            int index = info.getOldIndex();
            if (index >= 0) {
                PsiParameter parameter = parameters[index];
                newParams[i] = parameter;

                String oldName = oldParameterNames[index];
                if (!oldName.equals(info.getName()) && oldName.equals(parameter.getName())) {
                    PsiIdentifier newIdentifier = factory.createIdentifier(info.getName());
                    parameter.getNameIdentifier().replace(newIdentifier);
                }

                String oldType = oldParameterTypes[index];
                if (!oldType.equals(info.getTypeText())) {
                    parameter.normalizeDeclaration();
                    PsiType newType =
                        substitutor.substitute(info.createType(changeInfo.getMethod().getParameterList(), method.getManager()));

                    parameter.getTypeElement().replace(factory.createTypeElement(newType));
                }
            }
            else {
                newParams[i] = createNewParameter(changeInfo, info, substitutor);
            }
        }

        resolveParameterVsFieldsConflicts(newParams, method, list, changeInfo.toRemoveParm());
        fixJavadocsForChangedMethod(method, changeInfo, newParams.length);
        if (changeInfo.isExceptionSetOrOrderChanged()) {
            PsiClassType[] newExceptions = getPrimaryChangedExceptionInfo(changeInfo);
            fixPrimaryThrowsLists(method, newExceptions);
        }
    }

    private static PsiClassType[] getPrimaryChangedExceptionInfo(JavaChangeInfo changeInfo) throws IncorrectOperationException {
        ThrownExceptionInfo[] newExceptionInfos = changeInfo.getNewExceptions();
        PsiClassType[] newExceptions = new PsiClassType[newExceptionInfos.length];
        PsiMethod method = changeInfo.getMethod();
        for (int i = 0; i < newExceptions.length; i++) {
            newExceptions[i] =
                (PsiClassType) newExceptionInfos[i].createType(method, method.getManager()); //context really does not matter here
        }
        return newExceptions;
    }

    private static void processCallerMethod(
        JavaChangeInfo changeInfo,
        PsiMethod caller,
        PsiMethod baseMethod,
        boolean toInsertParams,
        boolean toInsertThrows
    ) throws IncorrectOperationException {
        LOG.assertTrue(toInsertParams || toInsertThrows);
        if (toInsertParams) {
            List<PsiParameter> newParameters = new ArrayList<>();
            ContainerUtil.addAll(newParameters, caller.getParameterList().getParameters());
            JavaParameterInfo[] primaryNewParams = changeInfo.getNewParameters();
            PsiSubstitutor substitutor =
                baseMethod == null ? PsiSubstitutor.EMPTY : ChangeSignatureProcessor.calculateSubstitutor(caller, baseMethod);
            PsiClass aClass = changeInfo.getMethod().getContainingClass();
            PsiClass callerContainingClass = caller.getContainingClass();
            PsiSubstitutor psiSubstitutor = aClass != null && callerContainingClass != null && callerContainingClass.isInheritor(
                aClass,
                true
            ) ? TypeConversionUtil.getSuperClassSubstitutor
                (aClass, callerContainingClass, substitutor) : PsiSubstitutor.EMPTY;
            for (JavaParameterInfo info : primaryNewParams) {
                if (info.getOldIndex() < 0) {
                    newParameters.add(createNewParameter(changeInfo, info, psiSubstitutor, substitutor));
                }
            }
            PsiParameter[] arrayed = newParameters.toArray(new PsiParameter[newParameters.size()]);
            boolean[] toRemoveParam = new boolean[arrayed.length];
            Arrays.fill(toRemoveParam, false);
            resolveParameterVsFieldsConflicts(arrayed, caller, caller.getParameterList(), toRemoveParam);
        }

        if (toInsertThrows) {
            List<PsiJavaCodeReferenceElement> newThrowns = new ArrayList<>();
            PsiReferenceList throwsList = caller.getThrowsList();
            ContainerUtil.addAll(newThrowns, throwsList.getReferenceElements());
            ThrownExceptionInfo[] primaryNewExns = changeInfo.getNewExceptions();
            for (ThrownExceptionInfo thrownExceptionInfo : primaryNewExns) {
                if (thrownExceptionInfo.getOldIndex() < 0) {
                    PsiClassType type = (PsiClassType) thrownExceptionInfo.createType(caller, caller.getManager());
                    PsiJavaCodeReferenceElement ref =
                        JavaPsiFacade.getInstance(caller.getProject()).getElementFactory().createReferenceElementByType(type);
                    newThrowns.add(ref);
                }
            }
            PsiJavaCodeReferenceElement[] arrayed = newThrowns.toArray(new PsiJavaCodeReferenceElement[newThrowns.size()]);
            boolean[] toRemoveParam = new boolean[arrayed.length];
            Arrays.fill(toRemoveParam, false);
            ChangeSignatureUtil.synchronizeList(throwsList, Arrays.asList(arrayed), ThrowsList.INSTANCE, toRemoveParam);
        }
    }

    @RequiredReadAction
    private static void fixPrimaryThrowsLists(PsiMethod method, PsiClassType[] newExceptions) throws IncorrectOperationException {
        PsiElementFactory elementFactory = JavaPsiFacade.getInstance(method.getProject()).getElementFactory();
        PsiJavaCodeReferenceElement[] refs = new PsiJavaCodeReferenceElement[newExceptions.length];
        for (int i = 0; i < refs.length; i++) {
            refs[i] = elementFactory.createReferenceElementByType(newExceptions[i]);
        }
        PsiReferenceList throwsList = elementFactory.createReferenceList(refs);

        PsiReferenceList methodThrowsList = (PsiReferenceList) method.getThrowsList().replace(throwsList);
        methodThrowsList =
            (PsiReferenceList) JavaCodeStyleManager.getInstance(method.getProject()).shortenClassReferences(methodThrowsList);
        CodeStyleManager.getInstance(method.getManager().getProject())
            .reformatRange(method, method.getParameterList().getTextRange().getEndOffset(), methodThrowsList.getTextRange().getEndOffset());
    }

    private static void fixJavadocsForChangedMethod(
        PsiMethod method,
        JavaChangeInfo changeInfo,
        int newParamsLength
    ) throws IncorrectOperationException {
        PsiParameter[] parameters = method.getParameterList().getParameters();
        JavaParameterInfo[] newParams = changeInfo.getNewParameters();
        LOG.assertTrue(parameters.length <= newParamsLength);
        Set<PsiParameter> newParameters = new HashSet<>();
        String[] oldParameterNames = changeInfo.getOldParameterNames();
        for (int i = 0; i < newParamsLength; i++) {
            JavaParameterInfo newParam = newParams[i];
            if (newParam.getOldIndex() < 0
                || newParam.getOldIndex() == i && !(newParam.getName().equals(oldParameterNames[newParam.getOldIndex()])
                && newParam.getTypeText().equals(changeInfo.getOldParameterTypes()[newParam.getOldIndex()]))) {
                newParameters.add(parameters[i]);
            }
        }
        RefactoringUtil.fixJavadocsForParams(
            method,
            newParameters,
            pair -> {
                PsiParameter parameter = pair.first;
                String oldParamName = pair.second;
                int idx = Arrays.binarySearch(oldParameterNames, oldParamName);
                return idx >= 0 && idx == method.getParameterList().getParameterIndex(parameter)
                    && changeInfo.getNewParameters()[idx].getOldIndex() == idx;
            }
        );
    }

    private static PsiParameter createNewParameter(
        JavaChangeInfo changeInfo,
        JavaParameterInfo newParam,
        PsiSubstitutor... substitutor
    ) throws IncorrectOperationException {
        PsiParameterList list = changeInfo.getMethod().getParameterList();
        PsiElementFactory factory = JavaPsiFacade.getInstance(list.getProject()).getElementFactory();
        PsiType type = newParam.createType(list, list.getManager());
        for (PsiSubstitutor psiSubstitutor : substitutor) {
            type = psiSubstitutor.substitute(type);
        }
        return factory.createParameter(newParam.getName(), type);
    }

    private static void resolveParameterVsFieldsConflicts(
        PsiParameter[] newParams,
        PsiMethod method,
        PsiParameterList list,
        boolean[] toRemoveParam
    ) throws
        IncorrectOperationException {
        List<FieldConflictsResolver> conflictResolvers = new ArrayList<>();
        for (PsiParameter parameter : newParams) {
            conflictResolvers.add(new FieldConflictsResolver(parameter.getName(), method.getBody()));
        }
        ChangeSignatureUtil.synchronizeList(list, Arrays.asList(newParams), ParameterList.INSTANCE, toRemoveParam);
        JavaCodeStyleManager.getInstance(list.getProject()).shortenClassReferences(list);
        for (FieldConflictsResolver fieldConflictsResolver : conflictResolvers) {
            fieldConflictsResolver.fix();
        }
    }

    private static boolean needToCatchExceptions(JavaChangeInfo changeInfo, PsiMethod caller) {
        return changeInfo.isExceptionSetOrOrderChanged()
            && !(changeInfo instanceof JavaChangeInfoImpl changInfoImpl && changInfoImpl.propagateExceptionsMethods.contains(caller));
    }

    private static class ParameterList implements ChangeSignatureUtil.ChildrenGenerator<PsiParameterList, PsiParameter> {
        public static final ParameterList INSTANCE = new ParameterList();

        @Override
        public List<PsiParameter> getChildren(PsiParameterList psiParameterList) {
            return Arrays.asList(psiParameterList.getParameters());
        }
    }

    private static class ThrowsList implements ChangeSignatureUtil.ChildrenGenerator<PsiReferenceList, PsiJavaCodeReferenceElement> {
        public static final ThrowsList INSTANCE = new ThrowsList();

        @Override
        public List<PsiJavaCodeReferenceElement> getChildren(PsiReferenceList throwsList) {
            return Arrays.asList(throwsList.getReferenceElements());
        }
    }

    private static class ConflictSearcher {
        private final JavaChangeInfo myChangeInfo;

        private ConflictSearcher(JavaChangeInfo changeInfo) {
            this.myChangeInfo = changeInfo;
        }

        @RequiredReadAction
        public MultiMap<PsiElement, LocalizeValue> findConflicts(SimpleReference<UsageInfo[]> refUsages) {
            MultiMap<PsiElement, LocalizeValue> conflictDescriptions = new MultiMap<>();
            addMethodConflicts(conflictDescriptions);
            Set<UsageInfo> usagesSet = new HashSet<>(Arrays.asList(refUsages.get()));
            RenameUtil.removeConflictUsages(usagesSet);
            if (myChangeInfo.isVisibilityChanged()) {
                try {
                    addInaccessibilityDescriptions(usagesSet, conflictDescriptions);
                }
                catch (IncorrectOperationException e) {
                    LOG.error(e);
                }
            }

            for (UsageInfo usageInfo : usagesSet) {
                PsiElement element = usageInfo.getElement();
                if (usageInfo instanceof OverriderUsageInfo overriderUsageInfo) {
                    PsiMethod method = (PsiMethod) element;
                    PsiMethod baseMethod = overriderUsageInfo.getBaseMethod();
                    int delta = baseMethod.getParameterList().getParametersCount() - method.getParameterList().getParametersCount();
                    if (delta > 0) {
                        boolean[] toRemove = myChangeInfo.toRemoveParm();
                        if (toRemove[toRemove.length - 1]) { //todo check if implicit parameter is not the last one
                            conflictDescriptions.putValue(
                                baseMethod,
                                LocalizeValue.localizeTODO("Implicit last parameter should not be deleted")
                            );
                        }
                    }
                }
                else if (element instanceof PsiMethodReferenceExpression) {
                    conflictDescriptions.putValue(element, LocalizeValue.localizeTODO("Changed method is used in method reference"));
                }
            }

            return conflictDescriptions;
        }

        private boolean needToChangeCalls() {
            return myChangeInfo.isNameChanged() || myChangeInfo.isParameterSetOrOrderChanged() || myChangeInfo.isExceptionSetOrOrderChanged();
        }


        private void addInaccessibilityDescriptions(
            Set<UsageInfo> usages,
            MultiMap<PsiElement, LocalizeValue> conflictDescriptions
        ) throws IncorrectOperationException {
            PsiMethod method = myChangeInfo.getMethod();
            PsiModifierList modifierList = (PsiModifierList) method.getModifierList().copy();
            VisibilityUtil.setVisibility(modifierList, myChangeInfo.getNewVisibility());

            for (Iterator<UsageInfo> iterator = usages.iterator(); iterator.hasNext(); ) {
                UsageInfo usageInfo = iterator.next();
                PsiElement element = usageInfo.getElement();
                if (element != null) {
                    if (element instanceof PsiQualifiedReference qualifiedRef) {
                        PsiClass accessObjectClass = qualifiedRef.getQualifier() instanceof PsiExpression expression
                            ? (PsiClass) PsiUtil.getAccessObjectClass(expression).getElement()
                            : null;

                        if (!JavaPsiFacade.getInstance(element.getProject()).getResolveHelper()
                            .isAccessible(method, modifierList, element, accessObjectClass, null)) {
                            LocalizeValue message = RefactoringLocalize.zeroWith1VisibilityIsNotAccessibleFrom2(
                                RefactoringUIUtil.getDescription(method, true),
                                VisibilityUtil.toPresentableText(myChangeInfo.getNewVisibility()),
                                RefactoringUIUtil.getDescription(ConflictsUtil.getContainer(element), true)
                            );
                            conflictDescriptions.putValue(method, message);
                            if (!needToChangeCalls()) {
                                iterator.remove();
                            }
                        }
                    }
                }
            }
        }

        @RequiredReadAction
        private void addMethodConflicts(MultiMap<PsiElement, LocalizeValue> conflicts) {
            String newMethodName = myChangeInfo.getNewName();
            if (!(myChangeInfo instanceof JavaChangeInfo)) {
                return;
            }
            try {
                PsiMethod prototype;
                PsiMethod method = myChangeInfo.getMethod();
                if (!JavaLanguage.INSTANCE.equals(method.getLanguage())) {
                    return;
                }
                PsiManager manager = method.getManager();
                PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
                CanonicalTypes.Type returnType = myChangeInfo.getNewReturnType();
                if (returnType != null) {
                    prototype = factory.createMethod(newMethodName, returnType.getType(method, manager));
                }
                else {
                    prototype = factory.createConstructor();
                    prototype.setName(newMethodName);
                }
                JavaParameterInfo[] parameters = myChangeInfo.getNewParameters();


                for (JavaParameterInfo info : parameters) {
                    PsiType parameterType = info.createType(method, manager);
                    if (parameterType == null) {
                        parameterType = JavaPsiFacade.getElementFactory(method.getProject())
                            .createTypeFromText(CommonClassNames.JAVA_LANG_OBJECT, method);
                    }
                    PsiParameter param = factory.createParameter(info.getName(), parameterType);
                    prototype.getParameterList().add(param);
                }

                ConflictsUtil.checkMethodConflicts(method.getContainingClass(), method, prototype, conflicts);
            }
            catch (IncorrectOperationException e) {
                LOG.error(e);
            }
        }
    }

    private static class ExpressionList implements ChangeSignatureUtil.ChildrenGenerator<PsiExpressionList, PsiExpression> {
        public static final ExpressionList INSTANCE = new ExpressionList();

        @Override
        public List<PsiExpression> getChildren(PsiExpressionList psiExpressionList) {
            return Arrays.asList(psiExpressionList.getExpressions());
        }
    }

}
