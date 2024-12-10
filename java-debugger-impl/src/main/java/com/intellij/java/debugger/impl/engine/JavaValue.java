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
package com.intellij.java.debugger.impl.engine;

import com.intellij.java.debugger.DebuggerBundle;
import com.intellij.java.debugger.SourcePosition;
import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.engine.evaluation.expression.Modifier;
import com.intellij.java.debugger.impl.DebuggerContextImpl;
import com.intellij.java.debugger.impl.DebuggerUtilsEx;
import com.intellij.java.debugger.impl.actions.JavaReferringObjectsValue;
import com.intellij.java.debugger.impl.actions.JumpToObjectAction;
import com.intellij.java.debugger.impl.engine.evaluation.EvaluationContextImpl;
import com.intellij.java.debugger.impl.engine.evaluation.TextWithImportsImpl;
import com.intellij.java.debugger.impl.engine.events.DebuggerCommandImpl;
import com.intellij.java.debugger.impl.engine.events.SuspendContextCommandImpl;
import com.intellij.java.debugger.impl.ui.impl.DebuggerTreeRenderer;
import com.intellij.java.debugger.impl.ui.impl.watch.*;
import com.intellij.java.debugger.impl.ui.tree.DebuggerTreeNode;
import com.intellij.java.debugger.impl.ui.tree.NodeDescriptorFactory;
import com.intellij.java.debugger.impl.ui.tree.NodeManager;
import com.intellij.java.debugger.impl.ui.tree.ValueDescriptor;
import com.intellij.java.debugger.impl.ui.tree.render.*;
import com.intellij.java.debugger.ui.tree.NodeDescriptor;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.application.ReadAction;
import consulo.execution.debug.XDebuggerUtil;
import consulo.execution.debug.breakpoint.XExpression;
import consulo.execution.debug.evaluation.XDebuggerEvaluator;
import consulo.execution.debug.evaluation.XInstanceEvaluator;
import consulo.execution.debug.frame.*;
import consulo.execution.debug.frame.presentation.*;
import consulo.execution.debug.icon.ExecutionDebugIconGroup;
import consulo.execution.debug.ui.XValueTextProvider;
import consulo.internal.com.sun.jdi.ArrayReference;
import consulo.internal.com.sun.jdi.ArrayType;
import consulo.internal.com.sun.jdi.Value;
import consulo.java.language.module.util.JavaClassNames;
import consulo.language.psi.PsiElement;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.ui.image.Image;
import consulo.util.concurrent.AsyncResult;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ThreeState;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author egor
 */
public class JavaValue extends XNamedValue implements NodeDescriptorProvider, XValueTextProvider, XValueWithInlinePresentation {
    private static final Logger LOG = Logger.getInstance(JavaValue.class);

    private final JavaValue myParent;
    private final ValueDescriptorImpl myValueDescriptor;
    private final EvaluationContextImpl myEvaluationContext;
    private final NodeManagerImpl myNodeManager;
    private final boolean myContextSet;

    protected JavaValue(JavaValue parent,
                        @Nonnull ValueDescriptorImpl valueDescriptor,
                        @Nonnull EvaluationContextImpl evaluationContext,
                        NodeManagerImpl nodeManager,
                        boolean contextSet) {
        super(valueDescriptor.calcValueName());
        myParent = parent;
        myValueDescriptor = valueDescriptor;
        myEvaluationContext = evaluationContext;
        myNodeManager = nodeManager;
        myContextSet = contextSet;
    }

    public static JavaValue create(JavaValue parent,
                                   @Nonnull ValueDescriptorImpl valueDescriptor,
                                   @Nonnull EvaluationContextImpl evaluationContext,
                                   NodeManagerImpl nodeManager,
                                   boolean contextSet) {
        DebuggerManagerThreadImpl.assertIsManagerThread();
        return new JavaValue(parent, valueDescriptor, evaluationContext, nodeManager, contextSet);
    }

    static JavaValue create(@Nonnull ValueDescriptorImpl valueDescriptor,
                            @Nonnull EvaluationContextImpl evaluationContext,
                            NodeManagerImpl nodeManager) {
        return create(null, valueDescriptor, evaluationContext, nodeManager, false);
    }

    public JavaValue getParent() {
        return myParent;
    }

