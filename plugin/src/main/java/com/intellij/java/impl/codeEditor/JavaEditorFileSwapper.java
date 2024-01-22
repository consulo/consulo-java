// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.codeEditor;

import com.intellij.java.language.impl.psi.impl.compiled.ClsClassImpl;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiClassOwner;
import com.intellij.java.language.psi.PsiMember;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.fileEditor.EditorFileSwapper;
import consulo.fileEditor.FileEditorWithProviderComposite;
import consulo.fileEditor.TextEditor;
import consulo.language.psi.PsiCompiledFile;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.util.lang.Comparing;
import consulo.util.lang.Pair;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

@ExtensionImpl
public class JavaEditorFileSwapper extends EditorFileSwapper {
  @Override
  @RequiredReadAction
  public Pair<VirtualFile, Integer> getFileToSwapTo(Project project, FileEditorWithProviderComposite editor) {
    VirtualFile file = editor.getFile();
    VirtualFile sourceFile = findSourceFile(project, file);
    if (sourceFile == null) {
      return null;
    }

    Integer position = null;

    TextEditor oldEditor = findSinglePsiAwareEditor(editor.getEditors());
    if (oldEditor != null) {
      PsiCompiledFile clsFile = (PsiCompiledFile) PsiManager.getInstance(project).findFile(file);
      assert clsFile != null;

      int offset = oldEditor.getEditor().getCaretModel().getOffset();
      PsiElement elementAt = clsFile.findElementAt(offset);
      PsiMember member = PsiTreeUtil.getParentOfType(elementAt, PsiMember.class, false);
      if (member != null) {
        PsiElement navigationElement = member.getOriginalElement().getNavigationElement();
        if (Comparing.equal(navigationElement.getContainingFile().getVirtualFile(), sourceFile)) {
          position = navigationElement.getTextOffset();
        }
      }
    }

    return Pair.pair(sourceFile, position);
  }

  @Nullable
  @RequiredReadAction
  public static VirtualFile findSourceFile(@Nonnull Project project, @Nonnull VirtualFile file) {
    PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    if (psiFile instanceof PsiCompiledFile && psiFile instanceof PsiClassOwner) {
      PsiClass[] classes = ((PsiClassOwner) psiFile).getClasses();
      if (classes.length != 0 && classes[0] instanceof ClsClassImpl) {
        PsiClass sourceClass = ((ClsClassImpl) classes[0]).getSourceMirrorClass();
        if (sourceClass != null) {
          VirtualFile result = sourceClass.getContainingFile().getVirtualFile();
          assert result != null : sourceClass;
          return result;
        }
      }
    }

    return null;
  }
}