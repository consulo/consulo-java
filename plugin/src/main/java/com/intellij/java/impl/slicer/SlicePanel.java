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
package com.intellij.java.impl.slicer;

import consulo.application.AllIcons;
import consulo.application.progress.ProgressIndicator;
import consulo.dataContext.DataSink;
import consulo.dataContext.TypeSafeDataProvider;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.ide.impl.idea.openapi.wm.ex.ToolWindowManagerEx;
import consulo.navigation.Navigatable;
import consulo.platform.base.localize.IdeLocalize;
import consulo.project.Project;
import consulo.project.ui.view.tree.AbstractTreeNode;
import consulo.project.ui.wm.ToolWindowManagerListener;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.*;
import consulo.ui.ex.awt.*;
import consulo.ui.ex.awt.speedSearch.TreeSpeedSearch;
import consulo.ui.ex.awt.tree.Tree;
import consulo.ui.ex.awt.tree.TreeUtil;
import consulo.ui.ex.toolWindow.ToolWindow;
import consulo.ui.ex.toolWindow.ToolWindowAnchor;
import consulo.usage.*;
import consulo.util.dataholder.Key;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author cdr
 */
public abstract class SlicePanel extends JPanel implements TypeSafeDataProvider, Disposable {
  private final
  SliceTreeBuilder myBuilder;
  private final JTree myTree;

  private final AutoScrollToSourceHandler myAutoScrollToSourceHandler = new AutoScrollToSourceHandler() {
    @Override
    protected boolean isAutoScrollMode() {
      return isAutoScroll();
    }

    @Override
    protected void setAutoScrollMode(final boolean state) {
      setAutoScroll(state);
    }
  };
  private UsagePreviewPanel myUsagePreviewPanel;
  private final Project myProject;
  private boolean isDisposed;
  private final ToolWindow myToolWindow;

  @RequiredUIAccess
  public SlicePanel(
    @Nonnull final Project project,
    boolean dataFlowToThis,
    @Nonnull SliceNode rootNode,
    boolean splitByLeafExpressions,
    @Nonnull final ToolWindow toolWindow
  ) {
    super(new BorderLayout());
    myToolWindow = toolWindow;
    final ToolWindowManagerListener listener = new ToolWindowManagerListener() {
      ToolWindowAnchor myAnchor = toolWindow.getAnchor();

      @Override
      public void toolWindowRegistered(@Nonnull String id) {
      }

      @Override
      public void stateChanged() {
        if (!project.isOpen()) {
          return;
        }
        if (toolWindow.getAnchor() != myAnchor) {
          myAnchor = myToolWindow.getAnchor();
          layoutPanel();
        }
      }
    };
    ToolWindowManagerEx.getInstanceEx(project).addToolWindowManagerListener(listener);
    Disposer.register(this, () -> ToolWindowManagerEx.getInstanceEx(project).removeToolWindowManagerListener(listener));

    project.getApplication().assertIsDispatchThread();
    myProject = project;
    myTree = createTree();

    myBuilder = new SliceTreeBuilder(myTree, project, dataFlowToThis, rootNode, splitByLeafExpressions);
    myBuilder.setCanYieldUpdate(!project.getApplication().isUnitTestMode());

    Disposer.register(this, myBuilder);

    myBuilder.addSubtreeToUpdate((DefaultMutableTreeNode) myTree.getModel().getRoot(), () -> {
      if (isDisposed || myBuilder.isDisposed() || myProject.isDisposed()) {
        return;
      }
      final SliceNode rootNode1 = myBuilder.getRootSliceNode();
      myBuilder.expand(rootNode1, new Runnable() {
        @Override
        public void run() {
          if (isDisposed || myBuilder.isDisposed() || myProject.isDisposed()) {
            return;
          }
          myBuilder.select(rootNode1.myCachedChildren.get(0)); //first there is ony one child
        }
      });
      treeSelectionChanged();
    });

    layoutPanel();
  }

