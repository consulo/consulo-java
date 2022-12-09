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
package com.intellij.java.impl.codeInspection.i18n.folding;

import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiJavaToken;
import com.intellij.java.language.psi.PsiLiteralExpression;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import consulo.codeEditor.Editor;
import consulo.codeEditor.FoldRegion;
import consulo.language.editor.navigation.GotoDeclarationHandlerBase;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;

import javax.annotation.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public class I18nMessageGotoDeclarationHandler extends GotoDeclarationHandlerBase {

  @Override
  public PsiElement getGotoDeclarationTarget(PsiElement element, Editor editor) {
    if (!(element instanceof PsiJavaToken)) return null;
    FoldRegion region = editor.getFoldingModel().getCollapsedRegionAtOffset(element.getTextRange().getStartOffset());
    if (region == null) return null;

    PsiElement editableElement = EditPropertyValueAction.getEditableElement(region);
    //case: "literalAnnotatedWithPropertyKey"
    if (editableElement instanceof PsiLiteralExpression) {
      return resolve(editableElement);
    }

    //case: MyBundle.message("literalAnnotatedWithPropertyKey", param1, param2)
    if (editableElement instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression methodCall = (PsiMethodCallExpression)editableElement;
      for (PsiExpression expression : methodCall.getArgumentList().getExpressions()) {
        if (expression instanceof PsiLiteralExpression && PropertyFoldingBuilder.isI18nProperty((PsiLiteralExpression)expression)) {
          return resolve(expression);
        }
      }
    }

    return null;
  }

  @Nullable
  private static PsiElement resolve(PsiElement element) {
    if (element == null) return null;
    final PsiReference[] references = element.getReferences();
    return references.length == 0 ? null : references[0].resolve();
  }
}
