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

import com.intellij.java.analysis.impl.codeInsight.intention.AddAnnotationFix;
import com.intellij.java.language.psi.CommonClassNames;
import com.intellij.java.language.psi.PsiCodeBlock;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

/**
 * @author ven
 */
@ExtensionImpl
@IntentionMetaData(ignoreId = "java.AddOverrideAnnotationAction", categories = {"Java", "Declaration"}, fileExtensions = "java")
public class AddOverrideAnnotationAction implements IntentionAction {
  @Override
  @Nonnull
  public LocalizeValue getText() {
    return CodeInsightLocalize.intentionAddOverrideAnnotation();
  }

  @Override
  @RequiredReadAction
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    if (!PsiUtil.isLanguageLevel5OrHigher(file)) return false;
    if (!file.getManager().isInProject(file)) return false;
    PsiMethod method = findMethod(file, editor.getCaretModel().getOffset());
    if (method == null) return false;
    if (method.getModifierList().findAnnotation(CommonClassNames.JAVA_LANG_OVERRIDE) != null) return false;
    PsiMethod[] superMethods = method.findSuperMethods();
    for (PsiMethod superMethod : superMethods) {
      if (!superMethod.isAbstract()
          && new AddAnnotationFix(CommonClassNames.JAVA_LANG_OVERRIDE, method).isAvailable(project, editor, file)) {
        return true;
      }
    }

    return false;
  }

  @Override
  @RequiredReadAction
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiMethod method = findMethod(file, editor.getCaretModel().getOffset());
    if (method != null) {
      new AddAnnotationFix(CommonClassNames.JAVA_LANG_OVERRIDE, method).invoke(project, editor, file);
    }
  }

  @RequiredReadAction
  private static PsiMethod findMethod(PsiFile file, int offset) {
    PsiElement element = file.findElementAt(offset);
    PsiMethod res = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
    if (res == null) return null;

    //Not available in method's body
    PsiCodeBlock body = res.getBody();
    if (body == null) return null;
    TextRange textRange = body.getTextRange();
    if (textRange == null || textRange.getStartOffset() <= offset) return null;

    return res;
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
