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
import consulo.language.editor.refactoring.ui.RadioUpDownListener;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.project.Project;
import consulo.ui.ex.awt.DialogWrapper;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.awt.*;

@ExtensionImpl
public class MoveInnerToUpperOrMembersHandler extends MoveHandlerDelegate {
  public boolean canMove(final PsiElement[] elements, @Nullable final PsiElement targetContainer) {
    if (elements.length != 1) return false;
    PsiElement element = elements [0];
    return isStaticInnerClass(element) &&
           (targetContainer == null || targetContainer.equals(MoveInnerImpl.getTargetContainer((PsiClass)elements[0], false)));
  }

  private static boolean isStaticInnerClass(final PsiElement element) {
    return element instanceof PsiClass && element.getParent() instanceof PsiClass &&
           ((PsiClass) element).hasModifierProperty(PsiModifier.STATIC);
  }

  public void doMove(final Project project, final PsiElement[] elements, final PsiElement targetContainer, final MoveCallback callback) {
    SelectInnerOrMembersRefactoringDialog dialog = new SelectInnerOrMembersRefactoringDialog((PsiClass)elements[0], project);
    dialog.show();
    if (!dialog.isOK()) {
      return;
    }
    MoveHandlerDelegate delegate = dialog.getRefactoringHandler();
    if (delegate != null) {
      delegate.doMove(project, elements, targetContainer, callback);
    }
  }

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
          moveHandlerDelegate.doMove(project, new PsiElement[] { aClass }, null, null);
        }
      }
      return true;
    }
    return false;
  }

  private static class SelectInnerOrMembersRefactoringDialog extends DialogWrapper {
    private JRadioButton myRbMoveInner;
    private JRadioButton myRbMoveMembers;
    private final String myClassName;

    public SelectInnerOrMembersRefactoringDialog(final PsiClass innerClass, Project project) {
      super(project, true);
      setTitle(RefactoringLocalize.selectRefactoringTitle());
      myClassName = innerClass.getName();
      init();
    }

    protected JComponent createNorthPanel() {
      return new JLabel(RefactoringLocalize.whatWouldYouLikeToDo().get());
    }

    public JComponent getPreferredFocusedComponent() {
      return myRbMoveInner;
    }

    protected String getDimensionServiceKey() {
      return "#com.intellij.refactoring.move.MoveHandler.SelectRefactoringDialog";
    }

    protected JComponent createCenterPanel() {
      JPanel panel = new JPanel(new BorderLayout());
      myRbMoveInner = new JRadioButton();
      myRbMoveInner.setText(RefactoringLocalize.moveInnerClassToUpperLevel(myClassName).get());
      myRbMoveInner.setSelected(true);
      myRbMoveMembers = new JRadioButton();
      myRbMoveMembers.setText(RefactoringLocalize.moveInnerClassToAnotherClass(myClassName).get());


      ButtonGroup gr = new ButtonGroup();
      gr.add(myRbMoveInner);
      gr.add(myRbMoveMembers);

      new RadioUpDownListener(myRbMoveInner, myRbMoveMembers);

      Box box = Box.createVerticalBox();
      box.add(Box.createVerticalStrut(5));
      box.add(myRbMoveInner);
      box.add(myRbMoveMembers);
      panel.add(box, BorderLayout.CENTER);
      return panel;
    }

    @Nullable
    public MoveHandlerDelegate getRefactoringHandler() {
      if (myRbMoveInner.isSelected()) {
        return new MoveInnerToUpperHandler();
      }
      if (myRbMoveMembers.isSelected()) {
        return new MoveMembersHandler();
      }
      return null;
    }
  }
}
