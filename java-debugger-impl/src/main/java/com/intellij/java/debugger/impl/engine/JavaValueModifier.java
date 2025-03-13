/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.java.debugger.engine.DebuggerUtils;
import com.intellij.java.debugger.engine.evaluation.CodeFragmentKind;
import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.java.debugger.engine.evaluation.expression.ExpressionEvaluator;
import com.intellij.java.debugger.impl.DebuggerContextImpl;
import com.intellij.java.debugger.impl.DebuggerInvocationUtil;
import com.intellij.java.debugger.impl.DebuggerSession;
import com.intellij.java.debugger.impl.engine.evaluation.EvaluationContextImpl;
import com.intellij.java.debugger.impl.engine.evaluation.TextWithImportsImpl;
import com.intellij.java.debugger.impl.engine.evaluation.expression.BoxingEvaluator;
import com.intellij.java.debugger.impl.engine.evaluation.expression.EvaluatorBuilderImpl;
import com.intellij.java.debugger.impl.engine.evaluation.expression.IdentityEvaluator;
import com.intellij.java.debugger.impl.engine.evaluation.expression.UnBoxingEvaluator;
import com.intellij.java.debugger.impl.engine.events.DebuggerContextCommandImpl;
import com.intellij.java.debugger.impl.engine.events.SuspendContextCommandImpl;
import com.intellij.java.debugger.impl.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.java.debugger.impl.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.java.debugger.localize.JavaDebuggerLocalize;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.progress.Task;
import consulo.execution.debug.frame.XValueModifier;
import consulo.internal.com.sun.jdi.*;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;

import static consulo.java.language.module.util.JavaClassNames.JAVA_LANG_STRING;

/*
 * Class SetValueAction
 * @author Jeka
 */
public abstract class JavaValueModifier extends XValueModifier {
    private final JavaValue myJavaValue;

    public JavaValueModifier(JavaValue javaValue) {
        myJavaValue = javaValue;
    }

    @Override
    public void calculateInitialValueEditorText(final XInitialValueCallback callback) {
        final Value value = myJavaValue.getDescriptor().getValue();
        if (value instanceof PrimitiveValue) {
            String valueString = myJavaValue.getValueString();
            int pos = valueString.lastIndexOf('('); //skip hex presentation if any
            if (pos > 1) {
                valueString = valueString.substring(0, pos).trim();
            }
            callback.setValue(valueString);
        }
        else if (value instanceof StringReference) {
            final EvaluationContextImpl evaluationContext = myJavaValue.getEvaluationContext();
            evaluationContext.getManagerThread().schedule(new SuspendContextCommandImpl(evaluationContext.getSuspendContext()) {
                @Override
                public Priority getPriority() {
                    return Priority.NORMAL;
                }

                @Override
                public void contextAction() throws Exception {
                    callback.setValue(StringUtil.wrapWithDoubleQuote(DebuggerUtils.translateStringValue(DebuggerUtils.getValueAsString(evaluationContext, value))));
                }
            });
        }
        else {
            callback.setValue(null);
        }
    }

    protected static void update(final DebuggerContextImpl context) {
        DebuggerInvocationUtil.swingInvokeLater(context.getProject(), () ->
        {
            final DebuggerSession session = context.getDebuggerSession();
            if (session != null) {
                session.refresh(false);
            }
        });
    }

    protected abstract void setValueImpl(@Nonnull String expression, @Nonnull XModificationCallback callback);

    @Override
    public void setValue(@Nonnull String expression, @Nonnull XModificationCallback callback) {
        final NodeDescriptorImpl descriptor = myJavaValue.getDescriptor();
        if (!((ValueDescriptorImpl) descriptor).canSetValue()) {
            return;
        }

        if (myJavaValue.getEvaluationContext().getSuspendContext().isResumed()) {
            callback.errorOccurred(DebuggerBundle.message("error.context.has.changed"));
            return;
        }

        setValueImpl(expression, callback);
    }

    protected static Value preprocessValue(EvaluationContextImpl context, Value value, Type varType) throws EvaluateException {
        if (value != null && JAVA_LANG_STRING.equals(varType.name()) && !(value instanceof StringReference)) {
            String v = DebuggerUtils.getValueAsString(context, value);
            if (v != null) {
                value = context.getSuspendContext().getDebugProcess().getVirtualMachineProxy().mirrorOf(v);
            }
        }
        if (value instanceof DoubleValue) {
            double dValue = ((DoubleValue) value).doubleValue();
            if (varType instanceof FloatType && Float.MIN_VALUE <= dValue && dValue <= Float.MAX_VALUE) {
                value = context.getSuspendContext().getDebugProcess().getVirtualMachineProxy().mirrorOf((float) dValue);
            }
        }
        if (value != null) {
            if (varType instanceof PrimitiveType) {
                if (!(value instanceof PrimitiveValue)) {
                    value = (Value) new UnBoxingEvaluator(new IdentityEvaluator(value)).evaluate(context);
                }
            }
            else if (varType instanceof ReferenceType) {
                if (value instanceof PrimitiveValue) {
                    value = (Value) new BoxingEvaluator(new IdentityEvaluator(value)).evaluate(context);
                }
            }
        }
        return value;
    }

