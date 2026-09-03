// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.engine;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.process.cmd.ParametersList;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Properties;

@ExtensionAPI(ComponentScope.APPLICATION)
public interface DebuggerAgentParametersModifier {
    ExtensionPointName<DebuggerAgentParametersModifier> EP = ExtensionPointName.create(DebuggerAgentParametersModifier.class);

    default void modifyParameters(ParametersList parametersList, @Nullable Project project) {
    }

    default void modifyProperties(Properties properties, @Nullable Project project) {
    }

    static List<DebuggerAgentParametersModifier> getAgentModifiers() {
        return EP.getExtensionList();
    }
}
