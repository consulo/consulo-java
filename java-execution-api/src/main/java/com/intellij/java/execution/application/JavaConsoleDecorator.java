// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.execution.application;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.application.Application;
import consulo.execution.configuration.RunConfigurationBase;
import consulo.execution.executor.Executor;
import consulo.execution.ui.console.ConsoleView;

/**
 * Wraps the console of a Java run configuration (application, tests) before the process is attached to it.
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class JavaConsoleDecorator {
    public static ConsoleView decorate(ConsoleView console, RunConfigurationBase runConfiguration, Executor executor) {
        ConsoleView result = console;
        for (JavaConsoleDecorator decorator : Application.get().getExtensionList(JavaConsoleDecorator.class)) {
            result = decorator.decorateConsole(result, runConfiguration, executor);
        }
        return result;
    }

    protected abstract ConsoleView decorateConsole(ConsoleView console, RunConfigurationBase runConfiguration, Executor executor);
}
