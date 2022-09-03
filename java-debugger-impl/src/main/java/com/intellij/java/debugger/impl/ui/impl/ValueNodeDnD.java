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
package com.intellij.java.debugger.impl.ui.impl;

import com.intellij.java.debugger.impl.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.java.debugger.impl.ui.impl.watch.ValueDescriptorImpl;
import consulo.ui.ex.awt.dnd.*;
import consulo.ui.ex.awt.dnd.DnDSource;
import consulo.ide.impl.idea.ide.dnd.aware.DnDAwareTree;
import consulo.util.lang.Pair;
import com.intellij.ui.treeStructure.Tree;
import consulo.disposer.Disposable;
import consulo.ui.ex.awt.dnd.DnDAction;
import consulo.ui.ex.awt.dnd.DnDManager;

import javax.annotation.Nullable;

import javax.swing.tree.TreePath;
import java.awt.*;

public class ValueNodeDnD {
  private final DnDAwareTree myTree;

  public ValueNodeDnD(DnDAwareTree tree, Disposable parent) {
    myTree = tree;

    DnDManager.getInstance().registerSource(new DnDSource() {
      public boolean canStartDragging(final DnDAction action, final Point dragOrigin) {
        return getNodesToDrag().length > 0;
      }

      public DnDDragStartBean startDragging(final DnDAction action, final Point dragOrigin) {
        DebuggerTreeNodeImpl[] nodes = getNodesToDrag();
        return new DnDDragStartBean(nodes);
      }

      @Nullable
      public Pair<Image, Point> createDraggedImage(final DnDAction action, final Point dragOrigin) {
        DebuggerTreeNodeImpl[] nodes = getNodesToDrag();

        Pair<Image, Point> image;
        if (nodes.length == 1) {
          image = DnDAwareTree.getDragImage(myTree, new TreePath(nodes[0].getPath()), dragOrigin);
        } else {
          image = DnDAwareTree.getDragImage(myTree, nodes.length + " elements", dragOrigin);
        }

        return image;
      }

      public void dragDropEnd() {
      }

      public void dropActionChanged(final int gestureModifiers) {
      }
    }, tree);
  }

  private DebuggerTreeNodeImpl[] getNodesToDrag() {
    return myTree.getSelectedNodes(DebuggerTreeNodeImpl.class, new Tree.NodeFilter<DebuggerTreeNodeImpl>() {
      public boolean accept(final DebuggerTreeNodeImpl node) {
        return node.getDescriptor() instanceof ValueDescriptorImpl;
      }
    });
  }

}
