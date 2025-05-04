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
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.indexing.search.searches.OverridingMethodsSearch;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import com.intellij.java.language.psi.util.PsiFormatUtilBase;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.util.VisibilityUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.codeEditor.Editor;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.inspection.LocalQuickFixAndIntentionActionOnPsiElement;
import consulo.language.editor.util.LanguageUndoUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.SmartPointerManager;
import consulo.language.psi.SmartPsiElementPointer;
import consulo.language.psi.resolve.PsiElementProcessor;
import consulo.language.psi.resolve.PsiElementProcessorAdapter;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ModifierFix extends LocalQuickFixAndIntentionActionOnPsiElement {
    private static final Logger LOG = Logger.getInstance(ModifierFix.class);

    @PsiModifier.ModifierConstant
    private final String myModifier;
    private final boolean myShouldHave;
    private final boolean myShowContainingClass;
    private final String myName;
    private final SmartPsiElementPointer<PsiVariable> myVariable;

    @RequiredReadAction
    public ModifierFix(
        PsiModifierList modifierList,
        @PsiModifier.ModifierConstant @Nonnull String modifier,
        boolean shouldHave,
        boolean showContainingClass
    ) {
        super(modifierList);
        myModifier = modifier;
        myShouldHave = shouldHave;
        myShowContainingClass = showContainingClass;
        myName = format(null, modifierList);
        myVariable = null;
    }

    @RequiredReadAction
    public ModifierFix(
        @Nonnull PsiModifierListOwner owner,
        @PsiModifier.ModifierConstant @Nonnull String modifier,
        boolean shouldHave,
        boolean showContainingClass
    ) {
        super(owner.getModifierList());
        myModifier = modifier;
        myShouldHave = shouldHave;
        myShowContainingClass = showContainingClass;
        PsiVariable variable = owner instanceof PsiVariable psiVariable ? psiVariable : null;
        myName = format(variable, owner.getModifierList());

        myVariable = variable == null ? null : SmartPointerManager.getInstance(owner.getProject()).createSmartPsiElementPointer(variable);
    }

    @Nonnull
    @Override
    public String getText() {
        return myName;
    }

    @RequiredReadAction
    private String format(PsiVariable variable, PsiModifierList modifierList) {
        String name;
        PsiElement parent = variable == null ? modifierList == null ? null : modifierList.getParent() : variable;
        if (parent instanceof PsiClass psiClass) {
            name = psiClass.getName();
        }
        else {
            int options = PsiFormatUtilBase.SHOW_NAME | (myShowContainingClass ? PsiFormatUtilBase.SHOW_CONTAINING_CLASS : 0);
            if (parent instanceof PsiMethod method) {
                name = PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, options, 0);
            }
            else if (parent instanceof PsiVariable psiVariable) {
                name = PsiFormatUtil.formatVariable(psiVariable, options, PsiSubstitutor.EMPTY);
            }
            else if (parent instanceof PsiClassInitializer classInitializer) {
                PsiClass containingClass = classInitializer.getContainingClass();
                LocalizeValue className = containingClass instanceof PsiAnonymousClass anonymousClass
                    ? JavaQuickFixLocalize.anonymousClassPresentation(anonymousClass.getBaseClassType().getPresentableText())
                    : containingClass != null ? LocalizeValue.of(containingClass.getName()) : LocalizeValue.localizeTODO("unknown");
                name = JavaQuickFixLocalize.classInitializerPresentation(className).get();
            }
            else {
                name = "?";
            }
        }

        String modifierText = VisibilityUtil.toPresentableText(myModifier);

        return myShouldHave
            ? JavaQuickFixLocalize.addModifierFix(name, modifierText).get()
            : JavaQuickFixLocalize.removeModifierFix(name, modifierText).get();
    }

    @Nonnull
    @Override
    public String getFamilyName() {
        return JavaQuickFixLocalize.fixModifiersFamily().get();
    }

    @Override
    @RequiredReadAction
    public boolean isAvailable(
        @Nonnull Project project,
        @Nonnull PsiFile file,
        @Nonnull PsiElement startElement,
        @Nonnull PsiElement endElement
    ) {
        final PsiModifierList myModifierList = (PsiModifierList)startElement;
        PsiVariable variable = myVariable == null ? null : myVariable.getElement();
        return myModifierList.isValid()
            && myModifierList.getManager().isInProject(myModifierList)
            && myModifierList.hasExplicitModifier(myModifier) != myShouldHave
            && (variable == null || variable.isValid());
    }

    private void changeModifierList(PsiModifierList modifierList) {
        try {
            modifierList.setModifierProperty(myModifier, myShouldHave);
        }
        catch (IncorrectOperationException e) {
            LOG.error(e);
        }
    }

    @Override
    @RequiredUIAccess
    public void invoke(
        @Nonnull Project project,
        @Nonnull PsiFile file,
        @Nullable Editor editor,
        @Nonnull PsiElement startElement,
        @Nonnull PsiElement endElement
    ) {
        final PsiModifierList myModifierList = (PsiModifierList)startElement;
        final PsiVariable variable = myVariable == null ? null : myVariable.getElement();
        if (!FileModificationService.getInstance().preparePsiElementForWrite(myModifierList)) {
            return;
        }
        final List<PsiModifierList> modifierLists = new ArrayList<>();
        final PsiFile containingFile = myModifierList.getContainingFile();
        final PsiModifierList modifierList;
        if (variable != null && variable.isValid()) {
            project.getApplication().runWriteAction(() -> {
                try {
                    variable.normalizeDeclaration();
                }
                catch (IncorrectOperationException e) {
                    LOG.error(e);
                }
            });
            modifierList = variable.getModifierList();
            assert modifierList != null;
        }
        else {
            modifierList = myModifierList;
        }
        PsiElement owner = modifierList.getParent();
        if (owner instanceof PsiMethod method) {
            PsiModifierList copy = (PsiModifierList)myModifierList.copy();
            changeModifierList(copy);
            final int accessLevel = PsiUtil.getAccessLevel(copy);

            OverridingMethodsSearch.search(method, owner.getResolveScope(), true)
                .forEach(new PsiElementProcessorAdapter<>((PsiElementProcessor<PsiMethod>)inheritor -> {
                    PsiModifierList list = inheritor.getModifierList();
                    if (inheritor.getManager().isInProject(inheritor) && PsiUtil.getAccessLevel(list) < accessLevel) {
                        modifierLists.add(list);
                    }
                    return true;
                }));
        }

        if (!FileModificationService.getInstance().prepareFileForWrite(containingFile)) {
            return;
        }

        if (!modifierLists.isEmpty()) {
            if (Messages.showYesNoDialog(
                project,
                JavaQuickFixLocalize.changeInheritorsVisibilityWarningText().get(),
                JavaQuickFixLocalize.changeInheritorsVisibilityWarningTitle().get(),
                UIUtil.getQuestionIcon()
            ) == DialogWrapper.OK_EXIT_CODE) {
                project.getApplication().runWriteAction(() -> {
                    if (!FileModificationService.getInstance().preparePsiElementsForWrite(modifierLists)) {
                        return;
                    }

                    for (final PsiModifierList modifierList1 : modifierLists) {
                        changeModifierList(modifierList1);
                    }
                });
            }
        }

        project.getApplication().runWriteAction(() -> {
            changeModifierList(modifierList);
            LanguageUndoUtil.markPsiFileForUndo(containingFile);
        });
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }
}
