/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.java.debugger.impl.ui.impl.watch;

import com.intellij.java.debugger.DebuggerContext;
import com.intellij.java.debugger.engine.DebugProcess;
import com.intellij.java.debugger.engine.DebuggerUtils;
import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.impl.DebuggerContextImpl;
import com.intellij.java.debugger.impl.DebuggerUtilsAsync;
import com.intellij.java.debugger.impl.DebuggerUtilsEx;
import com.intellij.java.debugger.impl.engine.DebugProcessImpl;
import com.intellij.java.debugger.impl.engine.DebuggerManagerThreadImpl;
import com.intellij.java.debugger.impl.engine.JavaValue;
import com.intellij.java.debugger.impl.engine.evaluation.EvaluationContextImpl;
import com.intellij.java.debugger.impl.memory.utils.NamesUtils;
import com.intellij.java.debugger.impl.settings.NodeRendererSettings;
import com.intellij.java.debugger.impl.ui.tree.DebuggerTreeNode;
import com.intellij.java.debugger.impl.ui.tree.ValueDescriptor;
import com.intellij.java.debugger.impl.ui.tree.render.*;
import com.intellij.java.debugger.localize.JavaDebuggerLocalize;
import com.intellij.java.debugger.ui.tree.NodeDescriptor;
import com.intellij.java.debugger.ui.tree.NodeDescriptorNameAdjuster;
import com.intellij.java.language.psi.PsiExpression;
import consulo.application.ApplicationManager;
import consulo.application.ReadAction;
import consulo.execution.debug.frame.XValueModifier;
import consulo.execution.debug.frame.XValueNode;
import consulo.execution.debug.frame.presentation.XRegularValuePresentation;
import consulo.execution.debug.icon.ExecutionDebugIconGroup;
import consulo.execution.debug.ui.ValueMarkup;
import consulo.internal.com.sun.jdi.*;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.image.Image;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;

public abstract class ValueDescriptorImpl extends NodeDescriptorImpl implements ValueDescriptor {
    protected final Project myProject;

    NodeRenderer myRenderer = null;

    NodeRenderer myAutoRenderer = null;

    private Value myValue;
    private boolean myValueReady;

    private EvaluateException myValueException;
    protected EvaluationContextImpl myStoredEvaluationContext = null;

    private String myIdLabel;
    private String myValueText;
    private boolean myFullValue = false;

    @Nullable
    private Image myValueIcon;

    protected boolean myIsNew = true;
    private boolean myIsDirty = false;
    private boolean myIsLvalue = false;
    private boolean myIsExpandable;

    private boolean myShowIdLabel = true;

    private OnDemandPresentationProvider myOnDemandPresentationProvider = node -> {
        node.setFullValueEvaluator(OnDemandRenderer.createFullValueEvaluator(JavaDebuggerLocalize.messageNodeEvaluate()));
        node.setPresentation(ExecutionDebugIconGroup.nodeWatch(), new XRegularValuePresentation("", null, ""), false);
    };

    protected ValueDescriptorImpl(Project project, Value value) {
        myProject = project;
        myValue = value;
        myValueReady = true;
    }

    protected ValueDescriptorImpl(Project project) {
        myProject = project;
    }

    public void applyOnDemandPresentation(XValueNode node) {
        myOnDemandPresentationProvider.setPresentation(node);
    }

    public void setOnDemandPresentationProvider(OnDemandPresentationProvider onDemandPresentationProvider) {
        myOnDemandPresentationProvider = onDemandPresentationProvider;
    }

    private void assertValueReady() {
        if (!myValueReady) {
            LOG.error("Value is not yet calculated for " + getClass());
        }
    }

    @Override
    public boolean isArray() {
        assertValueReady();
        return myValue instanceof ArrayReference;
    }


    public boolean isDirty() {
        assertValueReady();
        return myIsDirty;
    }

