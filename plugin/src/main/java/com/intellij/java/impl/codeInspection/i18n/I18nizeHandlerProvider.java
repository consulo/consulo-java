/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInspection.i18n;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.codeEditor.Editor;
import consulo.component.extension.ExtensionPointName;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiFile;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author sergey.evdokimov
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class I18nizeHandlerProvider {

  public static final ExtensionPointName<I18nizeHandlerProvider> EP_NAME = ExtensionPointName.create(I18nizeHandlerProvider.class);

  @Nullable
  public abstract I18nQuickFixHandler getHandler(@Nonnull final PsiFile psiFile, @Nonnull final Editor editor, @Nonnull TextRange range);
}
