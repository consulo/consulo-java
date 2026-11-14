// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.ui.tree.render;

import consulo.execution.debug.frame.XValueNode;

/**
 * Sets the presentation of a not yet calculated on-demand value node.
 *
 * @see com.intellij.java.debugger.impl.ui.impl.watch.ValueDescriptorImpl#setOnDemandPresentationProvider
 */
@FunctionalInterface
public interface OnDemandPresentationProvider {
    void setPresentation(XValueNode node);
}