    @Override
    public boolean isLvalue() {
        assertValueReady();
        return myIsLvalue;
    }

    @Override
    public boolean isNull() {
        assertValueReady();
        return myValue == null;
    }

    @Override
    public boolean isString() {
        assertValueReady();
        return myValue instanceof StringReference;
    }

    @Override
    public boolean isPrimitive() {
        assertValueReady();
        return myValue instanceof PrimitiveValue;
    }

    public boolean isEnumConstant() {
        assertValueReady();
        return myValue instanceof ObjectReference && isEnumConstant(((ObjectReference) myValue));
    }

    public boolean isValueValid() {
        return myValueException == null;
    }

    public boolean isShowIdLabel() {
        return myShowIdLabel;
    }

    public void setShowIdLabel(boolean showIdLabel) {
        myShowIdLabel = showIdLabel;
    }

    @Override
    public Value getValue() {
        assertValueReady();
        return myValue;
    }

    @Override
    public boolean isExpandable() {
        return myIsExpandable;
    }

    public abstract Value calcValue(EvaluationContextImpl evaluationContext) throws EvaluateException;

    @Override
    public final void setContext(EvaluationContextImpl evaluationContext) {
        DebuggerManagerThreadImpl.assertIsManagerThread();
        myStoredEvaluationContext = evaluationContext;
        Value value;
        try {
            value = calcValue(evaluationContext);

            if (!myIsNew) {
                try {
                    if (myValue instanceof DoubleValue && Double.isNaN(((DoubleValue) myValue).doubleValue())) {
                        myIsDirty = !(value instanceof DoubleValue);
                    }
                    else if (myValue instanceof FloatValue && Float.isNaN(((FloatValue) myValue).floatValue())) {
                        myIsDirty = !(value instanceof FloatValue);
                    }
                    else {
                        myIsDirty = (value == null) ? myValue != null : !value.equals(myValue);
                    }
                }
                catch (ObjectCollectedException ignored) {
                    myIsDirty = true;
                }
            }
            myValue = value;
            myValueException = null;
        }
        catch (EvaluateException e) {
            myValueException = e;
            setFailed(e);
            myValue = getTargetExceptionWithStackTraceFilled(evaluationContext, e);
            myIsExpandable = false;
        }
        finally {
            myValueReady = true;
        }

        myIsNew = false;
    }

    @Nullable
    private static ObjectReference getTargetExceptionWithStackTraceFilled(final EvaluationContextImpl evaluationContext, EvaluateException ex) {
        final ObjectReference exceptionObj = ex.getExceptionFromTargetVM();
        if (exceptionObj != null && evaluationContext != null) {
            try {
                final ReferenceType refType = exceptionObj.referenceType();
                final List<Method> methods = refType.methodsByName("getStackTrace", "()[Ljava/lang/StackTraceElement;");
                if (methods.size() > 0) {
                    final DebugProcessImpl process = evaluationContext.getDebugProcess();
                    process.invokeMethod(evaluationContext, exceptionObj, methods.get(0), Collections.emptyList());

                    // print to console as well

                    final Field traceField = refType.fieldByName("stackTrace");
                    final Value trace = traceField != null ? exceptionObj.getValue(traceField) : null;
                    if (trace instanceof ArrayReference) {
                        final ArrayReference traceArray = (ArrayReference) trace;
                        final Type componentType = ((ArrayType) traceArray.referenceType()).componentType();
                        if (componentType instanceof ClassType) {
                            process.printToConsole(DebuggerUtils.getValueAsString(evaluationContext, exceptionObj));
                            process.printToConsole("\n");
                            for (Value stackElement : traceArray.getValues()) {
                                process.printToConsole("\tat ");
                                process.printToConsole(DebuggerUtils.getValueAsString(evaluationContext, stackElement));
                                process.printToConsole("\n");
                            }
                        }
                    }
                }
            }
            catch (EvaluateException ignored) {
            }
            catch (ClassNotLoadedException ignored) {
            }
            catch (Throwable e) {
                LOG.info(e); // catch all exceptions to ensure the method returns gracefully
            }
        }
        return exceptionObj;
    }

