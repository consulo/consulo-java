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
package com.intellij.java.debugger.impl.settings;

import com.intellij.java.debugger.DebuggerContext;
import com.intellij.java.debugger.engine.DebugProcess;
import com.intellij.java.debugger.engine.evaluation.CodeFragmentKind;
import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.java.debugger.engine.evaluation.EvaluationContext;
import com.intellij.java.debugger.engine.evaluation.expression.ExpressionEvaluator;
import com.intellij.java.debugger.impl.DebuggerUtilsEx;
import com.intellij.java.debugger.impl.engine.evaluation.EvaluationContextImpl;
import com.intellij.java.debugger.impl.engine.evaluation.TextWithImportsImpl;
import com.intellij.java.debugger.impl.ui.impl.watch.ArrayElementDescriptorImpl;
import com.intellij.java.debugger.impl.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.java.debugger.impl.ui.impl.watch.WatchItemDescriptor;
import com.intellij.java.debugger.impl.ui.tree.DebuggerTreeNode;
import com.intellij.java.debugger.impl.ui.tree.ValueDescriptor;
import com.intellij.java.debugger.impl.ui.tree.render.*;
import com.intellij.java.debugger.localize.JavaDebuggerLocalize;
import com.intellij.java.language.impl.JavaFileType;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.disposer.Disposable;
import consulo.ide.ServiceManager;
import consulo.internal.com.sun.jdi.Value;
import consulo.java.language.module.util.JavaClassNames;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.proxy.EventDispatcher;
import consulo.ui.image.Image;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.JDOMExternalizerUtil;
import consulo.util.xml.serializer.WriteExternalException;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Singleton;
import org.jdom.Element;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

@Singleton
@State(name = "NodeRendererSettings", storages = @Storage("debugger.renderers.xml"))
@ServiceAPI(ComponentScope.APPLICATION)
@ServiceImpl
public class NodeRendererSettings implements PersistentStateComponent<Element> {
    private static final String REFERENCE_RENDERER = "Reference renderer";
    public static final String RENDERER_TAG = "Renderer";
    private static final String RENDERER_ID = "ID";

    private final EventDispatcher<NodeRendererSettingsListener> myDispatcher = EventDispatcher.create(NodeRendererSettingsListener.class);
    private RendererConfiguration myCustomRenderers = new RendererConfiguration(this);

    // base renderers
    private final PrimitiveRenderer myPrimitiveRenderer = new PrimitiveRenderer();
    private final ArrayRenderer myArrayRenderer = new ArrayRenderer();
    private final ClassRenderer myClassRenderer = new ClassRenderer();
    private final HexRenderer myHexRenderer = new HexRenderer();
    private final ToStringRenderer myToStringRenderer = new ToStringRenderer();
    // alternate collections
    private final NodeRenderer[] myAlternateCollectionRenderers = new NodeRenderer[]{
        createCompoundReferenceRenderer(
            "Map",
            JavaClassNames.JAVA_UTIL_MAP,
            createLabelRenderer(" size = ", "size()", null),
            createExpressionChildrenRenderer(
                "entrySet().toArray()",
                "!isEmpty()"
            )
        ),
        createCompoundReferenceRenderer(
            "Map.Entry",
            "java.util.Map$Entry",
            new MapEntryLabelRenderer()/*createLabelRenderer(null, "\" \" + getKey() + \" -> \" + getValue()", null)*/,
            createEnumerationChildrenRenderer(new String[][]{
                {
                    "key",
                    "getKey()"
                },
                {
                    "value",
                    "getValue()"
                }
            })
        ),
        new ListObjectRenderer(this),
        createCompoundReferenceRenderer(
            "Collection",
            "java.util.Collection",
            createLabelRenderer(" size = ", "size()", null),
            createExpressionChildrenRenderer("toArray()", "!isEmpty()")
        )
    };
    private static final String HEX_VIEW_ENABLED = "HEX_VIEW_ENABLED";
    private static final String ALTERNATIVE_COLLECTION_VIEW_ENABLED = "ALTERNATIVE_COLLECTION_VIEW_ENABLED";
    private static final String CUSTOM_RENDERERS_TAG_NAME = "CustomRenderers";

    public NodeRendererSettings() {
        // default configuration
        myHexRenderer.setEnabled(false);
        myToStringRenderer.setEnabled(true);
        setAlternateCollectionViewsEnabled(true);
    }

    public static NodeRendererSettings getInstance() {
        return ServiceManager.getService(NodeRendererSettings.class);
    }

