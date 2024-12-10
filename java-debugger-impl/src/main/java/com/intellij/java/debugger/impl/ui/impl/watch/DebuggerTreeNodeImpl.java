/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

/*
 * Class DebuggerTreeNodeImpl
 * @author Jeka
 */
package com.intellij.java.debugger.impl.ui.impl.watch;

import com.intellij.java.debugger.impl.DebuggerContextImpl;
import com.intellij.java.debugger.impl.engine.evaluation.EvaluationContextImpl;
import com.intellij.java.debugger.impl.engine.events.DebuggerContextCommandImpl;
import com.intellij.java.debugger.impl.ui.impl.DebuggerTreeRenderer;
import com.intellij.java.debugger.impl.ui.impl.tree.TreeBuilder;
import com.intellij.java.debugger.impl.ui.impl.tree.TreeBuilderNode;
import com.intellij.java.debugger.impl.ui.tree.DebuggerTreeNode;
import com.intellij.java.debugger.impl.ui.tree.ValueDescriptor;
import com.intellij.java.debugger.impl.ui.tree.render.DescriptorLabelListener;
import com.intellij.java.debugger.impl.ui.tree.render.NodeRenderer;
import com.intellij.java.debugger.ui.tree.NodeDescriptor;
import consulo.application.ApplicationManager;
import consulo.execution.debug.ui.ValueMarkup;
import consulo.project.Project;
import consulo.ui.ex.SimpleColoredText;
import consulo.ui.ex.awt.EditorColorsUtil;
import consulo.ui.image.Image;
import consulo.util.dataholder.Key;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import javax.swing.tree.MutableTreeNode;
import java.util.HashMap;
import java.util.Map;

public class DebuggerTreeNodeImpl extends TreeBuilderNode implements DebuggerTreeNode, NodeDescriptorProvider, MutableTreeNode {
    private Image myIcon;
    private SimpleColoredText myText;
    private String myMarkupTooltipText;
    private final DebuggerTree myTree;
    private final Map myProperties = new HashMap();

    public DebuggerTreeNodeImpl(DebuggerTree tree, NodeDescriptor descriptor) {
        super(descriptor);
        myTree = tree;
    }

    @Override
    public DebuggerTreeNodeImpl getParent() {
        return (DebuggerTreeNodeImpl) super.getParent();
    }

    @Override
    protected TreeBuilder getTreeBuilder() {
        return myTree.getMutableModel();
    }

    public DebuggerTree getTree() {
        return myTree;
    }

    public String toString() {
        return myText != null ? myText.toString() : "";
    }

    @Override
    public NodeDescriptorImpl getDescriptor() {
        return (NodeDescriptorImpl) getUserObject();
    }

    @Override
    public Project getProject() {
        return getTree().getProject();
    }

    @Override
    public void setRenderer(NodeRenderer renderer) {
        ((ValueDescriptorImpl) getDescriptor()).setRenderer(renderer);
        calcRepresentation();
    }

    private void updateCaches() {
        final NodeDescriptorImpl descriptor = getDescriptor();
        myIcon = DebuggerTreeRenderer.getDescriptorIcon(descriptor);
        final DebuggerContextImpl context = getTree().getDebuggerContext();
        myText = DebuggerTreeRenderer.getDescriptorText(context, descriptor, EditorColorsUtil.getColorSchemeForComponent(myTree), false);
        if (descriptor instanceof ValueDescriptor) {
            final ValueMarkup markup = ((ValueDescriptor) descriptor).getMarkup(context.getDebugProcess());
            myMarkupTooltipText = markup != null ? markup.getToolTipText() : null;
        }
        else {
            myMarkupTooltipText = null;
        }
    }

    public Image getIcon() {
        return myIcon;
    }

    public SimpleColoredText getText() {
        return myText;
    }

    @Nullable
    public String getMarkupTooltipText() {
        return myMarkupTooltipText;
    }

    @Override
    public void clear() {
        removeAllChildren();
        myIcon = null;
        myText = null;
        super.clear();
    }

