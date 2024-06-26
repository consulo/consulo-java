/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.java.compiler.impl.options;

import com.intellij.java.compiler.impl.javaCompiler.annotationProcessing.ProcessorConfigProfile;
import com.intellij.java.compiler.impl.javaCompiler.annotationProcessing.impl.ProcessorConfigProfileImpl;
import consulo.application.AllIcons;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.ui.ex.InputValidatorEx;
import consulo.ui.ex.RelativePoint;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.ShortcutSet;
import consulo.ui.ex.awt.*;
import consulo.ui.ex.awt.tree.ColoredTreeCellRenderer;
import consulo.ui.ex.awt.tree.EditableTreeModel;
import consulo.ui.ex.awt.tree.Tree;
import consulo.ui.ex.awt.tree.TreeUtil;
import consulo.ui.ex.popup.JBPopup;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.*;

/**
 * @author Konstantin Bulenkov
 */
public class AnnotationProcessorsPanel extends JPanel {
  private final ProcessorConfigProfile myDefaultProfile = new ProcessorConfigProfileImpl("");
  private final List<ProcessorConfigProfile> myModuleProfiles = new ArrayList<ProcessorConfigProfile>();
  private final Map<String, Module> myAllModulesMap = new HashMap<String, Module>();
  private final Project myProject;
  private final Tree myTree;
  private final ProcessorProfilePanel myProfilePanel;
  private ProcessorConfigProfile mySelectedProfile = null;

  public AnnotationProcessorsPanel(Project project) {
    super(new BorderLayout());
    Splitter splitter = new Splitter(false, 0.3f);
    add(splitter, BorderLayout.CENTER);
    myProject = project;
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      myAllModulesMap.put(module.getName(), module);
    }
    myTree = new Tree(new MyTreeModel());
    myTree.setRootVisible(false);
        final JPanel treePanel =
          ToolbarDecorator.createDecorator(myTree).addExtraAction(new AnActionButton("Move to", PlatformIconGroup.actionsForward()) {
            @Override
            public void actionPerformed(AnActionEvent e) {
              final MyModuleNode node = (MyModuleNode)myTree.getSelectionPath().getLastPathComponent();
              final TreePath[] selectedNodes = myTree.getSelectionPaths();
              final ProcessorConfigProfile nodeProfile = ((ProfileNode)node.getParent()).myProfile;
              final List<ProcessorConfigProfile> profiles = new ArrayList<ProcessorConfigProfile>();
              profiles.add(myDefaultProfile);
              for (ProcessorConfigProfile profile : myModuleProfiles) {
                profiles.add(profile);
              }
              profiles.remove(nodeProfile);
              final JBPopup popup = JBPopupFactory.getInstance().createPopupChooserBuilder(profiles)
                .setTitle("Move to")
                .setItemChosenCallback((value) -> {
                  if (value instanceof ProcessorConfigProfile) {
                    final ProcessorConfigProfile chosenProfile = (ProcessorConfigProfile)value;
                    final Module toSelect = (Module)node.getUserObject();
                    if (selectedNodes != null) {
                      for (TreePath selectedNode : selectedNodes) {
                        final Object node1 = selectedNode.getLastPathComponent();
                        if (node1 instanceof MyModuleNode) {
                          final Module module = (Module)((MyModuleNode) node1).getUserObject();
                          if (nodeProfile != myDefaultProfile) {
                            nodeProfile.removeModuleName(module.getName());
                          }
                          if (chosenProfile != myDefaultProfile) {
                            chosenProfile.addModuleName(module.getName());
                          }
                        }
                      }
                    }

                    final RootNode root = (RootNode)myTree.getModel().getRoot();
                    root.sync();
                    final DefaultMutableTreeNode node1 = TreeUtil.findNodeWithObject(root, toSelect);
                    if (node1 != null) {
                      TreeUtil.selectNode(myTree, node1);
                    }
                  }
                })
                .createPopup();
              RelativePoint point =
                e.getInputEvent() instanceof MouseEvent ? getPreferredPopupPoint() : TreeUtil.getPointForSelection(myTree);
              popup.show(point);
            }

            @Override
            public ShortcutSet getShortcut() {
              return ActionManager.getInstance().getAction("Move").getShortcutSet();
            }

            @Override
            public boolean isEnabled() {
              return myTree.getSelectionPath() != null
                     && myTree.getSelectionPath().getLastPathComponent() instanceof MyModuleNode
                     && !myModuleProfiles.isEmpty();
            }
          }).createPanel();
    splitter.setFirstComponent(treePanel);
    myTree.setCellRenderer(new MyCellRenderer());

