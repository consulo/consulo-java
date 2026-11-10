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

import consulo.annotation.DeprecationInfo;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.search.searches.DeepestSuperMethodsSearch;
import consulo.annotation.access.RequiredReadAction;
import consulo.codeEditor.Editor;
import consulo.ide.localize.IdeLocalize;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.java.localize.JavaLocalize;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.findUsage.DescriptiveNameUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.resolve.PsiElementProcessor;
import consulo.language.psi.util.SymbolPresentationUtil;
import consulo.localize.LocalizeValue;
import consulo.ui.MessageBoxBuilder;
import consulo.ui.MessageButtonRole;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.popup.BaseListPopupStep;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.ui.ex.popup.ListPopup;
import consulo.ui.ex.popup.PopupStep;
import consulo.util.collection.ArrayUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class SuperMethodWarningUtil {
    private SuperMethodWarningUtil() {
    }

    @Deprecated
    @DeprecationInfo("Use checkSuperMethodsAsync")
    @RequiredUIAccess
    public static PsiMethod[] checkSuperMethods(PsiMethod method, LocalizeValue actionString) {
        return checkSuperMethods(method, actionString, null);
    }

    @Deprecated
    @DeprecationInfo("Use checkSuperMethodsAsync")
    @RequiredUIAccess
    public static PsiMethod[] checkSuperMethods(PsiMethod method, LocalizeValue actionString, @Nullable Collection<PsiElement> ignore) {
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

    /**
     * Asks whether to act on the deepest super methods instead of the given one.
     *
     * @return the methods to act on, empty when the user cancelled
     */
    @RequiredUIAccess
    public static CompletableFuture<PsiMethod[]> checkSuperMethodsAsync(
        PsiMethod method,
        LocalizeValue actionString,
        @Nullable Collection<PsiElement> ignore
    ) {
        PsiClass aClass = method.getContainingClass();
        if (aClass == null) {
            return CompletableFuture.completedFuture(new PsiMethod[]{method});
        }

        Collection<PsiMethod> superMethods = DeepestSuperMethodsSearch.search(method).findAll();
        if (ignore != null) {
            superMethods.removeAll(ignore);
        }

        if (superMethods.isEmpty()) {
            return CompletableFuture.completedFuture(new PsiMethod[]{method});
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

        List<LocalizeValue> lines = messageLines(
            DescriptiveNameUtil.getDescriptiveName(method),
            actionString,
            superAbstract,
            parentInterface,
            aClass.isInterface(),
            ArrayUtil.toStringArray(superClasses)
        );
        return MessageBoxBuilder.<PsiMethod[]>create()
            .asQuestion()
            .title(IdeLocalize.titleWarning())
            .text(LocalizeValue.join("\n", lines.toArray(LocalizeValue[]::new)))
            .button(MessageButtonRole.YES, superMethods.toArray(PsiMethod.EMPTY_ARRAY))
            .asDefaultButton()
            .button(MessageButtonRole.NO, new PsiMethod[]{method})
            .button(MessageButtonRole.CANCEL, PsiMethod.EMPTY_ARRAY)
            .asExitButton()
            .showAsync(method.getProject());
    }

    @Deprecated
    @DeprecationInfo("Use checkSuperMethodAsync")
    @RequiredUIAccess
    public static PsiMethod checkSuperMethod(PsiMethod method, LocalizeValue actionString) {
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

    /**
     * Asks whether to act on the deepest super method instead of the given one.
     *
     * @return the method to act on, {@code null} when the user cancelled
     */
    @RequiredUIAccess
    public static CompletableFuture<@Nullable PsiMethod> checkSuperMethodAsync(PsiMethod method, LocalizeValue actionString) {
        PsiClass aClass = method.getContainingClass();
        if (aClass == null) {
            return CompletableFuture.completedFuture(method);
        }

        PsiMethod superMethod = method.findDeepestSuperMethod();
        if (superMethod == null) {
            return CompletableFuture.completedFuture(method);
        }

        if (method.getApplication().isUnitTestMode()) {
            return CompletableFuture.completedFuture(superMethod);
        }

        PsiClass containingClass = superMethod.getContainingClass();
        if (containingClass == null) {
            return CompletableFuture.completedFuture(method);
        }

        List<LocalizeValue> lines = messageLines(
            DescriptiveNameUtil.getDescriptiveName(method),
            actionString,
            containingClass.isInterface() || superMethod.isAbstract(),
            containingClass.isInterface(),
            aClass.isInterface(),
            containingClass.getQualifiedName()
        );
        return MessageBoxBuilder.<@Nullable PsiMethod>create()
            .asQuestion()
            .title(IdeLocalize.titleWarning())
            .text(LocalizeValue.join("\n", lines.toArray(LocalizeValue[]::new)))
            .button(MessageButtonRole.YES, superMethod)
            .asDefaultButton()
            .button(MessageButtonRole.NO, method)
            .button(MessageButtonRole.CANCEL, (PsiMethod) null)
            .asExitButton()
            .showAsync(method.getProject());
    }

    @RequiredReadAction
    public static void checkSuperMethod(
        PsiMethod method,
        LocalizeValue actionString,
        PsiElementProcessor<PsiMethod> processor,
        @Nullable Editor editor
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

        if (editor == null) {
            checkSuperMethodAsync(method, actionString).thenAccept(chosen -> {
                if (chosen != null) {
                    processor.execute(chosen);
                }
            });
            return;
        }

        String renameBase = actionString + " base method";
        String renameCurrent = actionString + " only current method";
        BaseListPopupStep<String> step = new BaseListPopupStep<>(
            method.getName() + (containingClass.isInterface() && !aClass.isInterface() ? " implements" : " overrides") +
                " method of " + SymbolPresentationUtil.getSymbolPresentableText(containingClass),
            renameBase,
            renameCurrent
        ) {
            @Override
            public @Nullable PopupStep onChosen(String selectedValue, boolean finalChoice) {
                return doFinalStep(() -> processor.execute(renameBase.equals(selectedValue) ? superMethod : method));
            }
        };
        ListPopup popup = JBPopupFactory.getInstance().createListPopup(step);
        editor.showPopupInBestPositionFor(popup);
    }

    @RequiredUIAccess
    public static int askWhetherShouldAnnotateBaseMethod(PsiMethod method, PsiMethod superMethod) {
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

    static List<LocalizeValue> messageLines(
        String name,
        LocalizeValue actionString,
        boolean isSuperAbstract,
        boolean isParentInterface,
        boolean isContainedInInterface,
        String... classNames
    ) {
        List<LocalizeValue> lines = new ArrayList<>();
        int classType = isParentInterface ? 0 : 1;
        lines.add(JavaLocalize.labelMethod(name));
        if (classNames.length == 1) {
            String className = classNames[0];
            lines.add(
                isContainedInInterface || !isSuperAbstract
                    ? JavaLocalize.labelOverridesMethodOfClassOrInterfaceName(classType, className)
                    : JavaLocalize.labelImplementsMethodOfClassOrInterfaceName(classType, className)
            );
        }
        else {
            lines.add(JavaLocalize.labelImplementsMethodOfInterfaces());
            for (String className : classNames) {
                lines.add(LocalizeValue.of("    " + className));
            }
        }
        lines.add(JavaLocalize.promptDoYouWantToActionVerbTheMethodFromClass(
            actionString.orIfEmpty(RefactoringLocalize.toRefactor()),
            classNames.length > 1 ? 2 : 1
        ));
        return lines;
    }
}
