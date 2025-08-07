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
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.impl.codeInsight.generation.PsiMethodMember;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiTypesUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.codeEditor.Editor;
import consulo.codeEditor.ScrollType;
import consulo.document.util.TextRange;
import consulo.ide.impl.idea.ide.util.MemberChooser;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.CodeInsightUtilCore;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class InitializeFinalFieldInConstructorFix implements SyntheticIntentionAction {
  private final PsiField myField;

  public InitializeFinalFieldInConstructorFix(@Nonnull PsiField field) {
    myField = field;
  }

  @Nonnull
  @Override
  public String getText() {
    return JavaQuickFixBundle.message("initialize.final.field.in.constructor.name");
  }

  @Override
  @RequiredReadAction
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    if (!myField.isValid() || myField.hasModifierProperty(PsiModifier.STATIC) || myField.hasInitializer()) {
      return false;
    }

    final PsiClass containingClass = myField.getContainingClass();
    if (containingClass == null || containingClass.getName() == null) {
      return false;
    }

    final PsiManager manager = myField.getManager();
    return manager != null && manager.isInProject(myField);
  }

  @Override
  @RequiredUIAccess
  public void invoke(@Nonnull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;

    final PsiClass myClass = myField.getContainingClass();
    if (myClass == null) {
      return;
    }
    if (myClass.getConstructors().length == 0) {
      createDefaultConstructor(myClass, project, editor, file);
    }

    final List<PsiMethod> constructors = choose(filterIfFieldAlreadyAssigned(myField, myClass.getConstructors()), project);

    project.getApplication().runWriteAction(() -> {
      final List<PsiExpressionStatement> statements = addFieldInitialization(constructors, myField, project);
      final PsiExpressionStatement highestStatement = getHighestElement(statements);
      if (highestStatement == null) return;

      final PsiAssignmentExpression expression = (PsiAssignmentExpression)highestStatement.getExpression();
      final PsiElement rightExpression = expression.getRExpression();

      final TextRange expressionRange = rightExpression.getTextRange();
      editor.getCaretModel().moveToOffset(expressionRange.getStartOffset());
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      editor.getSelectionModel().setSelection(expressionRange.getStartOffset(), expressionRange.getEndOffset());
    });
  }

  @Nullable
  @RequiredReadAction
  private static <T extends PsiElement> T getHighestElement(@Nonnull List<T> elements) {
    T highest = null;
    int highestTextOffset = Integer.MAX_VALUE;
    for (T element : elements) {
      final T forcedElem = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(element);
      final int startOffset = forcedElem.getTextOffset();
      if (startOffset < highestTextOffset) {
        highest = forcedElem;
        highestTextOffset = startOffset;
      }
    }
    return highest;
  }

  @Nonnull
  private static List<PsiExpressionStatement> addFieldInitialization(
    @Nonnull List<PsiMethod> constructors,
    @Nonnull PsiField field,
    @Nonnull Project project
  ) {
    final List<PsiExpressionStatement> statements = new ArrayList<>();
    for (PsiMethod constructor : constructors) {
      final PsiExpressionStatement statement = addFieldInitialization(constructor, field, project);
      if (statement != null) {
        statements.add(statement);
      }
    }
    return statements;
  }

  @Nullable
  private static PsiExpressionStatement addFieldInitialization(
    @Nonnull PsiMethod constructor,
    @Nonnull PsiField field,
    @Nonnull Project project
  ) {
    PsiCodeBlock methodBody = constructor.getBody();
    if (methodBody == null) return null;

    final String fieldName = field.getName();
    String stmtText = fieldName + " = " + suggestInitValue(field) + ";";
    if (methodContainsParameterWithName(constructor, fieldName)) {
      stmtText = "this." + stmtText;
    }

    final PsiManager psiManager = PsiManager.getInstance(project);
    final PsiElementFactory factory = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory();
    final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);

    return (PsiExpressionStatement)methodBody.add(codeStyleManager.reformat(factory.createStatementFromText(stmtText, methodBody)));
  }

  private static boolean methodContainsParameterWithName(@Nonnull PsiMethod constructor, @Nonnull String name) {
    for (PsiParameter parameter : constructor.getParameterList().getParameters()) {
      if (name.equals(parameter.getName())) {
        return true;
      }
    }
    return false;
  }

  @Nonnull
  private static List<PsiMethod> choose(@Nonnull PsiMethod[] ctors, @Nonnull final Project project) {
    if (project.getApplication().isUnitTestMode()) {
      return Arrays.asList(ctors);
    }

    if (ctors.length == 1) {
      return Arrays.asList(ctors[0]);
    }

    if (ctors.length > 1) {
      final MemberChooser<PsiMethodMember> chooser = new MemberChooser<>(toPsiMethodMemberArray(ctors), false, true, project);
      chooser.setTitle(JavaQuickFixBundle.message("initialize.final.field.in.constructor.choose.dialog.title"));
      chooser.show();

      final List<PsiMethodMember> chosenMembers = chooser.getSelectedElements();
      if (chosenMembers != null) {
        return Arrays.asList(toPsiMethodArray(chosenMembers));
      }
    }

    return Collections.emptyList();
  }

  private static PsiMethodMember[] toPsiMethodMemberArray(@Nonnull PsiMethod[] methods) {
    final PsiMethodMember[] result = new PsiMethodMember[methods.length];
    for (int i = 0; i < methods.length; i++) {
      result[i] = new PsiMethodMember(methods[i]);
    }
    return result;
  }

  private static PsiMethod[] toPsiMethodArray(@Nonnull List<PsiMethodMember> methodMembers) {
    final PsiMethod[] result = new PsiMethod[methodMembers.size()];
    int i = 0;
    for (PsiMethodMember methodMember : methodMembers) {
      result[i++] = methodMember.getElement();
    }
    return result;
  }

  @RequiredUIAccess
  private static void createDefaultConstructor(PsiClass psiClass, @Nonnull final Project project, final Editor editor, final PsiFile file) {
    final AddDefaultConstructorFix defaultConstructorFix = new AddDefaultConstructorFix(psiClass);
    project.getApplication().runWriteAction(() -> defaultConstructorFix.invoke(project, editor, file));
  }

  @RequiredReadAction
  private static PsiMethod[] filterIfFieldAlreadyAssigned(@Nonnull PsiField field, @Nonnull PsiMethod[] ctors) {
    final List<PsiMethod> result = new ArrayList<>(Arrays.asList(ctors));
    for (PsiReference reference : ReferencesSearch.search(field, new LocalSearchScope(ctors))) {
      final PsiElement element = reference.getElement();
      if (element instanceof PsiReferenceExpression referenceExpression && PsiUtil.isOnAssignmentLeftHand(referenceExpression)) {
        result.remove(PsiTreeUtil.getParentOfType(element, PsiMethod.class));
      }
    }
    return result.toArray(new PsiMethod[result.size()]);
  }

  private static String suggestInitValue(@Nonnull PsiField field) {
    PsiType type = field.getType();
    return PsiTypesUtil.getDefaultValueOfType(type);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
