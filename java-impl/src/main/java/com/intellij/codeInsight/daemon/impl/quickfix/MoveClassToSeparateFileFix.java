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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.FileModificationService;
import consulo.java.JavaQuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import consulo.logging.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.file.JavaDirectoryServiceImpl;
import com.intellij.util.IncorrectOperationException;
import javax.annotation.Nonnull;

public class MoveClassToSeparateFileFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance(MoveClassToSeparateFileFix.class);

  private final PsiClass myClass;

  public MoveClassToSeparateFileFix(PsiClass aClass) {
    myClass = aClass;
  }

  @Override
  @Nonnull
  public String getText() {
    return JavaQuickFixBundle.message("move.class.to.separate.file.text", myClass.getName());
  }

  @Override
  @Nonnull
  public String getFamilyName() {
    return JavaQuickFixBundle.message("move.class.to.separate.file.family");
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, @javax.annotation.Nullable Editor editor, @Nonnull PsiFile file) {
    if  (!myClass.isValid() || !myClass.getManager().isInProject(myClass)) return false;
    PsiDirectory dir = file.getContainingDirectory();
    if (dir == null) return false;
    try {
      JavaDirectoryServiceImpl.checkCreateClassOrInterface(dir, myClass.getName());
    }
    catch (IncorrectOperationException e) {
      return false;
    }

    return true;
  }

  @Override
  public void invoke(@Nonnull Project project, @javax.annotation.Nullable Editor editor, @Nonnull PsiFile file) {
    if (!FileModificationService.getInstance().prepareFileForWrite(myClass.getContainingFile())) return;

    PsiDirectory dir = file.getContainingDirectory();
    try{
      String name = myClass.getName();
      JavaDirectoryService directoryService = JavaDirectoryService.getInstance();
      PsiClass placeHolder = myClass.isInterface() ? directoryService.createInterface(dir, name) : directoryService.createClass(dir, name);
      PsiClass newClass = (PsiClass)placeHolder.replace(myClass);
      myClass.delete();

      OpenFileDescriptor descriptor = new OpenFileDescriptor(project, newClass.getContainingFile().getVirtualFile(), newClass.getTextOffset());
      FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
    }
    catch(IncorrectOperationException e){
      LOG.error(e);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

}