    @Override
    public void setAncestor(NodeDescriptor oldDescriptor) {
        super.setAncestor(oldDescriptor);
        myIsNew = false;
        if (!myValueReady) {
            ValueDescriptorImpl other = (ValueDescriptorImpl) oldDescriptor;
            if (other.myValueReady) {
                myValue = other.getValue();
                myValueReady = true;
            }
        }
    }

    protected void setLvalue(boolean value) {
        myIsLvalue = value;
    }

    @Override
    protected LocalizeValue calcRepresentation(EvaluationContextImpl context, DescriptorLabelListener labelListener) {
        DebuggerManagerThreadImpl.assertIsManagerThread();

        DebugProcessImpl debugProcess = context.getDebugProcess();
        getRenderer(debugProcess)
            .thenAccept(renderer -> calcRepresentation(context, labelListener, debugProcess, renderer))
            .exceptionally(throwable -> {
                setValueLabelFailed(this, throwable);
                labelListener.labelChanged();
                return null;
            });

        return LocalizeValue.empty(); // we have overridden getLabel
    }

    private void calcRepresentation(EvaluationContextImpl context,
                                    DescriptorLabelListener originalListener,
                                    DebugProcessImpl debugProcess,
                                    NodeRenderer renderer) {
        DelayedDescriptorLabelListener labelListener = new DelayedDescriptorLabelListener(originalListener);
        DebuggerManagerThreadImpl.assertIsManagerThread();

        EvaluateException valueException = myValueException;
        CompletableFuture<Boolean> expandableFuture;
        if (valueException == null || valueException.getExceptionFromTargetVM() != null) {
            expandableFuture = getChildrenRenderer(debugProcess)
                .thenCompose(r -> r.isExpandableAsync(getValue(), context, this));
        }
        else {
            expandableFuture = CompletableFuture.completedFuture(false);
        }

        if (!OnDemandRenderer.isOnDemandForced(debugProcess)) {
            try {
                setValueIcon(renderer.calcValueIcon(this, context, labelListener));
            }
            catch (EvaluateException e) {
                LOG.info(e);
                setValueIcon(null);
            }
        }

        String label;
        if (valueException == null) {
            try {
                label = renderer.calcLabel(this, context, labelListener);
            }
            catch (EvaluateException e) {
                label = setValueLabelFailed(e);
            }
        }
        else {
            label = setValueLabelFailed(valueException);
        }

        setValueLabel(label);

        // only call labelChanged when we have expandable value
        expandableFuture.whenComplete((res, ex) -> {
            if (ex == null) {
                myIsExpandable = res;
            }
            else {
                Throwable cause = DebuggerUtilsAsync.unwrap(ex);
                if (cause instanceof EvaluateException) {
                    LOG.warn(new Throwable(cause));
                }
                else if (!(cause instanceof CancellationException) && !(cause instanceof VMDisconnectedException)) {
                    LOG.error(new Throwable(cause));
                }
            }
            labelListener.labelChanged();
        });

        labelListener.start();
    }

    private String calcIdLabel() {
        //translate only strings in quotes
        if (isShowIdLabel() && myValueReady) {
            final Value value = getValue();
            Renderer lastRenderer = getLastRenderer();
            final EvaluationContextImpl evalContext = myStoredEvaluationContext;
            return evalContext != null && lastRenderer != null && !evalContext.getSuspendContext().isResumed() ? ((NodeRendererImpl) lastRenderer).getIdLabel(value, evalContext.getDebugProcess()) :
                null;
        }
        return null;
    }

    @Override
    public LocalizeValue getLabel() {
        return LocalizeValue.of(calcValueName() + getDeclaredTypeLabel() + " = " + getValueLabel());
    }