    public void setAlternateCollectionViewsEnabled(boolean enabled) {
        for (NodeRenderer myAlternateCollectionRenderer : myAlternateCollectionRenderers) {
            myAlternateCollectionRenderer.setEnabled(enabled);
        }
    }

    public boolean areAlternateCollectionViewsEnabled() {
        return myAlternateCollectionRenderers[0].isEnabled();
    }

    @Override
    public boolean equals(Object o) {
        return o == this
            || o instanceof NodeRendererSettings that
            && DebuggerUtilsEx.elementsEqual(getState(), that.getState());

    }

    public void addListener(NodeRendererSettingsListener listener, Disposable disposable) {
        myDispatcher.addListener(listener, disposable);
    }

    @Override
    @SuppressWarnings({"HardCodedStringLiteral"})
    public Element getState() {
        Element element = new Element("NodeRendererSettings");
        if (myHexRenderer.isEnabled()) {
            JDOMExternalizerUtil.writeField(element, HEX_VIEW_ENABLED, "true");
        }
        if (!areAlternateCollectionViewsEnabled()) {
            JDOMExternalizerUtil.writeField(element, ALTERNATIVE_COLLECTION_VIEW_ENABLED, "false");
        }

        try {
            element.addContent(writeRenderer(myArrayRenderer));
            element.addContent(writeRenderer(myToStringRenderer));
            element.addContent(writeRenderer(myClassRenderer));
            element.addContent(writeRenderer(myPrimitiveRenderer));
            if (myCustomRenderers.getRendererCount() > 0) {
                Element custom = new Element(CUSTOM_RENDERERS_TAG_NAME);
                element.addContent(custom);
                myCustomRenderers.writeExternal(custom);
            }
        }
        catch (WriteExternalException e) {
            // ignore
        }
        return element;
    }

    @Override
    @SuppressWarnings({"HardCodedStringLiteral"})
    public void loadState(Element root) {
        String hexEnabled = JDOMExternalizerUtil.readField(root, HEX_VIEW_ENABLED);
        if (hexEnabled != null) {
            myHexRenderer.setEnabled("true".equalsIgnoreCase(hexEnabled));
        }

        String alternativeEnabled = JDOMExternalizerUtil.readField(root, ALTERNATIVE_COLLECTION_VIEW_ENABLED);
        if (alternativeEnabled != null) {
            setAlternateCollectionViewsEnabled("true".equalsIgnoreCase(alternativeEnabled));
        }

        for (Element elem : root.getChildren(RENDERER_TAG)) {
            String id = elem.getAttributeValue(RENDERER_ID);
            if (id == null) {
                continue;
            }
            try {
                if (ArrayRenderer.UNIQUE_ID.equals(id)) {
                    myArrayRenderer.readExternal(elem);
                }
                else if (ToStringRenderer.UNIQUE_ID.equals(id)) {
                    myToStringRenderer.readExternal(elem);
                }
                else if (ClassRenderer.UNIQUE_ID.equals(id)) {
                    myClassRenderer.readExternal(elem);
                }
                else if (PrimitiveRenderer.UNIQUE_ID.equals(id)) {
                    myPrimitiveRenderer.readExternal(elem);
                }
            }
            catch (InvalidDataException e) {
                // ignore
            }
        }
        Element custom = root.getChild(CUSTOM_RENDERERS_TAG_NAME);
        if (custom != null) {
            myCustomRenderers.readExternal(custom);
        }

        myDispatcher.getMulticaster().renderersChanged();
    }

    public RendererConfiguration getCustomRenderers() {
        return myCustomRenderers;
    }

    public void setCustomRenderers(@Nonnull RendererConfiguration customRenderers) {
        RendererConfiguration oldConfig = myCustomRenderers;
        myCustomRenderers = customRenderers;
        if (oldConfig == null || !oldConfig.equals(customRenderers)) {
            fireRenderersChanged();
        }
    }

    public PrimitiveRenderer getPrimitiveRenderer() {
        return myPrimitiveRenderer;
    }

    public ArrayRenderer getArrayRenderer() {
        return myArrayRenderer;
    }

    public ClassRenderer getClassRenderer() {
        return myClassRenderer;
    }

    public HexRenderer getHexRenderer() {
        return myHexRenderer;
    }

    public ToStringRenderer getToStringRenderer() {
        return myToStringRenderer;
    }

    public NodeRenderer[] getAlternateCollectionRenderers() {
        return myAlternateCollectionRenderers;
    }

