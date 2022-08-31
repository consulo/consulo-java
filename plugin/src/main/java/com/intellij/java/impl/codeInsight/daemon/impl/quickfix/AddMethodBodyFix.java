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
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import javax.annotation.Nonnull;

import consulo.java.analysis.impl.JavaQuickFixBundle;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import consulo.logging.Logger;

public class AddMethodBodyFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance(AddMethodBodyFix.class);

  private final PsiMethod myMethod;

  public AddMethodBodyFix(PsiMethod method) {
    myMethod = method;
  }

  @Override
  @Nonnull
  public String getText() {
    return JavaQuickFixBundle.message("add.method.body.text");
  }

  @Override
  @Nonnull
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    return myMethod != null
        && myMethod.isValid()
        && myMethod.getBody() == null
        && myMethod.getContainingClass() != null
        && myMethod.getManager().isInProject(myMethod);
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) {
    if (!FileModificationService.getInstance().prepareFileForWrite(myMethod.getContainingFile())) return;

    try {
      PsiUtil.setModifierProperty(myMethod, PsiModifier.ABSTRACT, false);
      CreateFromUsageUtils.setupMethodBody(myMethod);
      CreateFromUsageUtils.setupEditor(myMethod, editor);
    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

}
