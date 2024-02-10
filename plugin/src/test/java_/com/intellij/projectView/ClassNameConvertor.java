package com.intellij.projectView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import jakarta.annotation.Nonnull;
import consulo.ui.ex.tree.PresentationData;
import consulo.project.ui.view.tree.ProjectViewNode;
import consulo.project.ui.view.tree.TreeStructureProvider;
import consulo.project.ui.view.tree.ViewSettings;
import consulo.project.ui.view.tree.AbstractTreeNode;
import consulo.project.Project;
import consulo.application.util.Queryable;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiFile;

class ClassNameConvertor implements TreeStructureProvider {

  private final Project myProject;

  public ClassNameConvertor(Project project) {
    myProject = project;
  }

  @Override
  public Collection<AbstractTreeNode> modify(AbstractTreeNode parent, Collection<AbstractTreeNode> children, ViewSettings settings) {
    ArrayList<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>();

    for (final AbstractTreeNode aChildren : children) {
      ProjectViewNode treeNode = (ProjectViewNode)aChildren;
      Object o = treeNode.getValue();
      if (o instanceof PsiFile && ((PsiFile)o).getVirtualFile().getExtension().equals("java")) {
        final String name = ((PsiFile)o).getName();
        ProjectViewNode viewNode = new ProjectViewNode<PsiFile>(myProject, (PsiFile)o, settings) {
          @Override
          @Nonnull
          public Collection<AbstractTreeNode> getChildren() {
            return Collections.emptyList();
          }

          @Override
          public String toTestString(Queryable.PrintInfo printInfo) {
            return super.toTestString(printInfo) + " converted";
          }

          @Override
          public String getTestPresentation() {
            return name + " converted";
          }

          @Override
          public boolean contains(@Nonnull VirtualFile file) {
            return false;
          }

          @Override
          public void update(PresentationData presentation) {
          }

        };
        result.add(viewNode);
      }
      else {
        result.add(treeNode);
      }
    }
    return result;
  }
}