    public void fireRenderersChanged() {
        myDispatcher.getMulticaster().renderersChanged();
    }

    public List<NodeRenderer> getAllRenderers() {
        // the order is important as the renderers are applied according to it
        List<NodeRenderer> allRenderers = new ArrayList<>();

        // user defined renderers must come first
        myCustomRenderers.iterateRenderers(renderer -> {
            allRenderers.add(renderer);
            return true;
        });

        // plugins registered renderers come after that
        Collections.addAll(allRenderers, NodeRenderer.EP_NAME.getExtensions());

        // now all predefined stuff
        allRenderers.add(myHexRenderer);
        allRenderers.add(myPrimitiveRenderer);
        Collections.addAll(allRenderers, myAlternateCollectionRenderers);
        allRenderers.add(myToStringRenderer);
        allRenderers.add(myArrayRenderer);
        allRenderers.add(myClassRenderer);
        return allRenderers;
    }

    public Renderer readRenderer(Element root) throws InvalidDataException {
        if (root == null) {
            return null;
        }

        if (!RENDERER_TAG.equals(root.getName())) {
            throw new InvalidDataException("Cannot read renderer - tag name is not '" + RENDERER_TAG + "'");
        }

        String rendererId = root.getAttributeValue(RENDERER_ID);
        if (rendererId == null) {
            throw new InvalidDataException("unknown renderer ID: null");
        }

        Renderer renderer = createRenderer(rendererId);
        if (renderer == null) {
            throw new InvalidDataException("unknown renderer ID: null");
        }

        renderer.readExternal(root);

        return renderer;
    }

    public Element writeRenderer(Renderer renderer) throws WriteExternalException {
        Element root = new Element(RENDERER_TAG);
        if (renderer != null) {
            root.setAttribute(RENDERER_ID, renderer.getUniqueId());
            renderer.writeExternal(root);
        }
        return root;
    }

    public Renderer createRenderer(String rendererId) {
        if (ClassRenderer.UNIQUE_ID.equals(rendererId)) {
            return myClassRenderer;
        }
        else if (ArrayRenderer.UNIQUE_ID.equals(rendererId)) {
            return myArrayRenderer;
        }
        else if (PrimitiveRenderer.UNIQUE_ID.equals(rendererId)) {
            return myPrimitiveRenderer;
        }
        else if (HexRenderer.UNIQUE_ID.equals(rendererId)) {
            return myHexRenderer;
        }
        else if (rendererId.equals(ExpressionChildrenRenderer.UNIQUE_ID)) {
            return new ExpressionChildrenRenderer();
        }
        else if (rendererId.equals(LabelRenderer.UNIQUE_ID)) {
            return new LabelRenderer();
        }
        else if (rendererId.equals(EnumerationChildrenRenderer.UNIQUE_ID)) {
            return new EnumerationChildrenRenderer();
        }
        else if (rendererId.equals(ToStringRenderer.UNIQUE_ID)) {
            return myToStringRenderer;
        }
        else if (rendererId.equals(CompoundNodeRenderer.UNIQUE_ID) || rendererId.equals(REFERENCE_RENDERER)) {
            return createCompoundReferenceRenderer("unnamed", JavaClassNames.JAVA_LANG_OBJECT, null, null);
        }
        else if (rendererId.equals(CompoundTypeRenderer.UNIQUE_ID)) {
            return createCompoundTypeRenderer("unnamed", JavaClassNames.JAVA_LANG_OBJECT, null, null);
        }
        return null;
    }

    public CompoundTypeRenderer createCompoundTypeRenderer(
        String rendererName,
        String className,
        ValueLabelRenderer labelRenderer,
        ChildrenRenderer childrenRenderer
    ) {
        CompoundTypeRenderer renderer = new CompoundTypeRenderer(this, rendererName, labelRenderer, childrenRenderer);
        renderer.setClassName(className);
        return renderer;
    }

    public CompoundReferenceRenderer createCompoundReferenceRenderer(
        String rendererName,
        String className,
        ValueLabelRenderer labelRenderer,
        ChildrenRenderer childrenRenderer
    ) {
        CompoundReferenceRenderer renderer = new CompoundReferenceRenderer(this, rendererName, labelRenderer, childrenRenderer);
        renderer.setClassName(className);
        return renderer;
    }

