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
package com.intellij.java.impl.refactoring.move.moveClassesOrPackages;

import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.impl.refactoring.JavaRefactoringSettings;
import com.intellij.java.impl.refactoring.MoveDestination;
import com.intellij.java.impl.refactoring.PackageWrapper;
import com.intellij.java.impl.refactoring.move.MoveClassesOrPackagesCallback;
import com.intellij.java.impl.refactoring.ui.ClassNameReferenceEditor;
import com.intellij.java.impl.refactoring.ui.PackageNameReferenceEditorCombo;
import com.intellij.java.impl.ui.ReferenceEditorComboWithBrowseButton;
import com.intellij.java.language.psi.*;
import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.application.HelpManager;
import consulo.configurable.ConfigurationException;
import consulo.document.Document;
import consulo.document.event.DocumentAdapter;
import consulo.document.event.DocumentEvent;
import consulo.language.Language;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.move.MoveCallback;
import consulo.language.editor.refactoring.move.MoveHandler;
import consulo.language.editor.refactoring.move.fileOrDirectory.MoveFilesOrDirectoriesUtil;
import consulo.language.editor.refactoring.ui.RefactoringDialog;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.editor.ui.awt.ReferenceEditorWithBrowseButton;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.project.content.scope.ProjectScopes;
import consulo.ui.ex.RecentsManager;
import consulo.ui.ex.awt.ComboboxWithBrowseButton;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.usage.UsageViewUtil;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class MoveClassesOrPackagesDialog extends RefactoringDialog {
  @NonNls
  private static final String RECENTS_KEY = "MoveClassesOrPackagesDialog.RECENTS_KEY";
  private final PsiElement[] myElementsToMove;
  private final MoveCallback myMoveCallback;

  private static final Logger LOG = Logger.getInstance(MoveClassesOrPackagesDialog.class);


  private JLabel myNameLabel;
  private ReferenceEditorComboWithBrowseButton myWithBrowseButtonReference;
  private JCheckBox myCbSearchInComments;
  private JCheckBox myCbSearchTextOccurences;
  private String myHelpID;
  private final boolean mySearchTextOccurencesEnabled;
  private final PsiManager myManager;
  private JPanel myMainPanel;
  private JRadioButton myToPackageRadioButton;
  private JRadioButton myMakeInnerClassOfRadioButton;
  private ReferenceEditorComboWithBrowseButton myClassPackageChooser;
  private JPanel myCardPanel;
  private ReferenceEditorWithBrowseButton myInnerClassChooser;
  private JPanel myMoveClassPanel;
  private JPanel myMovePackagePanel;
  private ComboboxWithBrowseButton myDestinationFolderCB;
  private JPanel myTargetPanel;
  private JLabel myTargetDestinationLabel;
  private boolean myHavePackages;
  private boolean myTargetDirectoryFixed;
  private boolean mySuggestToMoveToAnotherRoot;

  public MoveClassesOrPackagesDialog(Project project,
                                     boolean searchTextOccurences,
                                     PsiElement[] elementsToMove,
                                     final PsiElement initialTargetElement,
                                     MoveCallback moveCallback) {
    super(project, true);
    myElementsToMove = elementsToMove;
    myMoveCallback = moveCallback;
    myManager = PsiManager.getInstance(myProject);
    setTitle(MoveHandler.REFACTORING_NAME);
    mySearchTextOccurencesEnabled = searchTextOccurences;

    selectInitialCard();

    init();

    if (initialTargetElement instanceof PsiClass) {
      myMakeInnerClassOfRadioButton.setSelected(true);

      myInnerClassChooser.setText(((PsiClass) initialTargetElement).getQualifiedName());

      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          myInnerClassChooser.requestFocus();
        }
      }, Application.get().getModalityStateForComponent(myMainPanel));
    } else if (initialTargetElement instanceof PsiJavaPackage) {
      myClassPackageChooser.setText(((PsiJavaPackage) initialTargetElement).getQualifiedName());
    }

    updateControlsEnabled();
    myToPackageRadioButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateControlsEnabled();
        myClassPackageChooser.requestFocus();
      }
    });
    myMakeInnerClassOfRadioButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateControlsEnabled();
        myInnerClassChooser.requestFocus();
      }
    });
  }

  private void updateControlsEnabled() {
    myClassPackageChooser.setEnabled(myToPackageRadioButton.isSelected());
    myInnerClassChooser.setEnabled(myMakeInnerClassOfRadioButton.isSelected());
    UIUtil.setEnabled(myTargetPanel, isMoveToPackage() && getSourceRoots().length > 1 && !myTargetDirectoryFixed, true);
    validateButtons();
  }

  private void selectInitialCard() {
    myHavePackages = false;
    for (PsiElement psiElement : myElementsToMove) {
      if (!(psiElement instanceof PsiClass)) {
        myHavePackages = true;
        break;
      }
    }
    CardLayout cardLayout = (CardLayout) myCardPanel.getLayout();
    cardLayout.show(myCardPanel, myHavePackages ? "Package" : "Class");
  }

  public JComponent getPreferredFocusedComponent() {
    return myHavePackages ? myWithBrowseButtonReference.getChildComponent() : myClassPackageChooser.getChildComponent();
  }

  protected JComponent createCenterPanel() {
    final VirtualFile[] sourceRoots = getSourceRoots();
    boolean isDestinationVisible = sourceRoots.length > 1;
    myDestinationFolderCB.setVisible(isDestinationVisible);
    myTargetDestinationLabel.setVisible(isDestinationVisible);
    return null;
  }

  private void createUIComponents() {
    myMainPanel = new JPanel();

    myWithBrowseButtonReference = createPackageChooser();
    myClassPackageChooser = createPackageChooser();

    myInnerClassChooser = new ClassNameReferenceEditor(myProject, null, (GlobalSearchScope) ProjectScopes.getProjectScope(myProject));
    myInnerClassChooser.addDocumentListener(new DocumentAdapter() {
      public void documentChanged(DocumentEvent e) {
        validateButtons();
      }
    });

    // override CardLayout sizing behavior
    myCardPanel = new JPanel() {
      public Dimension getMinimumSize() {
        return myHavePackages ? myMovePackagePanel.getMinimumSize() : myMoveClassPanel.getMinimumSize();
      }

      public Dimension getPreferredSize() {
        return myHavePackages ? myMovePackagePanel.getPreferredSize() : myMoveClassPanel.getPreferredSize();
      }
    };

    myDestinationFolderCB = new DestinationFolderComboBox() {
      @Override
      public String getTargetPackage() {
        return MoveClassesOrPackagesDialog.this.getTargetPackage();
      }
    };
  }

  private ReferenceEditorComboWithBrowseButton createPackageChooser() {
    final ReferenceEditorComboWithBrowseButton packageChooser =
        new PackageNameReferenceEditorCombo("", myProject, RECENTS_KEY, RefactoringBundle.message("choose.destination.package"));
    final Document document = packageChooser.getChildComponent().getDocument();
    document.addDocumentListener(new DocumentAdapter() {
      public void documentChanged(DocumentEvent e) {
        validateButtons();
      }
    });

    return packageChooser;
  }

  protected JComponent createNorthPanel() {
    if (!mySearchTextOccurencesEnabled) {
      myCbSearchTextOccurences.setEnabled(false);
      myCbSearchTextOccurences.setVisible(false);
      myCbSearchTextOccurences.setSelected(false);
    }

    return myMainPanel;
  }

  protected String getDimensionServiceKey() {
    return myHavePackages
        ? "#com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesDialog.packages"
        : "#com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesDialog.classes";
  }

  public void setData(PsiElement[] psiElements,
                      String targetPackageName,
                      PsiDirectory initialTargetDirectory,
                      boolean isTargetDirectoryFixed,
                      boolean suggestToMoveToAnotherRoot,
                      boolean searchInComments,
                      boolean searchForTextOccurences,
                      String helpID) {
    myTargetDirectoryFixed = isTargetDirectoryFixed;
    mySuggestToMoveToAnotherRoot = suggestToMoveToAnotherRoot;
    if (targetPackageName.length() != 0) {
      myWithBrowseButtonReference.prependItem(targetPackageName);
      myClassPackageChooser.prependItem(targetPackageName);
    }

    String nameFromCallback = myMoveCallback instanceof MoveClassesOrPackagesCallback
        ? ((MoveClassesOrPackagesCallback) myMoveCallback).getElementsToMoveName()
        : null;
    if (nameFromCallback != null) {
      myNameLabel.setText(nameFromCallback);
    } else if (psiElements.length == 1) {
      PsiElement firstElement = psiElements[0];
      if (firstElement instanceof PsiClass) {
        LOG.assertTrue(!MoveClassesOrPackagesImpl.isClassInnerOrLocal((PsiClass) firstElement));
      } else {
        PsiElement parent = firstElement.getParent();
        LOG.assertTrue(parent != null);
      }
      myNameLabel.setText(RefactoringBundle.message("move.single.class.or.package.name.label", UsageViewUtil.getType(firstElement),
          UsageViewUtil.getLongName(firstElement)));
    } else if (psiElements.length > 1) {
      myNameLabel.setText(psiElements[0] instanceof PsiClass
          ? RefactoringBundle.message("move.specified.classes")
          : RefactoringBundle.message("move.specified.packages"));
    }
    selectInitialCard();

    myCbSearchInComments.setSelected(searchInComments);
    myCbSearchTextOccurences.setSelected(searchForTextOccurences);

    if (initialTargetDirectory != null &&
        JavaMoveClassesOrPackagesHandler.packageHasMultipleDirectoriesInModule(myProject, initialTargetDirectory)) {
      initialTargetDirectory = null;
    }
    ((DestinationFolderComboBox) myDestinationFolderCB).setData(myProject, initialTargetDirectory,
        s -> setErrorText(s), myHavePackages ? myWithBrowseButtonReference.getChildComponent() : myClassPackageChooser.getChildComponent());
    UIUtil.setEnabled(myTargetPanel, getSourceRoots().length > 0 && isMoveToPackage() && !isTargetDirectoryFixed, true);
    validateButtons();
    myHelpID = helpID;
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(myHelpID);
  }

  protected final boolean isSearchInComments() {
    return myCbSearchInComments.isSelected();
  }

  @Override
  protected void canRun() throws ConfigurationException {
    if (isMoveToPackage()) {
      String name = getTargetPackage().trim();
      if (name.length() != 0 && !PsiNameHelper.getInstance(myManager.getProject()).isQualifiedName(name)) {
        throw new ConfigurationException("\'" + name + "\' is invalid destination package name");
      }
    } else {
      if (findTargetClass() == null) throw new ConfigurationException("Destination class not found");
      final String validationError = getValidationError();
      if (validationError != null) throw new ConfigurationException(validationError);
    }
  }

  protected void validateButtons() {
    super.validateButtons();
    setErrorText(getValidationError());
  }

  @Nullable
  private String getValidationError() {
    if (!isMoveToPackage()) {
      return verifyInnerClassDestination();
    }
    return null;
  }

  @Nullable
  private PsiClass findTargetClass() {
    String name = myInnerClassChooser.getText().trim();
    return JavaPsiFacade.getInstance(myManager.getProject()).findClass(name, (GlobalSearchScope) ProjectScopes.getProjectScope(myProject));
  }

  protected boolean isMoveToPackage() {
    return myHavePackages || myToPackageRadioButton.isSelected();
  }

  protected String getTargetPackage() {
    return myHavePackages ? myWithBrowseButtonReference.getText() : myClassPackageChooser.getText();
  }

  @Nullable
  private static String verifyDestinationForElement(final PsiElement element, final MoveDestination moveDestination) {
    final String message;
    if (element instanceof PsiDirectory) {
      message = moveDestination.verify((PsiDirectory) element);
    } else if (element instanceof PsiJavaPackage) {
      message = moveDestination.verify((PsiJavaPackage) element);
    } else {
      message = moveDestination.verify(element.getContainingFile());
    }
    return message;
  }

  protected void doAction() {
    if (isMoveToPackage()) {
      invokeMoveToPackage();
    } else {
      invokeMoveToInner();
    }
  }

  private void invokeMoveToPackage() {
    final MoveDestination destination = selectDestination();
    if (destination == null) return;

    saveRefactoringSettings();
    for (final PsiElement element : myElementsToMove) {
      String message = verifyDestinationForElement(element, destination);
      if (message != null) {
        String helpId = HelpID.getMoveHelpID(myElementsToMove[0]);
        CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("error.title"), message, helpId, getProject());
        return;
      }
    }
    try {
      for (PsiElement element : myElementsToMove) {
        if (element instanceof PsiClass) {
          final PsiClass aClass = (PsiClass) element;
          LOG.assertTrue(aClass.isPhysical(), aClass);
          /*PsiElement toAdd;
          if (aClass.getContainingFile() instanceof PsiJavaFile && ((PsiJavaFile)aClass.getContainingFile()).getClasses().length > 1) {
            toAdd = aClass;
          }
          else {
            toAdd = aClass.getContainingFile();
          }*/

          final PsiDirectory targetDirectory = destination.getTargetIfExists(element.getContainingFile());
          if (targetDirectory != null) {
            MoveFilesOrDirectoriesUtil.checkMove(aClass, targetDirectory);
          }
        }
      }

      MoveClassesOrPackagesProcessor processor = createMoveToPackageProcessor(destination, myElementsToMove, myMoveCallback);
      if (processor.verifyValidPackageName()) {
        invokeRefactoring(processor);
      }
    } catch (IncorrectOperationException e) {
      String helpId = HelpID.getMoveHelpID(myElementsToMove[0]);
      CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("error.title"), e.getMessage(), helpId, getProject());
    }
  }

  //for scala plugin
  protected MoveClassesOrPackagesProcessor createMoveToPackageProcessor(MoveDestination destination, final PsiElement[] elementsToMove,
                                                                        final MoveCallback callback) {
    return new MoveClassesOrPackagesProcessor(getProject(), elementsToMove, destination, isSearchInComments(), isSearchInNonJavaFiles(), callback);
  }

  private void saveRefactoringSettings() {
    final JavaRefactoringSettings refactoringSettings = JavaRefactoringSettings.getInstance();
    final boolean searchInComments = isSearchInComments();
    final boolean searchForTextOccurences = isSearchInNonJavaFiles();
    refactoringSettings.MOVE_SEARCH_IN_COMMENTS = searchInComments;
    refactoringSettings.MOVE_SEARCH_FOR_TEXT = searchForTextOccurences;
    refactoringSettings.MOVE_PREVIEW_USAGES = isPreviewUsages();
  }

  @Nullable
  private String verifyInnerClassDestination() {
    PsiClass targetClass = findTargetClass();
    if (targetClass == null) return null;

    for (PsiElement element : myElementsToMove) {
      if (PsiTreeUtil.isAncestor(element, targetClass, false)) {
        return RefactoringBundle.message("move.class.to.inner.move.to.self.error");
      }
      final Language targetClassLanguage = targetClass.getLanguage();
      if (!element.getLanguage().equals(targetClassLanguage)) {
        return RefactoringBundle.message("move.to.different.language", UsageViewUtil.getType(element),
            ((PsiClass) element).getQualifiedName(), targetClass.getQualifiedName());
      }
    }

    while (targetClass != null) {
      if (targetClass.getContainingClass() != null && !targetClass.hasModifierProperty(PsiModifier.STATIC)) {
        return RefactoringBundle.message("move.class.to.inner.nonstatic.error");
      }
      targetClass = targetClass.getContainingClass();
    }

    return null;
  }

  private void invokeMoveToInner() {
    saveRefactoringSettings();
    final PsiClass targetClass = findTargetClass();
    final PsiClass[] classesToMove = new PsiClass[myElementsToMove.length];
    for (int i = 0; i < myElementsToMove.length; i++) {
      classesToMove[i] = (PsiClass) myElementsToMove[i];
    }
    invokeRefactoring(createMoveToInnerProcessor(targetClass, classesToMove, myMoveCallback));
  }

  //for scala plugin
  protected MoveClassToInnerProcessor createMoveToInnerProcessor(PsiClass destination, @Nonnull PsiClass[] classesToMove, @Nullable final MoveCallback callback) {
    return new MoveClassToInnerProcessor(getProject(), classesToMove, destination, isSearchInComments(), isSearchInNonJavaFiles(), callback);
  }

  protected final boolean isSearchInNonJavaFiles() {
    return myCbSearchTextOccurences.isSelected();
  }

  @Nullable
  private MoveDestination selectDestination() {
    final String packageName = getTargetPackage().trim();
    if (packageName.length() > 0 && !PsiNameHelper.getInstance(myManager.getProject()).isQualifiedName(packageName)) {
      Messages.showErrorDialog(myProject, RefactoringBundle.message("please.enter.a.valid.target.package.name"),
          RefactoringBundle.message("move.title"));
      return null;
    }
    RecentsManager.getInstance(myProject).registerRecentEntry(RECENTS_KEY, packageName);
    PackageWrapper targetPackage = new PackageWrapper(myManager, packageName);
    if (!targetPackage.exists()) {
      final int ret = Messages.showYesNoDialog(myProject, RefactoringBundle.message("package.does.not.exist", packageName),
          RefactoringBundle.message("move.title"), Messages.getQuestionIcon());
      if (ret != 0) return null;
    }

    return ((DestinationFolderComboBox) myDestinationFolderCB).selectDirectory(targetPackage, mySuggestToMoveToAnotherRoot);
  }

  private VirtualFile[] getSourceRoots() {
    return ProjectRootManager.getInstance(myProject).getContentSourceRoots();
  }
}
