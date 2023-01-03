package com.intellij.projectView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import javax.annotation.Nonnull;

import consulo.ui.ex.tree.PresentationData;
import consulo.project.ui.view.tree.ProjectViewNode;
import consulo.project.ui.view.tree.TreeStructureProvider;
import consulo.project.ui.view.tree.ViewSettings;
import consulo.project.ui.view.tree.AbstractTreeNode;
import consulo.util.collection.MultiValuesMap;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;

class SameNamesJoiner implements TreeStructureProvider {
  @Override
  public Collection<AbstractTreeNode> modify(AbstractTreeNode parent, Collection<AbstractTreeNode> children, ViewSettings settings) {
    if (parent instanceof JoinedNode) return children;

    ArrayList<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>();

    MultiValuesMap<Object, AbstractTreeNode> executed = new MultiValuesMap<Object, AbstractTreeNode>();
    for (Iterator<AbstractTreeNode> iterator = children.iterator(); iterator.hasNext();) {
      ProjectViewNode treeNode = (ProjectViewNode)iterator.next();
      Object o = treeNode.getValue();
      if (o instanceof PsiFile) {
        String name = ((PsiFile)o).getVirtualFile().getNameWithoutExtension();
        executed.put(name, treeNode);
      }
      else {
        executed.put(o, treeNode);
      }
    }

    Iterator<Object> keys = executed.keySet().iterator();
    while (keys.hasNext()) {
      Object each = keys.next();
      Collection<AbstractTreeNode> objects = executed.get(each);
      if (objects.size() > 1) {
        result.add(new JoinedNode(objects, new Joined(findPsiFileIn(objects))));
      }
      else if (objects.size() == 1) {
        result.add(objects.iterator().next());
      }
    }

    return result;
  }


  public PsiElement getTopLevelElement(final PsiElement element) {
    return null;
  }

  private PsiFile findPsiFileIn(Collection<AbstractTreeNode> objects) {
    for (Iterator<AbstractTreeNode> iterator = objects.iterator(); iterator.hasNext();) {
      AbstractTreeNode treeNode = iterator.next();
      if (treeNode.getValue() instanceof PsiFile) return (PsiFile)treeNode.getValue();
    }
    return null;
  }

  private boolean hasElementWithTheSameName(PsiFile element) {
    PsiDirectory psiDirectory = element.getParent();
    PsiElement[] children = psiDirectory.getChildren();
    for (int i = 0; i < children.length; i++) {
      PsiElement child = children[i];

      if (child != element && element.getVirtualFile().getNameWithoutExtension().equals(((PsiFile)child).getVirtualFile().getNameWithoutExtension())){
        return true;
      }
    }

    return false;
  }

  private class Joined{
    private final String myName;
    private final PsiFile myFile;

    public Joined(PsiFile file) {
      myFile = file;
      myName = file.getName();
    }

    public String toString() {
      return myFile.getVirtualFile().getNameWithoutExtension();
    }

    public PsiFile getPsiFile() {
      return myFile;
    }

    public boolean equals(Object object) {
      if (!(object instanceof Joined)) return false;
      return myFile.getVirtualFile().getNameWithoutExtension()
        .equals(((Joined)object).myFile.getVirtualFile().getNameWithoutExtension());
    }
  }

  private class JoinedNode extends ProjectViewNode<Joined>{
    Collection<AbstractTreeNode> myChildren;

    @Override
    @Nonnull
    public Collection<AbstractTreeNode> getChildren() {
      return myChildren;
    }

    public JoinedNode(Collection<AbstractTreeNode> children, Joined formFile) {
      super(null, formFile, null);
      myChildren = children;
    }

    @Override
    public String getTestPresentation() {
      return getValue().toString() + " joined";
    }

    @Override
    public boolean contains(@Nonnull VirtualFile file) {
      return false;
    }

    @Override
    public void update(PresentationData presentation) {
    }
  }
}
