/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.java.analysis.impl.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.java.language.codeInsight.Nullability;
import com.intellij.java.language.psi.PsiExpression;
import consulo.application.ReadAction;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.progress.Task;
import consulo.language.editor.PsiEquivalenceUtil;
import consulo.language.psi.PsiElement;
import consulo.project.ui.view.tree.AbstractTreeNode;
import consulo.ui.ex.tree.AbstractTreeStructure;
import consulo.util.collection.FactoryMap;
import consulo.util.collection.Sets;
import consulo.util.collection.util.WalkingState;
import consulo.util.lang.ref.Ref;
import jakarta.annotation.Nonnull;

import java.util.*;

/**
 * User: cdr
 */
public class SliceNullnessAnalyzer {
    private static void groupByNullness(NullAnalysisResult result, SliceRootNode oldRoot, final Map<SliceNode, NullAnalysisResult> map) {
        SliceRootNode root = createNewTree(result, oldRoot, map);

        SliceUsage rootUsage = oldRoot.myCachedChildren.get(0).getValue();
        SliceManager.getInstance(root.getProject()).createToolWindow(true, root, true, SliceManager.getElementDescription(null, rootUsage.getElement(), " Grouped by Nullness"));
    }

    public static SliceRootNode createNewTree(NullAnalysisResult result, SliceRootNode oldRoot, final Map<SliceNode, NullAnalysisResult> map) {
        SliceRootNode root = oldRoot.copy();
        assert oldRoot.myCachedChildren.size() == 1;
        SliceNode oldRootStart = oldRoot.myCachedChildren.get(0);
        root.setChanged();
        root.targetEqualUsages.clear();
        root.myCachedChildren = new ArrayList<SliceNode>();

        createValueRootNode(result, oldRoot, map, root, oldRootStart, "Null Values", NullAnalysisResult.NULLS);
        createValueRootNode(result, oldRoot, map, root, oldRootStart, "NotNull Values", NullAnalysisResult.NOT_NULLS);
        createValueRootNode(result, oldRoot, map, root, oldRootStart, "Other Values", NullAnalysisResult.UNKNOWNS);

        return root;
    }

    private static void createValueRootNode(NullAnalysisResult result, SliceRootNode oldRoot,
                                            final Map<SliceNode, NullAnalysisResult> map,
                                            SliceRootNode root,
                                            SliceNode oldRootStart, String nodeName, final int group) {
        Collection<PsiElement> groupedByValue = result.groupedByValue[group];
        if (groupedByValue.isEmpty()) {
            return;
        }
        SliceLeafValueClassNode valueRoot = new SliceLeafValueClassNode(root.getProject(), root, nodeName);
        root.myCachedChildren.add(valueRoot);

        Set<PsiElement> uniqueValues = Sets.newHashSet(groupedByValue, SliceLeafAnalyzer.LEAF_ELEMENT_EQUALITY);
        for (final PsiElement expression : uniqueValues) {
            SliceNode newRoot = SliceLeafAnalyzer.filterTree(oldRootStart, oldNode -> {
                if (oldNode.getDuplicate() != null) {
                    return null;
                }

                for (PsiElement nullSuspect : group(oldNode, map, group)) {
                    if (PsiEquivalenceUtil.areElementsEquivalent(nullSuspect, expression)) {
                        return oldNode.copy();
                    }
                }
                return null;
            }, (node, children) -> {
                if (!children.isEmpty()) {
                    return true;
                }
                PsiElement element = node.getValue().getElement();
                if (element == null) {
                    return false;
                }
                return PsiEquivalenceUtil.areElementsEquivalent(element, expression); // leaf can be there only if it's filtering expression
            });
            valueRoot.myCachedChildren.add(new SliceLeafValueRootNode(root.getProject(), expression, valueRoot, Collections.singletonList(newRoot),
                oldRoot.getValue().params));
        }
    }

