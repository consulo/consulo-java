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
package com.intellij.java.impl.refactoring.inline;

import consulo.annotation.component.ExtensionImpl;
import consulo.application.Result;
import consulo.language.editor.WriteCommandAction;
import consulo.codeEditor.Editor;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiImportStaticStatement;
import com.intellij.java.language.psi.PsiJavaCodeReferenceElement;
import consulo.language.psi.util.PsiTreeUtil;

import java.util.List;

import static com.intellij.java.language.impl.psi.util.ImportsUtil.collectReferencesThrough;
import static com.intellij.java.language.impl.psi.util.ImportsUtil.replaceAllAndDeleteImport;

/**
 * User: anna
 * Date: 9/1/11
 */
@ExtensionImpl
public class InlineStaticImportHandler extends JavaInlineActionHandler {

  private static final String REFACTORING_NAME = "Expand static import";

  @Override
  public boolean canInlineElement(PsiElement element) {
    if (element.getContainingFile() == null) return false;
    return PsiTreeUtil.getParentOfType(element, PsiImportStaticStatement.class) != null;
  }

  @Override
  public void inlineElement(Project project, Editor editor, PsiElement element) {
    final PsiImportStaticStatement staticStatement = PsiTreeUtil.getParentOfType(element, PsiImportStaticStatement.class);
    final List<PsiJavaCodeReferenceElement> referenceElements =
      collectReferencesThrough(element.getContainingFile(), null, staticStatement);
    new WriteCommandAction(project, REFACTORING_NAME){
      @Override
      protected void run(Result result) throws Throwable {
        replaceAllAndDeleteImport(referenceElements, null, staticStatement);
      }
    }.execute();
  }
}