    @Override
    @Nonnull
    public ValueDescriptorImpl getDescriptor() {
        return myValueDescriptor;
    }

    @Nonnull
    public EvaluationContextImpl getEvaluationContext() {
        return myEvaluationContext;
    }

    public NodeManagerImpl getNodeManager() {
        return myNodeManager;
    }

    private boolean isOnDemand() {
        return OnDemandRenderer.ON_DEMAND_CALCULATED.isIn(myValueDescriptor);
    }

    private boolean isCalculated() {
        return OnDemandRenderer.isCalculated(myValueDescriptor);
    }

    @Override
    public void computePresentation(@Nonnull final XValueNode node, @Nonnull XValuePlace place) {
        if (isOnDemand() && !isCalculated()) {
            node.setFullValueEvaluator(OnDemandRenderer.createFullValueEvaluator(DebuggerBundle.message("message.node.evaluate")));
            node.setPresentation(ExecutionDebugIconGroup.nodeWatch(), new XRegularValuePresentation("", null, ""), false);
            return;
        }
        myEvaluationContext.getManagerThread().schedule(new SuspendContextCommandImpl(myEvaluationContext.getSuspendContext()) {
            @Override
            public Priority getPriority() {
                return Priority.NORMAL;
            }

            @Override
            protected void commandCancelled() {
                node.setPresentation(null, new XErrorValuePresentation(DebuggerBundle.message("error.context.has.changed")), false);
            }

            @Override
            public void contextAction(@Nonnull SuspendContextImpl suspendContext) throws Exception {
                if (node.isObsolete()) {
                    return;
                }
                if (!myContextSet) {
                    myValueDescriptor.setContext(myEvaluationContext);
                }
                myValueDescriptor.updateRepresentation(myEvaluationContext, new DescriptorLabelListener() {
                    @Override
                    public void labelChanged() {
                        Image nodeIcon = DebuggerTreeRenderer.getValueIcon(myValueDescriptor, myParent != null ? myParent.getDescriptor() : null);
                        final String value = getValueString();
                        @SuppressWarnings("ThrowableResultOfMethodCallIgnored") EvaluateException exception = myValueDescriptor.getEvaluateException();
                        XValuePresentation presentation = new JavaValuePresentation(value,
                            myValueDescriptor.getIdLabel(),
                            exception != null ? exception.getMessage() : null,
                            myValueDescriptor);

                        Renderer lastRenderer = myValueDescriptor.getLastRenderer();
                        boolean fullEvaluatorSet = setFullValueEvaluator(lastRenderer);
                        if (!fullEvaluatorSet && lastRenderer instanceof CompoundNodeRenderer) {
                            fullEvaluatorSet = setFullValueEvaluator(((CompoundNodeRenderer) lastRenderer).getLabelRenderer());
                        }
                        if (!fullEvaluatorSet && value.length() > XValueNode.MAX_VALUE_LENGTH) {
                            node.setFullValueEvaluator(new JavaFullValueEvaluator(myEvaluationContext) {
                                @Override
                                public void evaluate(@Nonnull final XFullValueEvaluationCallback callback) {
                                    final ValueDescriptorImpl fullValueDescriptor = myValueDescriptor.getFullValueDescriptor();
                                    fullValueDescriptor.updateRepresentation(myEvaluationContext, new DescriptorLabelListener() {
                                        @Override
                                        public void labelChanged() {
                                            callback.evaluated(fullValueDescriptor.getValueText());
                                        }
                                    });
                                }
                            });
                        }
                        node.setPresentation(nodeIcon, presentation, myValueDescriptor.isExpandable());
                    }

                    private boolean setFullValueEvaluator(Renderer renderer) {
                        if (renderer instanceof FullValueEvaluatorProvider) {
                            XFullValueEvaluator evaluator =
                                ((FullValueEvaluatorProvider) renderer).getFullValueEvaluator(myEvaluationContext, myValueDescriptor);
                            if (evaluator != null) {
                                node.setFullValueEvaluator(evaluator);
                                return true;
                            }
                        }
                        return false;
                    }
                });
            }
        });
    }

    public abstract static class JavaFullValueEvaluator extends XFullValueEvaluator {
        protected final EvaluationContextImpl myEvaluationContext;

        public JavaFullValueEvaluator(@Nonnull String linkText, EvaluationContextImpl evaluationContext) {
            super(linkText);
            myEvaluationContext = evaluationContext;
        }

