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
package com.intellij.java.impl.codeInsight.generation.actions;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.codeEditor.Editor;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.action.CodeInsightActionHandler;
import consulo.language.editor.util.PsiUtilBase;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;

import jakarta.annotation.Nonnull;

/**
 * @author Konstantin Bulenkov
 */
public class GenerateCreateUIHandler implements CodeInsightActionHandler {
  @Override
  public void invoke(@Nonnull Project project, @Nonnull Editor editor, @Nonnull PsiFile file) {
    final PsiElement element = PsiUtilBase.getElementAtCaret(editor);
    final PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    if (psiClass == null) return;

    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    String annotation = "";
    if (PsiUtil.getLanguageLevel(file).isAtLeast(LanguageLevel.JDK_1_5)) {
      annotation = "@SuppressWarnings({\"MethodOverridesStaticMethodOfSuperclass\", \"UnusedDeclaration\"})";
    }
    final PsiMethod createUI = factory.createMethodFromText(annotation +
                                                            "\npublic static javax.swing.plaf.ComponentUI createUI(javax.swing.JComponent c) {" +
                                                        "\n  return new " + psiClass.getName() + "();\n}", psiClass);
    final PsiMethod newMethod = (PsiMethod)psiClass.add(CodeStyleManager.getInstance(project).reformat(createUI));
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(newMethod);
    final PsiReturnStatement returnStatement = PsiTreeUtil.findChildOfType(newMethod, PsiReturnStatement.class);
    if (returnStatement != null) {
      final int offset = returnStatement.getTextRange().getEndOffset();
      editor.getCaretModel().moveToOffset(offset - 2);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
