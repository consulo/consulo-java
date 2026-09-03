// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.engine;

import com.intellij.java.debugger.impl.memory.utils.StackFrameItem;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import org.jspecify.annotations.Nullable;

import java.util.List;

@ExtensionAPI(ComponentScope.APPLICATION)
public interface CreationStackTraceProvider {
    ExtensionPointName<CreationStackTraceProvider> EP = ExtensionPointName.create(CreationStackTraceProvider.class);

    @Nullable List<@Nullable StackFrameItem> getCreationStackTrace(JavaStackFrame stackFrame, SuspendContextImpl suspendContext);
}
