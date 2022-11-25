/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import consulo.language.editor.PsiEquivalenceUtil;
import consulo.ide.impl.idea.concurrency.ConcurrentCollectionFactory;
import consulo.project.ui.view.tree.AbstractTreeNode;
import consulo.ui.ex.tree.AbstractTreeStructure;
import consulo.application.ApplicationManager;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.progress.Task;
import consulo.ui.ex.awt.Messages;
import consulo.util.lang.Comparing;
import consulo.application.util.function.Computable;
import consulo.util.lang.ref.Ref;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiJavaReference;
import consulo.language.psi.PsiNamedElement;
import consulo.language.impl.internal.ast.AstBufferUtil;
import consulo.ide.impl.idea.util.NullableFunction;
import consulo.util.lang.function.PairProcessor;
import consulo.util.collection.util.WalkingState;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.FactoryMap;
import consulo.util.collection.HashingStrategy;
import consulo.util.collection.Sets;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * @author cdr
 */
public class SliceLeafAnalyzer {
  public static final HashingStrategy<PsiElement> LEAF_ELEMENT_EQUALITY = new HashingStrategy<PsiElement>() {
    @Override
    public int hashCode(final PsiElement element) {
      if (element == null) return 0;
      String text = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
        @Override
        public String compute() {
          PsiElement elementToCompare = element;
          if (element instanceof PsiJavaReference) {
            PsiElement resolved = ((PsiJavaReference)element).resolve();
            if (resolved != null) {
              elementToCompare = resolved;
            }
          }
          return elementToCompare instanceof PsiNamedElement ? ((PsiNamedElement)elementToCompare).getName()
                                                             : AstBufferUtil.getTextSkippingWhitespaceComments(elementToCompare.getNode());
        }
      });
      return Comparing.hashcode(text);
    }

    @Override
    public boolean equals(final PsiElement o1, final PsiElement o2) {
      return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
        @Override
        public Boolean compute() {
          return o1 != null && o2 != null && PsiEquivalenceUtil.areElementsEquivalent(o1, o2);
        }
      });
    }
  };

  static SliceNode filterTree(SliceNode oldRoot, NullableFunction<SliceNode, SliceNode> filter, PairProcessor<SliceNode, List<SliceNode>> postProcessor){
    SliceNode filtered = filter.apply(oldRoot);
    if (filtered == null) return null;

    List<SliceNode> childrenFiltered = new ArrayList<SliceNode>();
    if (oldRoot.myCachedChildren != null) {
      for (SliceNode child : oldRoot.myCachedChildren) {
        SliceNode childFiltered = filterTree(child, filter,postProcessor);
        if (childFiltered != null) {
          childrenFiltered.add(childFiltered);
        }
      }
    }
    boolean success = postProcessor == null || postProcessor.process(filtered, childrenFiltered);
    if (!success) return null;
    filtered.myCachedChildren = new ArrayList<SliceNode>(childrenFiltered);
    return filtered;
  }

  private static void groupByValues(@Nonnull Collection<PsiElement> leaves,
                                    @Nonnull SliceRootNode oldRoot,
                                    @Nonnull Map<SliceNode, Collection<PsiElement>> map) {
    assert oldRoot.myCachedChildren.size() == 1;
    SliceRootNode root = createTreeGroupedByValues(leaves, oldRoot, map);

    SliceNode oldRootStart = oldRoot.myCachedChildren.get(0);
    SliceUsage rootUsage = oldRootStart.getValue();
    String description = SliceManager.getElementDescription(null, rootUsage.getElement(), " (grouped by value)");
    SliceManager.getInstance(root.getProject()).createToolWindow(true, root, true, description);
  }

  @Nonnull
  public static SliceRootNode createTreeGroupedByValues(Collection<PsiElement> leaves, SliceRootNode oldRoot, final Map<SliceNode, Collection<PsiElement>> map) {
    SliceNode oldRootStart = oldRoot.myCachedChildren.get(0);
    SliceRootNode root = oldRoot.copy();
    root.setChanged();
    root.targetEqualUsages.clear();
    root.myCachedChildren = new ArrayList<SliceNode>(leaves.size());

    for (final PsiElement leafExpression : leaves) {
      SliceNode newNode = filterTree(oldRootStart, new NullableFunction<SliceNode, SliceNode>() {
        @Override
        public SliceNode apply(SliceNode oldNode) {
          if (oldNode.getDuplicate() != null) return null;
          if (!node(oldNode, map).contains(leafExpression)) return null;

          return oldNode.copy();
        }
      }, new PairProcessor<SliceNode, List<SliceNode>>() {
        @Override
        public boolean process(SliceNode node, List<SliceNode> children) {
          if (!children.isEmpty()) return true;
          PsiElement element = node.getValue().getElement();
          if (element == null) return false;
          return element.getManager().areElementsEquivalent(element, leafExpression); // leaf can be there only if it's filtering expression
        }
      });

      SliceLeafValueRootNode lvNode = new SliceLeafValueRootNode(root.getProject(), leafExpression, root, Collections.singletonList(newNode),
                                                                 oldRoot.getValue().params);
      root.myCachedChildren.add(lvNode);
    }
    return root;
  }

  public static void startAnalyzeValues(@Nonnull final AbstractTreeStructure treeStructure, @Nonnull final Runnable finish) {
    final SliceRootNode root = (SliceRootNode)treeStructure.getRootElement();
    final Ref<Collection<PsiElement>> leafExpressions = Ref.create(null);

    final Map<SliceNode, Collection<PsiElement>> map = createMap();

    ProgressManager.getInstance().run(new Task.Backgroundable(root.getProject(), "Expanding all nodes... (may very well take the whole day)", true) {
      @Override
      public void run(@Nonnull final ProgressIndicator indicator) {
        Collection<PsiElement> l = calcLeafExpressions(root, treeStructure, map);
        leafExpressions.set(l);
      }

      @Override
      public void onCancel() {
        finish.run();
      }

      @Override
      public void onSuccess() {
        try {
          Collection<PsiElement> leaves = leafExpressions.get();
          if (leaves == null) return;  //cancelled

          if (leaves.isEmpty()) {
            Messages.showErrorDialog("Unable to find leaf expressions to group by", "Cannot group");
            return;
          }

          groupByValues(leaves, root, map);
        }
        finally {
          finish.run();
        }
      }
    });
  }

  public static Map<SliceNode, Collection<PsiElement>> createMap() {
    return FactoryMap.createMap(sliceNode -> ConcurrentCollectionFactory.createConcurrentSet(SliceLeafAnalyzer.LEAF_ELEMENT_EQUALITY), () -> ConcurrentCollectionFactory.createMap(ContainerUtil.<SliceNode>identityStrategy()));
  }

  static class SliceNodeGuide implements WalkingState.TreeGuide<SliceNode> {
    private final AbstractTreeStructure myTreeStructure;
    // use tree structure because it's setting 'parent' fields in the process

    SliceNodeGuide(@Nonnull AbstractTreeStructure treeStructure) {
      myTreeStructure = treeStructure;
    }

    @Override
    public SliceNode getNextSibling(@Nonnull SliceNode element) {
      AbstractTreeNode parent = (AbstractTreeNode) element.getParent();
      if (parent == null) return null;

      return element.getNext((List)parent.getChildren());
    }

    @Override
    public SliceNode getPrevSibling(@Nonnull SliceNode element) {
      AbstractTreeNode parent = (AbstractTreeNode) element.getParent();
      if (parent == null) return null;
      return element.getPrev((List)parent.getChildren());
    }

    @Override
    public SliceNode getFirstChild(@Nonnull SliceNode element) {
      Object[] children = myTreeStructure.getChildElements(element);
      return children.length == 0 ? null : (SliceNode)children[0];
    }

    @Override
    public SliceNode getParent(@Nonnull SliceNode element) {
      AbstractTreeNode parent = (AbstractTreeNode) element.getParent();
      return parent instanceof SliceNode ? (SliceNode)parent : null;
    }
  }

  private static Collection<PsiElement> node(SliceNode node, Map<SliceNode, Collection<PsiElement>> map) {
    return map.get(node);
  }

  @Nonnull
  public static Collection<PsiElement> calcLeafExpressions(@Nonnull final SliceNode root,
                                                           @Nonnull AbstractTreeStructure treeStructure,
                                                           @Nonnull final Map<SliceNode, Collection<PsiElement>> map) {
    final SliceNodeGuide guide = new SliceNodeGuide(treeStructure);
    WalkingState<SliceNode> walkingState = new WalkingState<SliceNode>(guide) {
      @Override
      public void visit(@Nonnull SliceNode element) {
        element.calculateDupNode();
        node(element, map).clear();
        SliceNode duplicate = element.getDuplicate();
        if (duplicate != null) {
          node(element, map).addAll(node(duplicate, map));
        }
        else {
          final SliceUsage sliceUsage = element.getValue();

          Collection<? extends AbstractTreeNode> children = element.getChildren();
          if (children.isEmpty()) {
            PsiElement value = ApplicationManager.getApplication().runReadAction((Computable<PsiElement>) sliceUsage::getElement);
            node(element, map).addAll(Sets.newHashSet(Set.of(value), LEAF_ELEMENT_EQUALITY));
          }
          super.visit(element);
        }
      }

      @Override
      public void elementFinished(@Nonnull SliceNode element) {
        SliceNode parent = guide.getParent(element);
        if (parent != null) {
          node(parent, map).addAll(node(element, map));
        }
      }
    };
    walkingState.visit(root);

    return node(root, map);
  }
}
