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

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorColors;
import consulo.colorScheme.EditorColorsManager;
import consulo.colorScheme.TextAttributes;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.PsiEquivalenceUtil;
import consulo.language.editor.highlight.HighlightManager;
import consulo.language.editor.intention.BaseIntentionAction;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.psi.PsiCompiledElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.util.lang.ref.Ref;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author cdr
 */
@ExtensionImpl
@IntentionMetaData(ignoreId = "java.MoveFieldAssignmentToInitializerAction", categories = {"Java", "Declaration"}, fileExtensions = "java")
public class MoveFieldAssignmentToInitializerAction extends BaseIntentionAction {
  @Override
  @Nonnull
  public LocalizeValue getText() {
    return CodeInsightLocalize.intentionMoveFieldAssignmentToDeclaration();
  }

  @Override
  @RequiredReadAction
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    PsiAssignmentExpression assignment = getAssignmentUnderCaret(editor, file);
    if (assignment == null) {
      return false;
    }
    PsiField field = getAssignedField(assignment);
    if (field == null || field.hasInitializer()) {
      return false;
    }
    PsiClass psiClass = field.getContainingClass();

    if (psiClass == null || psiClass.isInterface() || psiClass.getContainingFile() != file) {
      return false;
    }
    PsiModifierListOwner ctrOrInitializer = enclosingMethodOrClassInitializer(assignment, field);
    if (ctrOrInitializer == null
      || ctrOrInitializer.hasModifierProperty(PsiModifier.STATIC) != field.hasModifierProperty(PsiModifier.STATIC)) {
      return false;
    }
    return isValidAsFieldInitializer(assignment.getRExpression(), ctrOrInitializer)
      && isInitializedWithSameExpression(field, assignment, new ArrayList<>());
  }

  private static boolean isValidAsFieldInitializer(final PsiExpression initializer, final PsiModifierListOwner ctrOrInitializer) {
    if (initializer == null) return false;
    final Ref<Boolean> result = new Ref<>(Boolean.TRUE);
    initializer.accept(new JavaRecursiveElementWalkingVisitor() {
      @RequiredReadAction
      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        PsiElement resolved = expression.resolve();
        if (resolved == null) return;
        if (PsiTreeUtil.isAncestor(ctrOrInitializer, resolved, false) && !PsiTreeUtil.isAncestor(initializer, resolved, false)) {
          // resolved somewhere inside construcor but outside initializer
          result.set(Boolean.FALSE);
        }
      }
    });
    return result.get();
  }

  private static PsiModifierListOwner enclosingMethodOrClassInitializer(PsiAssignmentExpression assignment, PsiField field) {
    PsiElement parentOwner = assignment;
    while (true) {
      parentOwner = PsiTreeUtil.getParentOfType(parentOwner, PsiModifierListOwner.class, true, PsiMember.class);
      if (parentOwner == null) return null;
      PsiElement parent = parentOwner.getParent();

      if (parent == field.getContainingClass()) return (PsiModifierListOwner) parentOwner;
    }
  }

  private static boolean isInitializedWithSameExpression(final PsiField field, final PsiAssignmentExpression assignment, final Collection<PsiAssignmentExpression> initializingAssignments) {
    final PsiExpression expression = assignment.getRExpression();
    final Ref<Boolean> result = new Ref<>(Boolean.TRUE);
    final List<PsiAssignmentExpression> totalUsages = new ArrayList<>();
    PsiClass containingClass = field.getContainingClass();
    assert containingClass != null;
    containingClass.accept(new JavaRecursiveElementVisitor() {
      private PsiCodeBlock currentInitializingBlock; //ctr or class initializer

      @Override
      public void visitCodeBlock(@Nonnull PsiCodeBlock block) {
        PsiElement parent = block.getParent();
        if (parent instanceof PsiClassInitializer || parent instanceof PsiMethod method && method.isConstructor()) {
          currentInitializingBlock = block;
          super.visitCodeBlock(block);
          currentInitializingBlock = null;
        } else {
          super.visitCodeBlock(block);
        }
      }

      @Override
      @RequiredReadAction
      public void visitReferenceExpression(PsiReferenceExpression reference) {
        if (!result.get()) return;
        super.visitReferenceExpression(reference);
        if (!PsiUtil.isOnAssignmentLeftHand(reference)) return;
        PsiElement resolved = reference.resolve();
        if (resolved != field) return;
        PsiExpression rValue = ((PsiAssignmentExpression) reference.getParent()).getRExpression();
        if (currentInitializingBlock != null) {
          // ignore usages other than intializing
          if (rValue == null || !PsiEquivalenceUtil.areElementsEquivalent(rValue, expression)) {
            result.set(Boolean.FALSE);
          }
          initializingAssignments.add((PsiAssignmentExpression) reference.getParent());
        }
        totalUsages.add(assignment);
      }
    });
    // the only assignment is OK
    if (totalUsages.size() == 1 && initializingAssignments.isEmpty()) {
      initializingAssignments.addAll(totalUsages);
      return true;
    }
    return result.get();
  }

  @RequiredReadAction
  private static PsiField getAssignedField(PsiAssignmentExpression assignment) {
    return assignment.getLExpression() instanceof PsiReferenceExpression lReferenceExpression
      && lReferenceExpression.resolve() instanceof PsiField field ? field : null;
  }

  @RequiredReadAction
  private static PsiAssignmentExpression getAssignmentUnderCaret(Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    if (element == null || element instanceof PsiCompiledElement) return null;
    return PsiTreeUtil.getParentOfType(element, PsiAssignmentExpression.class, false, PsiMember.class);
  }

  @Override
  @RequiredReadAction
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiAssignmentExpression assignment = getAssignmentUnderCaret(editor, file);
    if (assignment == null) return;
    PsiField field = getAssignedField(assignment);
    if (field == null) return;
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;

    ArrayList<PsiAssignmentExpression> assignments = new ArrayList<>();
    if (!isInitializedWithSameExpression(field, assignment, assignments)) return;
    PsiExpression initializer = assignment.getRExpression();
    field.setInitializer(initializer);

    for (PsiAssignmentExpression assignmentExpression : assignments) {
      PsiElement statement = assignmentExpression.getParent();
      PsiElement parent = statement.getParent();
      if (parent instanceof PsiIfStatement
          || parent instanceof PsiWhileStatement
          || parent instanceof PsiForStatement
          || parent instanceof PsiForeachStatement) {
        PsiStatement emptyStatement =
            JavaPsiFacade.getInstance(file.getProject()).getElementFactory().createStatementFromText(";", statement);
        statement.replace(emptyStatement);
      } else {
        statement.delete();
      }
    }

    HighlightManager.getInstance(project).addOccurrenceHighlights(editor, new PsiElement[]{field.getInitializer()}, EditorColors.SEARCH_RESULT_ATTRIBUTES, false, null);
  }
}
