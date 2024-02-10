/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.java.impl.usageView;

import com.intellij.java.impl.slicer.*;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.ide.impl.idea.usages.impl.UsageContextPanelBase;
import consulo.language.editor.scope.AnalysisScope;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import consulo.project.ui.wm.ToolWindowId;
import consulo.project.ui.wm.ToolWindowManager;
import consulo.ui.ex.toolWindow.ToolWindow;
import consulo.usage.*;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class UsageContextDataflowToPanel extends UsageContextPanelBase {
  @Nonnull
  private final UsageViewPresentation myPresentation;
  private JComponent myPanel;

  public UsageContextDataflowToPanel(@Nonnull Project project, @Nonnull UsageViewPresentation presentation) {
    super(project, presentation);
    myPresentation = presentation;
  }

  @Override
  public void dispose() {
    super.dispose();
    myPanel = null;
  }

  @Override
  public void updateLayoutLater(@Nullable final List<? extends UsageInfo> infos) {
    if (infos == null) {
      removeAll();
      JComponent titleComp = new JLabel(UsageViewBundle.message("select.the.usage.to.preview", myPresentation.getUsagesWord()),
          SwingConstants.CENTER);
      add(titleComp, BorderLayout.CENTER);
      revalidate();
    } else {
      PsiElement element = getElementToSliceOn(infos);
      if (element == null) {
        return;
      }
      if (myPanel != null) {
        Disposer.dispose((Disposable) myPanel);
      }

      JComponent panel = createPanel(element, isDataflowToThis());
      myPanel = panel;
      Disposer.register(this, (Disposable) panel);
      removeAll();
      add(panel, BorderLayout.CENTER);
      revalidate();
    }
  }

  protected boolean isDataflowToThis() {
    return true;
  }

  @Nonnull
  private static SliceAnalysisParams createParams(PsiElement element, boolean dataFlowToThis) {
    SliceAnalysisParams params = new SliceAnalysisParams();
    params.scope = new AnalysisScope(element.getProject());
    params.dataFlowToThis = dataFlowToThis;
    params.showInstanceDereferences = true;
    return params;
  }

  @Nonnull
  protected JComponent createPanel(@Nonnull PsiElement element, final boolean dataFlowToThis) {
    ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.FIND);
    SliceAnalysisParams params = createParams(element, dataFlowToThis);

    SliceRootNode rootNode = new SliceRootNode(myProject, new DuplicateMap(), SliceUsage.createRootUsage(element, params));

    return new SlicePanel(myProject, dataFlowToThis, rootNode, false, toolWindow) {
      @Override
      public boolean isToShowAutoScrollButton() {
        return false;
      }

      @Override
      public boolean isToShowPreviewButton() {
        return false;
      }

      @Override
      public boolean isToShowCloseButton() {
        return false;
      }

      @Override
      public boolean isAutoScroll() {
        return false;
      }

      @Override
      public void setAutoScroll(boolean autoScroll) {
      }

      @Override
      public boolean isPreview() {
        return false;
      }

      @Override
      public void setPreview(boolean preview) {
      }
    };
  }

  private static PsiElement getElementToSliceOn(@Nonnull List<? extends UsageInfo> infos) {
    UsageInfo info = infos.get(0);
    return info.getElement();
  }
}