    public ValueDescriptorImpl getFullValueDescriptor() {
        ValueDescriptorImpl descriptor = new ValueDescriptorImpl(myProject, myValue) {
            @Override
            public Value calcValue(EvaluationContextImpl evaluationContext) throws EvaluateException {
                return myValue;
            }

            @Override
            public PsiExpression getDescriptorEvaluation(DebuggerContext context) throws EvaluateException {
                return null;
            }

            @Override
            public CompletableFuture<NodeRenderer> getRenderer(DebugProcessImpl debugProcess) {
                return ValueDescriptorImpl.this.getRenderer(debugProcess);
            }
        };
        descriptor.myFullValue = true;
        return descriptor;
    }

    @Override
    public void setValueLabel(String label) {
        if (!myFullValue) {
            label = DebuggerUtilsEx.truncateString(label);
        }
        myValueText = label;
        myIdLabel = calcIdLabel();
    }

    @Override
    public String setValueLabelFailed(EvaluateException e) {
        final String label = setFailed(e);
        setValueLabel(label);
        return label;
    }

    public static String setValueLabelFailed(ValueDescriptor descriptor, Throwable throwable) {
        Throwable cause = DebuggerUtilsAsync.unwrap(throwable);
        if (cause instanceof EvaluateException evaluateException) {
            return descriptor.setValueLabelFailed(evaluateException);
        }

        LocalizeValue message;
        if (cause instanceof CancellationException) {
            message = JavaDebuggerLocalize.errorContextHasChanged();
        }
        else if (cause instanceof VMDisconnectedException || cause instanceof RejectedExecutionException) {
            message = JavaDebuggerLocalize.errorVmDisconnected();
        }
        else {
            message = JavaDebuggerLocalize.internalDebuggerError();
            LOG.error(new Throwable(cause));
        }
        return descriptor.setValueLabelFailed(new EvaluateException(message));
    }

    @Override
    public Image setValueIcon(Image icon) {
        return myValueIcon = icon;
    }

    @Nullable
    public Image getValueIcon() {
        return myValueIcon;
    }

    public String calcValueName() {
        String name = getName();
        NodeDescriptorNameAdjuster nameAdjuster = NodeDescriptorNameAdjuster.findFor(this);
        if (nameAdjuster != null) {
            return nameAdjuster.fixName(name, this);
        }
        return name;
    }

    @Nullable
    public String getDeclaredType() {
        return null;
    }

    @Override
    public void displayAs(NodeDescriptor descriptor) {
        if (descriptor instanceof ValueDescriptorImpl) {
            ValueDescriptorImpl valueDescriptor = (ValueDescriptorImpl) descriptor;
            myRenderer = valueDescriptor.myRenderer;
        }
        super.displayAs(descriptor);
    }

    public Renderer getLastRenderer() {
        return myRenderer != null ? myRenderer : myAutoRenderer;
    }

    @Nullable
    public Type getType() {
        Value value = getValue();
        return value != null ? value.type() : null;
    }

    public CompletableFuture<NodeRenderer> getChildrenRenderer(DebugProcessImpl debugProcess) {
        if (OnDemandRenderer.isOnDemandForced(debugProcess)) {
            return CompletableFuture.completedFuture(DebugProcessImpl.getDefaultRenderer(getValue()));
        }
        return getRenderer(debugProcess);
    }

    public CompletableFuture<NodeRenderer> getRenderer(DebugProcessImpl debugProcess) {
        DebuggerManagerThreadImpl.assertIsManagerThread();
        return DebuggerUtilsAsync.type(getValue())
            .thenCompose(type -> getRenderer(type, debugProcess));
    }

