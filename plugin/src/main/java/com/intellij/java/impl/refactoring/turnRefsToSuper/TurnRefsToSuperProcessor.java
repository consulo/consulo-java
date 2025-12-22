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
package com.intellij.java.impl.refactoring.turnRefsToSuper;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.findUsage.DescriptiveNameUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.Messages;
import consulo.usage.UsageInfo;
import consulo.usage.UsageViewDescriptor;
import consulo.usage.UsageViewUtil;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class TurnRefsToSuperProcessor extends TurnRefsToSuperProcessorBase {
    private static final Logger LOG = Logger.getInstance(TurnRefsToSuperProcessor.class);

    private PsiClass mySuper;

    @RequiredReadAction
    public TurnRefsToSuperProcessor(
        Project project,
        @Nonnull PsiClass aClass,
        @Nonnull PsiClass aSuper,
        boolean replaceInstanceOf
    ) {
        super(project, replaceInstanceOf, aSuper.getName());
        myClass = aClass;
        mySuper = aSuper;
    }

    @Nonnull
    @Override
    @RequiredReadAction
    protected LocalizeValue getCommandName() {
        return RefactoringLocalize.turnRefsToSuperCommand(
            DescriptiveNameUtil.getDescriptiveName(myClass),
            DescriptiveNameUtil.getDescriptiveName(mySuper)
        );
    }

    @Nonnull
    @Override
    protected UsageViewDescriptor createUsageViewDescriptor(@Nonnull UsageInfo[] usages) {
        return new RefsToSuperViewDescriptor(myClass, mySuper);
    }

    private void setClasses(@Nonnull PsiClass aClass, @Nonnull PsiClass aSuper) {
        myClass = aClass;
        mySuper = aSuper;
    }

    @Nonnull
    @Override
    @RequiredReadAction
    protected UsageInfo[] findUsages() {
        PsiReference[] refs =
            ReferencesSearch.search(myClass, GlobalSearchScope.projectScope(myProject), false).toArray(new PsiReference[0]);

        List<UsageInfo> result = detectTurnToSuperRefs(refs, new ArrayList<>());

        UsageInfo[] usageInfos = result.toArray(new UsageInfo[result.size()]);
        return UsageViewUtil.removeDuplicatedUsages(usageInfos);
    }

    @Override
    protected void refreshElements(PsiElement[] elements) {
        LOG.assertTrue(elements.length == 2 && elements[0] instanceof PsiClass && elements[1] instanceof PsiClass);
        setClasses((PsiClass)elements[0], (PsiClass)elements[1]);
    }

    @Override
    @RequiredUIAccess
    protected boolean preprocessUsages(@Nonnull SimpleReference<UsageInfo[]> refUsages) {
        if (!myProject.getApplication().isUnitTestMode() && refUsages.get().length == 0) {
            LocalizeValue message = RefactoringLocalize.noUsagesCanBeReplaced(myClass.getQualifiedName(), mySuper.getQualifiedName());
            Messages.showInfoMessage(myProject, message.get(), TurnRefsToSuperHandler.REFACTORING_NAME.get());
            return false;
        }

        return super.preprocessUsages(refUsages);
    }

    @Override
    protected boolean canTurnToSuper(PsiElement refElement) {
        return super.canTurnToSuper(refElement)
            && JavaPsiFacade.getInstance(myProject).getResolveHelper().isAccessible(mySuper, refElement, null);
    }

    @Override
    @RequiredWriteAction
    protected void performRefactoring(@Nonnull UsageInfo[] usages) {
        try {
            PsiClass aSuper = mySuper;
            processTurnToSuperRefs(usages, aSuper);
        }
        catch (IncorrectOperationException e) {
            LOG.error(e);
        }

        performVariablesRenaming();
    }

    @Override
    @RequiredReadAction
    protected boolean isInSuper(PsiElement member) {
        if (!(member instanceof PsiMember psiMember)) {
            return false;
        }
        PsiManager manager = member.getManager();
        if (InheritanceUtil.isInheritorOrSelf(mySuper, psiMember.getContainingClass(), true)) {
            return true;
        }

        if (member instanceof PsiField field) {
            PsiClass containingClass = field.getContainingClass();
            LanguageLevel languageLevel = PsiUtil.getLanguageLevel(field);
            if (manager.areElementsEquivalent(
                containingClass,
                JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().getArrayClass(languageLevel)
            )) {
                return true;
            }
        }
        else if (member instanceof PsiMethod method) {
            return mySuper.findMethodBySignature(method, true) != null;
        }

        return false;
    }

    @Override
    protected boolean isSuperInheritor(PsiClass aClass) {
        return InheritanceUtil.isInheritorOrSelf(mySuper, aClass, true);
    }

    public PsiClass getSuper() {
        return mySuper;
    }

    public PsiClass getTarget() {
        return myClass;
    }

    public boolean isReplaceInstanceOf() {
        return myReplaceInstanceOf;
    }

    @Nonnull
    @Override
    protected Collection<? extends PsiElement> getElementsToWrite(@Nonnull UsageViewDescriptor descriptor) {
        return Collections.emptyList(); // neither myClass nor mySuper are subject to change, it's just references that are going to change
    }
}