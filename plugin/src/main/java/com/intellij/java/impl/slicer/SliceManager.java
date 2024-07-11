/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.impl.slicer;

import com.intellij.java.language.psi.PsiReferenceExpression;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.component.ProcessCanceledException;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.persist.StoragePathMacros;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.ide.ServiceManager;
import consulo.language.editor.refactoring.util.RefactoringDescriptionLocation;
import consulo.language.editor.ui.scope.AnalysisUIOptions;
import consulo.language.psi.ElementDescriptionUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.event.PsiTreeChangeAdapter;
import consulo.language.psi.event.PsiTreeChangeEvent;
import consulo.project.Project;
import consulo.project.ui.wm.ToolWindowManager;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.content.Content;
import consulo.ui.ex.content.ContentManager;
import consulo.ui.ex.toolWindow.ContentManagerWatcher;
import consulo.ui.ex.toolWindow.ToolWindow;
import consulo.ui.ex.toolWindow.ToolWindowAnchor;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.awt.*;
import java.util.regex.Pattern;

@Singleton
@State(name = "SliceManager", storages = {@Storage(file = StoragePathMacros.WORKSPACE_FILE)})
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public class SliceManager implements PersistentStateComponent<SliceManager.StoredSettingsBean> {
  private final Project myProject;
  private ContentManager myBackContentManager;
  private ContentManager myForthContentManager;
  private final StoredSettingsBean myStoredSettings = new StoredSettingsBean();
  private static final String BACK_TOOLWINDOW_ID = "Analyze Dataflow to";
  private static final String FORTH_TOOLWINDOW_ID = "Analyze Dataflow from";

  public static class StoredSettingsBean {
    public boolean showDereferences = true; // to show in dataflow/from dialog
    public AnalysisUIOptions analysisUIOptions = new AnalysisUIOptions();
  }

  public static SliceManager getInstance(@Nonnull Project project) {
    return ServiceManager.getService(project, SliceManager.class);
  }

  @Inject
  public SliceManager(@Nonnull Project project) {
    myProject = project;
  }

  @Nonnull
  private Disposable addPsiListener(@Nonnull final ProgressIndicator indicator) {
    Disposable disposable = Disposable.newDisposable();
    PsiManager.getInstance(myProject).addPsiTreeChangeListener(new PsiTreeChangeAdapter() {
      @Override
      public void beforeChildAddition(@Nonnull PsiTreeChangeEvent event) {
        indicator.cancel();
      }

      @Override
      public void beforeChildRemoval(@Nonnull PsiTreeChangeEvent event) {
        indicator.cancel();
      }

      @Override
      public void beforeChildReplacement(@Nonnull PsiTreeChangeEvent event) {
        indicator.cancel();
      }

      @Override
      public void beforeChildMovement(@Nonnull PsiTreeChangeEvent event) {
        indicator.cancel();
      }

      @Override
      public void beforeChildrenChange(@Nonnull PsiTreeChangeEvent event) {
        indicator.cancel();
      }

      @Override
      public void beforePropertyChange(@Nonnull PsiTreeChangeEvent event) {
        indicator.cancel();
      }
    }, disposable);
    return disposable;
  }

  private ContentManager getContentManager(boolean dataFlowToThis) {
    if (dataFlowToThis) {
      if (myBackContentManager == null) {
        ToolWindow backToolWindow =
          ToolWindowManager.getInstance(myProject).registerToolWindow(BACK_TOOLWINDOW_ID, true, ToolWindowAnchor.BOTTOM, myProject);
        myBackContentManager = backToolWindow.getContentManager();
        new ContentManagerWatcher(backToolWindow, myBackContentManager);
      }
      return myBackContentManager;
    }

    if (myForthContentManager == null) {
      ToolWindow forthToolWindow =
        ToolWindowManager.getInstance(myProject).registerToolWindow(FORTH_TOOLWINDOW_ID, true, ToolWindowAnchor.BOTTOM, myProject);
      myForthContentManager = forthToolWindow.getContentManager();
      new ContentManagerWatcher(forthToolWindow, myForthContentManager);
    }
    return myForthContentManager;
  }

  public void slice(@Nonnull PsiElement element, boolean dataFlowToThis, @Nonnull SliceHandler handler) {
    String dialogTitle = getElementDescription((dataFlowToThis ? BACK_TOOLWINDOW_ID : FORTH_TOOLWINDOW_ID) + " ", element, null);

    dialogTitle = Pattern.compile("(<style>.*</style>)|<[^<>]*>").matcher(dialogTitle).replaceAll("");
    SliceAnalysisParams params = handler.askForParams(element, dataFlowToThis, myStoredSettings, dialogTitle);
    if (params == null) {
      return;
    }

    SliceRootNode rootNode = new SliceRootNode(myProject, new DuplicateMap(), SliceUsage.createRootUsage(element, params));

    createToolWindow(dataFlowToThis, rootNode, false, getElementDescription(null, element, null));
  }

  public void createToolWindow(boolean dataFlowToThis,
                               @Nonnull SliceRootNode rootNode,
                               boolean splitByLeafExpressions,
                               @Nonnull String displayName) {
    final SliceToolwindowSettings sliceToolwindowSettings = SliceToolwindowSettings.getInstance(myProject);
    final ContentManager contentManager = getContentManager(dataFlowToThis);
    final Content[] myContent = new Content[1];
    ToolWindow toolWindow =
      ToolWindowManager.getInstance(myProject).getToolWindow(dataFlowToThis ? BACK_TOOLWINDOW_ID : FORTH_TOOLWINDOW_ID);
    final SlicePanel slicePanel = new SlicePanel(myProject, dataFlowToThis, rootNode, splitByLeafExpressions, toolWindow) {
      @Override
      protected void close() {
        super.close();
        contentManager.removeContent(myContent[0], true);
      }

      @Override
      public boolean isAutoScroll() {
        return sliceToolwindowSettings.isAutoScroll();
      }

      @Override
      public void setAutoScroll(boolean autoScroll) {
        sliceToolwindowSettings.setAutoScroll(autoScroll);
      }

      @Override
      public boolean isPreview() {
        return sliceToolwindowSettings.isPreview();
      }

      @Override
      public void setPreview(boolean preview) {
        sliceToolwindowSettings.setPreview(preview);
      }
    };

    myContent[0] = contentManager.getFactory().createContent(slicePanel, displayName, true);
    contentManager.addContent(myContent[0]);
    contentManager.setSelectedContent(myContent[0]);

    toolWindow.activate(null);
  }

  public static String getElementDescription(String prefix, PsiElement element, String suffix) {
    PsiElement elementToSlice = element;
    if (element instanceof PsiReferenceExpression) {
      elementToSlice = ((PsiReferenceExpression)element).resolve();
    }
    if (elementToSlice == null) {
      elementToSlice = element;
    }
    String desc = ElementDescriptionUtil.getElementDescription(elementToSlice, RefactoringDescriptionLocation.WITHOUT_PARENT);
    return "<html><head>" + UIUtil.getCssFontDeclaration(getLabelFont()) + "</head><body>" + (prefix == null ? "" : prefix) + StringUtil.first(
      desc,
      100,
      true) + (suffix == null ? "" : suffix) +
      "</body></html>";
  }

  // copy from com.intellij.openapi.wm.impl.content.BaseLabel since desktop libraries is not exported
  public static Font getLabelFont() {
    Font f = UIUtil.getLabelFont();
    return f.deriveFont(f.getStyle(), Math.max(11, f.getSize() - 2));
  }

  public void runInterruptibly(@Nonnull ProgressIndicator progress,
                               @Nonnull Runnable onCancel,
                               @Nonnull Runnable runnable) throws ProcessCanceledException {
    Disposable disposable = addPsiListener(progress);
    try {
      progress.checkCanceled();
      ProgressManager.getInstance().executeProcessUnderProgress(runnable, progress);
    }
    catch (ProcessCanceledException e) {
      progress.cancel();
      //reschedule for later
      onCancel.run();
      throw e;
    }
    finally {
      Disposer.dispose(disposable);
    }
  }

  @Override
  public StoredSettingsBean getState() {
    return myStoredSettings;
  }

  @Override
  public void loadState(StoredSettingsBean state) {
    myStoredSettings.analysisUIOptions.save(state.analysisUIOptions);
  }
}
