// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection;

import consulo.language.editor.intention.LowPriorityAction;
import consulo.language.editor.inspection.scheme.InspectionToolWrapper;
import consulo.application.AllIcons;
import consulo.undoRedo.BasicUndoableAction;
import consulo.undoRedo.UndoManager;
import consulo.project.Project;
import consulo.component.util.Iconable;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.editor.inspection.scheme.InspectionProjectProfileManager;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.util.lang.reflect.ReflectionUtil;
import consulo.ui.image.Image;
import org.jetbrains.annotations.Nls;

import javax.annotation.Nonnull;
import java.io.IOException;

public class SetInspectionOptionFix implements LocalQuickFix, LowPriorityAction, Iconable {
  private final String myID;
  private final String myProperty;
  private final String myMessage;
  private final boolean myValue;

  public SetInspectionOptionFix(LocalInspectionTool inspection, String property, String message, boolean value) {
    myID = inspection.getShortName();
    myProperty = property;
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
    UndoManager.getInstance(project).undoableActionPerformed(new BasicUndoableAction(vFile) {
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
      ReflectionUtil.setField(tool.getClass(), tool, boolean.class, myProperty, value);
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
