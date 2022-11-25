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

import com.intellij.java.impl.codeInsight.intention.impl.TypeExpression;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.application.ApplicationManager;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.ide.impl.idea.codeInsight.CodeInsightUtilBase;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.language.editor.impl.internal.template.TemplateBuilderImpl;
import consulo.language.editor.template.Template;
import consulo.language.editor.template.event.TemplateEditingAdapter;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.logging.Logger;
import consulo.project.Project;

import javax.annotation.Nonnull;

/**
 * @author Mike
 */
public class CreateLocalFromUsageFix extends CreateVarFromUsageFix {

  public CreateLocalFromUsageFix(PsiReferenceExpression referenceExpression) {
    super(referenceExpression);
  }

  private static final Logger LOG = Logger.getInstance(CreateLocalFromUsageFix.class);

  @Override
  public String getText(String varName) {
    return JavaQuickFixBundle.message("create.local.from.usage.text", varName);
  }

  @Override
  protected boolean isAvailableImpl(int offset) {
    if (!super.isAvailableImpl(offset)) return false;
    if(myReferenceExpression.isQualified()) return false;
    PsiElement scope = PsiTreeUtil.getParentOfType(myReferenceExpression, PsiModifierListOwner.class);
    return scope instanceof PsiMethod || scope instanceof PsiClassInitializer ||
           scope instanceof PsiLocalVariable || scope instanceof PsiAnonymousClass;
  }

  @Override
  protected void invokeImpl(final PsiClass targetClass) {
    if (CreateFromUsageUtils.isValidReference(myReferenceExpression, false)) {
      return;
    }

    final Project project = myReferenceExpression.getProject();
    PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();

    final PsiFile targetFile = targetClass.getContainingFile();

    PsiType[] expectedTypes = CreateFromUsageUtils.guessType(myReferenceExpression, false);
    PsiType type = expectedTypes[0];

    String varName = myReferenceExpression.getReferenceName();
    PsiExpression initializer = null;
    boolean isInline = false;
    PsiExpression[] expressions = CreateFromUsageUtils.collectExpressions(myReferenceExpression, PsiMember.class, PsiFile.class);
    PsiStatement anchor = getAnchor(expressions);
    if (anchor instanceof PsiExpressionStatement &&
        ((PsiExpressionStatement)anchor).getExpression() instanceof PsiAssignmentExpression) {
      PsiAssignmentExpression assignment = (PsiAssignmentExpression)((PsiExpressionStatement)anchor).getExpression();
      if (assignment.getLExpression().textMatches(myReferenceExpression)) {
        initializer = assignment.getRExpression();
        isInline = true;
      }
    }

    PsiDeclarationStatement decl = factory.createVariableDeclarationStatement(varName, type, initializer);

    TypeExpression expression = new TypeExpression(project, expectedTypes);

    if (isInline) {
      final PsiExpression expr = ((PsiExpressionStatement)anchor).getExpression();
      final PsiElement semicolon = expr.getNextSibling();
      if (semicolon != null) {
        final PsiElement nextSibling = semicolon.getNextSibling();
        if (nextSibling != null) {
          decl.addRange(nextSibling, anchor.getLastChild());
        }
      }
      decl = (PsiDeclarationStatement)anchor.replace(decl);
    }
    else {
      decl = (PsiDeclarationStatement)anchor.getParent().addBefore(decl, anchor);
    }

    PsiVariable var = (PsiVariable)decl.getDeclaredElements()[0];
    boolean isFinal =
      CodeStyleSettingsManager.getSettings(project).GENERATE_FINAL_LOCALS && !CreateFromUsageUtils.isAccessedForWriting(expressions);
    PsiUtil.setModifierProperty(var, PsiModifier.FINAL, isFinal);

    var = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(var);
    if (var == null) return;
    TemplateBuilderImpl builder = new TemplateBuilderImpl(var);
    builder.replaceElement(var.getTypeElement(), expression);
    builder.setEndVariableAfter(var.getNameIdentifier());
    Template template = builder.buildTemplate();

    final Editor newEditor = positionCursor(project, targetFile, var);
    if (newEditor == null) return;
    TextRange range = var.getTextRange();
    newEditor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());

    startTemplate(newEditor, template, project, new TemplateEditingAdapter() {
      @Override
      public void templateFinished(Template template, boolean brokenOff) {
        PsiDocumentManager.getInstance(project).commitDocument(newEditor.getDocument());
        final int offset = newEditor.getCaretModel().getOffset();
        final PsiLocalVariable localVariable = PsiTreeUtil.findElementOfClassAtOffset(targetFile, offset, PsiLocalVariable.class, false);
        if (localVariable != null) {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              CodeStyleManager.getInstance(project).reformat(localVariable);
            }
          });
        }
      }
    });
  }

  @Override
  protected boolean isAllowOuterTargetClass() {
    return false;
  }

  private static PsiStatement getAnchor(PsiExpression[] expressionOccurences) {
    PsiElement parent = expressionOccurences[0];
    int minOffset = expressionOccurences[0].getTextRange().getStartOffset();
    for (int i = 1; i < expressionOccurences.length; i++) {
      parent = PsiTreeUtil.findCommonParent(parent, expressionOccurences[i]);
      LOG.assertTrue(parent != null);
      minOffset = Math.min(minOffset, expressionOccurences[i].getTextRange().getStartOffset());
    }

    final PsiCodeBlock block = PsiTreeUtil.getParentOfType(parent, PsiCodeBlock.class, false);
    LOG.assertTrue(block != null && block.getStatements().length > 0, "block: " + block +"; parent: " + parent);
    PsiStatement[] statements = block.getStatements();
    for (int i = 1; i < statements.length; i++) {
      if (statements[i].getTextRange().getStartOffset() > minOffset) return statements[i-1];
    }
    return statements[statements.length - 1];
  }

  @Override
  @Nonnull
  public String getFamilyName() {
    return JavaQuickFixBundle.message("create.local.from.usage.family");
  }

}