    protected interface SetValueRunnable {
        void setValue(EvaluationContextImpl evaluationContext, Value newValue) throws ClassNotLoadedException, InvalidTypeException, EvaluateException, IncompatibleThreadStateException;

        ReferenceType loadClass(EvaluationContextImpl evaluationContext,
                                String className) throws EvaluateException, InvocationException, ClassNotLoadedException, IncompatibleThreadStateException, InvalidTypeException;
    }

    private static void setValue(String expressionToShow, ExpressionEvaluator evaluator, EvaluationContextImpl evaluationContext, SetValueRunnable setValueRunnable) throws EvaluateException {
        Value value;
        try {
            value = evaluator.evaluate(evaluationContext);

            setValueRunnable.setValue(evaluationContext, value);
        }
        catch (IllegalArgumentException ex) {
            throw EvaluateExceptionUtil.createEvaluateException(ex.getMessage());
        }
        catch (InvalidTypeException ex) {
            throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.type.mismatch"));
        }
        catch (IncompatibleThreadStateException e) {
            throw EvaluateExceptionUtil.createEvaluateException(e);
        }
        catch (ClassNotLoadedException ex) {
            if (!evaluationContext.isAutoLoadClasses()) {
                throw EvaluateExceptionUtil.createEvaluateException(ex);
            }
            final ReferenceType refType;
            try {
                refType = setValueRunnable.loadClass(evaluationContext, ex.className());
                if (refType != null) {
                    //try again
                    setValue(expressionToShow, evaluator, evaluationContext, setValueRunnable);
                }
            }
            catch (InvocationException | InvalidTypeException | IncompatibleThreadStateException | ClassNotLoadedException e) {
                throw EvaluateExceptionUtil.createEvaluateException(e);
            }
            catch (ObjectCollectedException e) {
                throw EvaluateExceptionUtil.OBJECT_WAS_COLLECTED;
            }
        }
    }

    protected void set(@Nonnull final String expression, final XModificationCallback callback, final DebuggerContextImpl debuggerContext, final SetValueRunnable setValueRunnable) {
        final EvaluationContextImpl evaluationContext = myJavaValue.getEvaluationContext();

        SuspendContextCommandImpl askSetAction = new DebuggerContextCommandImpl(debuggerContext) {
            @Override
            public Priority getPriority() {
                return Priority.HIGH;
            }

            @Override
            public void threadAction(@Nonnull SuspendContextImpl suspendContext) {
                ExpressionEvaluator evaluator;
                try {
                    Project project = evaluationContext.getProject();
                    SourcePosition position = ContextUtil.getSourcePosition(evaluationContext);
                    PsiElement context = ContextUtil.getContextElement(evaluationContext, position);
                    evaluator = DebuggerInvocationUtil.commitAndRunReadAction(project, () -> EvaluatorBuilderImpl.build(new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, expression), context, position, project));

                    setValue(expression, evaluator, evaluationContext, new SetValueRunnable() {
                        @Override
                        public void setValue(EvaluationContextImpl evaluationContext,
                                             Value newValue) throws ClassNotLoadedException, InvalidTypeException, EvaluateException, IncompatibleThreadStateException {

                            ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();

                            if (indicator == null || !indicator.isCanceled()) {
                                setValueRunnable.setValue(evaluationContext, newValue);
                            }
                        }

                        @Override
                        public ReferenceType loadClass(EvaluationContextImpl evaluationContext,
                                                       String className) throws InvocationException, ClassNotLoadedException, EvaluateException, IncompatibleThreadStateException, InvalidTypeException {
                            return setValueRunnable.loadClass(evaluationContext, className);
                        }
                    });
                    callback.valueModified();
                }
                catch (EvaluateException e) {
                    callback.errorOccurred(e.getMessage());
                }
            }
        };

        new Task.Backgroundable(debuggerContext.getProject(), JavaDebuggerLocalize.titleEvaluating(), true) {
            @Override
            public void run(@Nonnull ProgressIndicator progressIndicator) {
                evaluationContext.getDebugProcess().getManagerThread().invokeAndWait(askSetAction);
            }

            @RequiredUIAccess
            @Override
            public void onCancel() {
                askSetAction.release();
            }
        }.queue();
    }
}
