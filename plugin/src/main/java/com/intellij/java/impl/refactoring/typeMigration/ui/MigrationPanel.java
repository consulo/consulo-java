/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.typeMigration.ui;

import com.intellij.java.impl.refactoring.typeMigration.TypeMigrationLabeler;
import com.intellij.java.impl.refactoring.typeMigration.TypeMigrationProcessor;
import com.intellij.java.impl.refactoring.typeMigration.usageInfo.TypeMigrationUsageInfo;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import com.intellij.java.language.psi.util.PsiFormatUtilBase;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.Application;
import consulo.application.HelpManager;
import consulo.application.ReadAction;
import consulo.application.Result;
import consulo.application.progress.ProgressManager;
import consulo.dataContext.DataProvider;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.ide.impl.idea.openapi.vfs.VfsUtilCore;
import consulo.ide.impl.idea.ui.DuplicateNodeRenderer;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.psi.PsiBundle;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.SymbolPresentationUtil;
import consulo.localize.LocalizeValue;
import consulo.platform.base.localize.CommonLocalize;
import consulo.project.Project;
import consulo.project.ui.view.tree.AbstractTreeNode;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.ui.ex.action.*;
import consulo.ui.ex.awt.*;
import consulo.ui.ex.awt.speedSearch.TreeSpeedSearch;
import consulo.ui.ex.awt.tree.ColoredTreeCellRenderer;
import consulo.ui.ex.awt.tree.SmartExpander;
import consulo.ui.ex.awt.tree.Tree;
import consulo.ui.ex.awt.tree.TreeUtil;
import consulo.ui.ex.content.Content;
import consulo.usage.*;
import consulo.util.dataholder.Key;
import consulo.virtualFileSystem.ReadonlyStatusHandler;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author anna
 */
public class MigrationPanel extends JPanel implements Disposable {
  private static final Key<TypeMigrationUsageInfo[]> MIGRATION_USAGES_KEYS = Key.create("migration.usages");

  private final PsiElement[] myInitialRoots;
  private final TypeMigrationLabeler myLabeler;


  private final MyTree myRootsTree;
  private final Project myProject;
  private Content myContent;
  private final MigrationUsagesPanel myUsagesPanel;
  private final MigrationConflictsPanel myConflictsPanel;

  public MigrationPanel(final PsiElement[] roots, TypeMigrationLabeler labeler, final Project project, final boolean previewUsages) {
    super(new BorderLayout());
    myInitialRoots = roots;
    myLabeler = labeler;
    myProject = project;

    myRootsTree = new MyTree(new DefaultTreeModel(new DefaultMutableTreeNode()));
    final TypeMigrationTreeBuilder builder = new TypeMigrationTreeBuilder(myRootsTree, project);

    final MigrationRootNode currentRoot = new MigrationRootNode(project, myLabeler, roots, previewUsages);
    builder.setRoot(currentRoot);
    initTree(myRootsTree);
    myRootsTree.getSelectionModel().addTreeSelectionListener(e -> selectionChanged());

    final Splitter treeSplitter = new Splitter();
    Disposer.register(this, treeSplitter::dispose);
    treeSplitter.setFirstComponent(ScrollPaneFactory.createScrollPane(myRootsTree));


    myUsagesPanel = new MigrationUsagesPanel(myProject);
    treeSplitter.setSecondComponent(myUsagesPanel);
    Disposer.register(this, myUsagesPanel);

    add(createToolbar(), BorderLayout.SOUTH);

    final Splitter conflictsSplitter = new Splitter(true, .8f);
    Disposer.register(this, conflictsSplitter::dispose);
    conflictsSplitter.setFirstComponent(treeSplitter);
    myConflictsPanel = new MigrationConflictsPanel(myProject);
    conflictsSplitter.setSecondComponent(myConflictsPanel);
    add(conflictsSplitter, BorderLayout.CENTER);
    Disposer.register(this, myConflictsPanel);

    builder.addSubtreeToUpdate((DefaultMutableTreeNode) myRootsTree.getModel().getRoot(), () -> SwingUtilities.invokeLater(() -> {
      if (builder.isDisposed()) {
        return;
      }
      myRootsTree.expandPath(new TreePath(myRootsTree.getModel().getRoot()));
      final Collection<? extends AbstractTreeNode> children = currentRoot.getChildren();
      if (!children.isEmpty()) {
        builder.select(children.iterator().next());
      }
    }));

    Disposer.register(this, builder);
  }