    protected final CompletableFuture<NodeRenderer> getRenderer(@Nullable Type type, DebugProcessImpl debugProcess) {
        DebuggerManagerThreadImpl.assertIsManagerThread();
        CompletableFuture<Boolean> customCheck = CompletableFuture.completedFuture(false);
        if (type != null && myRenderer != null) {
            customCheck = myRenderer.isApplicableAsync(type);
        }
        return customCheck.thenCompose(custom -> {
            DebuggerManagerThreadImpl.assertIsManagerThread();
            if (custom) {
                return CompletableFuture.completedFuture(myRenderer);
            }
            else {
                return DebuggerUtilsAsync.reschedule(debugProcess.getAutoRendererAsync(type))
                    .thenApply(r -> {
                        myAutoRenderer = r;
                        return r;
                    });
            }
        });
    }

    public void setRenderer(NodeRenderer renderer) {
        DebuggerManagerThreadImpl.assertIsManagerThread();
        myRenderer = renderer;
        myAutoRenderer = null;
    }

    //returns expression that evaluates tree to this descriptor
    public CompletableFuture<@Nullable PsiElement> getTreeEvaluation(JavaValue value, DebuggerContextImpl context) throws EvaluateException {
        JavaValue parent = value.getParent();
        if (parent != null) {
            ValueDescriptorImpl vDescriptor = parent.getDescriptor();

            return vDescriptor.getTreeEvaluation(parent, context).thenCompose(parentEvaluation -> {
                if (!(parentEvaluation instanceof PsiExpression parentExpression)) {
                    return CompletableFuture.completedFuture(null);
                }

                return vDescriptor.getChildrenRenderer(context.getDebugProcess())
                    .thenApply(childrenRenderer -> {
                        try {
                            return ReadAction.compute(() -> DebuggerTreeNodeExpression.substituteThis(
                                childrenRenderer.getChildValueExpression(new DebuggerTreeNodeMock(value), context),
                                parentExpression,
                                vDescriptor.getValue()
                            ));
                        }
                        catch (EvaluateException e) {
                            throw new CompletionException(e);
                        }
                    });
            });
        }

        return CompletableFuture.completedFuture(ReadAction.compute(() -> getDescriptorEvaluation(context)));
    }

    protected static class NeedMarkException extends EvaluateException {
        private final String myMarkName;

        public NeedMarkException(ObjectReference reference) {
            super(LocalizeValue.empty());
            myMarkName = NamesUtils.getUniqueName(reference).replace("@", "");
        }

        @Override
        public Throwable fillInStackTrace() {
            return this;
        }

        public String getMarkName() {
            return myMarkName;
        }
    }

    private static class DebuggerTreeNodeMock implements DebuggerTreeNode {
        private final JavaValue value;

        public DebuggerTreeNodeMock(JavaValue value) {
            this.value = value;
        }

        @Override
        public DebuggerTreeNode getParent() {
            return new DebuggerTreeNodeMock(value.getParent());
        }

        @Override
        public ValueDescriptorImpl getDescriptor() {
            return value.getDescriptor();
        }

        @Override
        public Project getProject() {
            return value.getProject();
        }

        @Override
        public void setRenderer(NodeRenderer renderer) {
        }
    }

    //returns expression that evaluates descriptor value
    //use 'this' to reference parent node
    //for ex. FieldDescriptorImpl should return
    //this.fieldName
    @Override
    public abstract PsiExpression getDescriptorEvaluation(DebuggerContext context) throws EvaluateException;

