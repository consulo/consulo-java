/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.move.moveClassesOrPackages;

import com.intellij.java.impl.refactoring.JavaRefactoringFactory;
import com.intellij.java.impl.refactoring.JavaRefactoringSettings;
import com.intellij.java.impl.refactoring.MoveDestination;
import com.intellij.java.language.psi.JavaDirectoryService;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaPackage;
import consulo.application.ApplicationManager;
import consulo.application.util.function.Computable;
import consulo.fileChooser.FileChooserDescriptor;
import consulo.fileChooser.FileChooserDescriptorFactory;
import consulo.fileChooser.IdeaFileChooser;
import consulo.ide.impl.idea.openapi.util.io.FileUtil;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.move.MoveCallback;
import consulo.language.editor.refactoring.move.MoveHandler;
import consulo.language.editor.refactoring.util.DirectoryUtil;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.TextFieldWithBrowseButton;
import consulo.ui.ex.awt.event.DocumentAdapter;
import consulo.usage.UsageViewUtil;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashSet;
import java.util.Set;

/**
 * @author ven
 */
public class MoveClassesOrPackagesToNewDirectoryDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance("com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesToNewDirectoryDialog");

  private final PsiDirectory myDirectory;
  private final PsiElement[] myElementsToMove;
  private final MoveCallback myMoveCallback;

  public MoveClassesOrPackagesToNewDirectoryDialog(@Nonnull final PsiDirectory directory, PsiElement[] elementsToMove,
                                                   final MoveCallback moveCallback) {
    this(directory, elementsToMove, true, moveCallback);
  }

  public MoveClassesOrPackagesToNewDirectoryDialog(@Nonnull final PsiDirectory directory, PsiElement[] elementsToMove,
                                                   boolean canShowPreserveSourceRoots,
                                                   final MoveCallback moveCallback) {
    super(false);
    setTitle(MoveHandler.REFACTORING_NAME);
    myDirectory = directory;
    myElementsToMove = elementsToMove;
    myMoveCallback = moveCallback;
    myDestDirectoryField.setText(FileUtil.toSystemDependentName(directory.getVirtualFile().getPath()));
    final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    myDestDirectoryField.getButton().addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final VirtualFile file = IdeaFileChooser.chooseFile(descriptor, myDirectory.getProject(), directory.getVirtualFile());
        if (file != null) {
          myDestDirectoryField.setText(FileUtil.toSystemDependentName(file.getPath()));
        }
      }
    });
    if (elementsToMove.length == 1) {
      PsiElement firstElement = elementsToMove[0];
      myNameLabel.setText(RefactoringLocalize.moveSingleClassOrPackageNameLabel(
        UsageViewUtil.getType(firstElement),
        UsageViewUtil.getLongName(firstElement)
      ).get());
    }
    else if (elementsToMove.length > 1) {
      myNameLabel.setText(
        elementsToMove[0] instanceof PsiClass
          ? RefactoringLocalize.moveSpecifiedClasses().get()
          : RefactoringLocalize.moveSpecifiedPackages().get()
      );
    }
    final JavaRefactoringSettings refactoringSettings = JavaRefactoringSettings.getInstance();
    mySearchInCommentsAndStringsCheckBox.setSelected(refactoringSettings.MOVE_SEARCH_IN_COMMENTS);
    mySearchForTextOccurrencesCheckBox.setSelected(refactoringSettings.MOVE_SEARCH_FOR_TEXT);

    myDestDirectoryField.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      public void textChanged(DocumentEvent event) {
        setOKActionEnabled(myDestDirectoryField.getText().length() > 0);
      }
    });

    if (canShowPreserveSourceRoots) {
      final Set<VirtualFile> sourceRoots = new HashSet<VirtualFile>();
      final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(directory.getProject()).getFileIndex();
      final Module destinationModule = fileIndex.getModuleForFile(directory.getVirtualFile());
      boolean sameModule = true;
      for (PsiElement element : elementsToMove) {
        if (element instanceof PsiJavaPackage) {
          for (PsiDirectory psiDirectory : ((PsiJavaPackage)element).getDirectories()) {
            final VirtualFile virtualFile = psiDirectory.getVirtualFile();
            sourceRoots.add(fileIndex.getSourceRootForFile(virtualFile));
            //sameModule &= destinationModule == fileIndex.getModuleForFile(virtualFile);
          }
        } else if (element instanceof PsiClass) {
          final VirtualFile virtualFile = element.getContainingFile().getVirtualFile();
          LOG.assertTrue(virtualFile != null);
          sourceRoots.add(fileIndex.getSourceRootForFile(virtualFile));
          sameModule &= destinationModule == fileIndex.getModuleForFile(virtualFile);
        }
      }
      myPreserveSourceRoot.setVisible(sourceRoots.size() > 1);
      myPreserveSourceRoot.setSelected(sameModule);
    }
    init();
  }

  private TextFieldWithBrowseButton myDestDirectoryField;
  private JCheckBox mySearchForTextOccurrencesCheckBox;
  private JCheckBox mySearchInCommentsAndStringsCheckBox;
  private JPanel myRootPanel;
  private JLabel myNameLabel;
  private JCheckBox myPreserveSourceRoot;

  private boolean isSearchInNonJavaFiles() {
    return mySearchForTextOccurrencesCheckBox.isSelected();
  }

  private boolean isSearchInComments() {
    return mySearchInCommentsAndStringsCheckBox.isSelected();
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myRootPanel;
  }

  @RequiredUIAccess
  protected void doOKAction() {
    final String path = FileUtil.toSystemIndependentName(myDestDirectoryField.getText());
    final Project project = myDirectory.getProject();
    PsiDirectory directory = ApplicationManager.getApplication().runWriteAction(new Computable<PsiDirectory>() {
      public PsiDirectory compute() {
        try {
          return DirectoryUtil.mkdirs(PsiManager.getInstance(project), path);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
          return null;
        }
      }
    });
    if (directory == null) {
      Messages.showErrorDialog(
        project,
        RefactoringLocalize.cannotFindOrCreateDestinationDirectory().get(),
        RefactoringLocalize.cannotMove().get()
      );
      return;
    }

    super.doOKAction();
    final PsiJavaPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);
    if (aPackage == null) {
      Messages.showErrorDialog(
        project,
        RefactoringLocalize.destinationDirectoryDoesNotCorrespondToAnyPackage().get(),
        RefactoringLocalize.cannotMove().get()
      );
      return;
    }

    final JavaRefactoringSettings refactoringSettings = JavaRefactoringSettings.getInstance();
    final boolean searchInComments = isSearchInComments();
    final boolean searchForTextOccurences = isSearchInNonJavaFiles();
    refactoringSettings.MOVE_SEARCH_IN_COMMENTS = searchInComments;
    refactoringSettings.MOVE_SEARCH_FOR_TEXT = searchForTextOccurences;

    performRefactoring(project, directory, aPackage, searchInComments, searchForTextOccurences);
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myDestDirectoryField.getTextField();
  }

  @RequiredUIAccess
  protected void performRefactoring(
    Project project, PsiDirectory directory, PsiJavaPackage aPackage,
    boolean searchInComments,
    boolean searchForTextOccurences
  ) {
    final VirtualFile sourceRoot = ProjectRootManager.getInstance(project).getFileIndex().getSourceRootForFile(directory.getVirtualFile());
    if (sourceRoot == null) {
      Messages.showErrorDialog(
        project,
        RefactoringLocalize.destinationDirectoryDoesNotCorrespondToAnyPackage().get(),
        RefactoringLocalize.cannotMove().get()
      );
      return;
    }
    final JavaRefactoringFactory factory = JavaRefactoringFactory.getInstance(project);
    final MoveDestination destination = myPreserveSourceRoot.isSelected() && myPreserveSourceRoot.isVisible()
                                        ? factory.createSourceFolderPreservingMoveDestination(aPackage.getQualifiedName())
                                        : factory.createSourceRootMoveDestination(aPackage.getQualifiedName(), sourceRoot);

    MoveClassesOrPackagesProcessor processor = new MoveClassesOrPackagesProcessor(myDirectory.getProject(), myElementsToMove, destination,
                                                                                  searchInComments, searchForTextOccurences,
                                                                                  myMoveCallback);
    if (processor.verifyValidPackageName()) {
      processor.setPrepareSuccessfulSwingThreadCallback(new Runnable() {
        @Override
        public void run() {
        }
      });

      processor.run();
    }
  }
}



