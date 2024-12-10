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
package com.intellij.java.debugger.impl.actions;

import com.intellij.java.debugger.impl.DebuggerContextImpl;
import com.intellij.java.debugger.impl.engine.DebugProcessImpl;
import com.intellij.java.debugger.impl.engine.JavaValue;
import com.intellij.java.debugger.impl.engine.SuspendContextImpl;
import com.intellij.java.debugger.impl.engine.events.DebuggerContextCommandImpl;
import com.intellij.java.debugger.impl.settings.NodeRendererSettings;
import com.intellij.java.debugger.impl.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.java.debugger.impl.ui.tree.render.NodeRenderer;
import consulo.application.dumb.DumbAware;
import consulo.execution.debug.frame.XValue;
import consulo.execution.debug.frame.XValueNode;
import consulo.execution.debug.ui.XValueTree;
import consulo.logging.Logger;
import consulo.ui.ex.action.*;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ViewAsGroup extends ActionGroup implements DumbAware {
    private static final Logger LOG = Logger.getInstance(ViewAsGroup.class);

    private volatile AnAction[] myChildren = AnAction.EMPTY_ARRAY;

    public ViewAsGroup() {
        setPopup(true);
    }

    private static class RendererAction extends ToggleAction {
        private final NodeRenderer myNodeRenderer;

        public RendererAction(NodeRenderer nodeRenderer) {
            super(nodeRenderer.getName());
            myNodeRenderer = nodeRenderer;
        }

        public boolean isSelected(AnActionEvent e) {
            List<JavaValue> values = getSelectedValues(e);
            if (values.isEmpty()) {
                return false;
            }
            for (JavaValue value : values) {
                if (value.getDescriptor().getLastRenderer() != myNodeRenderer) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public void setSelected(AnActionEvent e, final boolean state) {
            if (!state) {
                return;
            }

            final DebuggerContextImpl debuggerContext = DebuggerAction.getDebuggerContext(e.getDataContext());
            final List<JavaValue> values = getSelectedValues(e);
            XValueTree tree = e.getData(XValueTree.KEY);
            final List<XValueNode> selectedNodes = tree == null ? List.of() : tree.getSelectedNodes();

            LOG.assertTrue(!values.isEmpty());

            DebugProcessImpl process = debuggerContext.getDebugProcess();
            if (process == null) {
                return;
            }

            process.getManagerThread().schedule(new DebuggerContextCommandImpl(debuggerContext) {
                @Override
                public void threadAction(SuspendContextImpl context) {
                    for (XValueNode node : selectedNodes) {
                        final XValue container = node.getValueContainer();
                        if (container instanceof JavaValue) {
                            ((JavaValue) container).setRenderer(myNodeRenderer, node);
                        }
                    }
                }
            });
        }
    }

    @Nonnull
    public AnAction[] getChildren(@Nullable final AnActionEvent e) {
        return myChildren;
    }

    private static AnAction[] calcChildren(List<JavaValue> values) {
        List<AnAction> renderers = new ArrayList<>();

        List<NodeRenderer> allRenderers = NodeRendererSettings.getInstance().getAllRenderers();

        boolean anyValueDescriptor = false;

        for (NodeRenderer nodeRenderer : allRenderers) {
            boolean allApp = true;

            for (JavaValue value : values) {
                if (value instanceof JavaReferringObjectsValue) { // disable for any referrers at all
                    return AnAction.EMPTY_ARRAY;
                }
                ValueDescriptorImpl valueDescriptor = value.getDescriptor();
                anyValueDescriptor = true;
                if (!valueDescriptor.isValueValid() || !nodeRenderer.isApplicable(valueDescriptor.getType())) {
                    allApp = false;
                    break;
                }
            }

            if (!anyValueDescriptor) {
                return AnAction.EMPTY_ARRAY;
            }

            if (allApp) {
                renderers.add(new RendererAction(nodeRenderer));
            }
        }

        List<AnAction> children = new ArrayList<>();
        AnAction[] viewAsActions = ((DefaultActionGroup) ActionManager.getInstance().getAction(DebuggerActions.REPRESENTATION_LIST)).getChildren(null);
        for (AnAction viewAsAction : viewAsActions) {
            if (viewAsAction instanceof AutoRendererAction) {
                if (renderers.size() > 1) {
                    viewAsAction.getTemplatePresentation().setVisible(true);
                    children.add(viewAsAction);
                }
            }
            else {
                children.add(viewAsAction);
            }
        }

        if (!children.isEmpty()) {
            children.add(AnSeparator.getInstance());
        }
        children.addAll(renderers);

        return children.toArray(new AnAction[children.size()]);
    }

    public void update(final AnActionEvent event) {
        if (!DebuggerAction.isFirstStart(event)) {
            return;
        }

        myChildren = AnAction.EMPTY_ARRAY;
        final DebuggerContextImpl debuggerContext = DebuggerAction.getDebuggerContext(event.getDataContext());
        final List<JavaValue> values = getSelectedValues(event);
        if (values.isEmpty()) {
            event.getPresentation().setEnabledAndVisible(false);
            return;
        }

        final DebugProcessImpl process = debuggerContext.getDebugProcess();
        if (process == null) {
            event.getPresentation().setEnabled(false);
            return;
        }

        process.getManagerThread().schedule(new DebuggerContextCommandImpl(debuggerContext) {
            public void threadAction() {
                myChildren = calcChildren(values);
                DebuggerAction.enableAction(event, myChildren.length > 0);
            }
        });
    }

    @Nonnull
    public static List<JavaValue> getSelectedValues(AnActionEvent event) {
        XValueTree tree = event.getData(XValueTree.KEY);
        List<XValueNode> selectedNodes = tree == null ? List.of() : tree.getSelectedNodes();

        List<JavaValue> values = new ArrayList<>();
        for (XValueNode node : selectedNodes) {
            XValue valueContainer = node.getValueContainer();
            if (valueContainer instanceof JavaValue) {
                values.add((JavaValue) valueContainer);
            }
        }
        return values;
    }
}
