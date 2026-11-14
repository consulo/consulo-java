// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.ui.tree.render;

import com.intellij.java.debugger.impl.DebuggerUtilsAsync;
import com.intellij.java.debugger.impl.engine.FullValueEvaluatorProvider;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.internal.com.sun.jdi.Type;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Allows to construct a renderer with the provided capabilities
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class CompoundRendererProvider {
    public static final ExtensionPointName<CompoundRendererProvider> EP_NAME = ExtensionPointName.create(CompoundRendererProvider.class);

    protected abstract String getName();

    protected @Nullable String getClassName() {
        return null;
    }

    protected @Nullable ValueLabelRenderer getValueLabelRenderer() {
        return null;
    }

    protected @Nullable ValueIconRenderer getIconRenderer() {
        return null;
    }

    protected @Nullable ChildrenRenderer getChildrenRenderer() {
        return null;
    }

    protected @Nullable FullValueEvaluatorProvider getFullValueEvaluatorProvider() {
        return null;
    }

    protected @Nullable Function<Type, CompletableFuture<Boolean>> getIsApplicableChecker() {
        return null;
    }

    public boolean isApplicable(Project project) {
        return true;
    }

    protected boolean isEnabled() {
        return false;
    }

    public final NodeRenderer createRenderer() {
        CompoundReferenceRenderer res = new CompoundReferenceRenderer(getName(), getValueLabelRenderer(), getChildrenRenderer());
        res.setIconRenderer(getIconRenderer());
        res.setFullValueEvaluator(getFullValueEvaluatorProvider());
        String className = getClassName();
        if (!StringUtil.isEmpty(className)) {
            res.setClassName(className);
            res.setIsApplicableChecker(type -> DebuggerUtilsAsync.instanceOf(type, res.getClassName()));
        }
        Function<Type, CompletableFuture<Boolean>> isApplicableChecker = getIsApplicableChecker();
        if (isApplicableChecker != null) {
            res.setIsApplicableChecker(isApplicableChecker);
        }
        res.setEnabled(isEnabled());
        return res;
    }
}
