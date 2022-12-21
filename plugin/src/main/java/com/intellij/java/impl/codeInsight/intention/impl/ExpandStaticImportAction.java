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
package com.intellij.java.impl.codeInsight.intention.impl;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiIdentifier;
import com.intellij.java.language.psi.PsiImportStaticStatement;
import com.intellij.java.language.psi.PsiJavaCodeReferenceElement;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.ApplicationManager;
import consulo.application.Result;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorPopupHelper;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.editor.intention.PsiElementBaseIntentionAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.ex.popup.BaseListPopupStep;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.ui.ex.popup.ListPopup;
import consulo.ui.ex.popup.PopupStep;

import javax.annotation.Nonnull;
import java.util.List;

import static com.intellij.java.language.impl.psi.util.ImportsUtil.*;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ExpandStaticImportAction", categories = {"Java", "Imports"}, fileExtensions = "java")
public class ExpandStaticImportAction extends PsiElementBaseIntentionAction {
  private static final Logger LOG = Logger.getInstance(ExpandStaticImportAction.class);
  private static final String REPLACE_THIS_OCCURRENCE = "Replace this occurrence and keep the method";
  private static final String REPLACE_ALL_AND_DELETE_IMPORT = "Replace all and delete the import";

  public ExpandStaticImportAction() {
    setText("Expand Static Import");
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, @Nonnull PsiElement element) {
    if (!PsiUtil.isLanguageLevel5OrHigher(element)) return false;
    final PsiElement parent = element.getParent();
    if (!(element instanceof PsiIdentifier) || !(parent instanceof PsiJavaCodeReferenceElement)) {
      return false;
    }
    final PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)parent;
    final PsiElement resolveScope = referenceElement.advancedResolve(true).getCurrentFileResolveScope();
    if (resolveScope instanceof PsiImportStaticStatement) {
      final PsiClass targetClass = ((PsiImportStaticStatement)resolveScope).resolveTargetClass();
      if (targetClass == null) return false;
      setText("Expand static import to " + targetClass.getName() + "." + referenceElement.getReferenceName());
      return true;
    }
    return false;
  }

  public void invoke(final Project project, final PsiFile file, final Editor editor, PsiElement element) {
    if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return;

    final PsiJavaCodeReferenceElement refExpr = (PsiJavaCodeReferenceElement)element.getParent();
    final PsiImportStaticStatement staticImport = (PsiImportStaticStatement)refExpr.advancedResolve(true).getCurrentFileResolveScope();
    final List<PsiJavaCodeReferenceElement> expressionToExpand = collectReferencesThrough(file, refExpr, staticImport);

    if (expressionToExpand.isEmpty()) {
      expand(refExpr, staticImport);
      staticImport.delete();
    }
    else {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        replaceAllAndDeleteImport(expressionToExpand, refExpr, staticImport);
      }
      else {
        final BaseListPopupStep<String> step =
          new BaseListPopupStep<String>("Multiple Similar Calls Found",
                                        new String[]{REPLACE_THIS_OCCURRENCE, REPLACE_ALL_AND_DELETE_IMPORT}) {
            @Override
            public PopupStep onChosen(final String selectedValue, boolean finalChoice) {
              new WriteCommandAction(project, ExpandStaticImportAction.this.getText()) {
                @Override
                protected void run(Result result) throws Throwable {
                  if (selectedValue == REPLACE_THIS_OCCURRENCE) {
                    expand(refExpr, staticImport);
                  }
                  else {
                    replaceAllAndDeleteImport(expressionToExpand, refExpr, staticImport);
                  }
                }
              }.execute();
              return FINAL_CHOICE;
            }
          };
        ListPopup popup = JBPopupFactory.getInstance().createListPopup(step);

        EditorPopupHelper.getInstance().showPopupInBestPositionFor(editor, popup);
      }
    }
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, @Nonnull PsiElement element) throws IncorrectOperationException {
    invoke(project, element.getContainingFile(), editor, element);
  }
}
