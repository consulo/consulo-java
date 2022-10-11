// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection;

import consulo.annotation.access.RequiredReadAction;
import consulo.application.AllIcons;
import consulo.component.util.Iconable;
import consulo.language.editor.inspection.LocalInspectionTool;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.scheme.*;
import consulo.language.editor.intention.LowPriorityAction;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.project.Project;
import consulo.ui.image.Image;
import consulo.undoRedo.BasicUndoableAction;
import consulo.undoRedo.ProjectUndoManager;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.annotations.Nls;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.function.BiConsumer;

public class SetInspectionOptionFix<I extends LocalInspectionTool> implements LocalQuickFix, LowPriorityAction, Iconable {
  private final String myID;
  private final BiConsumer<I, Boolean> myPropertySetter;
  private final String myMessage;
  private final boolean myValue;

  public SetInspectionOptionFix(I inspection, BiConsumer<I, Boolean> propertySetter, String message, boolean value) {
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
  @SuppressWarnings("unchecked")
  private void setOption(@Nonnull Project project, @Nonnull VirtualFile vFile, boolean value) {
    PsiFile file = PsiManager.getInstance(project).findFile(vFile);
    if (file == null) {
      return;
    }

    InspectionProjectProfileManager manager = InspectionProjectProfileManager.getInstance(project);
    InspectionProfile inspectionProfile = manager.getInspectionProfile();

    ModifiableModel modifiableModel = inspectionProfile.getModifiableModel();

    InspectionToolWrapper inspectionTool = modifiableModel.getInspectionTool(myID, file);
    if (inspectionTool != null) {
      InspectionProfileEntry tool = inspectionTool.getTool();
      myPropertySetter.accept((I)tool, value);
    }
    try {
      modifiableModel.commit();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Image getIcon(int flags) {
    return AllIcons.Actions.Cancel;
  }
}
