/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/**
 * created at Sep 17, 2001
 *
 * @author Jeka
 */
package com.intellij.java.impl.refactoring.changeSignature;

import com.intellij.java.impl.refactoring.util.CanonicalTypes;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.*;
import com.intellij.java.language.util.VisibilityUtil;
import consulo.java.impl.refactoring.changeSignature.ChangeSignatureUsageProcessorEx;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.refactoring.changeSignature.ChangeSignatureProcessorBase;
import consulo.language.editor.refactoring.changeSignature.ChangeSignatureUsageProcessor;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.rename.RenameUtil;
import consulo.language.editor.refactoring.ui.ConflictsDialog;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.usage.UsageInfo;
import consulo.usage.UsageViewDescriptor;
import consulo.util.collection.MultiMap;
import consulo.util.lang.ref.Ref;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;

public class ChangeSignatureProcessor extends ChangeSignatureProcessorBase {
    private static final Logger LOG = Logger.getInstance(ChangeSignatureProcessor.class);

    public ChangeSignatureProcessor(
        Project project,
        PsiMethod method,
        final boolean generateDelegate,
        @PsiModifier.ModifierConstant String newVisibility,
        String newName,
        PsiType newType,
        @Nonnull ParameterInfoImpl[] parameterInfo
    ) {
        this(
            project,
            method,
            generateDelegate,
            newVisibility,
            newName,
            newType != null ? CanonicalTypes.createTypeWrapper(newType) : null,
            parameterInfo,
            null,
            null,
            null
        );
    }

    public ChangeSignatureProcessor(
        Project project,
        PsiMethod method,
        final boolean generateDelegate,
        @PsiModifier.ModifierConstant String newVisibility,
        String newName,
        PsiType newType,
        ParameterInfoImpl[] parameterInfo,
        ThrownExceptionInfo[] exceptionInfos
    ) {
        this(
            project,
            method,
            generateDelegate,
            newVisibility,
            newName,
            newType != null ? CanonicalTypes.createTypeWrapper(newType) : null,
            parameterInfo,
            exceptionInfos,
            null,
            null
        );
    }

    public ChangeSignatureProcessor(
        Project project,
        PsiMethod method,
        boolean generateDelegate,
        @PsiModifier.ModifierConstant String newVisibility,
        String newName,
        CanonicalTypes.Type newType,
        @Nonnull ParameterInfoImpl[] parameterInfo,
        ThrownExceptionInfo[] thrownExceptions,
        Set<PsiMethod> propagateParametersMethods,
        Set<PsiMethod> propagateExceptionsMethods
    ) {
        this(
            project,
            generateChangeInfo(
                method,
                generateDelegate,
                newVisibility,
                newName,
                newType,
                parameterInfo,
                thrownExceptions,
                propagateParametersMethods,
                propagateExceptionsMethods
            )
        );
    }

    public ChangeSignatureProcessor(Project project, final JavaChangeInfo changeInfo) {
        super(project, changeInfo);
        LOG.assertTrue(myChangeInfo.getMethod().isValid());
    }

    private static JavaChangeInfo generateChangeInfo(
        PsiMethod method,
        boolean generateDelegate,
        @PsiModifier.ModifierConstant String newVisibility,
        String newName,
        CanonicalTypes.Type newType,
        @Nonnull ParameterInfoImpl[] parameterInfo,
        ThrownExceptionInfo[] thrownExceptions,
        Set<PsiMethod> propagateParametersMethods,
        Set<PsiMethod> propagateExceptionsMethods
    ) {
        Set<PsiMethod> myPropagateParametersMethods = propagateParametersMethods != null ? propagateParametersMethods : new HashSet<>();
        Set<PsiMethod> myPropagateExceptionsMethods = propagateExceptionsMethods != null ? propagateExceptionsMethods : new HashSet<>();

        LOG.assertTrue(method.isValid());
        if (newVisibility == null) {
            newVisibility = VisibilityUtil.getVisibilityModifier(method.getModifierList());
        }

        return new JavaChangeInfoImpl(
            newVisibility,
            method,
            newName,
            newType,
            parameterInfo,
            thrownExceptions,
            generateDelegate,
            myPropagateParametersMethods,
            myPropagateExceptionsMethods
        );
    }

