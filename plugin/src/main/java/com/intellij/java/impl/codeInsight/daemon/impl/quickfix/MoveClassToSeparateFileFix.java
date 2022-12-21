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
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import consulo.fileEditor.FileEditorManager;
import consulo.language.editor.FileModificationService;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.logging.Logger;
import consulo.codeEditor.Editor;
import consulo.navigation.OpenFileDescriptor;
import consulo.navigation.OpenFileDescriptorFactory;
import consulo.project.Project;
import com.intellij.java.language.psi.JavaDirectoryService;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiFile;
import com.intellij.java.impl.psi.impl.file.JavaDirectoryServiceImpl;
import consulo.language.util.IncorrectOperationException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class MoveClassToSeparateFileFix implements SyntheticIntentionAction {
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
  public boolean isAvailable(@Nonnull Project project, @Nullable Editor editor, @Nonnull PsiFile file) {
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
  public void invoke(@Nonnull Project project, @Nullable Editor editor, @Nonnull PsiFile file) {
    if (!FileModificationService.getInstance().prepareFileForWrite(myClass.getContainingFile())) return;

    PsiDirectory dir = file.getContainingDirectory();
    try{
      String name = myClass.getName();
      JavaDirectoryService directoryService = JavaDirectoryService.getInstance();
      PsiClass placeHolder = myClass.isInterface() ? directoryService.createInterface(dir, name) : directoryService.createClass(dir, name);
      PsiClass newClass = (PsiClass)placeHolder.replace(myClass);
      myClass.delete();

      OpenFileDescriptor descriptor = OpenFileDescriptorFactory.getInstance(project).builder(newClass.getContainingFile().getVirtualFile()).offset(newClass.getTextOffset()).build();
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
