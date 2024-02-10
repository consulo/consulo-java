// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection;

import com.intellij.java.analysis.codeInspection.AbstractBaseJavaLocalInspectionTool;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.AllIcons;
import consulo.component.util.Iconable;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.scheme.InspectionProfile;
import consulo.language.editor.inspection.scheme.InspectionProjectProfileManager;
import consulo.language.editor.intention.LowPriorityAction;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.project.Project;
import consulo.ui.image.Image;
import consulo.undoRedo.BasicUndoableAction;
import consulo.undoRedo.ProjectUndoManager;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.annotations.Nls;

import jakarta.annotation.Nonnull;
import java.util.function.BiConsumer;

public class SetInspectionOptionFix<I extends AbstractBaseJavaLocalInspectionTool<State>, State> implements LocalQuickFix, LowPriorityAction, Iconable {
  private final String myID;
  private final BiConsumer<State, Boolean> myPropertySetter;
  private final String myMessage;
  private final boolean myValue;

  public SetInspectionOptionFix(I inspection, BiConsumer<State, Boolean> propertySetter, String message, boolean value) {
    myID = inspection.getShortName();
    myPropertySetter = propertySetter;
    myMessage = message;
    myValue = value;
  }

  @Nls
  @Nonnull
  @Override
  public String getName() {
    return myMessage;
  }

  @Nls
  @Nonnull
  @Override
  public String getFamilyName() {
    return "Set inspection option";
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  @RequiredReadAction
  public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
    VirtualFile vFile = descriptor.getPsiElement().getContainingFile().getVirtualFile();
    setOption(project, vFile, myValue);
    ProjectUndoManager.getInstance(project).undoableActionPerformed(new BasicUndoableAction(vFile) {
      @Override
      public void undo() {
        setOption(project, vFile, !myValue);
      }

      @Override
      public void redo() {
        setOption(project, vFile, myValue);
      }
    });
  }

  @RequiredReadAction
  private void setOption(@Nonnull Project project, @Nonnull VirtualFile vFile, boolean value) {
    PsiFile file = PsiManager.getInstance(project).findFile(vFile);
    if (file == null) {
      return;
    }

    InspectionProjectProfileManager manager = InspectionProjectProfileManager.getInstance(project);

    InspectionProfile inspectionProfile = manager.getInspectionProfile();

    inspectionProfile.<I, State>modifyToolSettings(myID, file, (inspectionTool, state) -> {
      myPropertySetter.accept(state, value);
    });
  }

  @Override
  public Image getIcon(int flags) {
    return AllIcons.Actions.Cancel;
  }
}
