/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.siyeh.ig.psiutils.CommentTracker;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.editor.intention.PsiElementBaseIntentionAction;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.psi.PsiCompiledElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.util.lang.ref.Ref;
import jakarta.annotation.Nonnull;

/**
 * @author max
 */
@ExtensionImpl
@IntentionMetaData(ignoreId = "java.SplitDeclarationAction", categories = {"Java", "Declaration"}, fileExtensions = "java")
public class SplitDeclarationAction extends PsiElementBaseIntentionAction {
  public SplitDeclarationAction() {
    setText(CodeInsightLocalize.intentionSplitDeclarationFamily());
  }

  @Override
  @RequiredReadAction
  public boolean isAvailable(@Nonnull Project project, Editor editor, @Nonnull PsiElement element) {
    if (element instanceof PsiCompiledElement
      || !element.getManager().isInProject(element)
      || !element.getLanguage().isKindOf(JavaLanguage.INSTANCE)) {
      return false;
    }

    PsiElement context = PsiTreeUtil.getParentOfType(element, PsiDeclarationStatement.class, PsiClass.class);
    if (context instanceof PsiDeclarationStatement declarationStatement) {
      return isAvailableOnDeclarationStatement(declarationStatement, element);
    }

    PsiField field = PsiTreeUtil.getParentOfType(element, PsiField.class);
    if (field != null && PsiTreeUtil.getParentOfType(element, PsiDocComment.class) == null && isAvailableOnField(field)) {
      setText(CodeInsightLocalize.intentionSplitDeclarationText());
      return true;
    }
    return false;
  }

  @RequiredReadAction
  private static boolean isAvailableOnField(PsiField field) {
    PsiTypeElement typeElement = field.getTypeElement();
    if (typeElement == null) {
      return false;
    }
    if (PsiTreeUtil.getParentOfType(typeElement, PsiField.class) != field) {
      return true;
    }

    PsiElement nextField = field.getNextSibling();
    while (nextField != null && !(nextField instanceof PsiField)) {
      nextField = nextField.getNextSibling();
    }

    return nextField != null && ((PsiField)nextField).getTypeElement() == typeElement;
  }

  @RequiredReadAction
  private boolean isAvailableOnDeclarationStatement(PsiDeclarationStatement decl, PsiElement element) {
    PsiElement[] declaredElements = decl.getDeclaredElements();
    if (declaredElements.length == 0 || !(declaredElements[0] instanceof PsiLocalVariable)) {
      return false;
    }
    if (declaredElements.length == 1) {
      PsiLocalVariable var = (PsiLocalVariable)declaredElements[0];
      if (var.getInitializer() == null || var.getTypeElement().isInferredType()) {
        return false;
      }
      PsiElement parent = decl.getParent();
      if (parent instanceof PsiForStatement) {
        String varName = var.getName();
        if (varName == null) {
          return false;
        }

        parent = parent.getNextSibling();
        while (parent != null) {
          Ref<Boolean> conflictFound = new Ref<>(false);
          parent.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitClass(@Nonnull PsiClass aClass) {
            }

            @Override
            @RequiredReadAction
            public void visitVariable(@Nonnull PsiVariable variable) {
              super.visitVariable(variable);
              if (varName.equals(variable.getName())) {
                conflictFound.set(true);
                stopWalking();
              }
            }
          });
          if (conflictFound.get()) {
            return false;
          }
          parent = parent.getNextSibling();
        }
      }
      setText(CodeInsightLocalize.intentionSplitDeclarationAssignmentText());
      return true;
    }
    else {
      if (decl.getParent() instanceof PsiForStatement) {
        return false;
      }

      setText(CodeInsightLocalize.intentionSplitDeclarationText());
      return true;
    }
  }

  @Override
  @RequiredReadAction
  public void invoke(@Nonnull Project project, Editor editor, @Nonnull PsiElement element) throws IncorrectOperationException {
    PsiDeclarationStatement decl = PsiTreeUtil.getParentOfType(element, PsiDeclarationStatement.class);

    PsiManager psiManager = PsiManager.getInstance(project);
    if (decl != null) {
      invokeOnDeclarationStatement(decl, psiManager, project);
    }
    else {
      PsiField field = PsiTreeUtil.getParentOfType(element, PsiField.class);
      if (field != null) {
        field.normalizeDeclaration();
      }
    }
  }

  @RequiredReadAction
  public static PsiAssignmentExpression invokeOnDeclarationStatement(
    PsiDeclarationStatement decl,
    PsiManager psiManager,
    Project project
  ) throws IncorrectOperationException {
    if (decl.getDeclaredElements().length == 1) {
      PsiLocalVariable var = (PsiLocalVariable)decl.getDeclaredElements()[0];
      var.normalizeDeclaration();
      PsiExpressionStatement statement = (PsiExpressionStatement)JavaPsiFacade.getInstance(psiManager.getProject())
        .getElementFactory()
        .createStatementFromText(var.getName() + "=xxx;", null);
      statement = (PsiExpressionStatement)CodeStyleManager.getInstance(project).reformat(statement);
      PsiAssignmentExpression assignment = (PsiAssignmentExpression)statement.getExpression();
      CommentTracker commentTracker = new CommentTracker();
      PsiExpression initializer = var.getInitializer();
      PsiExpression rExpression = RefactoringUtil.convertInitializerToNormalExpression(initializer, var.getType());

      commentTracker.replace(assignment.getRExpression(), rExpression);
      commentTracker.deleteAndRestoreComments(initializer);

      PsiElement block = decl.getParent();
      if (block instanceof PsiForStatement) {
        PsiDeclarationStatement varDeclStatement = JavaPsiFacade.getInstance(psiManager.getProject())
          .getElementFactory()
          .createVariableDeclarationStatement(var.getName(), var.getType(), null);

        // For index can't be final, right?
        for (PsiElement varDecl : varDeclStatement.getDeclaredElements()) {
          if (varDecl instanceof PsiModifierListOwner modifierListOwner) {
            PsiModifierList modList = modifierListOwner.getModifierList();
            assert modList != null;
            modList.setModifierProperty(PsiModifier.FINAL, false);
          }
        }

        PsiElement parent = block.getParent();
        PsiExpressionStatement replaced = (PsiExpressionStatement)decl.replace(statement);
        if (!(parent instanceof PsiCodeBlock)) {
          PsiBlockStatement blockStatement =
            (PsiBlockStatement)JavaPsiFacade.getElementFactory(project).createStatementFromText("{}", null);
          PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
          codeBlock.add(varDeclStatement);
          codeBlock.add(block);
          block.replace(blockStatement);
        }
        else {
          parent.addBefore(varDeclStatement, block);
        }
        return (PsiAssignmentExpression)replaced.getExpression();
      }
      else {
        return (PsiAssignmentExpression)((PsiExpressionStatement)block.addAfter(statement, decl)).getExpression();
      }
    }
    else {
      ((PsiLocalVariable)decl.getDeclaredElements()[0]).normalizeDeclaration();
    }
    return null;
  }
}
