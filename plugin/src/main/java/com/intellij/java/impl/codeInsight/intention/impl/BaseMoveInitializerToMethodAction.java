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
package com.intellij.java.impl.codeInsight.intention.impl;

import com.intellij.java.impl.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils;
import com.intellij.java.language.impl.refactoring.util.RefactoringChangeUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorColors;
import consulo.colorScheme.EditorColorsManager;
import consulo.colorScheme.TextAttributes;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.highlight.HighlightManager;
import consulo.language.editor.intention.PsiElementBaseIntentionAction;
import consulo.language.psi.PsiCompiledElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.util.lang.ref.Ref;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * refactored from {@link MoveInitializerToConstructorAction}
 *
 * @author Danila Ponomarenko
 */
public abstract class BaseMoveInitializerToMethodAction extends PsiElementBaseIntentionAction {
  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, @Nonnull PsiElement element) {
    if (element instanceof PsiCompiledElement) return false;
    final PsiField field = PsiTreeUtil.getParentOfType(element, PsiField.class, false, PsiMember.class, PsiCodeBlock.class, PsiDocComment.class);
    if (field == null || hasUnsuitableModifiers(field)) return false;
    if (!field.hasInitializer()) return false;
    PsiClass psiClass = field.getContainingClass();

    return psiClass != null && !psiClass.isInterface() && !(psiClass instanceof PsiAnonymousClass)/* && !(psiClass instanceof JspClass)*/;
  }

  private boolean hasUnsuitableModifiers(@Nonnull PsiField field) {
    for (@PsiModifier.ModifierConstant String modifier : getUnsuitableModifiers()) {
      if (field.hasModifierProperty(modifier)) {
        return true;
      }
    }
    return false;
  }

  @Nonnull
  protected abstract Collection<String> getUnsuitableModifiers();


  @Override
  public void invoke(@Nonnull Project project, Editor editor, @Nonnull PsiElement element) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return;

    final PsiField field = PsiTreeUtil.getParentOfType(element, PsiField.class);
    assert field != null;
    final PsiClass aClass = field.getContainingClass();
    if (aClass == null) return;

    final Collection<PsiMethod> methodsToAddInitialization = getOrCreateMethods(project, editor, element.getContainingFile(), aClass);

    if (methodsToAddInitialization.isEmpty()) return;

    final List<PsiExpressionStatement> assignments = addFieldAssignments(field, methodsToAddInitialization);
    field.getInitializer().delete();

    if (!assignments.isEmpty()) {
      highlightRExpression((PsiAssignmentExpression) assignments.get(0).getExpression(), project, editor);
    }
  }

  private static void highlightRExpression(@Nonnull PsiAssignmentExpression assignment, @Nonnull Project project, Editor editor) {
    final EditorColorsManager manager = EditorColorsManager.getInstance();
    final TextAttributes attributes = manager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    final PsiExpression expression = assignment.getRExpression();

    HighlightManager.getInstance(project).addOccurrenceHighlights(editor, new PsiElement[]{expression}, attributes, false, null);
  }

  @Nonnull
  private static List<PsiExpressionStatement> addFieldAssignments(@Nonnull PsiField field, @Nonnull Collection<PsiMethod> methods) {
    final List<PsiExpressionStatement> assignments = new ArrayList<PsiExpressionStatement>();
    for (PsiMethod method : methods) {
      assignments.add(addAssignment(getOrCreateMethodBody(method), field));
    }
    return assignments;
  }

  @Nonnull
  private static PsiCodeBlock getOrCreateMethodBody(@Nonnull PsiMethod method) {
    PsiCodeBlock codeBlock = method.getBody();
    if (codeBlock == null) {
      CreateFromUsageUtils.setupMethodBody(method);
      codeBlock = method.getBody();
    }
    return codeBlock;
  }

  @Nonnull
  protected abstract Collection<PsiMethod> getOrCreateMethods(@Nonnull Project project, @Nonnull Editor editor, PsiFile file, @Nonnull PsiClass aClass);


  @Nonnull
  private static PsiExpressionStatement addAssignment(@Nonnull PsiCodeBlock codeBlock, @Nonnull PsiField field) throws IncorrectOperationException {
    final PsiElementFactory factory = JavaPsiFacade.getInstance(codeBlock.getProject()).getElementFactory();

    final PsiExpressionStatement statement = (PsiExpressionStatement) factory.createStatementFromText(field.getName() + " = y;", codeBlock);

    PsiExpression initializer = field.getInitializer();
    if (initializer instanceof PsiArrayInitializerExpression) {
      initializer = arrayInitializerToNewExpression((PsiArrayInitializerExpression) initializer, factory, codeBlock);
    }

    final PsiAssignmentExpression expression = (PsiAssignmentExpression) statement.getExpression();
    expression.getRExpression().replace(initializer);

    final PsiElement newStatement = codeBlock.addBefore(statement, findFirstFieldUsage(codeBlock.getStatements(), field));
    replaceWithQualifiedReferences(newStatement, newStatement, factory);
    return (PsiExpressionStatement) newStatement;
  }

  @Nullable
  private static PsiElement findFirstFieldUsage(@Nonnull PsiStatement[] statements, @Nonnull PsiField field) {
    for (PsiStatement blockStatement : statements) {
      if (!isSuperOrThisMethodCall(blockStatement) && containsReference(blockStatement, field)) {
        return blockStatement;
      }
    }
    return null;
  }

  private static boolean isSuperOrThisMethodCall(@Nonnull PsiStatement statement) {
    if (statement instanceof PsiExpressionStatement) {
      final PsiElement expression = ((PsiExpressionStatement) statement).getExpression();
      if (RefactoringChangeUtil.isSuperOrThisMethodCall(expression)) {
        return true;
      }
    }
    return false;
  }

  private static PsiExpression arrayInitializerToNewExpression(@Nonnull PsiArrayInitializerExpression initializer,
                                                               @Nonnull PsiElementFactory factory,
                                                               @Nonnull PsiElement context) {
    final PsiType type = initializer.getType();
    final PsiNewExpression newExpression = (PsiNewExpression) factory.createExpressionFromText("new " + type.getCanonicalText() + "{}", context);
    newExpression.getArrayInitializer().replace(initializer);
    return newExpression;
  }

  private static boolean containsReference(final @Nonnull PsiElement element,
                                           final @Nonnull PsiField field) {
    final Ref<Boolean> result = new Ref<Boolean>(Boolean.FALSE);
    element.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        if (expression.resolve() == field) {
          result.set(Boolean.TRUE);
        }
        super.visitReferenceExpression(expression);
      }
    });
    return result.get().booleanValue();
  }

  private static void replaceWithQualifiedReferences(@Nonnull PsiElement expression, @Nonnull PsiElement root, @Nonnull PsiElementFactory factory) throws IncorrectOperationException {
    final PsiReference reference = expression.getReference();
    if (reference == null) {
      for (PsiElement child : expression.getChildren()) {
        replaceWithQualifiedReferences(child, root, factory);
      }
      return;
    }

    final PsiElement resolved = reference.resolve();
    if (resolved instanceof PsiVariable && !(resolved instanceof PsiField) && !PsiTreeUtil.isAncestor(root, resolved, false)) {
      final PsiVariable variable = (PsiVariable) resolved;
      PsiElement qualifiedExpr = factory.createExpressionFromText("this." + variable.getName(), expression);
      expression.replace(qualifiedExpr);
    }
  }
}