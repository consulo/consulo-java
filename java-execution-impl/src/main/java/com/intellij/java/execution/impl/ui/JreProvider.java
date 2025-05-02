/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.execution.impl.ui;

import consulo.annotation.DeprecationInfo;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;

import jakarta.annotation.Nonnull;

/**
 * Extension point for providing custom jre to be shown at run configuration control.
 *
 * @author Denis Zhdanov
 * @since 5/9/13 10:04 PM
 */
@Deprecated
@DeprecationInfo("Unused")
@ExtensionAPI(ComponentScope.APPLICATION)
public interface JreProvider {
    ExtensionPointName<JreProvider> EP_NAME = ExtensionPointName.create(JreProvider.class);

    @Nonnull
    String getJrePath();
}
