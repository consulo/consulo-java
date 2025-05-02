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
package com.intellij.java.language.jvm.facade;

import com.intellij.java.language.jvm.JvmClass;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.language.psi.scope.GlobalSearchScope;
import jakarta.annotation.Nonnull;

import java.util.List;

@ExtensionAPI(ComponentScope.PROJECT)
public interface JvmElementProvider {
    ExtensionPointName<JvmElementProvider> EP_NAME = ExtensionPointName.create(JvmElementProvider.class);

    @Nonnull
    List<? extends JvmClass> getClasses(@Nonnull String qualifiedName, @Nonnull GlobalSearchScope scope);
}
