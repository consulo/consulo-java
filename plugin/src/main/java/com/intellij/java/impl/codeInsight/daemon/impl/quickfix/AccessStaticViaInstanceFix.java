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

import com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis.HighlightMessageUtil;
import com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiExpressionTrimRenderer;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorColors;
import consulo.colorScheme.EditorColorsManager;
import consulo.colorScheme.TextAttributes;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.highlight.HighlightManager;
import consulo.language.editor.inspection.LocalQuickFixAndIntentionActionOnPsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class AccessStaticViaInstanceFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  private static final Logger LOG = Logger.getInstance(AccessStaticViaInstanceFix.class);
  private final boolean myOnTheFly;
  private final String myText;

  public AccessStaticViaInstanceFix(@Nonnull PsiReferenceExpression expression, @Nonnull JavaResolveResult result, boolean onTheFly) {
    super(expression);
    myOnTheFly = onTheFly;
    PsiMember member = (PsiMember)result.getElement();
    myText = calcText(member, result.getSubstitutor());
  }

  @Nonnull
  @Override
  public String getText() {
    return myText;
  }

  private static String calcText(PsiMember member, PsiSubstitutor substitutor) {
    PsiClass aClass = member.getContainingClass();
    if (aClass == null) return "";
    return JavaQuickFixBundle.message("access.static.via.class.reference.text",
                                  HighlightMessageUtil.getSymbolName(member, substitutor),
                                  HighlightUtil.formatClass(aClass),
                                  HighlightUtil.formatClass(aClass, false));
  }

  @Override
  @Nonnull
  public String getFamilyName() {
    return JavaQuickFixBundle.message("access.static.via.class.reference.family");
  }

  @Override
  @RequiredReadAction
  public void invoke(
    @Nonnull Project project,
    @Nonnull PsiFile file,
    @Nullable Editor editor,
    @Nonnull PsiElement startElement,
    @Nonnull PsiElement endElement
  ) {
    final PsiReferenceExpression myExpression = (PsiReferenceExpression)startElement;

    if (!myExpression.isValid()) return;
    if (!FileModificationService.getInstance().prepareFileForWrite(myExpression.getContainingFile())) return;
    PsiElement element = myExpression.resolve();
    if (!(element instanceof PsiMember)) return;
    PsiMember myMember = (PsiMember)element;
    if (!myMember.isValid()) return;

    PsiClass containingClass = myMember.getContainingClass();
    if (containingClass == null) return;
    try {
      final PsiExpression qualifierExpression = myExpression.getQualifierExpression();
      PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
      if (qualifierExpression != null) {
        if (!checkSideEffects(project, containingClass, qualifierExpression, factory, myExpression,editor)) return;
        PsiElement newQualifier = qualifierExpression.replace(factory.createReferenceExpression(containingClass));
        PsiElement qualifiedWithClassName = myExpression.copy();
        newQualifier.delete();
        if (myExpression.resolve() != myMember) {
          myExpression.replace(qualifiedWithClassName);
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @RequiredReadAction
  private boolean checkSideEffects(
    final Project project,
    PsiClass containingClass,
    final PsiExpression qualifierExpression,
    PsiElementFactory factory,
    final PsiElement myExpression,
    Editor editor
  ) {
    final List<PsiElement> sideEffects = new ArrayList<>();
    boolean hasSideEffects = RemoveUnusedVariableUtil.checkSideEffects(qualifierExpression, null, sideEffects);
    if (hasSideEffects && !myOnTheFly) return false;
    if (!hasSideEffects || project.getApplication().isUnitTestMode()) {
      return true;
    }
    if (editor == null) {
      return false;
    }
    HighlightManager.getInstance(project).addOccurrenceHighlights(editor, PsiUtilCore.toPsiElementArray(sideEffects), EditorColors.SEARCH_RESULT_ATTRIBUTES, true, null);
    try {
      hasSideEffects = PsiUtil.isStatement(factory.createStatementFromText(qualifierExpression.getText(), qualifierExpression));
    }
    catch (IncorrectOperationException e) {
      hasSideEffects = false;
    }
    final PsiReferenceExpression qualifiedWithClassName = (PsiReferenceExpression)myExpression.copy();
    qualifiedWithClassName.setQualifierExpression(factory.createReferenceExpression(containingClass));
    final boolean canCopeWithSideEffects = hasSideEffects;
    final SideEffectWarningDialog dialog =
      new SideEffectWarningDialog(
        project,
        false,
        null,
        sideEffects.get(0).getText(),
        PsiExpressionTrimRenderer.render(qualifierExpression),
        canCopeWithSideEffects
      ) {
        @Override
        @RequiredReadAction
        protected String sideEffectsDescription() {
          if (canCopeWithSideEffects) {
            return "<html><body>" +
              "  There are possible side effects found in expression '" +
              qualifierExpression.getText() +
              "'<br>" +
              "  You can:<ul><li><b>Remove</b> class reference along with whole expressions involved, or</li>" +
              "  <li><b>Transform</b> qualified expression into the statement on its own.<br>" +
              "  That is,<br>" +
              "  <table border=1><tr><td><code>" +
              myExpression.getText() +
              "</code></td></tr></table><br> becomes: <br>" +
              "  <table border=1><tr><td><code>" +
              qualifierExpression.getText() +
              ";<br>" +
              qualifiedWithClassName.getText() +
              "       </code></td></tr></table></li>" +
              "  </body></html>";
          }
          return "<html><body>  There are possible side effects found in expression '" + qualifierExpression.getText() + "'<br>" +
            "You can:<ul><li><b>Remove</b> class reference along with whole expressions involved, or</li></body></html>";
        }
      };
    dialog.show();
    int res = dialog.getExitCode();
    if (res == RemoveUnusedVariableUtil.CANCEL) return false;
    try {
      if (res == RemoveUnusedVariableUtil.MAKE_STATEMENT) {
        final PsiStatement statementFromText = factory.createStatementFromText(qualifierExpression.getText() + ";", null);
        final PsiStatement statement = PsiTreeUtil.getParentOfType(myExpression, PsiStatement.class);
        statement.getParent().addBefore(statementFromText, statement);
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return true;
  }
}