  private void selectionChanged() {
    myConflictsPanel.setToInitialPosition();
    myUsagesPanel.setToInitialPosition();
    final DefaultMutableTreeNode[] migrationNodes = myRootsTree.getSelectedNodes(DefaultMutableTreeNode.class, null);
    if (migrationNodes.length == 0) {
      return;
    }
    final Object userObject = migrationNodes[0].getUserObject();
    if (userObject instanceof MigrationNode migrationNode) {
      final UsageInfo[] failedUsages = myLabeler.getFailedUsages(migrationNode.getInfo());
      if (failedUsages.length > 0) {
        myConflictsPanel.showUsages(PsiElement.EMPTY_ARRAY, failedUsages);
      }
      final AbstractTreeNode rootNode = (AbstractTreeNode) migrationNode.getParent();
      if (rootNode instanceof MigrationNode rootMigrationNode) {
        myUsagesPanel.showRootUsages(rootMigrationNode.getInfo(), migrationNode.getInfo(), myLabeler);
      }
    }
  }

  private JComponent createToolbar() {
    final JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gc = new GridBagConstraints(
      GridBagConstraints.RELATIVE, 0, 1, 1, 0, 1, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
      JBUI.insets(5, 10, 5, 0), 0, 0
    );
    final JButton performButton = new JButton(RefactoringLocalize.typeMigrationMigrateButtonText().get());
    performButton.addActionListener(new ActionListener() {
      private void expandTree(MigrationNode migrationNode) {
        if (!migrationNode.getInfo().isExcluded() || migrationNode.areChildrenInitialized()) {
          //do not walk into excluded collapsed nodes: nothing to migrate can be found
          final Collection<? extends AbstractTreeNode> nodes = migrationNode.getChildren();
          for (final AbstractTreeNode node : nodes) {
            Application.get().runReadAction(() -> expandTree((MigrationNode) node));
          }
        }
      }

      public void actionPerformed(final ActionEvent e) {
        final Object root = myRootsTree.getModel().getRoot();
        if (root instanceof DefaultMutableTreeNode defaultMutableTreeNode) {
          final Object userObject = defaultMutableTreeNode.getUserObject();
          if (userObject instanceof MigrationRootNode migrationRootNode) {
            ProgressManager.getInstance().runProcessWithProgressSynchronously(
              () -> {
                final HashSet<VirtualFile> files = new HashSet<>();
                final TypeMigrationUsageInfo[] usages = ReadAction.compute(() -> {
                  final Collection<? extends AbstractTreeNode> children = migrationRootNode.getChildren();
                  for (AbstractTreeNode child : children) {
                    expandTree((MigrationNode) child);
                  }
                  final TypeMigrationUsageInfo[] usages1 = myLabeler.getMigratedUsages();
                  for (TypeMigrationUsageInfo usage : usages1) {
                    if (!usage.isExcluded()) {
                      final PsiElement element = usage.getElement();
                      if (element != null) {
                        files.add(element.getContainingFile().getVirtualFile());
                      }
                    }
                  }
                  return usages1;
                });

                Application.get().invokeLater(
                  () -> {
                    if (ReadonlyStatusHandler.getInstance(myProject)
                      .ensureFilesWritable(VfsUtilCore.toVirtualFileArray(files)).hasReadonlyFiles()) {
                      return;
                    }
                    new WriteCommandAction(myProject) {
                      protected void run(@Nonnull Result result) throws Throwable {
                        TypeMigrationProcessor.change(usages, myLabeler, myProject);
                      }
                    }.execute();
                  },
                  myProject.getDisposed()
                );
              },
              "Type Migration",
              false,
              myProject
            );
          }
        }
        UsageViewContentManager.getInstance(myProject).closeContent(myContent);
      }
    });
    panel.add(performButton, gc);
    final JButton closeButton = new JButton(CommonLocalize.buttonCancel().get());
    closeButton.addActionListener(e -> UsageViewContentManager.getInstance(myProject).closeContent(myContent));
    panel.add(closeButton, gc);
    final JButton rerunButton = new JButton(RefactoringLocalize.typeMigrationRerunButtonText().get());
    rerunButton.addActionListener(e -> {
      UsageViewContentManager.getInstance(myProject).closeContent(myContent);
      final TypeMigrationDialog.MultipleElements dialog = new TypeMigrationDialog.MultipleElements(
        myProject,
        myInitialRoots,
        myLabeler.getMigrationRootTypeFunction(),
        myLabeler.getRules()
      );
      dialog.show();
    });
    panel.add(rerunButton, gc);
    final JButton helpButton = new JButton(CommonLocalize.buttonHelp().get());
    helpButton.addActionListener(e -> HelpManager.getInstance().invokeHelp("reference.typeMigrationPreview"));
    gc.weightx = 1;
    panel.add(helpButton, gc);

    return panel;
  }