    public static void startAnalyzeNullness(final AbstractTreeStructure treeStructure, final Runnable finish) {
        final SliceRootNode root = (SliceRootNode) treeStructure.getRootElement();
        final Ref<NullAnalysisResult> leafExpressions = Ref.create(null);
        final Map<SliceNode, NullAnalysisResult> map = createMap();

        ProgressManager.getInstance().run(new Task.Backgroundable(root.getProject(), "Expanding all nodes... (may very well take the whole day)", true) {
            @Override
            public void run(@Nonnull final ProgressIndicator indicator) {
                NullAnalysisResult l = calcNullableLeaves(root, treeStructure, map);
                leafExpressions.set(l);
            }

            @Override
            public void onCancel() {
                finish.run();
            }

            @Override
            public void onSuccess() {
                try {
                    NullAnalysisResult leaves = leafExpressions.get();
                    if (leaves == null) {
                        return;  //cancelled
                    }

                    groupByNullness(leaves, root, map);
                }
                finally {
                    finish.run();
                }
            }
        });
    }

    public static Map<SliceNode, NullAnalysisResult> createMap() {
        return FactoryMap.createMap(sliceNode -> new NullAnalysisResult(), HashMap::new);
    }

    private static NullAnalysisResult node(SliceNode node, Map<SliceNode, NullAnalysisResult> nulls) {
        return nulls.get(node);
    }

    private static Collection<PsiElement> group(SliceNode node, Map<SliceNode, NullAnalysisResult> nulls, int group) {
        return nulls.get(node).groupedByValue[group];
    }

    @Nonnull
    public static NullAnalysisResult calcNullableLeaves(@Nonnull final SliceNode root, @Nonnull AbstractTreeStructure treeStructure,
                                                        final Map<SliceNode, NullAnalysisResult> map) {
        final SliceLeafAnalyzer.SliceNodeGuide guide = new SliceLeafAnalyzer.SliceNodeGuide(treeStructure);
        WalkingState<SliceNode> walkingState = new WalkingState<>(guide) {
            @Override
            public void visit(final @Nonnull SliceNode element) {
                element.calculateDupNode();
                node(element, map).clear();
                SliceNode duplicate = element.getDuplicate();
                if (duplicate != null) {
                    node(element, map).add(node(duplicate, map));
                }
                else {
                    final PsiElement value = ReadAction.compute(() -> element.getValue().getElement());
                    Nullability nullability = ReadAction.compute(() -> checkNullability(value));
                    if (nullability == Nullability.NULLABLE) {
                        group(element, map, NullAnalysisResult.NULLS).add(value);
                    }
                    else if (nullability == Nullability.NOT_NULL) {
                        group(element, map, NullAnalysisResult.NOT_NULLS).add(value);
                    }
                    else {
                        Collection<? extends AbstractTreeNode<?>> children = ReadAction.compute(element::getChildren);
                        if (children.isEmpty()) {
                            group(element, map, NullAnalysisResult.UNKNOWNS).add(value);
                        }
                        super.visit(element);
                    }
                }
            }

            @Override
            public void elementFinished(@Nonnull SliceNode element) {
                SliceNode parent = guide.getParent(element);
                if (parent != null) {
                    node(parent, map).add(node(element, map));
                }
            }
        };
        walkingState.visit(root);

        return node(root, map);
    }

    @Nonnull
    private static Nullability checkNullability(PsiElement element) {
        if (element instanceof PsiExpression) {
            return NullabilityUtil.getExpressionNullability((PsiExpression) element, true);
        }
        return Nullability.UNKNOWN;
    }

    public static class NullAnalysisResult {
        public static int NULLS = 0;
        public static int NOT_NULLS = 1;
        public static int UNKNOWNS = 2;
        public final Collection<PsiElement>[] groupedByValue = new Collection[]{
            new HashSet<PsiElement>(),
            new HashSet<PsiElement>(),
            new HashSet<PsiElement>()
        };

        public void clear() {
            for (Collection<PsiElement> elements : groupedByValue) {
                elements.clear();
            }
        }

        public void add(NullAnalysisResult duplicate) {
            for (int i = 0; i < groupedByValue.length; i++) {
                Collection<PsiElement> elements = groupedByValue[i];
                Collection<PsiElement> other = duplicate.groupedByValue[i];
                elements.addAll(other);
            }
        }
    }
}
