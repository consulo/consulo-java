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
package com.intellij.java.impl.codeInsight;

import com.intellij.java.language.impl.ui.PackageChooser;
import com.intellij.java.language.psi.JavaDirectoryService;
import com.intellij.java.language.psi.PsiJavaPackage;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.AllIcons;
import consulo.content.FileIndex;
import consulo.ide.localize.IdeLocalize;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiNamedElement;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.ProjectRootManager;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.platform.base.localize.CommonLocalize;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.InputValidator;
import consulo.ui.ex.action.*;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.ScrollPaneFactory;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.awt.speedSearch.TreeSpeedSearch;
import consulo.ui.ex.awt.tree.Tree;
import consulo.ui.ex.awt.tree.TreeUtil;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.undoRedo.CommandProcessor;
import consulo.util.lang.Comparing;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.Enumeration;
import java.util.List;

public class PackageChooserDialog extends DialogWrapper implements PackageChooser {
  private static final Logger LOG = Logger.getInstance(PackageChooserDialog.class);

  private Tree myTree;
  private DefaultTreeModel myModel;
  private final Project myProject;
  private final String myTitle;
  private Module myModule;

  public PackageChooserDialog(String title, @Nonnull Module module) {
    super(module.getProject(), true);
    setTitle(title);
    myTitle = title;
    myProject = module.getProject();
    myModule = module;
    init();
  }

  public PackageChooserDialog(String title, Project project) {
    super(project, true);
    setTitle(title);
    myTitle = title;
    myProject = project;
    init();
  }

