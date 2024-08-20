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
package com.intellij.java.impl.refactoring.move.moveInner;

import com.intellij.java.impl.refactoring.move.moveClassesOrPackages.JavaMoveClassesOrPackagesHandler;
import com.intellij.java.impl.refactoring.move.moveMembers.MoveMembersHandler;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiModifier;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.dataContext.DataContext;
import consulo.externalService.statistic.FeatureUsageTracker;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.move.MoveCallback;
import consulo.language.editor.refactoring.move.MoveHandlerDelegate;
import consulo.language.editor.ui.RadioUpDownListener;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.project.Project;
import consulo.ui.Label;
import consulo.ui.RadioButton;
import consulo.ui.ValueGroup;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.layout.LabeledLayout;
import consulo.ui.layout.VerticalLayout;
import jakarta.annotation.Nullable;

import javax.swing.*;

@ExtensionImpl
public class MoveInnerToUpperOrMembersHandler extends MoveHandlerDelegate {
    @Override
    public boolean canMove(final PsiElement[] elements, @Nullable final PsiElement targetContainer) {
        if (elements.length != 1) {
            return false;
        }
        PsiElement element = elements[0];
        return isStaticInnerClass(element) &&
            (targetContainer == null || targetContainer.equals(MoveInnerImpl.getTargetContainer((PsiClass) elements[0], false)));
    }

    private static boolean isStaticInnerClass(final PsiElement element) {
        return element instanceof PsiClass && element.getParent() instanceof PsiClass &&
            ((PsiClass) element).hasModifierProperty(PsiModifier.STATIC);
    }

    @Override
    public void doMove(final Project project, final PsiElement[] elements, final PsiElement targetContainer, final MoveCallback callback) {
        SelectInnerOrMembersRefactoringDialog dialog = new SelectInnerOrMembersRefactoringDialog((PsiClass) elements[0], project);
        dialog.show();
        if (!dialog.isOK()) {
            return;
        }
        MoveHandlerDelegate delegate = dialog.getRefactoringHandler();
        if (delegate != null) {
            delegate.doMove(project, elements, targetContainer, callback);
        }
    }

    @Override
    public boolean tryToMove(final PsiElement element, final Project project, final DataContext dataContext, final PsiReference reference,
                             final Editor editor) {
        if (isStaticInnerClass(element) && !JavaMoveClassesOrPackagesHandler.isReferenceInAnonymousClass(reference)) {
            FeatureUsageTracker.getInstance().triggerFeatureUsed("refactoring.move.moveInner");
            PsiClass aClass = (PsiClass) element;
            SelectInnerOrMembersRefactoringDialog dialog = new SelectInnerOrMembersRefactoringDialog(aClass, project);
            dialog.show();
            if (dialog.isOK()) {
                final MoveHandlerDelegate moveHandlerDelegate = dialog.getRefactoringHandler();
                if (moveHandlerDelegate != null) {
                    moveHandlerDelegate.doMove(project, new PsiElement[]{aClass}, null, null);
                }
            }
            return true;
        }
        return false;
    }

    private static class SelectInnerOrMembersRefactoringDialog extends DialogWrapper {
        private RadioButton myRbMoveInner;
        private RadioButton myRbMoveMembers;
        private final String myClassName;

        public SelectInnerOrMembersRefactoringDialog(final PsiClass innerClass, Project project) {
            super(project, true);
            setTitle(RefactoringLocalize.selectRefactoringTitle());
            myClassName = innerClass.getName();
            init();
        }

        @Override
        public JComponent getPreferredFocusedComponent() {
            return (JComponent) TargetAWT.to(myRbMoveInner);
        }

        @Override
        protected String getDimensionServiceKey() {
            return "#com.intellij.refactoring.move.MoveHandler.SelectRefactoringDialog";
        }

        @Override
        protected JComponent createCenterPanel() {
            myRbMoveInner = RadioButton.create(RefactoringLocalize.moveInnerClassToUpperLevel(myClassName));
            myRbMoveInner.setValue(true);
            myRbMoveMembers = RadioButton.create(RefactoringLocalize.moveInnerClassToAnotherClass(myClassName));

            ValueGroup<Boolean> group = ValueGroup.createBool();
            group.add(myRbMoveInner);
            group.add(myRbMoveMembers);

            new RadioUpDownListener((JRadioButton) TargetAWT.to(myRbMoveInner), (JRadioButton) TargetAWT.to(myRbMoveMembers));

            VerticalLayout layout = VerticalLayout.create();
            layout.add(myRbMoveInner);
            layout.add(myRbMoveMembers);

            LabeledLayout labeledLayout = LabeledLayout.create(RefactoringLocalize.whatWouldYouLikeToDo(), layout);
            return (JComponent) TargetAWT.to(labeledLayout);
        }

        @Nullable
        public MoveHandlerDelegate getRefactoringHandler() {
            if (myRbMoveInner.getValueOrError()) {
                return new MoveInnerToUpperHandler();
            }
            if (myRbMoveMembers.getValueOrError()) {
                return new MoveMembersHandler();
            }
            return null;
        }
    }
}