  private void layoutPanel() {
    if (myUsagePreviewPanel != null) {
      Disposer.dispose(myUsagePreviewPanel);
    }
    removeAll();
    JScrollPane pane = ScrollPaneFactory.createScrollPane(myTree);

    if (isPreview()) {
      pane.setBorder(IdeBorderFactory.createBorder(SideBorder.LEFT | SideBorder.RIGHT));

      boolean vertical = myToolWindow.getAnchor() == ToolWindowAnchor.LEFT || myToolWindow.getAnchor() == ToolWindowAnchor.RIGHT;
      Splitter splitter = new Splitter(vertical, UsageViewSettings.getInstance().PREVIEW_USAGES_SPLITTER_PROPORTIONS);
      splitter.setFirstComponent(pane);
      myUsagePreviewPanel = UsagePreviewPanelFactory.getInstance().createPreviewPanel(myProject, new UsageViewPresentation());
      JComponent component = myUsagePreviewPanel.createComponent();
      component.setBorder(IdeBorderFactory.createBorder(SideBorder.LEFT));

      Disposer.register(this, myUsagePreviewPanel);
      splitter.setSecondComponent(component);
      add(splitter, BorderLayout.CENTER);
    } else {
      pane.setBorder(IdeBorderFactory.createBorder(SideBorder.LEFT));
      add(pane, BorderLayout.CENTER);
    }

    add(createToolbar().getComponent(), BorderLayout.WEST);

    myTree.getParent().setBackground(UIManager.getColor("Tree.background"));

    revalidate();
  }

  @Override
  public void dispose() {
    if (myUsagePreviewPanel != null) {
      UsageViewSettings.getInstance().PREVIEW_USAGES_SPLITTER_PROPORTIONS = ((Splitter) myUsagePreviewPanel.createComponent().getParent()).getProportion();
      myUsagePreviewPanel = null;
    }

    isDisposed = true;
    ToolTipManager.sharedInstance().unregisterComponent(myTree);
  }

