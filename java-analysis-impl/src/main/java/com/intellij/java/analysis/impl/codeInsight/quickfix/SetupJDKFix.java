/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.java.analysis.impl.codeInsight.quickfix;

import consulo.application.ApplicationManager;
import consulo.codeEditor.Editor;
import consulo.content.bundle.Sdk;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.language.editor.intention.HighPriorityAction;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.psi.PsiFile;
import consulo.language.util.ModuleUtilCore;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

/**
 * @author mike
 *         Date: Aug 20, 2002
 */
public class SetupJDKFix implements IntentionAction, HighPriorityAction, SyntheticIntentionAction {
  private static final SetupJDKFix ourInstance = new SetupJDKFix();

  public static SetupJDKFix getInstance() {
    return ourInstance;
  }

  private SetupJDKFix() {
  }

  @Override
  @Nonnull
  public LocalizeValue getText() {
    return JavaQuickFixLocalize.setupJdkLocationText();
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    return false;
    //return JavaPsiFacade.getInstance(project).findClass(CommonClassNames.JAVA_LANG_OBJECT, file.getResolveScope()) == null;
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, final PsiFile file) {
    Sdk projectJdk = null;
    if (projectJdk == null) return;
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        Module module = ModuleUtilCore.findModuleForPsiElement(file);
        if (module != null) {
          //TODO [VISTALL] ModuleRootModificationUtil.setSdkInherited(module);
        }
      }
    });
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
