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
package com.intellij.java.impl.refactoring.extractInterface;

import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.impl.refactoring.extractSuperclass.ExtractSuperClassUtil;
import com.intellij.java.impl.refactoring.memberPullUp.PullUpProcessor;
import com.intellij.java.impl.refactoring.util.classMembers.MemberInfo;
import com.intellij.java.language.psi.*;
import consulo.application.ApplicationManager;
import consulo.codeEditor.Editor;
import consulo.codeEditor.ScrollType;
import consulo.dataContext.DataContext;
import consulo.ide.impl.idea.refactoring.util.DocCommentPolicy;
import consulo.language.editor.refactoring.ElementsHandler;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.findUsage.DescriptiveNameUtil;
import consulo.language.psi.*;
import consulo.language.util.IncorrectOperationException;
import consulo.localHistory.LocalHistory;
import consulo.localHistory.LocalHistoryAction;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.undoRedo.CommandProcessor;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.MultiMap;
import jakarta.annotation.Nonnull;

import javax.swing.*;

public class ExtractInterfaceHandler implements RefactoringActionHandler, ElementsHandler {
  private static final Logger LOG = Logger.getInstance(ExtractInterfaceHandler.class);

  public static final String REFACTORING_NAME = RefactoringBundle.message("extract.interface.title");


  private Project myProject;
  private PsiClass myClass;

  private String myInterfaceName;
  private MemberInfo[] mySelectedMembers;
  private PsiDirectory myTargetDir;
  private DocCommentPolicy myJavaDocPolicy;

  public void invoke(@Nonnull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    int offset = editor.getCaretModel().getOffset();
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    PsiElement element = file.findElementAt(offset);
    while (true) {
      if (element == null || element instanceof PsiFile) {
        String message = RefactoringBundle.getCannotRefactorMessage(RefactoringLocalize.errorWrongCaretPositionClass().get());
        CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.EXTRACT_INTERFACE);
        return;
      }
      if (element instanceof PsiClass && !(element instanceof PsiAnonymousClass)) {
        invoke(project, new PsiElement[]{element}, dataContext);
        return;
      }
      element = element.getParent();
    }
  }

  public void invoke(@Nonnull final Project project, @Nonnull PsiElement[] elements, DataContext dataContext) {
    if (elements.length != 1) return;

    myProject = project;
    myClass = (PsiClass) elements[0];


    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, myClass)) return;

    final ExtractInterfaceDialog dialog = new ExtractInterfaceDialog(myProject, myClass);
    dialog.show();
    if (!dialog.isOK() || !dialog.isExtractSuperclass()) return;
    final MultiMap<PsiElement, String> conflicts = new MultiMap<PsiElement, String>();
    ExtractSuperClassUtil.checkSuperAccessible(dialog.getTargetDirectory(), conflicts, myClass);
    if (!ExtractSuperClassUtil.showConflicts(dialog, conflicts, myProject)) return;
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            myInterfaceName = dialog.getExtractedSuperName();
            mySelectedMembers = ArrayUtil.toObjectArray(dialog.getSelectedMemberInfos(), MemberInfo.class);
            myTargetDir = dialog.getTargetDirectory();
            myJavaDocPolicy = new DocCommentPolicy(dialog.getDocCommentPolicy());
            try {
              doRefactoring();
            } catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        });
      }
    }, REFACTORING_NAME, null);

  }


  private void doRefactoring() throws IncorrectOperationException {
    LocalHistoryAction a = LocalHistory.getInstance().startAction(getCommandName());
    final PsiClass anInterface;
    try {
      anInterface = extractInterface(myTargetDir, myClass, myInterfaceName, mySelectedMembers, myJavaDocPolicy);
    } finally {
      a.finish();
    }

    if (anInterface != null) {
      final SmartPsiElementPointer<PsiClass> classPointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(myClass);
      final SmartPsiElementPointer<PsiClass> interfacePointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(anInterface);
      final Runnable turnRefsToSuperRunnable = new Runnable() {
        @Override
        public void run() {
          ExtractClassUtil.askAndTurnRefsToSuper(myProject, classPointer, interfacePointer);
        }
      };
      SwingUtilities.invokeLater(turnRefsToSuperRunnable);
    }
  }

  static PsiClass extractInterface(PsiDirectory targetDir,
                                   PsiClass aClass,
                                   String interfaceName,
                                   MemberInfo[] selectedMembers,
                                   DocCommentPolicy javaDocPolicy) throws IncorrectOperationException {
    PsiClass anInterface = JavaDirectoryService.getInstance().createInterface(targetDir, interfaceName);
    PsiJavaCodeReferenceElement ref = ExtractSuperClassUtil.createExtendingReference(anInterface, aClass, selectedMembers);
    final PsiReferenceList referenceList = aClass.isInterface() ? aClass.getExtendsList() : aClass.getImplementsList();
    assert referenceList != null;
    referenceList.add(ref);
    PullUpProcessor pullUpHelper = new PullUpProcessor(aClass, anInterface, selectedMembers, javaDocPolicy);
    pullUpHelper.moveMembersToBase();
    return anInterface;
  }

  private String getCommandName() {
    return RefactoringLocalize.extractInterfaceCommandName(myInterfaceName, DescriptiveNameUtil.getDescriptiveName(myClass)).get();
  }

  public boolean isEnabledOnElements(PsiElement[] elements) {
    return elements.length == 1 && elements[0] instanceof PsiClass;
  }
}
