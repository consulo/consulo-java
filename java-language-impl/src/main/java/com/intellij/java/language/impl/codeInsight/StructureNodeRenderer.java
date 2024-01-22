/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.java.language.impl.codeInsight;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiDocCommentOwner;
import com.intellij.java.language.psi.PsiMember;
import consulo.language.psi.PsiElement;
import consulo.ui.ex.ColoredTextContainer;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.ui.ex.awt.tree.ColoredTreeCellRenderer;
import consulo.ui.ex.tree.NodeDescriptor;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;

public class StructureNodeRenderer extends ColoredTreeCellRenderer {
  @Override
  public void customizeCellRenderer(@Nonnull JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    forNodeDescriptorInTree(this, value, expanded);
  }

  public static void forNodeDescriptorInTree(ColoredTextContainer component, Object node, boolean expanded) {
    NodeDescriptor descriptor = getNodeDescriptor(node);
    if (descriptor == null) return;
    String name = descriptor.toString();
    Object psiElement = descriptor.getElement();
    if (psiElement instanceof PsiElement && !((PsiElement) psiElement).isValid()) {
      component.append(name);
    } else {
      PsiClass psiClass = getContainingClass(psiElement);

      if (isInheritedMember(node, psiClass) && psiClass != null) {
        component.append(name, applyDeprecation(psiElement, SimpleTextAttributes.DARK_TEXT));
        component.append("(" + psiClass.getName() + ")", applyDeprecation(psiClass, SimpleTextAttributes.GRAY_ATTRIBUTES));
      } else {
        SimpleTextAttributes textAttributes = applyDeprecation(psiElement, SimpleTextAttributes.REGULAR_ATTRIBUTES);
        component.append(name, textAttributes);
      }
    }

    component.setIcon(descriptor.getIcon());
  }

  private static boolean isInheritedMember(Object node, PsiClass psiClass) {
    PsiClass treeParentClass = getTreeParentClass(node);
    return treeParentClass != psiClass;
  }

  public static SimpleTextAttributes applyDeprecation(Object value, SimpleTextAttributes nameAttributes) {
    return isDeprecated(value) ? makeStrikeout(nameAttributes) : nameAttributes;
  }

  private static SimpleTextAttributes makeStrikeout(SimpleTextAttributes nameAttributes) {
    return new SimpleTextAttributes(nameAttributes.getStyle() | SimpleTextAttributes.STYLE_STRIKEOUT, nameAttributes.getFgColor());
  }

  private static boolean isDeprecated(Object psiElement) {
    return psiElement instanceof PsiDocCommentOwner && ((PsiDocCommentOwner) psiElement).isDeprecated();
  }

  private static PsiClass getContainingClass(Object element) {
    if (element instanceof PsiMember)
      return ((PsiMember) element).getContainingClass();
    return null;
  }

  private static PsiClass getTreeParentClass(Object value) {
    if (!(value instanceof TreeNode))
      return null;
    for (TreeNode treeNode = ((TreeNode) value).getParent(); treeNode != null; treeNode = treeNode.getParent()) {
      Object element = getElement(treeNode);
      if (element instanceof PsiClass)
        return (PsiClass) element;
    }
    return null;
  }

  private static NodeDescriptor getNodeDescriptor(Object value) {
    if (value instanceof DefaultMutableTreeNode) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
      Object userObject = node.getUserObject();
      if (userObject instanceof NodeDescriptor) {
        return (NodeDescriptor) userObject;
      }
    }
    return null;
  }

  private static Object getElement(Object node) {
    NodeDescriptor descriptor = getNodeDescriptor(node);
    return descriptor == null ? null : descriptor.getElement();
  }
}