    public static String getIdLabel(ObjectReference objRef) {
        final ClassRenderer classRenderer = NodeRendererSettings.getInstance().getClassRenderer();
        if (objRef instanceof StringReference && !classRenderer.SHOW_STRINGS_TYPE) {
            return null;
        }
        StringBuilder buf = new StringBuilder();
        final boolean showConcreteType = !classRenderer.SHOW_DECLARED_TYPE || (!(objRef instanceof StringReference) && !(objRef instanceof ClassObjectReference) && !isEnumConstant(objRef));
        if (showConcreteType || classRenderer.SHOW_OBJECT_ID) {
            //buf.append('{');
            if (showConcreteType) {
                buf.append(classRenderer.renderTypeName(objRef.type().name()));
            }
            if (classRenderer.SHOW_OBJECT_ID) {
                buf.append('@');
                if (ApplicationManager.getApplication().isUnitTestMode()) {
                    //noinspection HardCodedStringLiteral
                    buf.append("uniqueID");
                }
                else {
                    buf.append(objRef.uniqueID());
                }
            }
            //buf.append('}');
        }

        if (objRef instanceof ArrayReference) {
            int idx = buf.indexOf("[");
            if (idx >= 0) {
                buf.insert(idx + 1, Integer.toString(((ArrayReference) objRef).length()));
            }
        }

        return buf.toString();
    }

    private static boolean isEnumConstant(final ObjectReference objRef) {
        try {
            Type type = objRef.type();
            return type instanceof ClassType && ((ClassType) type).isEnum();
        }
        catch (ObjectCollectedException ignored) {
        }
        return false;
    }

    public boolean canSetValue() {
        return myValueReady && !myIsSynthetic && isLvalue();
    }

    public XValueModifier getModifier(JavaValue value) {
        return null;
    }

    public String getIdLabel() {
        return StringUtil.notNullize(myIdLabel);
    }

    public String getValueLabel() {
        String label = getIdLabel();
        if (!StringUtil.isEmpty(label)) {
            return '{' + label + '}' + getValueText();
        }
        return getValueText();
    }

    public String getValueText() {
        return StringUtil.notNullize(myValueText);
    }

    //Context is set to null
    @Override
    public void clear() {
        super.clear();
        setValueLabel("");
        myIsExpandable = false;
    }

    @Override
    @Nullable
    public ValueMarkup getMarkup(final DebugProcess debugProcess) {
        final Value value = getValue();
        if (value instanceof ObjectReference) {
            final ObjectReference objRef = (ObjectReference) value;
            final Map<ObjectReference, ValueMarkup> map = getMarkupMap(debugProcess);
            if (map != null) {
                return map.get(objRef);
            }
        }
        return null;
    }

    @Override
    public void setMarkup(final DebugProcess debugProcess, @Nullable final ValueMarkup markup) {
        final Value value = getValue();
        if (value instanceof ObjectReference) {
            final Map<ObjectReference, ValueMarkup> map = getMarkupMap(debugProcess);
            if (map != null) {
                final ObjectReference objRef = (ObjectReference) value;
                if (markup != null) {
                    map.put(objRef, markup);
                }
                else {
                    map.remove(objRef);
                }
            }
        }
    }

    public boolean canMark() {
        if (!myValueReady) {
            return false;
        }
        return getValue() instanceof ObjectReference;
    }

    public Project getProject() {
        return myProject;
    }

    public String getDeclaredTypeLabel() {
        ClassRenderer classRenderer = NodeRendererSettings.getInstance().getClassRenderer();
        if (classRenderer.SHOW_DECLARED_TYPE) {
            String declaredType = getDeclaredType();
            if (!StringUtil.isEmpty(declaredType)) {
                return ": " + classRenderer.renderTypeName(declaredType);
            }
        }
        return "";
    }

    public EvaluationContextImpl getStoredEvaluationContext() {
        return myStoredEvaluationContext;
    }

    private static class DelayedDescriptorLabelListener implements DescriptorLabelListener {
        private final DescriptorLabelListener myLabelListener;
        private volatile boolean myStarted = false;
        private volatile boolean myFired = false;

        DelayedDescriptorLabelListener(DescriptorLabelListener labelListener) {
            myLabelListener = labelListener;
        }

        @Override
        public void labelChanged() {
            myFired = true;
            if (myStarted) {
                myLabelListener.labelChanged();
            }
        }

        public void start() {
            myStarted = true;
            if (myFired) {
                myLabelListener.labelChanged();
            }
        }
    }
}
