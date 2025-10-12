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
package com.intellij.java.impl.codeInsight.intention.impl;

import com.intellij.java.language.psi.PsiMethod;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.localize.LocalizeValue;
import consulo.project.Project;

/**
 * @author yole
 */
@ExtensionImpl
@IntentionMetaData(ignoreId = "java.CopyAbstractMethodImplementationAction", categories = {"Java", "Declaration"}, fileExtensions = "java")
public class CopyAbstractMethodImplementationAction extends ImplementAbstractMethodAction {
  public CopyAbstractMethodImplementationAction() {
    setText(LocalizeValue.localizeTODO("Copy Abstract Method Implementation"));
  }

  @Override
  protected LocalizeValue getIntentionName(final PsiMethod method) {
    return CodeInsightLocalize.copyAbstractMethodIntentionName(method.getName());
  }

  @Override
  protected boolean isAvailable(final MyElementProcessor processor) {
    return processor.hasMissingImplementations() && processor.hasExistingImplementations();
  }

  @Override
  protected void invokeHandler(final Project project, final Editor editor, final PsiMethod method) {
    new CopyAbstractMethodImplementationHandler(project, editor, method).invoke();
  }
}
