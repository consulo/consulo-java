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
package com.intellij.java.impl.refactoring.removemiddleman;

import com.intellij.java.impl.codeInsight.generation.GenerateMembersUtil;
import com.intellij.java.impl.refactoring.removemiddleman.usageInfo.DeleteMethod;
import com.intellij.java.impl.refactoring.removemiddleman.usageInfo.InlineDelegatingCall;
import com.intellij.java.impl.refactoring.util.FixableUsageInfo;
import com.intellij.java.impl.refactoring.util.FixableUsagesRefactoringProcessor;
import com.intellij.java.impl.refactoring.util.classMembers.MemberInfo;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import com.intellij.java.language.psi.util.PropertyUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.java.localize.JavaRefactoringLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.SymbolPresentationUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.usage.UsageInfo;
import consulo.usage.UsageViewDescriptor;
import consulo.util.collection.MultiMap;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nonnull;

import java.util.List;

public class RemoveMiddlemanProcessor extends FixableUsagesRefactoringProcessor {
    private static final Logger LOG = Logger.getInstance(RemoveMiddlemanProcessor.class);

    private final PsiField field;
    private final PsiClass containingClass;
    private final List<MemberInfo> myDelegateMethodInfos;
    private PsiMethod getter;

    public RemoveMiddlemanProcessor(PsiField field, List<MemberInfo> memberInfos) {
        super(field.getProject());
        this.field = field;
        containingClass = field.getContainingClass();
        String propertyName = PropertyUtil.suggestPropertyName(field);
        boolean isStatic = field.isStatic();
        getter = PropertyUtil.findPropertyGetter(containingClass, propertyName, isStatic, false);
        myDelegateMethodInfos = memberInfos;
    }

    @Nonnull
    @Override
    protected UsageViewDescriptor createUsageViewDescriptor(@Nonnull UsageInfo[] usageInfos) {
        return new RemoveMiddlemanUsageViewDescriptor(field);
    }

    @Override
    @RequiredReadAction
    public void findUsages(@Nonnull List<FixableUsageInfo> usages) {
        for (MemberInfo memberInfo : myDelegateMethodInfos) {
            if (!memberInfo.isChecked()) {
                continue;
            }
            PsiMethod method = (PsiMethod)memberInfo.getMember();
            String getterName = PropertyUtil.suggestGetterName(field);
            int[] paramPermutation = DelegationUtils.getParameterPermutation(method);
            PsiMethod delegatedMethod = DelegationUtils.getDelegatedMethod(method);
            LOG.assertTrue(!DelegationUtils.isAbstract(method));
            processUsagesForMethod(memberInfo.isToAbstract(), method, paramPermutation, getterName, delegatedMethod, usages);
        }
    }

    @Override
    @RequiredUIAccess
    protected boolean preprocessUsages(@Nonnull SimpleReference<UsageInfo[]> refUsages) {
        MultiMap<PsiElement, String> conflicts = new MultiMap<>();
        for (MemberInfo memberInfo : myDelegateMethodInfos) {
            if (memberInfo.isChecked() && memberInfo.isToAbstract()
                && memberInfo.getMember() instanceof PsiMethod method && method.findDeepestSuperMethods().length > 0) {
                conflicts.putValue(
                    method,
                    JavaRefactoringLocalize.removeMiddlemanDeletedHierarchyConflict(
                        SymbolPresentationUtil.getSymbolPresentableText(method)
                    ).get()
                );
            }
        }
        return showConflicts(conflicts, refUsages.get());
    }

    @RequiredReadAction
    private void processUsagesForMethod(
        boolean deleteMethodHierarchy,
        PsiMethod method,
        int[] paramPermutation,
        String getterName,
        PsiMethod delegatedMethod,
        List<FixableUsageInfo> usages
    ) {
        for (PsiReference reference : ReferencesSearch.search(method)) {
            PsiElement referenceElement = reference.getElement();
            PsiMethodCallExpression call = (PsiMethodCallExpression)referenceElement.getParent();
            String access;
            if (call.getMethodExpression().getQualifierExpression() == null) {
                access = field.getName();
            }
            else {
                access = getterName + "()";
                if (getter == null) {
                    getter = GenerateMembersUtil.generateGetterPrototype(field);
                }
            }
            usages.add(new InlineDelegatingCall(call, paramPermutation, access, delegatedMethod.getName()));
        }
        if (deleteMethodHierarchy) {
            usages.add(new DeleteMethod(method));
        }
    }

    @Override
    protected void performRefactoring(@Nonnull UsageInfo[] usageInfos) {
        if (getter != null) {
            try {
                if (containingClass.findMethodBySignature(getter, false) == null) {
                    containingClass.add(getter);
                }
            }
            catch (IncorrectOperationException e) {
                LOG.error(e);
            }
        }

        super.performRefactoring(usageInfos);
    }

    @Nonnull
    @Override
    @RequiredReadAction
    protected String getCommandName() {
        return JavaRefactoringLocalize.exposedDelegationCommandName(containingClass.getName(), '.', field.getName()).get();
    }
}
