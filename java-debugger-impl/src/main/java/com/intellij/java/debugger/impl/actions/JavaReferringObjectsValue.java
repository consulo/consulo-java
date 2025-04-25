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

import com.intellij.java.debugger.DebuggerContext;
import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.impl.engine.JavaValue;
import com.intellij.java.debugger.impl.engine.SuspendContextImpl;
import com.intellij.java.debugger.impl.engine.evaluation.EvaluationContextImpl;
import com.intellij.java.debugger.impl.engine.events.SuspendContextCommandImpl;
import com.intellij.java.debugger.impl.ui.impl.watch.FieldDescriptorImpl;
import com.intellij.java.debugger.impl.ui.impl.watch.NodeManagerImpl;
import com.intellij.java.debugger.impl.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.java.debugger.localize.JavaDebuggerLocalize;
import com.intellij.java.language.psi.PsiExpression;
import consulo.execution.debug.frame.*;
import consulo.execution.debug.frame.presentation.XValueNodePresentationConfigurator;
import consulo.execution.debug.frame.presentation.XValuePresentation;
import consulo.execution.debug.ui.XValueTree;
import consulo.internal.com.sun.jdi.Field;
import consulo.internal.com.sun.jdi.ObjectCollectedException;
import consulo.internal.com.sun.jdi.ObjectReference;
import consulo.internal.com.sun.jdi.Value;
import consulo.ui.image.Image;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;

public class JavaReferringObjectsValue extends JavaValue {
    private static final long MAX_REFERRING = 100;
    private final boolean myIsField;

    private JavaReferringObjectsValue(
        @Nullable JavaValue parent,
        @Nonnull ValueDescriptorImpl valueDescriptor,
        @Nonnull EvaluationContextImpl evaluationContext,
        NodeManagerImpl nodeManager,
        boolean isField
    ) {
        super(parent, valueDescriptor, evaluationContext, nodeManager, false);
        myIsField = isField;
    }

    public JavaReferringObjectsValue(@Nonnull JavaValue javaValue, boolean isField) {
        super(null, javaValue.getDescriptor(), javaValue.getEvaluationContext(), javaValue.getNodeManager(), false);
        myIsField = isField;
    }

    @Override
    public boolean canNavigateToSource() {
        return true;
    }

    @Override
    public void computeChildren(@Nonnull XCompositeNode node) {
        scheduleCommand(getEvaluationContext(), node, new SuspendContextCommandImpl(getEvaluationContext().getSuspendContext()) {
            @Override
            public Priority getPriority() {
                return Priority.NORMAL;
            }

            @Override
            public void contextAction(@Nonnull SuspendContextImpl suspendContext) throws Exception {
                XValueChildrenList children = new XValueChildrenList();

                Value value = getDescriptor().getValue();

                List<ObjectReference> references;
                try {
                    references = ((ObjectReference)value).referringObjects(MAX_REFERRING);
                }
                catch (ObjectCollectedException e) {
                    node.setErrorMessage(JavaDebuggerLocalize.evaluationErrorObjectCollected().get());
                    return;
                }

                int i = 1;
                for (ObjectReference reference : references) {
                    // try to find field name
                    Field field = findField(reference, value);
                    if (field != null) {
                        ValueDescriptorImpl descriptor = new FieldDescriptorImpl(getProject(), reference, field) {
                            @Override
                            public Value calcValue(EvaluationContextImpl evaluationContext) throws EvaluateException {
                                return reference;
                            }
                        };
                        children.add(new JavaReferringObjectsValue(null, descriptor, getEvaluationContext(), getNodeManager(), true));
                        i++;
                    }
                    else {
                        ValueDescriptorImpl descriptor = new ValueDescriptorImpl(getProject(), reference) {
                            @Override
                            public Value calcValue(EvaluationContextImpl evaluationContext) throws EvaluateException {
                                return reference;
                            }

                            @Override
                            public String getName() {
                                return "Ref";
                            }

                            @Override
                            public PsiExpression getDescriptorEvaluation(DebuggerContext context) throws EvaluateException {
                                return null;
                            }
                        };
                        children.add(
                            "Referrer " + i++,
                            new JavaReferringObjectsValue(null, descriptor, getEvaluationContext(), getNodeManager(), false)
                        );
                    }
                }

                node.addChildren(children, true);
            }
        });
    }

    @Override
    public void computePresentation(@Nonnull XValueNode node, @Nonnull XValuePlace place) {
        if (!myIsField) {
            super.computePresentation(node, place);
        }
        else {
            super.computePresentation(
                new XValueNodePresentationConfigurator.ConfigurableXValueNodeImpl() {
                    @Override
                    public void applyPresentation(
                        @Nullable Image icon,
                        @Nonnull XValuePresentation valuePresenter,
                        boolean hasChildren
                    ) {
                        node.setPresentation(icon, new XValuePresentation() {
                                @Nonnull
                                @Override
                                public String getSeparator() {
                                    return " in ";
                                }

                                @Nullable
                                @Override
                                public String getType() {
                                    return valuePresenter.getType();
                                }

                                @Override
                                public void renderValue(@Nonnull XValueTextRenderer renderer) {
                                    valuePresenter.renderValue(renderer);
                                }
                            },
                            hasChildren
                        );
                    }

                    @Override
                    public void setFullValueEvaluator(@Nonnull XFullValueEvaluator fullValueEvaluator) {
                    }

                    @Nullable
                    @Override
                    public String getName() {
                        return null;
                    }

                    @Nullable
                    @Override
                    public XValue getValueContainer() {
                        return null;
                    }

                    @Nullable
                    @Override
                    public XValueTree getTree() {
                        return null;
                    }

                    @Override
                    public boolean isObsolete() {
                        return false;
                    }
                },
                place
            );
        }
    }

    private static Field findField(ObjectReference reference, Value value) {
        for (Field field : reference.referenceType().allFields()) {
            if (reference.getValue(field) == value) {
                return field;
            }
        }
        return null;
    }

    @Nullable
    @Override
    public XValueModifier getModifier() {
        return null;
    }
}