    public static ExpressionChildrenRenderer createExpressionChildrenRenderer(String expressionText, String childrenExpandableText) {
        ExpressionChildrenRenderer childrenRenderer = new ExpressionChildrenRenderer();
        childrenRenderer.setChildrenExpression(new TextWithImportsImpl(
            CodeFragmentKind.EXPRESSION,
            expressionText,
            "",
            JavaFileType.INSTANCE
        ));
        if (childrenExpandableText != null) {
            childrenRenderer.setChildrenExpandable(new TextWithImportsImpl(
                CodeFragmentKind.EXPRESSION,
                childrenExpandableText,
                "",
                JavaFileType.INSTANCE
            ));
        }
        return childrenRenderer;
    }

    public static EnumerationChildrenRenderer createEnumerationChildrenRenderer(String[][] expressions) {
        EnumerationChildrenRenderer childrenRenderer = new EnumerationChildrenRenderer();
        if (expressions != null && expressions.length > 0) {
            ArrayList<EnumerationChildrenRenderer.ChildInfo> childrenList = new ArrayList<>(expressions.length);
            for (String[] expression : expressions) {
                childrenList.add(new EnumerationChildrenRenderer.ChildInfo(
                    expression[0],
                    new TextWithImportsImpl(
                        CodeFragmentKind.EXPRESSION,
                        expression[1],
                        "",
                        JavaFileType.INSTANCE
                    ),
                    false
                ));
            }
            childrenRenderer.setChildren(childrenList);
        }
        return childrenRenderer;
    }

