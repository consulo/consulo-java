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
package com.intellij.java.impl.slicer;

import consulo.ui.ex.awt.tree.AbstractTreeBuilder;
import consulo.ui.ex.tree.AlphaComparator;
import consulo.ui.ex.tree.NodeDescriptor;
import consulo.project.Project;
import consulo.util.lang.Comparing;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;

import jakarta.annotation.Nonnull;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.util.Comparator;

/**
 * @author cdr
 */
public class SliceTreeBuilder extends AbstractTreeBuilder {
  public final boolean splitByLeafExpressions;
  public final boolean dataFlowToThis;
  public volatile boolean analysisInProgress;

  public static final Comparator<NodeDescriptor> SLICE_NODE_COMPARATOR = new Comparator<NodeDescriptor>() {
    @Override
    public int compare(NodeDescriptor o1, NodeDescriptor o2) {
      if (!(o1 instanceof SliceNode) || !(o2 instanceof SliceNode)) {
        return AlphaComparator.INSTANCE.compare(o1, o2);
      }
      SliceNode node1 = (SliceNode)o1;
      SliceNode node2 = (SliceNode)o2;
      SliceUsage usage1 = node1.getValue();
      SliceUsage usage2 = node2.getValue();

      PsiElement element1 = usage1.getElement();
      PsiElement element2 = usage2.getElement();

      PsiFile file1 = element1 == null ? null : element1.getContainingFile();
      PsiFile file2 = element2 == null ? null : element2.getContainingFile();

      if (file1 == null) return file2 == null ? 0 : 1;
      if (file2 == null) return -1;

      if (file1 == file2) {
        return element1.getTextOffset() - element2.getTextOffset();
      }

      return Comparing.compare(file1.getName(), file2.getName());
    }
  };

  public SliceTreeBuilder(@Nonnull JTree tree,
                          @Nonnull Project project,
                          boolean dataFlowToThis,
                          @Nonnull SliceNode rootNode,
                          boolean splitByLeafExpressions) {
    super(tree, (DefaultTreeModel)tree.getModel(), new SliceTreeStructure(project, rootNode), SLICE_NODE_COMPARATOR, false);
    this.dataFlowToThis = dataFlowToThis;
    this.splitByLeafExpressions = splitByLeafExpressions;
    initRootNode();
  }

  public SliceNode getRootSliceNode() {
    return (SliceNode)getTreeStructure().getRootElement();
  }

  @Override
  protected boolean isAutoExpandNode(NodeDescriptor nodeDescriptor) {
    return false;
  }

  public void switchToGroupedByLeavesNodes() {
    analysisInProgress = true;
    SliceLeafAnalyzer.startAnalyzeValues(getTreeStructure(), new Runnable(){
      @Override
      public void run() {
        analysisInProgress = false;
      }
    });
  }


  public void switchToLeafNulls() {
    analysisInProgress = true;
    SliceNullnessAnalyzer.startAnalyzeNullness(getTreeStructure(), new Runnable(){
      @Override
      public void run() {
        analysisInProgress = false;
      }
    });
  }
}