        public JavaFullValueEvaluator(EvaluationContextImpl evaluationContext) {
            myEvaluationContext = evaluationContext;
        }

        public abstract void evaluate(@Nonnull XFullValueEvaluationCallback callback) throws Exception;

        protected EvaluationContextImpl getEvaluationContext() {
            return myEvaluationContext;
        }

        @Override
        public void startEvaluation(@Nonnull final XFullValueEvaluationCallback callback) {
            if (callback.isObsolete()) {
                return;
            }
            myEvaluationContext.getManagerThread().schedule(new SuspendContextCommandImpl(myEvaluationContext.getSuspendContext()) {
                @Override
                public Priority getPriority() {
                    return Priority.NORMAL;
                }

                @Override
                protected void commandCancelled() {
                    callback.errorOccurred(DebuggerBundle.message("error.context.has.changed"));
                }

                @Override
                public void contextAction(@Nonnull SuspendContextImpl suspendContext) throws Exception {
                    if (callback.isObsolete()) {
                        return;
                    }
                    evaluate(callback);
                }
            });
        }
    }

    private static String truncateToMaxLength(@Nonnull String value) {
        return value.substring(0, Math.min(value.length(), XValueNode.MAX_VALUE_LENGTH));
    }

    private static class JavaValuePresentation extends XValueExtendedPresentation implements XValueCompactPresentation {
        private final String myValue;
        private final String myType;
        private final String myError;
        private final ValueDescriptorImpl myValueDescriptor;

        public JavaValuePresentation(@Nonnull String value,
                                     @Nullable String type,
                                     @Nullable String error,
                                     ValueDescriptorImpl valueDescriptor) {
            myValue = value;
            myType = type;
            myError = error;
            myValueDescriptor = valueDescriptor;
        }

        @Nullable
        @Override
        public String getType() {
            return StringUtil.nullize(myType);
        }

        @Override
        public void renderValue(@Nonnull XValuePresentation.XValueTextRenderer renderer) {
            renderValue(renderer, null);
        }

        @Override
        public void renderValue(@Nonnull XValuePresentation.XValueTextRenderer renderer, @Nullable XValueNode node) {
            boolean compact = node != null;
            if (myError != null) {
                if (myValue.endsWith(myError)) {
                    renderer.renderValue(myValue.substring(0, myValue.length() - myError.length()));
                }
                renderer.renderError(myError);
            }
            else {
                if (compact && node.getValueContainer() instanceof JavaValue) {
                    final JavaValue container = (JavaValue) node.getValueContainer();

                    if (container.getDescriptor().isArray()) {
                        final ArrayReference value = (ArrayReference) container.getDescriptor().getValue();
                        final ArrayType type = (ArrayType) container.getDescriptor().getType();
                        if (type != null) {
                            final String typeName = type.componentTypeName();
                            if (TypeConversionUtil.isPrimitive(typeName) || JavaClassNames.JAVA_LANG_STRING.equals(typeName)) {
                                int size = value.length();
                                int max = Math.min(size, JavaClassNames.JAVA_LANG_STRING.equals(typeName) ? 5 : 10);
                                //TODO [eu]: this is a quick fix for IDEA-136606, need to move this away from EDT!!!
                                final List<Value> values = value.getValues(0, max);
                                int i = 0;
                                final List<String> vals = new ArrayList<>(max);
                                while (i < values.size()) {
                                    vals.add(StringUtil.first(values.get(i).toString(), 15, true));
                                    i++;
                                }
                                String more = "";
                                if (vals.size() < size) {
                                    more = ", + " + (size - vals.size()) + " more";
                                }

                                renderer.renderValue("{" + StringUtil.join(vals, ", ") + more + "}");
                                return;
                            }
                        }
                    }
                }

                if (myValueDescriptor.isString()) {
                    renderer.renderStringValue(myValue, "\"", XValueNode.MAX_VALUE_LENGTH);
                    return;
                }

                String value = truncateToMaxLength(myValue);
                Renderer lastRenderer = myValueDescriptor.getLastRenderer();
                if (lastRenderer instanceof CompoundTypeRenderer) {
                    lastRenderer = ((CompoundTypeRenderer) lastRenderer).getLabelRenderer();
                }
                if (lastRenderer instanceof ToStringRenderer) {
                    if (!((ToStringRenderer) lastRenderer).isShowValue(myValueDescriptor, myValueDescriptor.getStoredEvaluationContext())) {
                        return; // to avoid empty line for not calculated toStrings
                    }
                    value = StringUtil.wrapWithDoubleQuote(value);
                }
                renderer.renderValue(value);
            }
        }