    private void update(final DebuggerContextImpl context, final Runnable runnable, boolean labelOnly) {
        if (!labelOnly) {
            clear();
        }

        if (context != null && context.getDebugProcess() != null) {
            getTree().saveState(this);

            myIcon = DebuggerTreeRenderer.getDescriptorIcon(MessageDescriptor.EVALUATING);
            myText = DebuggerTreeRenderer.getDescriptorText(context, MessageDescriptor.EVALUATING, false);

            context.getDebugProcess().getManagerThread().invoke(new DebuggerContextCommandImpl(context) {
                @Override
                public void threadAction() {
                    runnable.run();
                }

                @Override
                protected void commandCancelled() {
                    clear();
                    getDescriptor().clear();
                    updateCaches();

                    labelChanged();
                    childrenChanged(true);
                }

                @Override
                public Priority getPriority() {
                    return Priority.NORMAL;
                }

            });
        }

        labelChanged();
        if (!labelOnly) {
            childrenChanged(true);
        }
    }

    public void calcLabel() {
        final DebuggerContextImpl context = getTree().getDebuggerContext();
        update(context, () -> getDescriptor().updateRepresentation(context.createEvaluationContext(), new DescriptorLabelListener() {
            @Override
            public void labelChanged() {
                updateCaches();
                DebuggerTreeNodeImpl.this.labelChanged();
            }
        }), true);
    }

    public void calcRepresentation() {
        final DebuggerContextImpl context = getTree().getDebuggerContext();
        update(context, () -> getDescriptor().updateRepresentation(context.createEvaluationContext(), new DescriptorLabelListener() {
            @Override
            public void labelChanged() {
                updateCaches();
                DebuggerTreeNodeImpl.this.labelChanged();
            }
        }), false);
    }

    public void calcValue() {
        final DebuggerContextImpl context = getTree().getDebuggerContext();
        update(context, () ->
        {
            EvaluationContextImpl evaluationContext = context.createEvaluationContext();
            getDescriptor().setContext(evaluationContext);
            getDescriptor().updateRepresentation(evaluationContext, new DescriptorLabelListener() {
                @Override
                public void labelChanged() {
                    updateCaches();
                    DebuggerTreeNodeImpl.this.labelChanged();
                }
            });
            childrenChanged(true);
        }, false);
    }

    private static void invoke(Runnable r) {
        if (ApplicationManager.getApplication().isDispatchThread()) {
            r.run();
        }
        else {
            SwingUtilities.invokeLater(r);
        }
    }

    public void labelChanged() {
        invoke(() ->
        {
            updateCaches();
            getTree().getMutableModel().nodeChanged(this);
        });
    }

    public void childrenChanged(final boolean scrollToVisible) {
        invoke(() ->
        {
            getTree().getMutableModel().nodeStructureChanged(this);
            getTree().restoreState(this);
        });
    }

    public DebuggerTreeNodeImpl add(MessageDescriptor message) {
        DebuggerTreeNodeImpl node = getNodeFactory().createMessageNode(message);
        add(node);
        return node;
    }

    public NodeManagerImpl getNodeFactory() {
        return myTree.getNodeFactory();
    }

    public Object getProperty(Key key) {
        return myProperties.get(key);
    }

    public void putProperty(Key key, Object data) {
        myProperties.put(key, data);
    }

    @Nonnull
    public static DebuggerTreeNodeImpl createNodeNoUpdate(DebuggerTree tree, NodeDescriptor descriptor) {
        DebuggerTreeNodeImpl node = new DebuggerTreeNodeImpl(tree, descriptor);
        node.updateCaches();
        return node;
    }

    @Nonnull
    protected static DebuggerTreeNodeImpl createNode(DebuggerTree tree, NodeDescriptorImpl descriptor, EvaluationContextImpl evaluationContext) {
        final DebuggerTreeNodeImpl node = new DebuggerTreeNodeImpl(tree, descriptor);
        descriptor.updateRepresentationNoNotify(evaluationContext, new DescriptorLabelListener() {
            @Override
            public void labelChanged() {
                node.updateCaches();
                node.labelChanged();
            }
        });
        node.updateCaches();
        return node;
    }
}
