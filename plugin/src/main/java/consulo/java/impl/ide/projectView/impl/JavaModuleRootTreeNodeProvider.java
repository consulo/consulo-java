package consulo.java.impl.ide.projectView.impl;

import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.java.impl.ide.JavaModuleIconDescriptorUpdater;
import consulo.language.psi.PsiDirectory;
import consulo.project.Project;
import consulo.project.ui.view.tree.AbstractTreeNode;
import consulo.project.ui.view.tree.TreeStructureProvider;
import consulo.project.ui.view.tree.ViewSettings;
import jakarta.inject.Inject;

import java.util.ArrayDeque;
import java.util.Collection;

/**
 * @author VISTALL
 * @since 2024-02-25
 */
@ExtensionImpl
public class JavaModuleRootTreeNodeProvider implements TreeStructureProvider, DumbAware {
  private final Project myProject;

  @Inject
  public JavaModuleRootTreeNodeProvider(Project project) {
    myProject = project;
  }

  @Override
  @RequiredReadAction
  public Collection<AbstractTreeNode> modify(AbstractTreeNode abstractTreeNode,
                                             Collection<AbstractTreeNode> collection,
                                             ViewSettings viewSettings) {
    Collection<AbstractTreeNode> result = new ArrayDeque<>(collection.size());
    for (AbstractTreeNode child : collection) {
      Object value = child.getValue();
      if (value instanceof PsiDirectory psiDirectory && JavaModuleIconDescriptorUpdater.isModuleDirectory(psiDirectory)) {
        result.add(new JavaModuleRootTreeNode(myProject, psiDirectory, viewSettings));
      } else {
        result.add(child);
      }
    }
    return result;
  }
}