        @Nonnull
        @Override
        public String getSeparator() {
            boolean emptyAfterSeparator = !myValueDescriptor.isShowIdLabel() && StringUtil.isEmpty(myValue);
            String declaredType = myValueDescriptor.getDeclaredTypeLabel();
            if (!StringUtil.isEmpty(declaredType)) {
                return emptyAfterSeparator ? declaredType : declaredType + " " + DEFAULT_SEPARATOR;
            }
            return emptyAfterSeparator ? "" : DEFAULT_SEPARATOR;
        }

        @Override
        public boolean isModified() {
            return myValueDescriptor.isDirty();
        }
    }

    @Nonnull
    String getValueString() {
        return myValueDescriptor.getValueText();
    }

    private int myChildrenRemaining = -1;

    @Override
    public void computeChildren(@Nonnull final XCompositeNode node) {
        scheduleCommand(myEvaluationContext, node, new SuspendContextCommandImpl(myEvaluationContext.getSuspendContext()) {
            @Override
            public Priority getPriority() {
                return Priority.NORMAL;
            }

            @Override
            public void contextAction(@Nonnull SuspendContextImpl suspendContext) throws Exception {
                myValueDescriptor.getRenderer(myEvaluationContext.getDebugProcess())
                    .buildChildren(myValueDescriptor.getValue(), new ChildrenBuilder() {
                        @Override
                        public NodeDescriptorFactory getDescriptorManager() {
                            return myNodeManager;
                        }

                        @Override
                        public NodeManager getNodeManager() {
                            return myNodeManager;
                        }

                        @Override
                        public ValueDescriptor getParentDescriptor() {
                            return myValueDescriptor;
                        }

                        @Override
                        public void initChildrenArrayRenderer(ArrayRenderer renderer, int arrayLength) {
                            renderer.myStartIndex = 0;
                            if (myChildrenRemaining >= 0) {
                                renderer.myStartIndex = Math.max(0, arrayLength - myChildrenRemaining);
                            }
                        }

                        @Override
                        public void addChildren(List<DebuggerTreeNode> nodes, boolean last) {
                            XValueChildrenList childrenList = XValueChildrenList.EMPTY;
                            if (!nodes.isEmpty()) {
                                childrenList = new XValueChildrenList(nodes.size());
                                for (DebuggerTreeNode treeNode : nodes) {
                                    NodeDescriptor descriptor = treeNode.getDescriptor();
                                    if (descriptor instanceof ValueDescriptorImpl) {
                                        // Value is calculated already in NodeManagerImpl
                                        childrenList.add(create(JavaValue.this,
                                            (ValueDescriptorImpl) descriptor,
                                            myEvaluationContext,
                                            myNodeManager,
                                            false));
                                    }
                                    else if (descriptor instanceof MessageDescriptor) {
                                        childrenList.add(new JavaStackFrame.DummyMessageValueNode(descriptor.getLabel(),
                                            DebuggerTreeRenderer.getDescriptorIcon(
                                                descriptor)));
                                    }
                                }
                            }
                            node.addChildren(childrenList, last);
                        }

                        @Override
                        public void setChildren(List<DebuggerTreeNode> nodes) {
                            addChildren(nodes, true);
                        }

                        @Override
                        public void setMessage(@Nonnull String message,
                                               @Nullable Image icon,
                                               @Nonnull SimpleTextAttributes attributes,
                                               @Nullable XDebuggerTreeNodeHyperlink link) {
                            node.setMessage(message, icon, attributes, link);
                        }

                        @Override
                        public void addChildren(@Nonnull XValueChildrenList children, boolean last) {
                            node.addChildren(children, last);
                        }

                        @Override
                        public void tooManyChildren(int remaining) {
                            myChildrenRemaining = remaining;
                            node.tooManyChildren(remaining);
                        }

                        @Override
                        public void setAlreadySorted(boolean alreadySorted) {
                            node.setAlreadySorted(alreadySorted);
                        }

                        @Override
                        public void setErrorMessage(@Nonnull String errorMessage) {
                            node.setErrorMessage(errorMessage);
                        }

                        @Override
                        public void setErrorMessage(@Nonnull String errorMessage, @Nullable XDebuggerTreeNodeHyperlink link) {
                            node.setErrorMessage(errorMessage, link);
                        }

                        @Override
                        public boolean isObsolete() {
                            return node.isObsolete();
                        }
                    }, myEvaluationContext);
            }
        });
    }