  private void initTree(final Tree tree) {
    final MigrationRootsTreeCellRenderer rootsTreeCellRenderer = new MigrationRootsTreeCellRenderer();
    tree.setCellRenderer(rootsTreeCellRenderer);
    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);
    UIUtil.setLineStyleAngled(tree);

    TreeUtil.installActions(tree);
    TreeUtil.expandAll(tree);
    SmartExpander.installOn(tree);
    EditSourceOnDoubleClickHandler.install(tree);
    new TreeSpeedSearch(tree);
    PopupHandler.installUnknownPopupHandler(tree, createTreePopupActions(), ActionManager.getInstance());
  }

  private ActionGroup createTreePopupActions() {
    final DefaultActionGroup group = new DefaultActionGroup();
    //group.add(new PerformRefactoringAction());
    group.add(new ExcludeAction());
    group.add(new IncludeAction());
    group.addSeparator();
    final ActionManager actionManager = ActionManager.getInstance();
    group.add(actionManager.getAction(IdeActions.ACTION_EDIT_SOURCE));
    group.add(actionManager.getAction(IdeActions.GROUP_VERSION_CONTROLS));
    return group;
  }

  public void dispose() {
  }

  public void setContent(final Content content) {
    myContent = content;
    Disposer.register(content, this);
  }

  private static class MyTree extends Tree implements DataProvider {
    private MyTree(final TreeModel treemodel) {
      super(treemodel);
    }

    @Override
    protected void paintComponent(final Graphics g) {
      DuplicateNodeRenderer.paintDuplicateNodesBackground(g, this);
      super.paintComponent(g);
    }

    public Object getData(@NonNls final Key dataId) {
      if (PsiElement.KEY == dataId) {
        final DefaultMutableTreeNode[] selectedNodes = getSelectedNodes(DefaultMutableTreeNode.class, null);
        return selectedNodes.length == 1 && selectedNodes[0].getUserObject() instanceof MigrationNode migrationNode
          ? migrationNode.getInfo().getElement() : null;
      }
      if (MIGRATION_USAGES_KEYS == dataId) {
        DefaultMutableTreeNode[] selectedNodes = getSelectedNodes(DefaultMutableTreeNode.class, null);
        final Set<TypeMigrationUsageInfo> usageInfos = new HashSet<>();
        for (DefaultMutableTreeNode selectedNode : selectedNodes) {
          final Object userObject = selectedNode.getUserObject();
          if (userObject instanceof MigrationNode migrationNode) {
            collectInfos(usageInfos, migrationNode);
          }
        }
        return usageInfos.toArray(new TypeMigrationUsageInfo[0]);
      }
      return null;
    }

    private static void collectInfos(final Set<TypeMigrationUsageInfo> usageInfos, final MigrationNode currentNode) {
      usageInfos.add(currentNode.getInfo());
      if (!currentNode.areChildrenInitialized()) {
        return;
      }
      final Collection<? extends AbstractTreeNode> nodes = currentNode.getChildren();
      for (AbstractTreeNode node : nodes) {
        collectInfos(usageInfos, (MigrationNode) node);
      }
    }
  }

  private class ExcludeAction extends ExcludeIncludeActionBase {
    public ExcludeAction() {
      super(RefactoringLocalize.typeMigrationExcludeActionText());
      registerCustomShortcutSet(CommonShortcuts.getDelete(), myRootsTree);
    }

    protected void processUsage(final TypeMigrationUsageInfo usageInfo) {
      usageInfo.setExcluded(true);
    }
  }

  private class IncludeAction extends ExcludeIncludeActionBase {
    public IncludeAction() {
      super(RefactoringLocalize.typeMigrationIncludeActionText());
      registerCustomShortcutSet(CommonShortcuts.INSERT, myRootsTree);
    }

    protected void processUsage(final TypeMigrationUsageInfo usageInfo) {
      usageInfo.setExcluded(false);
    }

    @Override
    public void update(final AnActionEvent e) {
      final Presentation presentation = e.getPresentation();
      presentation.setEnabled(false);
      final DefaultMutableTreeNode[] selectedNodes = myRootsTree.getSelectedNodes(DefaultMutableTreeNode.class, null);
      for (DefaultMutableTreeNode node : selectedNodes) {
        final Object userObject = node.getUserObject();
        if (!(userObject instanceof MigrationNode)) {
          return;
        }
        final AbstractTreeNode parent = (AbstractTreeNode) ((MigrationNode) userObject).getParent(); //disable include if parent was excluded
        if (parent instanceof MigrationNode parentMigrationNode && parentMigrationNode.getInfo().isExcluded()) {
          return;
        }
      }
      presentation.setEnabled(true);
    }
  }

  private abstract class ExcludeIncludeActionBase extends AnAction {
    protected abstract void processUsage(TypeMigrationUsageInfo usageInfo);

    ExcludeIncludeActionBase(final LocalizeValue text) {
      super(text);
    }

    @Nullable
    private TypeMigrationUsageInfo[] getUsages(AnActionEvent context) {
      return context.getData(MIGRATION_USAGES_KEYS);
    }

    public void update(AnActionEvent e) {
      final TreePath[] selectionPaths = myRootsTree.getSelectionPaths();
      e.getPresentation().setEnabled(selectionPaths != null && selectionPaths.length > 0);
    }

    public void actionPerformed(@Nonnull AnActionEvent e) {
      final TypeMigrationUsageInfo[] usages = getUsages(e);
      assert usages != null;
      for (TypeMigrationUsageInfo usageInfo : usages) {
        processUsage(usageInfo);
      }
      myRootsTree.repaint();
    }
  }

  private static class MigrationRootsTreeCellRenderer extends ColoredTreeCellRenderer {
    @RequiredReadAction
    public void customizeCellRenderer(
      @Nonnull final JTree tree,
      final Object value,
      final boolean selected,
      final boolean expanded,
      final boolean leaf,
      final int row,
      final boolean hasFocus
    ) {
      final Object userObject = ((DefaultMutableTreeNode) value).getUserObject();
      if (!(userObject instanceof MigrationNode)) {
        return;
      }
      final TypeMigrationUsageInfo usageInfo = ((MigrationNode) userObject).getInfo();
      if (usageInfo != null) {
        final PsiElement element = usageInfo.getElement();
        if (element != null) {
          PsiElement typeElement = null;
          if (element instanceof PsiVariable variable) {
            typeElement = variable.getTypeElement();
          } else if (element instanceof PsiMethod method) {
            typeElement = method.getReturnTypeElement();
          }
          if (typeElement == null) {
            typeElement = element;
          }

          final UsagePresentation presentation =
            UsageInfoToUsageConverter.convert(new PsiElement[]{typeElement}, new UsageInfo(typeElement)).getPresentation();
          boolean isPrefix = true;  //skip usage position
          for (TextChunk chunk : presentation.getText()) {
            if (!isPrefix) {
              append(chunk.getText(), patchAttrs(usageInfo, chunk.getSimpleAttributesIgnoreBackground()));
            }
            isPrefix = false;
          }
          setIcon(presentation.getIcon());

          String location;
          if (element instanceof PsiMember) {
            location = SymbolPresentationUtil.getSymbolContainerText(element);
          } else {
            final PsiMember member = PsiTreeUtil.getParentOfType(element, PsiMember.class);
            if (member instanceof PsiField field) {
              location = PsiFormatUtil.formatVariable(
                field,
                PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_CONTAINING_CLASS | PsiFormatUtilBase.SHOW_FQ_NAME,
                PsiSubstitutor.EMPTY
              );
            } else if (member instanceof PsiMethod method) {
              location = PsiFormatUtil.formatMethod(
                method,
                PsiSubstitutor.EMPTY,
                PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_CONTAINING_CLASS | PsiFormatUtilBase.SHOW_FQ_NAME,
                PsiFormatUtilBase.SHOW_TYPE
              );
            } else if (member instanceof PsiClass psiClass) {
              location = PsiFormatUtil.formatClass(psiClass, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_FQ_NAME);
            } else {
              location = null;
            }
            if (location != null) {
              location = PsiBundle.message("aux.context.display", location);
            }
          }
          if (location != null) {
            append(location, SimpleTextAttributes.GRAYED_ATTRIBUTES);
          }
        } else {
          append(UsageViewBundle.message("node.invalid"), SimpleTextAttributes.ERROR_ATTRIBUTES);
        }
      }
    }

    private static SimpleTextAttributes patchAttrs(TypeMigrationUsageInfo usageInfo, SimpleTextAttributes original) {
      if (usageInfo.isExcluded()) {
        original = new SimpleTextAttributes(original.getStyle() | SimpleTextAttributes.STYLE_STRIKEOUT, original.getFgColor(), original.getWaveColor());
      }
      return original;
    }
  }
}
