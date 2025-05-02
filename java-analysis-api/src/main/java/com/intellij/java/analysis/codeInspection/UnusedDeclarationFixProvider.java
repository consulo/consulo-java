/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.java.analysis.codeInspection;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;

/**
 * Provides quick fixes for "Unused declaration" inspection
 *
 * @author Dmitry Avdeev
 * Date: 1/19/12
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface UnusedDeclarationFixProvider {
    ExtensionPointName<UnusedDeclarationFixProvider> EP_NAME = ExtensionPointName.create(UnusedDeclarationFixProvider.class);

    @Nonnull
    IntentionAction[] getQuickFixes(PsiElement unusedElement);
}