    protected static boolean scheduleCommand(EvaluationContextImpl evaluationContext,
                                             @Nonnull final XCompositeNode node,
                                             final SuspendContextCommandImpl command) {
        if (node.isObsolete()) {
            return false;
        }
        evaluationContext.getManagerThread().schedule(new SuspendContextCommandImpl(command.getSuspendContext()) {
            @Override
            public void contextAction(@Nonnull SuspendContextImpl suspendContext) throws Exception {
                if (node.isObsolete()) {
                    return;
                }
                command.contextAction(suspendContext);
            }

            @Override
            protected void commandCancelled() {
                node.setErrorMessage(DebuggerBundle.message("error.context.has.changed"));
            }
        });
        return true;
    }

    @Override
    public void computeSourcePosition(@Nonnull final XNavigatable navigatable) {
        computeSourcePosition(navigatable, false);
    }

    private void computeSourcePosition(@Nonnull final XNavigatable navigatable, final boolean inline) {
        myEvaluationContext.getManagerThread().schedule(new SuspendContextCommandImpl(myEvaluationContext.getSuspendContext()) {
            @Override
            public Priority getPriority() {
                return inline ? Priority.LOWEST : Priority.NORMAL;
            }

            @Override
            protected void commandCancelled() {
                navigatable.setSourcePosition(null);
            }

            @Override
            public void contextAction(@Nonnull SuspendContextImpl suspendContext) throws Exception {
                ApplicationManager.getApplication().runReadAction(() ->
                {
                    SourcePosition position = SourcePositionProvider.getSourcePosition(
                        myValueDescriptor,
                        getProject(),
                        getDebuggerContext(),
                        false);
                    if (position != null) {
                        navigatable.setSourcePosition(DebuggerUtilsEx.toXSourcePosition(position));
                    }
                    if (inline) {
                        position = SourcePositionProvider.getSourcePosition(myValueDescriptor,
                            getProject(),
                            getDebuggerContext(),
                            true);
                        if (position != null) {
                            navigatable.setSourcePosition(DebuggerUtilsEx.toXSourcePosition(position));
                        }
                    }
                });
            }
        });
    }

    @Nonnull
    @Override
    public ThreeState computeInlineDebuggerData(@Nonnull final XInlineDebuggerDataCallback callback) {
        computeSourcePosition(callback::computed, true);
        return ThreeState.YES;
    }

    private DebuggerContextImpl getDebuggerContext() {
        return myEvaluationContext.getDebugProcess().getDebuggerContext();
    }

    public Project getProject() {
        return myValueDescriptor.getProject();
    }

    @Override
    public boolean canNavigateToTypeSource() {
        return true;
    }

    @Override
    public void computeTypeSourcePosition(@Nonnull final XNavigatable navigatable) {
        if (myEvaluationContext.getSuspendContext().isResumed()) {
            return;
        }
        DebugProcessImpl debugProcess = myEvaluationContext.getDebugProcess();
        debugProcess.getManagerThread()
            .schedule(new JumpToObjectAction.NavigateCommand(getDebuggerContext(), myValueDescriptor, debugProcess, null) {
                @Override
                public Priority getPriority() {
                    return Priority.HIGH;
                }

                @Override
                protected void doAction(@Nullable final SourcePosition sourcePosition) {
                    if (sourcePosition != null) {
                        ApplicationManager.getApplication()
                            .runReadAction(() -> navigatable.setSourcePosition(DebuggerUtilsEx.toXSourcePosition(sourcePosition)));
                    }
                }
            });
    }

    @Nullable
    @Override
    public XValueModifier getModifier() {
        return myValueDescriptor.canSetValue() ? myValueDescriptor.getModifier(this) : null;
    }

    private volatile XExpression evaluationExpression = null;