    @Override
    @Nonnull
    protected UsageViewDescriptor createUsageViewDescriptor(@Nonnull UsageInfo[] usages) {
        return new ChangeSignatureViewDescriptor(getChangeInfo().getMethod());
    }

    @Override
    public JavaChangeInfoImpl getChangeInfo() {
        return (JavaChangeInfoImpl)super.getChangeInfo();
    }

    @Override
    protected void refreshElements(PsiElement[] elements) {
        boolean condition = elements.length == 1 && elements[0] instanceof PsiMethod;
        LOG.assertTrue(condition);
        getChangeInfo().updateMethod((PsiMethod)elements[0]);
    }

    @Override
    @RequiredUIAccess
    protected boolean preprocessUsages(@Nonnull SimpleReference<UsageInfo[]> refUsages) {
        for (ChangeSignatureUsageProcessor processor : ChangeSignatureUsageProcessor.EP_NAME.getExtensions()) {
            if (processor instanceof ChangeSignatureUsageProcessorEx changeSignatureUsageProcessorEx
                && changeSignatureUsageProcessorEx.setupDefaultValues(myChangeInfo, refUsages, myProject)) {
                return false;
            }
        }
        MultiMap<PsiElement, String> conflictDescriptions = new MultiMap<>();
        for (ChangeSignatureUsageProcessor usageProcessor : ChangeSignatureUsageProcessor.EP_NAME.getExtensions()) {
            final MultiMap<PsiElement, String> conflicts = usageProcessor.findConflicts(myChangeInfo, refUsages);
            for (PsiElement key : conflicts.keySet()) {
                Collection<String> collection = conflictDescriptions.get(key);
                if (collection.size() == 0) {
                    collection = new HashSet<>();
                }
                collection.addAll(conflicts.get(key));
                conflictDescriptions.put(key, collection);
            }
        }

        final UsageInfo[] usagesIn = refUsages.get();
        RenameUtil.addConflictDescriptions(usagesIn, conflictDescriptions);
        Set<UsageInfo> usagesSet = new HashSet<>(Arrays.asList(usagesIn));
        RenameUtil.removeConflictUsages(usagesSet);
        if (!conflictDescriptions.isEmpty()) {
            if (myProject.getApplication().isUnitTestMode()) {
                throw new ConflictsInTestsException(conflictDescriptions.values());
            }
            if (myPrepareSuccessfulSwingThreadCallback != null) {
                ConflictsDialog dialog = prepareConflictsDialog(conflictDescriptions, usagesIn);
                dialog.show();
                if (!dialog.isOK()) {
                    if (dialog.isShowConflicts()) {
                        prepareSuccessful();
                    }
                    return false;
                }
            }
        }

        if (myChangeInfo.isReturnTypeChanged()) {
            askToRemoveCovariantOverriders(usagesSet);
        }

        refUsages.set(usagesSet.toArray(new UsageInfo[usagesSet.size()]));
        prepareSuccessful();
        return true;
    }

    @RequiredUIAccess
    private void askToRemoveCovariantOverriders(Set<UsageInfo> usages) {
        if (PsiUtil.isLanguageLevel5OrHigher(myChangeInfo.getMethod())) {
            List<UsageInfo> covariantOverriderInfos = new ArrayList<>();
            for (UsageInfo usageInfo : usages) {
                if (usageInfo instanceof OverriderUsageInfo) {
                    final OverriderUsageInfo info = (OverriderUsageInfo)usageInfo;
                    PsiMethod overrider = info.getElement();
                    PsiMethod baseMethod = info.getBaseMethod();
                    PsiSubstitutor substitutor = calculateSubstitutor(overrider, baseMethod);
                    PsiType type;
                    try {
                        type = substitutor.substitute(getChangeInfo().newReturnType.getType(myChangeInfo.getMethod(), myManager));
                    }
                    catch (IncorrectOperationException e) {
                        LOG.error(e);
                        return;
                    }
                    final PsiType overriderType = overrider.getReturnType();
                    if (overriderType != null && type.isAssignableFrom(overriderType)) {
                        covariantOverriderInfos.add(usageInfo);
                    }
                }
            }

            // to be able to do filtering
            preprocessCovariantOverriders(covariantOverriderInfos);

            if (!covariantOverriderInfos.isEmpty()) {
                if (myProject.getApplication().isUnitTestMode() || !isProcessCovariantOverriders()) {
                    for (UsageInfo usageInfo : covariantOverriderInfos) {
                        usages.remove(usageInfo);
                    }
                }
            }
        }
    }

