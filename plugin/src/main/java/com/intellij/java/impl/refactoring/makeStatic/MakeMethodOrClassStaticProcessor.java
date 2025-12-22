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
package com.intellij.java.impl.refactoring.makeStatic;

import com.intellij.java.impl.refactoring.util.ConflictsUtil;
import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.indexing.search.searches.OverridingMethodsSearch;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.refactoring.BaseRefactoringProcessor;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.ui.ConflictsDialog;
import consulo.language.editor.refactoring.ui.RefactoringUIUtil;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.findUsage.DescriptiveNameUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiReference;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.usage.UsageInfo;
import consulo.usage.UsageViewDescriptor;
import consulo.usage.UsageViewUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.MultiMap;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author dsl
 * @since 2002-04-16
 */
public abstract class MakeMethodOrClassStaticProcessor<T extends PsiTypeParameterListOwner> extends BaseRefactoringProcessor {
    private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.makeMethodStatic.MakeMethodStaticProcessor");

    protected T myMember;
    protected Settings mySettings;

    public MakeMethodOrClassStaticProcessor(Project project, T member, Settings settings) {
        super(project);
        myMember = member;
        mySettings = settings;
    }

    @Nonnull
    @Override
    protected UsageViewDescriptor createUsageViewDescriptor(@Nonnull UsageInfo[] usages) {
        return new MakeMethodOrClassStaticViewDescriptor(myMember);
    }

    @Override
    @RequiredUIAccess
    protected final boolean preprocessUsages(@Nonnull SimpleReference<UsageInfo[]> refUsages) {
        UsageInfo[] usagesIn = refUsages.get();
        if (myPrepareSuccessfulSwingThreadCallback != null) {
            MultiMap<PsiElement, LocalizeValue> conflicts = getConflictDescriptions(usagesIn);
            if (conflicts.size() > 0) {
                ConflictsDialog conflictsDialog = prepareConflictsDialog(conflicts, refUsages.get());
                conflictsDialog.show();
                if (!conflictsDialog.isOK()) {
                    if (conflictsDialog.isShowConflicts()) {
                        prepareSuccessful();
                    }
                    return false;
                }
            }
            if (!mySettings.isChangeSignature()) {
                refUsages.set(filterInternalUsages(usagesIn));
            }
        }
        refUsages.set(filterOverriding(usagesIn));

        prepareSuccessful();
        return true;
    }

    private static UsageInfo[] filterOverriding(UsageInfo[] usages) {
        List<UsageInfo> result = new ArrayList<>();
        for (UsageInfo usage : usages) {
            if (!(usage instanceof OverridingMethodUsageInfo)) {
                result.add(usage);
            }
        }
        return result.toArray(new UsageInfo[result.size()]);
    }

    private static UsageInfo[] filterInternalUsages(UsageInfo[] usages) {
        List<UsageInfo> result = new ArrayList<>();
        for (UsageInfo usage : usages) {
            if (!(usage instanceof InternalUsageInfo)) {
                result.add(usage);
            }
        }
        return result.toArray(new UsageInfo[result.size()]);
    }

    @RequiredReadAction
    protected MultiMap<PsiElement, LocalizeValue> getConflictDescriptions(UsageInfo[] usages) {
        MultiMap<PsiElement, LocalizeValue> conflicts = new MultiMap<>();
        Set<PsiElement> processed = new HashSet<>();
        String typeString = StringUtil.capitalize(UsageViewUtil.getType(myMember));
        for (UsageInfo usageInfo : usages) {
            if (usageInfo instanceof InternalUsageInfo internalUsageInfo && !(internalUsageInfo instanceof SelfUsageInfo)) {
                PsiElement referencedElement = internalUsageInfo.getReferencedElement();
                if (!mySettings.isMakeClassParameter()) {
                    if (referencedElement instanceof PsiModifierListOwner modifierListOwner
                        && modifierListOwner.hasModifierProperty(PsiModifier.STATIC)) {
                        continue;
                    }

                    if (processed.contains(referencedElement)) {
                        continue;
                    }
                    processed.add(referencedElement);
                    if (referencedElement instanceof PsiField field) {
                        if (mySettings.getNameForField(field) == null) {
                            String description = RefactoringUIUtil.getDescription(field, true);
                            LocalizeValue message =
                                RefactoringLocalize.zeroUsesNonStatic1WhichIsNotPassedAsAParameter(typeString, description);
                            conflicts.putValue(field, message);
                        }
                    }
                    else {
                        String description = RefactoringUIUtil.getDescription(referencedElement, true);
                        LocalizeValue message = RefactoringLocalize.zeroUses1WhichNeedsClassInstance(typeString, description);
                        conflicts.putValue(referencedElement, message);
                    }
                }
            }
            if (usageInfo instanceof OverridingMethodUsageInfo) {
                LOG.assertTrue(myMember instanceof PsiMethod);
                PsiMethod overridingMethod = (PsiMethod) usageInfo.getElement();
                LocalizeValue message = RefactoringLocalize.method0IsOverriddenBy1(
                    RefactoringUIUtil.getDescription(myMember, false),
                    RefactoringUIUtil.getDescription(overridingMethod, true)
                );
                conflicts.putValue(overridingMethod, message);
            }
            else {
                PsiElement element = usageInfo.getElement();
                PsiElement container = ConflictsUtil.getContainer(element);
                if (processed.contains(container)) {
                    continue;
                }
                processed.add(container);
                List<Settings.FieldParameter> fieldParameters = mySettings.getParameterOrderList();
                List<PsiField> inaccessible = new ArrayList<>();

                for (Settings.FieldParameter fieldParameter : fieldParameters) {
                    if (!PsiUtil.isAccessible(fieldParameter.field, element, null)) {
                        inaccessible.add(fieldParameter.field);
                    }
                }

                if (inaccessible.isEmpty()) {
                    continue;
                }

                createInaccessibleFieldsConflictDescription(inaccessible, container, conflicts);
            }
        }
        return conflicts;
    }