    @Nonnull
    @Override
    public AsyncResult<XExpression> calculateEvaluationExpression() {
        if (evaluationExpression != null) {
            return AsyncResult.done(evaluationExpression);
        }
        else {
            final AsyncResult<XExpression> res = new AsyncResult<>();
            myEvaluationContext.getManagerThread().schedule(new SuspendContextCommandImpl(myEvaluationContext.getSuspendContext()) {
                @Override
                public Priority getPriority() {
                    return Priority.HIGH;
                }

                @Override
                public void contextAction(@Nonnull SuspendContextImpl suspendContext) throws Exception {
                    evaluationExpression = ReadAction.compute(() ->
                    {
                        try {
                            PsiElement psiExpression =
                                getDescriptor().getTreeEvaluation(JavaValue.this, getDebuggerContext());
                            if (psiExpression != null) {
                                XExpression res =
                                    TextWithImportsImpl.toXExpression(new TextWithImportsImpl(psiExpression));
                                // add runtime imports if any
                                Set<String> imports =
                                    psiExpression.getUserData(DebuggerTreeNodeExpression.ADDITIONAL_IMPORTS_KEY);
                                if (imports != null && res != null) {
                                    if (res.getCustomInfo() != null) {
                                        imports.add(res.getCustomInfo());
                                    }
                                    res = XDebuggerUtil.getInstance()
                                        .createExpression(res.getExpression(),
                                            res.getLanguage(),
                                            StringUtil.join(imports, ","),
                                            res.getMode());
                                }
                                return res;
                            }
                        }
                        catch (EvaluateException e) {
                            LOG.info(e);
                        }
                        return null;
                    });
                    res.setDone(evaluationExpression);
                }
            });
            return res;
        }
    }

    @Override
    public String getValueText() {
        return myValueDescriptor.getValueText();
    }

    @Nullable
    @Override
    public XReferrersProvider getReferrersProvider() {
        return new XReferrersProvider() {
            @Override
            public XValue getReferringObjectsValue() {
                return new JavaReferringObjectsValue(JavaValue.this, false);
            }
        };
    }

    @Nullable
    @Override
    public XInstanceEvaluator getInstanceEvaluator() {
        return new XInstanceEvaluator() {
            @Override
            public void evaluate(@Nonnull final XDebuggerEvaluator.XEvaluationCallback callback, @Nonnull final XStackFrame frame) {
                myEvaluationContext.getManagerThread().schedule(new DebuggerCommandImpl() {
                    @Override
                    protected void commandCancelled() {
                        callback.errorOccurred(DebuggerBundle.message("error.context.has.changed"));
                    }

                    @Override
                    protected void action() throws Exception {
                        ValueDescriptorImpl inspectDescriptor = myValueDescriptor;
                        if (myValueDescriptor instanceof WatchItemDescriptor) {
                            Modifier modifier = ((WatchItemDescriptor) myValueDescriptor).getModifier();
                            if (modifier != null) {
                                NodeDescriptor item = modifier.getInspectItem(getProject());
                                if (item != null) {
                                    inspectDescriptor = (ValueDescriptorImpl) item;
                                }
                            }
                        }
                        EvaluationContextImpl evaluationContext = ((JavaStackFrame) frame).getFrameDebuggerContext(null).createEvaluationContext();
                        if (evaluationContext != null) {
                            callback.evaluated(create(inspectDescriptor, evaluationContext, myNodeManager));
                        }
                        else {
                            callback.errorOccurred("Context is not available");
                        }
                    }
                });
            }
        };
    }

    public void setRenderer(NodeRenderer nodeRenderer, final XValueNode node) {
        DebuggerManagerThreadImpl.assertIsManagerThread();
        myValueDescriptor.setRenderer(nodeRenderer);
        reBuild(node);
    }

    public void reBuild(final XValueNode node) {
        DebuggerManagerThreadImpl.assertIsManagerThread();
        myChildrenRemaining = -1;
        Application.get().invokeLater(() -> {
            node.clearChildren();
            computePresentation(node, XValuePlace.TREE);
        });
    }

    @Nullable
    @Override
    public String computeInlinePresentation() {
        ValueDescriptorImpl descriptor = getDescriptor();
        return descriptor.isNull() || descriptor.isPrimitive() ? descriptor.getValueText() : null;
    }
}