    protected void preprocessCovariantOverriders(final List<UsageInfo> covariantOverriderInfos) {
    }

    @RequiredUIAccess
    protected boolean isProcessCovariantOverriders() {
        return Messages.showYesNoDialog(
            myProject,
            RefactoringLocalize.doYouWantToProcessOverridingMethodsWithCovariantReturnType().get(),
            JavaChangeSignatureHandler.REFACTORING_NAME.get(),
            UIUtil.getQuestionIcon()
        ) == DialogWrapper.OK_EXIT_CODE;
    }

    public static void makeEmptyBody(final PsiElementFactory factory, final PsiMethod delegate) throws IncorrectOperationException {
        PsiCodeBlock body = delegate.getBody();
        if (body != null) {
            body.replace(factory.createCodeBlock());
        }
        else {
            delegate.add(factory.createCodeBlock());
        }
        PsiUtil.setModifierProperty(delegate, PsiModifier.ABSTRACT, false);
    }

    @Nullable
    public static PsiCallExpression addDelegatingCallTemplate(
        final PsiMethod delegate,
        final String newName
    ) throws IncorrectOperationException {
        Project project = delegate.getProject();
        PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
        PsiCodeBlock body = delegate.getBody();
        assert body != null;
        final PsiCallExpression callExpression;
        if (delegate.isConstructor()) {
            PsiElement callStatement = factory.createStatementFromText("this();", null);
            callStatement = CodeStyleManager.getInstance(project).reformat(callStatement);
            callStatement = body.add(callStatement);
            callExpression = (PsiCallExpression)((PsiExpressionStatement)callStatement).getExpression();
        }
        else {
            if (PsiType.VOID.equals(delegate.getReturnType())) {
                PsiElement callStatement = factory.createStatementFromText(newName + "();", null);
                callStatement = CodeStyleManager.getInstance(project).reformat(callStatement);
                callStatement = body.add(callStatement);
                callExpression = (PsiCallExpression)((PsiExpressionStatement)callStatement).getExpression();
            }
            else {
                PsiElement callStatement = factory.createStatementFromText("return " + newName + "();", null);
                callStatement = CodeStyleManager.getInstance(project).reformat(callStatement);
                callStatement = body.add(callStatement);
                callExpression = (PsiCallExpression)((PsiReturnStatement)callStatement).getReturnValue();
            }
        }
        return callExpression;
    }

    public static PsiSubstitutor calculateSubstitutor(PsiMethod derivedMethod, PsiMethod baseMethod) {
        PsiSubstitutor substitutor;
        if (derivedMethod.getManager().areElementsEquivalent(derivedMethod, baseMethod)) {
            substitutor = PsiSubstitutor.EMPTY;
        }
        else {
            final PsiClass baseClass = baseMethod.getContainingClass();
            final PsiClass derivedClass = derivedMethod.getContainingClass();
            if (baseClass != null && derivedClass != null && InheritanceUtil.isInheritorOrSelf(derivedClass, baseClass, true)) {
                final PsiSubstitutor superClassSubstitutor =
                    TypeConversionUtil.getSuperClassSubstitutor(baseClass, derivedClass, PsiSubstitutor.EMPTY);
                final MethodSignature superMethodSignature = baseMethod.getSignature(superClassSubstitutor);
                final MethodSignature methodSignature = derivedMethod.getSignature(PsiSubstitutor.EMPTY);
                final PsiSubstitutor superMethodSubstitutor =
                    MethodSignatureUtil.getSuperMethodSignatureSubstitutor(methodSignature, superMethodSignature);
                substitutor = superMethodSubstitutor != null ? superMethodSubstitutor : superClassSubstitutor;
            }
            else {
                substitutor = PsiSubstitutor.EMPTY;
            }
        }
        return substitutor;
    }
}
