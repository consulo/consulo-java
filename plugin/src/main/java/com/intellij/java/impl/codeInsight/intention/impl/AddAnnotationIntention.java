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

/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Jul 20, 2007
 * Time: 2:57:38 PM
 */
package com.intellij.java.impl.codeInsight.intention.impl;

import com.intellij.java.analysis.impl.codeInsight.intention.AddAnnotationFix;
import com.intellij.java.analysis.impl.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.codeEditor.Editor;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.language.editor.intention.BaseIntentionAction;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.util.lang.Pair;
import jakarta.annotation.Nonnull;

public abstract class AddAnnotationIntention extends BaseIntentionAction {
  public AddAnnotationIntention() {
    setText(CodeInsightLocalize.intentionAddAnnotationFamily().get());
  }

  @Nonnull
  public abstract Pair<String, String[]> getAnnotations(@Nonnull Project project);

  // include not in project files
  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    final PsiModifierListOwner owner = AddAnnotationPsiFix.getContainer(file, editor.getCaretModel().getOffset());
    if (owner == null || owner.getManager().isInProject(owner) && !CodeStyleSettingsManager.getSettings(project).USE_EXTERNAL_ANNOTATIONS) {
      return false;
    }
    Pair<String, String[]> annotations = getAnnotations(project);
    String toAdd = annotations.first;
    String[] toRemove = annotations.second;
    if (toRemove.length > 0 && AnnotationUtil.isAnnotated(owner, toRemove[0], false, false)) {
      return false;
    }
    setText(AddAnnotationPsiFix.calcText(owner, toAdd));
    if (AnnotationUtil.isAnnotated(owner, toAdd, false, false)) {
      return false;
    }

    if (owner instanceof PsiMethod method) {
      PsiType returnType = method.getReturnType();

      return returnType != null && !(returnType instanceof PsiPrimitiveType);
    }

    return !(owner instanceof PsiClass) || PsiUtil.isLanguageLevel8OrHigher(owner);
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiModifierListOwner owner = AddAnnotationPsiFix.getContainer(file, editor.getCaretModel().getOffset());
    if (owner == null || !owner.isValid()) {
      return;
    }
    Pair<String, String[]> annotations = getAnnotations(project);
    String toAdd = annotations.first;
    String[] toRemove = annotations.second;
    AddAnnotationFix fix = new AddAnnotationFix(toAdd, owner, toRemove);
    fix.invoke(project, editor, file);
  }
}