    private static void createInaccessibleFieldsConflictDescription(
        List<PsiField> inaccessible,
        PsiElement container,
        MultiMap<PsiElement, LocalizeValue> conflicts
    ) {
        if (inaccessible.size() == 1) {
            PsiField field = inaccessible.get(0);
            conflicts.putValue(
                field,
                RefactoringLocalize.field0IsNotAccessible(
                    CommonRefactoringUtil.htmlEmphasize(field.getName()),
                    RefactoringUIUtil.getDescription(container, true)
                )
            );
        }
        else {
            for (PsiField field : inaccessible) {
                conflicts.putValue(
                    field,
                    RefactoringLocalize.field0IsNotAccessible(
                        CommonRefactoringUtil.htmlEmphasize(field.getName()),
                        RefactoringUIUtil.getDescription(container, true)
                    )
                );
            }
        }
    }

    @Nonnull
    @Override
    protected UsageInfo[] findUsages() {
        List<UsageInfo> result = new ArrayList<>();

        ContainerUtil.addAll(result, MakeStaticUtil.findClassRefsInMember(myMember, true));

        if (mySettings.isReplaceUsages()) {
            findExternalUsages(result);
        }

        if (myMember instanceof PsiMethod method) {
            PsiMethod[] overridingMethods =
                OverridingMethodsSearch.search(method, method.getUseScope(), false).toArray(PsiMethod.EMPTY_ARRAY);
            for (PsiMethod overridingMethod : overridingMethods) {
                if (overridingMethod != method) {
                    result.add(new OverridingMethodUsageInfo(overridingMethod));
                }
            }
        }

        return result.toArray(new UsageInfo[result.size()]);
    }

    protected abstract void findExternalUsages(List<UsageInfo> result);

    @RequiredReadAction
    protected void findExternalReferences(PsiMethod method, List<UsageInfo> result) {
        for (PsiReference ref : ReferencesSearch.search(method)) {
            PsiElement element = ref.getElement();
            PsiElement qualifier = null;
            if (element instanceof PsiReferenceExpression refExpr) {
                qualifier = refExpr.getQualifierExpression();
                if (qualifier instanceof PsiThisExpression) {
                    qualifier = null;
                }
            }
            if (!PsiTreeUtil.isAncestor(myMember, element, true) || qualifier != null) {
                result.add(new UsageInfo(element));
            }
        }
    }

    //should be called before setting static modifier
    @RequiredWriteAction
    protected void setupTypeParameterList() throws IncorrectOperationException {
        PsiTypeParameterList list = myMember.getTypeParameterList();
        assert list != null;
        PsiTypeParameterList newList = RefactoringUtil.createTypeParameterListWithUsedTypeParameters(myMember);
        if (newList != null) {
            list.replace(newList);
        }
    }

    protected boolean makeClassParameterFinal(UsageInfo[] usages) {
        for (UsageInfo usage : usages) {
            if (usage instanceof InternalUsageInfo internalUsageInfo
                && !(internalUsageInfo.getReferencedElement() instanceof PsiField field && mySettings.getNameForField(field) != null)
                && internalUsageInfo.isInsideAnonymous()) {
                return true;
            }
        }
        return false;
    }

    protected static boolean makeFieldParameterFinal(PsiField field, UsageInfo[] usages) {
        for (UsageInfo usage : usages) {
            if (usage instanceof InternalUsageInfo internalUsageInfo
                && internalUsageInfo.getReferencedElement() instanceof PsiField refField
                && field.equals(refField)
                && internalUsageInfo.isInsideAnonymous()) {
                return true;
            }
        }
        return false;
    }

    @Nonnull
    @Override
    @RequiredReadAction
    protected LocalizeValue getCommandName() {
        return RefactoringLocalize.makeStaticCommand(DescriptiveNameUtil.getDescriptiveName(myMember));
    }

    public T getMember() {
        return myMember;
    }

    public Settings getSettings() {
        return mySettings;
    }

    @Override
    protected void performRefactoring(UsageInfo[] usages) {
        PsiManager manager = myMember.getManager();
        PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();

        try {
            for (UsageInfo usage : usages) {
                if (usage instanceof SelfUsageInfo selfUsageInfo) {
                    changeSelfUsage(selfUsageInfo);
                }
                else if (usage instanceof InternalUsageInfo internalUsageInfo) {
                    changeInternalUsage(internalUsageInfo, factory);
                }
                else {
                    changeExternalUsage(usage, factory);
                }
            }
            changeSelf(factory, usages);
        }
        catch (IncorrectOperationException ex) {
            LOG.assertTrue(false);
        }
    }

    protected abstract void changeSelf(PsiElementFactory factory, UsageInfo[] usages) throws IncorrectOperationException;

    protected abstract void changeSelfUsage(SelfUsageInfo usageInfo) throws IncorrectOperationException;

    protected abstract void changeInternalUsage(InternalUsageInfo usage, PsiElementFactory factory) throws IncorrectOperationException;

    protected abstract void changeExternalUsage(UsageInfo usage, PsiElementFactory factory) throws IncorrectOperationException;
}
