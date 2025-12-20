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
package com.intellij.java.impl.ide;

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Result;
import consulo.dataContext.DataContext;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.ide.IdeView;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.FilePasteProvider;
import consulo.language.editor.WriteCommandAction;
import consulo.language.psi.*;
import consulo.language.util.IncorrectOperationException;
import consulo.navigation.OpenFileDescriptorFactory;
import consulo.project.Project;
import consulo.ui.ex.awt.CopyPasteManager;
import consulo.undoRedo.CommandProcessor;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;

/**
 * @author yole
 */
@ExtensionImpl
public class JavaFilePasteProvider implements FilePasteProvider {
  @RequiredReadAction
  public void performPaste(@Nonnull DataContext dataContext) {
    final Project project = dataContext.getData(Project.KEY);
    IdeView ideView = dataContext.getData(IdeView.KEY);
    if (project == null || ideView == null) {
      return;
    }
    final PsiJavaFile javaFile = createJavaFileFromClipboardContent(project);
    if (javaFile == null) {
      return;
    }
    PsiClass[] classes = javaFile.getClasses();
    if (classes.length < 1) {
      return;
    }
    final PsiDirectory targetDir = ideView.getOrChooseDirectory();
    if (targetDir == null) {
      return;
    }
    PsiClass publicClass = classes[0];
    for (PsiClass aClass : classes) {
      if (aClass.hasModifierProperty(PsiModifier.PUBLIC)) {
        publicClass = aClass;
        break;
      }
    }
    final PsiClass mainClass = publicClass;
    new WriteCommandAction(project, "Paste class '" + mainClass.getName() + "'") {
      @RequiredReadAction
      @Override
      protected void run(Result result) throws Throwable {
        PsiFile file;
        try {
          file = targetDir.createFile(mainClass.getName() + ".java");
        } catch (IncorrectOperationException e) {
          return;
        }
        Document document = PsiDocumentManager.getInstance(project).getDocument(file);
        document.setText(javaFile.getText());
        PsiDocumentManager.getInstance(project).commitDocument(document);
        if (file instanceof PsiJavaFile psiJavaFile) {
          updatePackageStatement(psiJavaFile, targetDir);
        }
        OpenFileDescriptorFactory.getInstance(project).builder(file.getVirtualFile()).build().navigate(true);
      }
    }.execute();
  }

  private static void updatePackageStatement(PsiJavaFile javaFile, PsiDirectory targetDir) {
    PsiJavaPackage aPackage = JavaDirectoryService.getInstance().getPackage(targetDir);
    if (aPackage == null) return;
    PsiPackageStatement oldStatement = javaFile.getPackageStatement();
    Project project = javaFile.getProject();
    if ((oldStatement != null && !oldStatement.getPackageName().equals(aPackage.getQualifiedName()) ||
        (oldStatement == null && aPackage.getQualifiedName().length() > 0))) {
      CommandProcessor.getInstance().executeCommand(
        project,
        () -> {
          try {
            PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
            PsiPackageStatement newStatement = factory.createPackageStatement(aPackage.getQualifiedName());
            if (oldStatement != null) {
              oldStatement.replace(newStatement);
            } else {
              PsiElement addedStatement = javaFile.addAfter(newStatement, null);
              TextRange textRange = addedStatement.getTextRange();
              // ensure line break is added after the statement
              CodeStyleManager.getInstance(project).reformatRange(javaFile, textRange.getStartOffset(), textRange.getEndOffset() + 1);
            }
          } catch (IncorrectOperationException e) {
            // ignore
          }
        },
        "Updating package statement",
        null
      );
    }
  }

  public boolean isPastePossible(@Nonnull DataContext dataContext) {
    return true;
  }

  public boolean isPasteEnabled(@Nonnull DataContext dataContext) {
    Project project = dataContext.getData(Project.KEY);
    IdeView ideView = dataContext.getData(IdeView.KEY);
    if (project == null || ideView == null || ideView.getDirectories().length == 0) {
      return false;
    }
    PsiJavaFile file = createJavaFileFromClipboardContent(project);
    return file != null && file.getClasses().length >= 1;
  }

  @Nullable
  private static PsiJavaFile createJavaFileFromClipboardContent(Project project) {
    PsiJavaFile file = null;
    Transferable content = CopyPasteManager.getInstance().getContents();
    if (content != null) {
      String text = null;
      try {
        text = (String) content.getTransferData(DataFlavor.stringFlavor);
      } catch (Exception e) {
        // ignore;
      }
      if (text != null) {
        file = (PsiJavaFile) PsiFileFactory.getInstance(project).createFileFromText("A.java", JavaLanguage.INSTANCE, text);
      }
    }
    return file;
  }
}