    myTree.addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        final TreePath path = myTree.getSelectionPath();
        if (path != null) {
          Object node = path.getLastPathComponent();
          if (node instanceof MyModuleNode) {
            node = ((MyModuleNode)node).getParent();
          }
          if (node instanceof ProfileNode) {
            final ProcessorConfigProfile nodeProfile = ((ProfileNode)node).myProfile;
            final ProcessorConfigProfile selectedProfile = mySelectedProfile;
            if (nodeProfile != selectedProfile) {
              if (selectedProfile != null) {
                myProfilePanel.saveTo(selectedProfile);
              }
              mySelectedProfile = nodeProfile;
              myProfilePanel.setProfile(nodeProfile);
            }
          }
        }
      }
    });
    myProfilePanel = new ProcessorProfilePanel(project);
    myProfilePanel.setBorder(IdeBorderFactory.createEmptyBorder(0, 6, 0, 0));
    splitter.setSecondComponent(myProfilePanel);
  }

  public void initProfiles(ProcessorConfigProfile defaultProfile, Collection<ProcessorConfigProfile> moduleProfiles) {
    myDefaultProfile.initFrom(defaultProfile);
    myModuleProfiles.clear();
    for (ProcessorConfigProfile profile : moduleProfiles) {
      ProcessorConfigProfile copy = new ProcessorConfigProfileImpl("");
      copy.initFrom(profile);
      myModuleProfiles.add(copy);
    }
    final RootNode root = (RootNode)myTree.getModel().getRoot();
    root.sync();
    final DefaultMutableTreeNode node = TreeUtil.findNodeWithObject(root, myDefaultProfile);
    if (node != null) {
      TreeUtil.selectNode(myTree, node);
    }

  }

  public ProcessorConfigProfile getDefaultProfile() {
    final ProcessorConfigProfile selectedProfile = mySelectedProfile;
    if (myDefaultProfile == selectedProfile) {
      myProfilePanel.saveTo(selectedProfile);
    }
    return myDefaultProfile;
  }

  public List<ProcessorConfigProfile> getModuleProfiles() {
    final ProcessorConfigProfile selectedProfile = mySelectedProfile;
    if (myDefaultProfile != selectedProfile) {
      myProfilePanel.saveTo(selectedProfile);
    }
    return myModuleProfiles;
  }

  private static void expand(JTree tree) {
    int oldRowCount = 0;
    do {
      int rowCount = tree.getRowCount();
      if (rowCount == oldRowCount) break;
      oldRowCount = rowCount;
      for (int i = 0; i < rowCount; i++) {
        tree.expandRow(i);
      }
    }
    while (true);
  }

  private class MyTreeModel extends DefaultTreeModel implements EditableTreeModel{
    public MyTreeModel() {
      super(new RootNode());
    }

    @Override
    public TreePath addNode(TreePath parentOrNeighbour) {
      final String newProfileName = Messages.showInputDialog(
        myProject, "Profile name", "Create new profile", null, "",
        new InputValidatorEx() {
          @Override
          public boolean checkInput(String inputString) {
            if (StringUtil.isEmpty(inputString) ||
                Comparing.equal(inputString, myDefaultProfile.getName())) {
              return false;
            }
            for (ProcessorConfigProfile profile : myModuleProfiles) {
              if (Comparing.equal(inputString, profile.getName())) {
                return false;
              }
            }
            return true;
          }

          @Override
          public boolean canClose(String inputString) {
            return checkInput(inputString);
          }

          @Override
          public String getErrorText(String inputString) {
            if (checkInput(inputString)) {
              return null;
            }
            return StringUtil.isEmpty(inputString)
                   ? "Profile name shouldn't be empty"
                   : "Profile " + inputString + " already exists";
          }
        });
      if (newProfileName != null) {
        final ProcessorConfigProfile profile = new ProcessorConfigProfileImpl(newProfileName);
        myModuleProfiles.add(profile);
        ((DataSynchronizable)getRoot()).sync();
        final DefaultMutableTreeNode object = TreeUtil.findNodeWithObject((DefaultMutableTreeNode)getRoot(), profile);
        if (object != null) {
          TreeUtil.selectNode(myTree, object);
        }
      }
      return null;
    }

    @Override
    public void removeNode(TreePath nodePath) {
      Object node = nodePath.getLastPathComponent();
      if (node instanceof ProfileNode) {
        final ProcessorConfigProfile nodeProfile = ((ProfileNode)node).myProfile;
        if (nodeProfile != myDefaultProfile) {
          if (mySelectedProfile == nodeProfile) {
            mySelectedProfile = null;
          }
          myModuleProfiles.remove(nodeProfile);
          ((DataSynchronizable)getRoot()).sync();
          final DefaultMutableTreeNode object = TreeUtil.findNodeWithObject((DefaultMutableTreeNode)getRoot(), myDefaultProfile);
          if (object != null) {
            TreeUtil.selectNode(myTree, object);
          }
        }
      }
    }

    @Override
    public void moveNodeTo(TreePath parentOrNeighbour) {
    }

  }


  private class RootNode extends DefaultMutableTreeNode implements DataSynchronizable {
    @Override
    public DataSynchronizable sync() {
      final Vector newKids =  new Vector();
      newKids.add(new ProfileNode(myDefaultProfile, this, true).sync());
      for (ProcessorConfigProfile profile : myModuleProfiles) {
        newKids.add(new ProfileNode(profile, this, false).sync());
      }
      children = newKids;
      ((DefaultTreeModel)myTree.getModel()).reload();
      expand(myTree);
      return this;
    }
  }

  private interface DataSynchronizable {
    DataSynchronizable sync();
  }

  private class ProfileNode extends DefaultMutableTreeNode implements DataSynchronizable {
    private final ProcessorConfigProfile myProfile;
    private final boolean myIsDefault;

    public ProfileNode(ProcessorConfigProfile profile, RootNode parent, boolean isDefault) {
      super(profile);
      setParent(parent);
      myIsDefault = isDefault;
      myProfile = profile;
    }

    @Override
    public DataSynchronizable sync() {
      final List<Module> nodeModules = new ArrayList<Module>();
      if (myIsDefault) {
        final Set<String> nonDefaultProfileModules = new HashSet<String>();
        for (ProcessorConfigProfile profile : myModuleProfiles) {
          nonDefaultProfileModules.addAll(profile.getModuleNames());
        }
        for (Map.Entry<String, Module> entry : myAllModulesMap.entrySet()) {
          if (!nonDefaultProfileModules.contains(entry.getKey())) {
            nodeModules.add(entry.getValue());
          }
        }
      }
      else {
        for (String moduleName : myProfile.getModuleNames()) {
          final Module module = myAllModulesMap.get(moduleName);
          if (module != null) {
            nodeModules.add(module);
          }
        }
      }
      Collections.sort(nodeModules, ModuleComparator.INSTANCE);
      final Vector vector = new Vector();
      for (Module module : nodeModules) {
        vector.add(new MyModuleNode(module, this));
      }
      children = vector;
      return this;
    }

  }

  private static class MyModuleNode extends DefaultMutableTreeNode {
    public MyModuleNode(Module module, ProfileNode parent) {
      super(module);
      setParent(parent);
      setAllowsChildren(false);
    }
  }

  private static class MyCellRenderer extends ColoredTreeCellRenderer {
    @Override
    public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      if (value instanceof ProfileNode) {
        append(((ProfileNode)value).myProfile.getName());
      }
      else if (value instanceof MyModuleNode) {
        final Module module = (Module)((MyModuleNode)value).getUserObject();
        setIcon(AllIcons.Nodes.Module);
        append(module.getName());
      }
    }
  }

  private static class ModuleComparator implements Comparator<Module> {
    static final ModuleComparator INSTANCE = new ModuleComparator();
    @Override
    public int compare(Module o1, Module o2) {
      return o1.getName().compareTo(o2.getName());
    }
  }

}