    private static LabelRenderer createLabelRenderer(final String prefix, String expressionText, final String postfix) {
        LabelRenderer labelRenderer = new LabelRenderer() {
            @Override
            public String calcLabel(ValueDescriptor descriptor, EvaluationContext evaluationContext, DescriptorLabelListener labelListener)
                throws EvaluateException {
                String evaluated = super.calcLabel(descriptor, evaluationContext, labelListener);
                if (prefix == null && postfix == null) {
                    return evaluated;
                }
                if (prefix != null && postfix != null) {
                    return prefix + evaluated + postfix;
                }
                if (prefix != null) {
                    return prefix + evaluated;
                }
                return evaluated + postfix;
            }
        };
        labelRenderer.setLabelExpression(new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, expressionText, "", JavaFileType.INSTANCE));
        return labelRenderer;
    }

    private static class MapEntryLabelRenderer extends ReferenceRenderer implements ValueLabelRenderer {
        private static final Supplier<String> NULL_LABEL_COMPUTABLE = () -> "null";

        private final MyCachedEvaluator myKeyExpression = new MyCachedEvaluator();
        private final MyCachedEvaluator myValueExpression = new MyCachedEvaluator();

        private MapEntryLabelRenderer() {
            super("java.util.Map$Entry");
            myKeyExpression.setReferenceExpression(new TextWithImportsImpl(
                CodeFragmentKind.EXPRESSION,
                "this.getKey()",
                "",
                JavaFileType.INSTANCE
            ));
            myValueExpression.setReferenceExpression(new TextWithImportsImpl(
                CodeFragmentKind.EXPRESSION,
                "this.getValue()",
                "",
                JavaFileType.INSTANCE
            ));
        }

        @Override
        public Image calcValueIcon(ValueDescriptor descriptor, EvaluationContext evaluationContext, DescriptorLabelListener listener)
            throws EvaluateException {
            return null;
        }

        @Override
        public String calcLabel(ValueDescriptor descriptor, EvaluationContext evaluationContext, DescriptorLabelListener listener)
            throws EvaluateException {
            DescriptorUpdater descriptorUpdater = new DescriptorUpdater(descriptor, listener);

            Value originalValue = descriptor.getValue();
            Pair<Supplier<String>, ValueDescriptorImpl> keyPair =
                createValueComputable(evaluationContext, originalValue, myKeyExpression, descriptorUpdater);
            Pair<Supplier<String>, ValueDescriptorImpl> valuePair =
                createValueComputable(evaluationContext, originalValue, myValueExpression, descriptorUpdater);

            descriptorUpdater.setKeyDescriptor(keyPair.second);
            descriptorUpdater.setValueDescriptor(valuePair.second);

            return DescriptorUpdater.constructLabelText(keyPair.first.get(), valuePair.first.get());
        }

        private Pair<Supplier<String>, ValueDescriptorImpl> createValueComputable(
            EvaluationContext evaluationContext,
            Value originalValue,
            MyCachedEvaluator evaluator,
            DescriptorLabelListener listener
        ) throws EvaluateException {
            Value eval = doEval(evaluationContext, originalValue, evaluator);
            if (eval != null) {
                WatchItemDescriptor evalDescriptor =
                    new WatchItemDescriptor(evaluationContext.getProject(), evaluator.getReferenceExpression(), eval);
                evalDescriptor.setShowIdLabel(false);
                return new Pair<>(
                    () -> {
                        evalDescriptor.updateRepresentation((EvaluationContextImpl)evaluationContext, listener);
                        return evalDescriptor.getValueLabel();
                    },
                    evalDescriptor
                );
            }
            return new Pair<>(NULL_LABEL_COMPUTABLE, null);
        }

        @Override
        public String getUniqueId() {
            return "MapEntry renderer";
        }

        private Value doEval(
            EvaluationContext evaluationContext,
            Value originalValue,
            MyCachedEvaluator cachedEvaluator
        ) throws EvaluateException {
            DebugProcess debugProcess = evaluationContext.getDebugProcess();
            if (originalValue == null) {
                return null;
            }
            try {
                ExpressionEvaluator evaluator = cachedEvaluator.getEvaluator(debugProcess.getProject());
                if (!debugProcess.isAttached()) {
                    throw EvaluateExceptionUtil.PROCESS_EXITED;
                }
                EvaluationContext thisEvaluationContext = evaluationContext.createEvaluationContext(originalValue);
                return evaluator.evaluate(thisEvaluationContext);
            }
            catch (EvaluateException ex) {
                LocalizeValue message = LocalizeValue.join(
                    JavaDebuggerLocalize.errorUnableToEvaluateExpression(),
                    LocalizeValue.space(),
                    LocalizeValue.ofNullable(ex.getMessage())
                );
                throw new EvaluateException(message.get(), ex);
            }
        }

        private class MyCachedEvaluator extends CachedEvaluator {
            @Override
            protected String getClassName() {
                return MapEntryLabelRenderer.this.getClassName();
            }

            @Override
            public ExpressionEvaluator getEvaluator(Project project) throws EvaluateException {
                return super.getEvaluator(project);
            }
        }
    }

    private static class ListObjectRenderer extends CompoundReferenceRenderer {
        public ListObjectRenderer(NodeRendererSettings rendererSettings) {
            super(
                rendererSettings,
                "List",
                createLabelRenderer(" size = ", "size()", null),
                createExpressionChildrenRenderer("toArray()", "!isEmpty()")
            );
            setClassName(JavaClassNames.JAVA_UTIL_LIST);
        }

        @Override
        public PsiElement getChildValueExpression(DebuggerTreeNode node, DebuggerContext context) throws EvaluateException {
            LOG.assertTrue(node.getDescriptor() instanceof ArrayElementDescriptorImpl);
            try {
                return getChildValueExpression(
                    "this.get(" + ((ArrayElementDescriptorImpl)node.getDescriptor()).getIndex() + ")",
                    node,
                    context
                );
            }
            catch (IncorrectOperationException e) {
                // fallback to original
                return super.getChildValueExpression(node, context);
            }
        }
    }

    private static class DescriptorUpdater implements DescriptorLabelListener {
        private final ValueDescriptor myTargetDescriptor;
        @Nullable
        private ValueDescriptorImpl myKeyDescriptor;
        @Nullable
        private ValueDescriptorImpl myValueDescriptor;
        private final DescriptorLabelListener myDelegate;

        private DescriptorUpdater(ValueDescriptor descriptor, DescriptorLabelListener delegate) {
            myTargetDescriptor = descriptor;
            myDelegate = delegate;
        }

        public void setKeyDescriptor(@Nullable ValueDescriptorImpl keyDescriptor) {
            myKeyDescriptor = keyDescriptor;
        }

        public void setValueDescriptor(@Nullable ValueDescriptorImpl valueDescriptor) {
            myValueDescriptor = valueDescriptor;
        }

        @Override
        public void labelChanged() {
            myTargetDescriptor.setValueLabel(constructLabelText(
                getDescriptorLabel(myKeyDescriptor),
                getDescriptorLabel(myValueDescriptor)
            ));
            myDelegate.labelChanged();
        }

        static String constructLabelText(String keylabel, String valueLabel) {
            StringBuilder sb = new StringBuilder();
            sb.append('\"').append(keylabel).append("\" -> ");
            if (!StringUtil.isEmpty(valueLabel)) {
                sb.append('\"').append(valueLabel).append('\"');
            }
            return sb.toString();
        }

        private static String getDescriptorLabel(ValueDescriptorImpl keyDescriptor) {
            return keyDescriptor == null ? "null" : keyDescriptor.getValueLabel();
        }
    }
}
