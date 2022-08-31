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
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import javax.annotation.Nonnull;

import consulo.java.analysis.impl.JavaQuickFixBundle;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.PsiFile;

/**
 * @author mike
 *         Date: Aug 20, 2002
 */
public class SetupJDKFix implements IntentionAction, HighPriorityAction {
  private static final SetupJDKFix ourInstance = new SetupJDKFix();

  public static SetupJDKFix getInstance() {
    return ourInstance;
  }

  private SetupJDKFix() {
  }

  @Override
  @Nonnull
  public String getText() {
    return JavaQuickFixBundle.message("setup.jdk.location.text");
  }

  @Override
  @Nonnull
  public String getFamilyName() {
    return JavaQuickFixBundle.message("setup.jdk.location.family");
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
