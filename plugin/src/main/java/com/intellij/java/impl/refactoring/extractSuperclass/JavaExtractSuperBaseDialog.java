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
package com.intellij.java.impl.refactoring.extractSuperclass;

import com.intellij.java.impl.refactoring.MoveDestination;
import com.intellij.java.impl.refactoring.PackageWrapper;
import com.intellij.java.impl.refactoring.move.moveClassesOrPackages.DestinationFolderComboBox;
import com.intellij.java.impl.refactoring.ui.PackageNameReferenceEditorCombo;
import com.intellij.java.impl.refactoring.util.RefactoringMessageUtil;
import com.intellij.java.impl.refactoring.util.classMembers.MemberInfo;
import com.intellij.java.language.psi.*;
import consulo.application.ApplicationManager;
import consulo.project.Project;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.ui.ex.awt.ComponentWithBrowseButton;
import consulo.ui.ex.awt.JBLabel;
import consulo.util.lang.Comparing;
import consulo.application.util.function.Computable;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.ide.impl.idea.refactoring.extractSuperclass.ExtractSuperBaseDialog;
import consulo.language.editor.ui.awt.EditorComboBox;

import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author dsl
 */
public abstract class JavaExtractSuperBaseDialog extends ExtractSuperBaseDialog<PsiClass, MemberInfo> {
  private static final String DESTINATION_PACKAGE_RECENT_KEY = "ExtractSuperBase.RECENT_KEYS";
  protected final DestinationFolderComboBox myDestinationFolderComboBox;


  public JavaExtractSuperBaseDialog(Project project, PsiClass sourceClass, List<MemberInfo> members, String refactoringName) {
    super(project, sourceClass, members, refactoringName);
    myDestinationFolderComboBox = new DestinationFolderComboBox() {
      @Override
      public String getTargetPackage() {
        return getTargetPackageName();
      }
    };
  }

  protected ComponentWithBrowseButton<EditorComboBox> createPackageNameField() {
    String name = "";
    PsiFile file = mySourceClass.getContainingFile();
    if (file instanceof PsiJavaFile) {
      name = ((PsiJavaFile) file).getPackageName();
    }
    return new PackageNameReferenceEditorCombo(name, myProject, DESTINATION_PACKAGE_RECENT_KEY,
        RefactoringBundle.message("choose.destination.package"));
  }

  @Override
  protected JPanel createDestinationRootPanel() {
    final VirtualFile[] sourceRoots = ProjectRootManager.getInstance(myProject).getContentSourceRoots();
    if (sourceRoots.length <= 1) return super.createDestinationRootPanel();
    final JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
    final JBLabel label = new JBLabel(RefactoringBundle.message("target.destination.folder"));
    panel.add(label, BorderLayout.NORTH);
    label.setLabelFor(myDestinationFolderComboBox);
    myDestinationFolderComboBox.setData(myProject, myTargetDirectory, new Consumer<String>() {
      @Override
      public void accept(String s) {
      }
    }, ((PackageNameReferenceEditorCombo) myPackageNameField).getChildComponent());
    panel.add(myDestinationFolderComboBox, BorderLayout.CENTER);
    return panel;
  }

  @Override
  protected String getTargetPackageName() {
    return ((PackageNameReferenceEditorCombo) myPackageNameField).getText().trim();
  }

  protected JTextField createSourceClassField() {
    JTextField result = new JTextField();
    result.setEditable(false);
    result.setText(mySourceClass.getQualifiedName());
    return result;
  }

  @Override
  protected JTextField createExtractedSuperNameField() {
    final JTextField superNameField = super.createExtractedSuperNameField();
    superNameField.setText(mySourceClass.getName());
    superNameField.selectAll();
    return superNameField;
  }

  private PsiDirectory getDirUnderSameSourceRoot(final PsiDirectory[] directories) {
    final VirtualFile sourceFile = mySourceClass.getContainingFile().getVirtualFile();
    if (sourceFile != null) {
      final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
      final VirtualFile sourceRoot = fileIndex.getSourceRootForFile(sourceFile);
      if (sourceRoot != null) {
        for (PsiDirectory dir : directories) {
          if (Comparing.equal(fileIndex.getSourceRootForFile(dir.getVirtualFile()), sourceRoot)) {
            return dir;
          }
        }
      }
    }
    return directories[0];
  }


  @Override
  protected void preparePackage() throws OperationFailedException {
    final String targetPackageName = getTargetPackageName();
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(myProject);
    final PsiFile containingFile = mySourceClass.getContainingFile();
    final boolean fromDefaultPackage = containingFile instanceof PsiClassOwner && ((PsiClassOwner) containingFile).getPackageName().isEmpty();
    if (!(fromDefaultPackage && StringUtil.isEmpty(targetPackageName)) && !PsiNameHelper.getInstance(myProject).isQualifiedName(targetPackageName)) {
      throw new OperationFailedException("Invalid package name: " + targetPackageName);
    }
    final PsiJavaPackage aPackage = psiFacade.findPackage(targetPackageName);
    if (aPackage != null) {
      final PsiDirectory[] directories = aPackage.getDirectories(mySourceClass.getResolveScope());
      if (directories.length >= 1) {
        myTargetDirectory = getDirUnderSameSourceRoot(directories);
      }
    }

    final MoveDestination moveDestination =
        myDestinationFolderComboBox.selectDirectory(new PackageWrapper(PsiManager.getInstance(myProject), targetPackageName), false);
    if (moveDestination == null) return;

    myTargetDirectory = myTargetDirectory != null ? ApplicationManager.getApplication().runWriteAction(new Computable<PsiDirectory>() {
      @Override
      public PsiDirectory compute() {
        return moveDestination.getTargetDirectory(getTargetDirectory());
      }
    }) : null;

    if (myTargetDirectory == null) {
      throw new OperationFailedException(""); // message already reported by PackageUtil
    }
    String error = RefactoringMessageUtil.checkCanCreateClass(myTargetDirectory, getExtractedSuperName());
    if (error != null) {
      throw new OperationFailedException(error);
    }
  }

  @Override
  protected String getDestinationPackageRecentKey() {
    return DESTINATION_PACKAGE_RECENT_KEY;
  }

  @Nullable
  @Override
  protected String validateName(String name) {
    return PsiNameHelper.getInstance(myProject).isIdentifier(name)
        ? null
        : RefactoringMessageUtil.getIncorrectIdentifierMessage(name);
  }
}
