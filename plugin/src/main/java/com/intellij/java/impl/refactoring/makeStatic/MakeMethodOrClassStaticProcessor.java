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
import consulo.util.lang.ref.Ref;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/*
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
    protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
        return new MakeMethodOrClassStaticViewDescriptor(myMember);
    }

    @Override
    @RequiredUIAccess
    protected final boolean preprocessUsages(@Nonnull SimpleReference<UsageInfo[]> refUsages) {
        UsageInfo[] usagesIn = refUsages.get();
        if (myPrepareSuccessfulSwingThreadCallback != null) {
            MultiMap<PsiElement, String> conflicts = getConflictDescriptions(usagesIn);
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
        ArrayList<UsageInfo> result = new ArrayList<UsageInfo>();
        for (UsageInfo usage : usages) {
            if (!(usage instanceof OverridingMethodUsageInfo)) {
                result.add(usage);
            }
        }
        return result.toArray(new UsageInfo[result.size()]);
    }

    private static UsageInfo[] filterInternalUsages(UsageInfo[] usages) {
        ArrayList<UsageInfo> result = new ArrayList<UsageInfo>();
        for (UsageInfo usage : usages) {
            if (!(usage instanceof InternalUsageInfo)) {
                result.add(usage);
            }
        }
        return result.toArray(new UsageInfo[result.size()]);
    }

    protected MultiMap<PsiElement, String> getConflictDescriptions(UsageInfo[] usages) {
        MultiMap<PsiElement, String> conflicts = new MultiMap<PsiElement, String>();
        HashSet<PsiElement> processed = new HashSet<PsiElement>();
        String typeString = StringUtil.capitalize(UsageViewUtil.getType(myMember));
        for (UsageInfo usageInfo : usages) {
            if (usageInfo instanceof InternalUsageInfo && !(usageInfo instanceof SelfUsageInfo)) {
                PsiElement referencedElement = ((InternalUsageInfo)usageInfo).getReferencedElement();
                if (!mySettings.isMakeClassParameter()) {
                    if (referencedElement instanceof PsiModifierListOwner) {
                        if (((PsiModifierListOwner)referencedElement).hasModifierProperty(PsiModifier.STATIC)) {
                            continue;
                        }
                    }

                    if (processed.contains(referencedElement)) {
                        continue;
                    }
                    processed.add(referencedElement);
                    if (referencedElement instanceof PsiField) {
                        PsiField field = (PsiField)referencedElement;

                        if (mySettings.getNameForField(field) == null) {
                            String description = RefactoringUIUtil.getDescription(field, true);
                            LocalizeValue message =
                                RefactoringLocalize.zeroUsesNonStatic1WhichIsNotPassedAsAParameter(typeString, description);
                            conflicts.putValue(field, message.get());
                        }
                    }
                    else {
                        String description = RefactoringUIUtil.getDescription(referencedElement, true);
                        LocalizeValue message = RefactoringLocalize.zeroUses1WhichNeedsClassInstance(typeString, description);
                        conflicts.putValue(referencedElement, message.get());
                    }
                }
            }
            if (usageInfo instanceof OverridingMethodUsageInfo) {
                LOG.assertTrue(myMember instanceof PsiMethod);
                final PsiMethod overridingMethod = (PsiMethod)usageInfo.getElement();
                LocalizeValue message = RefactoringLocalize.method0IsOverriddenBy1(
                    RefactoringUIUtil.getDescription(myMember, false),
                    RefactoringUIUtil.getDescription(overridingMethod, true)
                );
                conflicts.putValue(overridingMethod, message.get());
            }
            else {
                PsiElement element = usageInfo.getElement();
                PsiElement container = ConflictsUtil.getContainer(element);
                if (processed.contains(container)) {
                    continue;
                }
                processed.add(container);
                List<Settings.FieldParameter> fieldParameters = mySettings.getParameterOrderList();
                ArrayList<PsiField> inaccessible = new ArrayList<PsiField>();

                for (final Settings.FieldParameter fieldParameter : fieldParameters) {
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
        ArrayList<PsiField> inaccessible,
        PsiElement container,
        MultiMap<PsiElement, String> conflicts
    ) {
        if (inaccessible.size() == 1) {
            final PsiField field = inaccessible.get(0);
            conflicts.putValue(
                field,
                RefactoringLocalize.field0IsNotAccessible(
                    CommonRefactoringUtil.htmlEmphasize(field.getName()),
                    RefactoringUIUtil.getDescription(container, true)
                ).get()
            );
        }
        else {
            for (PsiField field : inaccessible) {
                conflicts.putValue(
                    field,
                    RefactoringLocalize.field0IsNotAccessible(
                        CommonRefactoringUtil.htmlEmphasize(field.getName()),
                        RefactoringUIUtil.getDescription(container, true)
                    ).get()
                );
            }
        }
    }

    @Nonnull
    protected UsageInfo[] findUsages() {
        ArrayList<UsageInfo> result = new ArrayList<UsageInfo>();

        ContainerUtil.addAll(result, MakeStaticUtil.findClassRefsInMember(myMember, true));

        if (mySettings.isReplaceUsages()) {
            findExternalUsages(result);
        }

        if (myMember instanceof PsiMethod) {
            final PsiMethod[] overridingMethods =
                OverridingMethodsSearch.search((PsiMethod)myMember, myMember.getUseScope(), false).toArray(PsiMethod.EMPTY_ARRAY);
            for (PsiMethod overridingMethod : overridingMethods) {
                if (overridingMethod != myMember) {
                    result.add(new OverridingMethodUsageInfo(overridingMethod));
                }
            }
        }

        return result.toArray(new UsageInfo[result.size()]);
    }

    protected abstract void findExternalUsages(ArrayList<UsageInfo> result);

    protected void findExternalReferences(final PsiMethod method, final ArrayList<UsageInfo> result) {
        for (PsiReference ref : ReferencesSearch.search(method)) {
            PsiElement element = ref.getElement();
            PsiElement qualifier = null;
            if (element instanceof PsiReferenceExpression) {
                qualifier = ((PsiReferenceExpression)element).getQualifierExpression();
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
    protected void setupTypeParameterList() throws IncorrectOperationException {
        final PsiTypeParameterList list = myMember.getTypeParameterList();
        assert list != null;
        final PsiTypeParameterList newList = RefactoringUtil.createTypeParameterListWithUsedTypeParameters(myMember);
        if (newList != null) {
            list.replace(newList);
        }
    }

    protected boolean makeClassParameterFinal(UsageInfo[] usages) {
        for (UsageInfo usage : usages) {
            if (usage instanceof InternalUsageInfo) {
                final InternalUsageInfo internalUsageInfo = (InternalUsageInfo)usage;
                PsiElement referencedElement = internalUsageInfo.getReferencedElement();
                if (!(referencedElement instanceof PsiField)
                    || mySettings.getNameForField((PsiField)referencedElement) == null) {
                    if (internalUsageInfo.isInsideAnonymous()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    protected static boolean makeFieldParameterFinal(PsiField field, UsageInfo[] usages) {
        for (UsageInfo usage : usages) {
            if (usage instanceof InternalUsageInfo) {
                final InternalUsageInfo internalUsageInfo = (InternalUsageInfo)usage;
                PsiElement referencedElement = internalUsageInfo.getReferencedElement();
                if (referencedElement instanceof PsiField && field.equals(referencedElement)) {
                    if (internalUsageInfo.isInsideAnonymous()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    protected String getCommandName() {
        return RefactoringLocalize.makeStaticCommand(DescriptiveNameUtil.getDescriptiveName(myMember)).get();
    }

    public T getMember() {
        return myMember;
    }

    public Settings getSettings() {
        return mySettings;
    }

    protected void performRefactoring(UsageInfo[] usages) {
        PsiManager manager = myMember.getManager();
        PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();

        try {
            for (UsageInfo usage : usages) {
                if (usage instanceof SelfUsageInfo) {
                    changeSelfUsage((SelfUsageInfo)usage);
                }
                else if (usage instanceof InternalUsageInfo) {
                    changeInternalUsage((InternalUsageInfo)usage, factory);
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
