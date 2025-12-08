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
package com.intellij.java.impl.refactoring.util;

import consulo.annotation.access.RequiredReadAction;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.usage.UsageInfo;
import consulo.language.util.IncorrectOperationException;
import jakarta.annotation.Nullable;


@SuppressWarnings({"AbstractClassExtendsConcreteClass"})
public abstract class FixableUsageInfo extends UsageInfo {
    @RequiredReadAction
    public FixableUsageInfo(PsiElement element) {
        super(element);
    }

    public abstract void fixUsage() throws IncorrectOperationException;

    @Nullable
    public LocalizeValue getConflictMessage() {
        return LocalizeValue.empty();
    }
}