  @Nonnull
  private JTree createTree() {
    DefaultMutableTreeNode root = new DefaultMutableTreeNode();
    final Tree tree = new Tree(new DefaultTreeModel(root))/* {
    @Override
      protected void paintComponent(Graphics g) {
        DuplicateNodeRenderer.paintDuplicateNodesBackground(g, this);
        super.paintComponent(g);
      }
    }*/;
    tree.setOpaque(false);

    tree.setToggleClickCount(-1);
    SliceUsageCellRenderer renderer = new SliceUsageCellRenderer();
    renderer.setOpaque(false);
    tree.setCellRenderer(renderer);
    UIUtil.setLineStyleAngled(tree);
    tree.setRootVisible(false);

    tree.setShowsRootHandles(true);
    tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    tree.setSelectionPath(new TreePath(root.getPath()));
    //ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_METHOD_HIERARCHY_POPUP);
    //PopupHandler.installPopupHandler(tree, group, ActionPlaces.METHOD_HIERARCHY_VIEW_POPUP, ActionManager.getInstance());
    EditSourceOnDoubleClickHandler.install(tree);

    new TreeSpeedSearch(tree);
    TreeUtil.installActions(tree);
    ToolTipManager.sharedInstance().registerComponent(tree);

    myAutoScrollToSourceHandler.install(tree);

    tree.getSelectionModel().addTreeSelectionListener(e -> treeSelectionChanged());

    tree.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (KeyEvent.VK_ENTER == e.getKeyCode()) {
          List<Navigatable> navigatables = getNavigatables();
          if (navigatables.isEmpty()) {
            return;
          }
          for (Navigatable navigatable : navigatables) {
            if (navigatable instanceof AbstractTreeNode treeNode && treeNode.getValue() instanceof Usage usage) {
              navigatable = usage;
            }
            if (navigatable.canNavigateToSource()) {
              navigatable.navigate(false);
              if (navigatable instanceof Usage usage) {
                usage.highlightInEditor();
              }
            }
          }
          e.consume();
        }
      }
    });

    tree.addTreeWillExpandListener(new TreeWillExpandListener() {
      @Override
      public void treeWillCollapse(TreeExpansionEvent event) {
      }

      @Override
      public void treeWillExpand(TreeExpansionEvent event) {
        TreePath path = event.getPath();
        SliceNode node = fromPath(path);
        node.calculateDupNode();
      }
    });

    return tree;
  }

  private void treeSelectionChanged() {
    SwingUtilities.invokeLater(() -> {
      if (isDisposed) {
        return;
      }
      List<UsageInfo> infos = getSelectedUsageInfos();
      if (infos != null && myUsagePreviewPanel != null) {
        myUsagePreviewPanel.updateLayout(infos);
      }
    });
  }

  private static SliceNode fromPath(TreePath path) {
    Object lastPathComponent = path.getLastPathComponent();
    if (lastPathComponent instanceof DefaultMutableTreeNode node) {
      Object userObject = node.getUserObject();
      if (userObject instanceof SliceNode sliceNode) {
        return sliceNode;
      }
    }
    return null;
  }

  @Nullable
  private List<UsageInfo> getSelectedUsageInfos() {
    TreePath[] paths = myTree.getSelectionPaths();
    if (paths == null) {
      return null;
    }
    final ArrayList<UsageInfo> result = new ArrayList<>();
    for (TreePath path : paths) {
      SliceNode sliceNode = fromPath(path);
      if (sliceNode != null) {
        result.add(sliceNode.getValue().getUsageInfo());
      }
    }
    if (result.isEmpty()) {
      return null;
    }
    return result;
  }

  @Override
  public void calcData(Key<?> key, DataSink sink) {
    if (key == Navigatable.KEY_OF_ARRAY) {
      List<Navigatable> navigatables = getNavigatables();
      if (!navigatables.isEmpty()) {
        sink.put(Navigatable.KEY_OF_ARRAY, navigatables.toArray(new Navigatable[navigatables.size()]));
      }
    }
  }

  @Nonnull
  private List<Navigatable> getNavigatables() {
    TreePath[] paths = myTree.getSelectionPaths();
    if (paths == null) {
      return Collections.emptyList();
    }
    final ArrayList<Navigatable> navigatables = new ArrayList<>();
    for (TreePath path : paths) {
      Object lastPathComponent = path.getLastPathComponent();
      if (lastPathComponent instanceof DefaultMutableTreeNode node) {
        Object userObject = node.getUserObject();
        if (userObject instanceof Navigatable userNavigatable) {
          navigatables.add(userNavigatable);
        } else if (node instanceof Navigatable navigatable) {
          navigatables.add(navigatable);
        }
      }
    }
    return navigatables;
  }

  @Nonnull
  private ActionToolbar createToolbar() {
    final DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.add(new MyRefreshAction(myTree));
    if (isToShowAutoScrollButton()) {
      actionGroup.add(myAutoScrollToSourceHandler.createToggleAction());
    }
    if (isToShowCloseButton()) {
      actionGroup.add(new CloseAction());
    }
    if (isToShowPreviewButton()) {
      actionGroup.add(new ToggleAction(UsageViewBundle.message("preview.usages.action.text"), "preview", AllIcons.Actions.PreviewDetails) {
        @Override
        public boolean isSelected(@Nonnull AnActionEvent e) {
          return isPreview();
        }

        @Override
        public void setSelected(@Nonnull AnActionEvent e, boolean state) {
          setPreview(state);
          layoutPanel();
        }
      });
    }

    if (myBuilder.dataFlowToThis) {
      actionGroup.add(new GroupByLeavesAction(myBuilder));
      actionGroup.add(new CanItBeNullAction(myBuilder));
    }

    //actionGroup.add(new ContextHelpAction(HELP_ID));

    return ActionManager.getInstance().createActionToolbar(ActionPlaces.TYPE_HIERARCHY_VIEW_TOOLBAR, actionGroup, false);
  }

  public boolean isToShowAutoScrollButton() {
    return true;
  }

  public abstract boolean isAutoScroll();

  public abstract void setAutoScroll(boolean autoScroll);

  public boolean isToShowCloseButton() {
    return true;
  }

  public boolean isToShowPreviewButton() {
    return true;
  }

  public abstract boolean isPreview();

  public abstract void setPreview(boolean preview);

  private class CloseAction extends CloseTabToolbarAction {
    @Override
    public final void actionPerformed(@Nonnull final AnActionEvent e) {
      close();
    }
  }

  protected void close() {
    final ProgressIndicator progress = myBuilder.getUi().getProgress();
    if (progress != null) {
      progress.cancel();
    }
  }

  private final class MyRefreshAction extends RefreshAction {
    private MyRefreshAction(JComponent tree) {
      super(IdeLocalize.actionRefresh().get(), IdeLocalize.actionRefresh().get(), AllIcons.Actions.Refresh);
      registerShortcutOn(tree);
    }

    @Override
    public final void actionPerformed(final AnActionEvent e) {
      SliceNode rootNode = (SliceNode) myBuilder.getRootNode().getUserObject();
      rootNode.setChanged();
      myBuilder.addSubtreeToUpdate(myBuilder.getRootNode());
    }

    @Override
    public final void update(final AnActionEvent event) {
      final Presentation presentation = event.getPresentation();
      presentation.setEnabled(true);
    }
  }

  @TestOnly
  public SliceTreeBuilder getBuilder() {
    return myBuilder;
  }
}
