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
package com.intellij.java.impl.ide.util;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.search.searches.DeepestSuperMethodsSearch;
import consulo.annotation.access.RequiredReadAction;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorPopupHelper;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.findUsage.DescriptiveNameUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.resolve.PsiElementProcessor;
import consulo.language.psi.util.SymbolPresentationUtil;
import consulo.localize.LocalizeValue;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.JBList;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.awt.popup.AWTPopupFactory;
import consulo.ui.ex.popup.JBPopup;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.util.collection.ArrayUtil;
import jakarta.annotation.Nonnull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class SuperMethodWarningUtil {
    private SuperMethodWarningUtil() {
    }

    @Nonnull
    @RequiredUIAccess
    public static PsiMethod[] checkSuperMethods(PsiMethod method, @Nonnull LocalizeValue actionString) {
        return checkSuperMethods(method, actionString, null);
    }

    @Nonnull
    @RequiredUIAccess
    public static PsiMethod[] checkSuperMethods(PsiMethod method, @Nonnull LocalizeValue actionString, Collection<PsiElement> ignore) {
        PsiClass aClass = method.getContainingClass();
        if (aClass == null) {
            return new PsiMethod[]{method};
        }

        Collection<PsiMethod> superMethods = DeepestSuperMethodsSearch.search(method).findAll();
        if (ignore != null) {
            superMethods.removeAll(ignore);
        }

        if (superMethods.isEmpty()) {
            return new PsiMethod[]{method};
        }

        Set<String> superClasses = new HashSet<>();
        boolean superAbstract = false;
        boolean parentInterface = false;
        for (PsiMethod superMethod : superMethods) {
            PsiClass containingClass = superMethod.getContainingClass();
            superClasses.add(containingClass.getQualifiedName());
            boolean isInterface = containingClass.isInterface();
            superAbstract |= isInterface || superMethod.isAbstract();
            parentInterface |= isInterface;
        }

        SuperMethodWarningDialog dialog = new SuperMethodWarningDialog(
            method.getProject(),
            DescriptiveNameUtil.getDescriptiveName(method),
            actionString,
            superAbstract,
            parentInterface,
            aClass.isInterface(),
            ArrayUtil.toStringArray(superClasses)
        );
        dialog.show();

        if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
            return superMethods.toArray(new PsiMethod[superMethods.size()]);
        }
        if (dialog.getExitCode() == SuperMethodWarningDialog.NO_EXIT_CODE) {
            return new PsiMethod[]{method};
        }

        return PsiMethod.EMPTY_ARRAY;
    }

    @RequiredUIAccess
    public static PsiMethod checkSuperMethod(PsiMethod method, @Nonnull LocalizeValue actionString) {
        PsiClass aClass = method.getContainingClass();
        if (aClass == null) {
            return method;
        }

        PsiMethod superMethod = method.findDeepestSuperMethod();
        if (superMethod == null) {
            return method;
        }

        if (method.getApplication().isUnitTestMode()) {
            return superMethod;
        }

        PsiClass containingClass = superMethod.getContainingClass();

        SuperMethodWarningDialog dialog = new SuperMethodWarningDialog(
            method.getProject(),
            DescriptiveNameUtil.getDescriptiveName(method),
            actionString,
            containingClass.isInterface() || superMethod.isAbstract(),
            containingClass.isInterface(),
            aClass.isInterface(),
            containingClass.getQualifiedName()
        );
        dialog.show();

        if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
            return superMethod;
        }
        if (dialog.getExitCode() == SuperMethodWarningDialog.NO_EXIT_CODE) {
            return method;
        }

        return null;
    }

    @RequiredReadAction
    public static void checkSuperMethod(
        PsiMethod method,
        @Nonnull LocalizeValue actionString,
        PsiElementProcessor<PsiMethod> processor,
        Editor editor
    ) {
        PsiClass aClass = method.getContainingClass();
        if (aClass == null) {
            processor.execute(method);
            return;
        }

        PsiMethod superMethod = method.findDeepestSuperMethod();
        if (superMethod == null) {
            processor.execute(method);
            return;
        }

        PsiClass containingClass = superMethod.getContainingClass();
        if (containingClass == null) {
            processor.execute(method);
            return;
        }

        if (method.getApplication().isUnitTestMode()) {
            processor.execute(superMethod);
            return;
        }

        PsiMethod[] methods = new PsiMethod[]{superMethod, method};
        String renameBase = actionString + " base method";
        String renameCurrent = actionString + " only current method";
        JBList<String> list = new JBList<>(renameBase, renameCurrent);
        JBPopup popup = ((AWTPopupFactory) JBPopupFactory.getInstance()).createListPopupBuilder(list)
            .setItemChoosenCallback(() -> {
                if (list.getSelectedValue() instanceof String stringValue) {
                    processor.execute(methods[stringValue.equals(renameBase) ? 0 : 1]);
                }
            })
            .setMovable(false)
            .setTitle(
                method.getName() + (containingClass.isInterface() && !aClass.isInterface() ? " implements" : " overrides") +
                    " method of " + SymbolPresentationUtil.getSymbolPresentableText(containingClass)
            )
            .setResizable(false)
            .setRequestFocus(true)
            .createPopup();

        EditorPopupHelper.getInstance().showPopupInBestPositionFor(editor, popup);
    }

    @RequiredUIAccess
    public static int askWhetherShouldAnnotateBaseMethod(@Nonnull PsiMethod method, @Nonnull PsiMethod superMethod) {
        LocalizeValue implement = !method.isAbstract() && superMethod.isAbstract()
            ? InspectionLocalize.inspectionAnnotateQuickfixImplements()
            : InspectionLocalize.inspectionAnnotateQuickfixOverrides();
        LocalizeValue message = InspectionLocalize.inspectionAnnotateQuickfixOverriddenMethodMessages(
            DescriptiveNameUtil.getDescriptiveName(method),
            implement,
            DescriptiveNameUtil.getDescriptiveName(superMethod)
        );
        LocalizeValue title = InspectionLocalize.inspectionAnnotateQuickfixOverriddenMethodWarning();
        return Messages.showYesNoCancelDialog(method.getProject(), message.get(), title.get(), UIUtil.getQuestionIcon());
    }
}