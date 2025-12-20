// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.codeInsight;

import com.intellij.java.language.codeInsight.NullableNotNullManager;
import consulo.dataContext.DataManager;
import consulo.java.impl.JavaBundle;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.project.ProjectManager;
import consulo.ui.Button;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.JBUI;
import consulo.ui.ex.awt.Splitter;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.HashSet;

public class NullableNotNullDialog extends DialogWrapper {
  private final Project myProject;
  private final AnnotationsPanel myNullablePanel;
  private final AnnotationsPanel myNotNullPanel;
  private final boolean myShowInstrumentationOptions;

  public NullableNotNullDialog(@Nonnull Project project) {
    this(project, false);
  }

  private NullableNotNullDialog(@Nonnull Project project, boolean showInstrumentationOptions) {
    super(project, true);
    myProject = project;
    myShowInstrumentationOptions = showInstrumentationOptions;

    NullableNotNullManager manager = NullableNotNullManager.getInstance(myProject);
    myNullablePanel = new AnnotationsPanel(
      project,
      "Nullable",
      manager.getDefaultNullable(),
      manager.getNullables(),
      manager.getDefaultNullables(),
      Collections.emptySet(),
      false,
      true
    );
    myNotNullPanel = new AnnotationsPanel(
      project,
      "NotNull",
      manager.getDefaultNotNull(),
      manager.getNotNulls(),
      manager.getDefaultNotNulls(),
      new HashSet<>(manager.getInstrumentedNotNulls()),
      showInstrumentationOptions,
      true
    );

    init();
    setTitle(JavaBundle.message("nullable.notnull.configuration.dialog.title"));
  }

  @Nonnull
  public static Button createConfigureAnnotationsButton() {
    Button button = Button.create(LocalizeValue.localizeTODO("Configure annotations"));
    button.addClickListener(clickEvent -> showDialog(TargetAWT.to(clickEvent.getComponent()), false));
    return button;
  }

  @RequiredUIAccess
  public static void showDialogWithInstrumentationOptions(@Nonnull Component context) {
    showDialog(context, true);
  }

  @RequiredUIAccess
  public static void showDialog(Component context, boolean showInstrumentationOptions) {
    Project project = DataManager.getInstance().getDataContext(context).getData(Project.KEY);
    if (project == null) {
      project = ProjectManager.getInstance().getDefaultProject();
    }
    NullableNotNullDialog dialog = new NullableNotNullDialog(project, showInstrumentationOptions);
    dialog.showAsync();
  }

  @Override
  protected JComponent createCenterPanel() {
    Splitter splitter = new Splitter(true);
    splitter.setFirstComponent(myNullablePanel.getComponent());
    splitter.setSecondComponent(myNotNullPanel.getComponent());
    splitter.setHonorComponentsMinimumSize(true);
    splitter.setPreferredSize(JBUI.size(300, 400));
    return splitter;
  }

  @Override
  protected void doOKAction() {
    NullableNotNullManager manager = NullableNotNullManager.getInstance(myProject);

    manager.setNotNulls(myNotNullPanel.getAnnotations());
    manager.setDefaultNotNull(myNotNullPanel.getDefaultAnnotation());

    manager.setNullables(myNullablePanel.getAnnotations());
    manager.setDefaultNullable(myNullablePanel.getDefaultAnnotation());

    if (myShowInstrumentationOptions) {
      manager.setInstrumentedNotNulls(myNotNullPanel.getCheckedAnnotations());
    }

    super.doOKAction();
  }
}