  @RequiredReadAction
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new BorderLayout());

    myModel = new DefaultTreeModel(new DefaultMutableTreeNode());
    createTreeModel();
    myTree = new Tree(myModel);

    UIUtil.setLineStyleAngled(myTree);
    myTree.setCellRenderer(
      new DefaultTreeCellRenderer() {
        public Component getTreeCellRendererComponent(
          JTree tree, Object value,
          boolean sel,
          boolean expanded,
          boolean leaf, int row,
          boolean hasFocus
        ) {
          super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
          setIcon(TargetAWT.to(PlatformIconGroup.nodesPackage()));

          if (value instanceof DefaultMutableTreeNode node) {
            Object object = node.getUserObject();
            if (object instanceof PsiJavaPackage javaPackage) {
              String name = javaPackage.getName();
              if (name != null && name.length() > 0) {
                setText(name);
              }
              else {
                setText(IdeLocalize.nodeDefault().get());
              }
            }
          }
          return this;
        }
      }
    );

    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTree);
    scrollPane.setPreferredSize(new Dimension(500, 300));

    new TreeSpeedSearch(myTree, path -> {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      Object object = node.getUserObject();
      return object instanceof PsiJavaPackage javaPackage ? javaPackage.getName() : "";
    });

    myTree.getSelectionModel().addTreeSelectionListener(e -> {
      PsiJavaPackage selection = getTreeSelection();
      if (selection != null) {
        String name = selection.getQualifiedName();
        setTitle(myTitle + " - " + ("".equals(name) ? IdeLocalize.nodeDefaultPackage().get() : name));
      }
      else {
        setTitle(myTitle);
      }
    });

    panel.add(scrollPane, BorderLayout.CENTER);
    DefaultActionGroup group = createActionGroup(myTree);

    ActionToolbar toolBar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true);
    panel.add(toolBar.getComponent(), BorderLayout.NORTH);
    toolBar.getComponent().setAlignmentX(JComponent.LEFT_ALIGNMENT);

    return panel;
  }

  private DefaultActionGroup createActionGroup(JComponent component) {
    final DefaultActionGroup group = new DefaultActionGroup();
    final DefaultActionGroup temp = new DefaultActionGroup();
    NewPackageAction newPackageAction = new NewPackageAction();
    newPackageAction.enableInModalConext();
    newPackageAction.registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_NEW_ELEMENT).getShortcutSet(), component);
    temp.add(newPackageAction);
    group.add(temp);
    return group;
  }

  protected void doOKAction(){
    super.doOKAction();
  }

  public String getDimensionServiceKey(){
    return "#com.intellij.java.impl.codeInsight.PackageChooserDialog";
  }

  public JComponent getPreferredFocusedComponent(){
    return myTree;
  }

  public PsiJavaPackage getSelectedPackage(){
    return getTreeSelection();
  }

  public List<PsiJavaPackage> getSelectedPackages() {
    return TreeUtil.collectSelectedObjectsOfType(myTree, PsiJavaPackage.class);
  }

  public void selectPackage(final String qualifiedName) {
    /*ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {*/
          DefaultMutableTreeNode node = findNodeForPackage(qualifiedName);
          if (node != null) {
            TreePath path = new TreePath(node.getPath());
            TreeUtil.selectPath(myTree, path);
          }
       /* }
      }, ModalityState.stateForComponent(getRootPane()));*/
  }

  @Nullable
  private PsiJavaPackage getTreeSelection() {
    if (myTree == null) return null;
    TreePath path = myTree.getSelectionPath();
    if (path == null) return null;
    DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
    return (PsiJavaPackage)node.getUserObject();
  }

  private void createTreeModel() {
    final PsiManager psiManager = PsiManager.getInstance(myProject);
    final FileIndex fileIndex = myModule != null ? ModuleRootManager.getInstance(myModule).getFileIndex() : ProjectRootManager.getInstance(myProject).getFileIndex();
    fileIndex.iterateContent(
      fileOrDir -> {
        if (fileOrDir.isDirectory() && fileIndex.isInSourceContent(fileOrDir)){
          final PsiDirectory psiDirectory = psiManager.findDirectory(fileOrDir);
          LOG.assertTrue(psiDirectory != null);
          PsiJavaPackage aPackage = JavaDirectoryService.getInstance().getPackage(psiDirectory);
          if (aPackage != null){
            addPackage(aPackage);
          }
        }
        return true;
      }
    );

    TreeUtil.sort(
      myModel,
      (o1, o2) -> {
        DefaultMutableTreeNode n1 = (DefaultMutableTreeNode) o1;
        DefaultMutableTreeNode n2 = (DefaultMutableTreeNode) o2;
        PsiNamedElement element1 = (PsiNamedElement) n1.getUserObject();
        PsiNamedElement element2 = (PsiNamedElement) n2.getUserObject();
        return element1.getName().compareToIgnoreCase(element2.getName());
      }
    );
  }

  @Nullable
  @Override
  public List<PsiJavaPackage> showAndSelect() {
    show();
    if (!isOK()) return null;
    return getSelectedPackages();
  }

  @Nonnull
  private DefaultMutableTreeNode addPackage(PsiJavaPackage aPackage) {
    final String qualifiedPackageName = aPackage.getQualifiedName();
    final PsiJavaPackage parentPackage = aPackage.getParentPackage();
    if (parentPackage == null) {
      final DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode)myModel.getRoot();
      if (qualifiedPackageName.isEmpty()) {
        rootNode.setUserObject(aPackage);
        return rootNode;
      }
      else {
        DefaultMutableTreeNode packageNode = findPackageNode(rootNode, qualifiedPackageName);
        if (packageNode != null) return packageNode;
        packageNode = new DefaultMutableTreeNode(aPackage);
        rootNode.add(packageNode);
        return packageNode;
      }
    }
    else {
      final DefaultMutableTreeNode parentNode = addPackage(parentPackage);
      DefaultMutableTreeNode packageNode = findPackageNode(parentNode, qualifiedPackageName);
      if (packageNode != null) {
        return packageNode;
      }
      packageNode = new DefaultMutableTreeNode(aPackage);
      parentNode.add(packageNode);
      return packageNode;
    }
  }

  @Nullable
  private static DefaultMutableTreeNode findPackageNode(DefaultMutableTreeNode rootNode, String qualifiedName) {
    for (int i = 0; i < rootNode.getChildCount(); i++) {
      final DefaultMutableTreeNode child = (DefaultMutableTreeNode)rootNode.getChildAt(i);
      final PsiJavaPackage nodePackage = (PsiJavaPackage)child.getUserObject();
      if (nodePackage != null) {
        if (Comparing.equal(nodePackage.getQualifiedName(), qualifiedName)) return child;
      }
    }
    return null;
  }

  private DefaultMutableTreeNode findNodeForPackage(String qualifiedPackageName) {
    DefaultMutableTreeNode root = (DefaultMutableTreeNode)myModel.getRoot();
    Enumeration enumeration = root.depthFirstEnumeration();
    while (enumeration.hasMoreElements()) {
      Object o = enumeration.nextElement();
      if (o instanceof DefaultMutableTreeNode node) {
        PsiJavaPackage nodePackage = (PsiJavaPackage)node.getUserObject();
        if (nodePackage != null) {
          if (Comparing.equal(nodePackage.getQualifiedName(), qualifiedPackageName)) return node;
        }
      }
    }
    return null;
  }

  private void createNewPackage() {
    final PsiJavaPackage selectedPackage = getTreeSelection();
    if (selectedPackage == null) return;

    final String newPackageName = Messages.showInputDialog(
      myProject,
      IdeLocalize.promptEnterANewPackageName().get(),
      IdeLocalize.titleNewPackage().get(),
      UIUtil.getQuestionIcon(),
      "",
      new InputValidator() {
        @RequiredUIAccess
        public boolean checkInput(final String inputString) {
          return inputString != null && inputString.length() > 0;
        }

        @RequiredUIAccess
        public boolean canClose(final String inputString) {
          return checkInput(inputString);
        }
      }
    );
    if (newPackageName == null) return;

    CommandProcessor.getInstance().executeCommand(
      myProject,
      () -> {
        final Runnable action = () -> {
          try {
            String newQualifiedName = selectedPackage.getQualifiedName();
            if (!Comparing.strEqual(newQualifiedName,"")) newQualifiedName += ".";
            newQualifiedName += newPackageName;
            final PsiDirectory dir = PackageUtil.findOrCreateDirectoryForPackage(myProject, newQualifiedName, null, false);
            if (dir == null) return;
            final PsiJavaPackage newPackage = JavaDirectoryService.getInstance().getPackage(dir);

            DefaultMutableTreeNode node = (DefaultMutableTreeNode)myTree.getSelectionPath().getLastPathComponent();
            final DefaultMutableTreeNode newChild = new DefaultMutableTreeNode();
            newChild.setUserObject(newPackage);
            node.add(newChild);

            final DefaultTreeModel model = (DefaultTreeModel)myTree.getModel();
            model.nodeStructureChanged(node);

            final TreePath selectionPath = myTree.getSelectionPath();
            TreePath path;
            if (selectionPath == null) {
              path = new TreePath(newChild.getPath());
            } else {
              path = selectionPath.pathByAddingChild(newChild);
            }
            myTree.setSelectionPath(path);
            myTree.scrollPathToVisible(path);
            myTree.expandPath(path);

          }
          catch (IncorrectOperationException e) {
            Messages.showMessageDialog(
              getContentPane(),
              e.getMessage(),
              CommonLocalize.titleError().get(),
              UIUtil.getErrorIcon()
            );
            if (LOG.isDebugEnabled()) {
              LOG.debug(e);
            }
          }
        };
        myProject.getApplication().runReadAction(action);
      },
      IdeLocalize.commandCreateNewPackage().get(),
      null
    );
  }

  private class NewPackageAction extends AnAction {
    public NewPackageAction() {
      super(IdeLocalize.actionNewPackage(), IdeLocalize.actionDescriptionCreateNewPackage(), AllIcons.Actions.NewFolder);
    }

    @RequiredUIAccess
    public void actionPerformed(@Nonnull AnActionEvent e) {
      createNewPackage();
    }

    @RequiredUIAccess
    public void update(AnActionEvent event) {
      Presentation presentation = event.getPresentation();
      presentation.setEnabled(getTreeSelection() != null);
    }

    public void enableInModalConext() {
      setEnabledInModalContext(true);
    }
  }
}
