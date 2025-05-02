/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.java.execution;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.module.Module;

import jakarta.annotation.Nonnull;

/**
 * User: anna
 * Date: Mar 4, 2005
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface JavaTestPatcher {
    ExtensionPointName<JavaTestPatcher> EP_NAME = ExtensionPointName.create(JavaTestPatcher.class);

    void patchJavaParameters(@Nonnull Module module, @Nonnull OwnJavaParameters javaParameters);
}
