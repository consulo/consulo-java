
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
package com.intellij.java.impl.refactoring.inline;

import com.intellij.java.analysis.codeInsight.intention.QuickFixFactory;
import com.intellij.java.analysis.impl.psi.controlFlow.DefUseUtil;
import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.impl.refactoring.util.InlineUtil;
import com.intellij.java.language.impl.codeInsight.ExceptionUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.application.util.query.Query;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorColors;
import consulo.language.ast.IElementType;
import consulo.language.editor.TargetElementUtil;
import consulo.language.editor.highlight.HighlightManager;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.editor.refactoring.util.RefactoringMessageDialog;
import consulo.language.editor.util.PsiUtilBase;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.project.ui.wm.WindowManager;
import consulo.undoRedo.CommandProcessor;
import consulo.util.collection.ArrayUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ExtensionImpl
public class InlineLocalHandler extends JavaInlineActionHandler {
  private static final Logger LOG = Logger.getInstance(InlineLocalHandler.class);

  private static final String REFACTORING_NAME = RefactoringBundle.message("inline.variable.title");

  public boolean canInlineElement(PsiElement element) {
    return element instanceof PsiLocalVariable;
  }

  @RequiredReadAction
  public void inlineElement(Project project, Editor editor, PsiElement element) {
    final PsiReference psiReference = TargetElementUtil.findReference(editor);
    final PsiReferenceExpression refExpr = psiReference instanceof PsiReferenceExpression referenceExpression ? referenceExpression : null;
    invoke(project, editor, (PsiLocalVariable) element, refExpr);
  }

  /**
   * should be called in AtomicAction
   */
  @RequiredReadAction
  public static void invoke(@Nonnull final Project project, final Editor editor, final PsiLocalVariable local, PsiReferenceExpression refExpr) {
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, local)) return;

    final HighlightManager highlightManager = HighlightManager.getInstance(project);

    final String localName = local.getName();

    final Query<PsiReference> query = ReferencesSearch.search(local, GlobalSearchScope.allScope(project), false);
    if (query.findFirst() == null) {
      LOG.assertTrue(refExpr == null);
      LocalizeValue message = RefactoringLocalize.variableIsNeverUsed(localName);
      CommonRefactoringUtil.showErrorHint(project, editor, message.get(), REFACTORING_NAME, HelpID.INLINE_VARIABLE);
      return;
    }

    final PsiClass containingClass = PsiTreeUtil.getParentOfType(local, PsiClass.class);
    final List<PsiElement> innerClassesWithUsages = Collections.synchronizedList(new ArrayList<PsiElement>());
    final List<PsiElement> innerClassUsages = Collections.synchronizedList(new ArrayList<PsiElement>());
    query.forEach(psiReference -> {
      final PsiElement element = psiReference.getElement();
      PsiElement innerClass = PsiTreeUtil.getParentOfType(element, PsiClass.class, PsiLambdaExpression.class);
      while (innerClass != containingClass && innerClass != null) {
        final PsiClass parentPsiClass = PsiTreeUtil.getParentOfType(innerClass, PsiClass.class, true);
        if (parentPsiClass == containingClass) {
          if (innerClass instanceof PsiLambdaExpression) {
            if (PsiTreeUtil.isAncestor(innerClass, local, false)) {
              innerClassesWithUsages.add(element);
            } else {
              innerClassesWithUsages.add(innerClass);
            }
            innerClass = parentPsiClass;
            continue;
          }
          innerClassesWithUsages.add(innerClass);
          innerClassUsages.add(element);
        }
        innerClass = parentPsiClass;
      }
      return true;
    });

    final PsiCodeBlock containerBlock = PsiTreeUtil.getParentOfType(local, PsiCodeBlock.class);
    if (containerBlock == null) {
      LocalizeValue message = RefactoringLocalize.cannotPerformRefactoringWithReason(
          LocalizeValue.localizeTODO("Variable is declared outside a code block")
      );
      CommonRefactoringUtil.showErrorHint(project, editor, message.get(), REFACTORING_NAME, HelpID.INLINE_VARIABLE);
      return;
    }

    final PsiExpression defToInline = innerClassesWithUsages.isEmpty()
        ? getDefToInline(local, refExpr, containerBlock)
        : getDefToInline(local, innerClassesWithUsages.get(0), containerBlock);
    if (defToInline == null) {
      LocalizeValue message = RefactoringLocalize.cannotPerformRefactoringWithReason(
        refExpr == null
          ? RefactoringLocalize.variableHasNoInitializer(localName)
          : LocalizeValue.localizeTODO(RefactoringBundle.message("variable.has.no.dominating.definition", localName))
      );
      CommonRefactoringUtil.showErrorHint(project, editor, message.get(), REFACTORING_NAME, HelpID.INLINE_VARIABLE);
      return;
    }

    final List<PsiElement> refsToInlineList = new ArrayList<>();
    Collections.addAll(refsToInlineList, DefUseUtil.getRefs(containerBlock, local, defToInline));
    for (PsiElement innerClassUsage : innerClassUsages) {
      if (!refsToInlineList.contains(innerClassUsage)) {
        refsToInlineList.add(innerClassUsage);
      }
    }
    if (refsToInlineList.size() == 0) {
      LocalizeValue message = RefactoringLocalize.variableIsNeverUsedBeforeModification(localName);
      CommonRefactoringUtil.showErrorHint(project, editor, message.get(), REFACTORING_NAME, HelpID.INLINE_VARIABLE);
      return;
    }
    final PsiElement[] refsToInline = PsiUtilBase.toPsiElementArray(refsToInlineList);

    if (refExpr != null && PsiUtil.isAccessedForReading(refExpr) && ArrayUtil.find(refsToInline, refExpr) < 0) {
      final PsiElement[] defs = DefUseUtil.getDefs(containerBlock, local, refExpr);
      LOG.assertTrue(defs.length > 0);
      highlightManager.addOccurrenceHighlights(editor, defs, EditorColors.SEARCH_RESULT_ATTRIBUTES, true, null);
      LocalizeValue message =
          RefactoringLocalize.cannotPerformRefactoringWithReason(RefactoringLocalize.variableIsAccessedForWriting(localName));
      CommonRefactoringUtil.showErrorHint(project, editor, message.get(), REFACTORING_NAME, HelpID.INLINE_VARIABLE);
      return;
    }

    PsiTryStatement tryStatement = PsiTreeUtil.getParentOfType(defToInline, PsiTryStatement.class);
    if (tryStatement != null) {
      if (ExceptionUtil.getThrownExceptions(defToInline).isEmpty()) {
        tryStatement = null;
      }
    }
    PsiFile workingFile = local.getContainingFile();
    for (PsiElement ref : refsToInline) {
      final PsiFile otherFile = ref.getContainingFile();
      if (!otherFile.equals(workingFile)) {
        LocalizeValue message = RefactoringLocalize.variableIsReferencedInMultipleFiles(localName);
        CommonRefactoringUtil.showErrorHint(project, editor, message.get(), REFACTORING_NAME, HelpID.INLINE_VARIABLE);
        return;
      }
      if (tryStatement != null && !PsiTreeUtil.isAncestor(tryStatement, ref, false)) {
        CommonRefactoringUtil.showErrorHint(
          project,
          editor,
          "Unable to inline outside try/catch statement",
          REFACTORING_NAME,
          HelpID.INLINE_VARIABLE
        );
        return;
      }
    }

    for (final PsiElement ref : refsToInline) {
      final PsiElement[] defs = DefUseUtil.getDefs(containerBlock, local, ref);
      boolean isSameDefinition = true;
      for (PsiElement def : defs) {
        isSameDefinition &= isSameDefinition(def, defToInline);
      }
      if (!isSameDefinition) {
        highlightManager.addOccurrenceHighlights(editor, defs, EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES, true, null);
        highlightManager.addOccurrenceHighlights(editor, new PsiElement[]{ref}, EditorColors.SEARCH_RESULT_ATTRIBUTES, true, null);
        LocalizeValue message = RefactoringLocalize.cannotPerformRefactoringWithReason(
            RefactoringLocalize.variableIsAccessedForWritingAndUsedWithInlined(localName));
        CommonRefactoringUtil.showErrorHint(project, editor, message.get(), REFACTORING_NAME, HelpID.INLINE_VARIABLE);
        return;
      }
    }

    final PsiElement writeAccess = checkRefsInAugmentedAssignmentOrUnaryModified(refsToInline);
    if (writeAccess != null) {
      HighlightManager.getInstance(project).addOccurrenceHighlights(editor, new PsiElement[]{writeAccess}, EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES, true, null);
      LocalizeValue message =
          RefactoringLocalize.cannotPerformRefactoringWithReason(RefactoringLocalize.variableIsAccessedForWriting(localName));
      CommonRefactoringUtil.showErrorHint(project, editor, message.get(), REFACTORING_NAME, HelpID.INLINE_VARIABLE);
      return;
    }

    if (editor != null && !project.getApplication().isUnitTestMode()) {
      // TODO : check if initializer uses fieldNames that possibly will be hidden by other
      // locals with the same names after inlining
      highlightManager.addOccurrenceHighlights(
          editor,
          refsToInline,
          EditorColors.SEARCH_RESULT_ATTRIBUTES, true, null
      );
      int occurrencesCount = refsToInline.length;
      LocalizeValue message = isInliningVariableInitializer(defToInline)
        ? RefactoringLocalize.inlineLocalVariablePrompt(localName)
        : RefactoringLocalize.inlineLocalVariableDefinitionPrompt(localName);
      LocalizeValue occurencesString = RefactoringLocalize.occurencesString(occurrencesCount);
      RefactoringMessageDialog dialog = new RefactoringMessageDialog(
          REFACTORING_NAME,
        message + " " + occurencesString,
          HelpID.INLINE_VARIABLE,
          "OptionPane.questionIcon",
          true,
          project);
      dialog.show();
      if (!dialog.isOK()) {
        return;
      }
    }

    final Runnable runnable = () -> {
      try {
        PsiExpression[] exprs = new PsiExpression[refsToInline.length];
        for (int idx = 0; idx < refsToInline.length; idx++) {
          PsiJavaCodeReferenceElement refElement = (PsiJavaCodeReferenceElement) refsToInline[idx];
          exprs[idx] = InlineUtil.inlineVariable(local, defToInline, refElement);
        }

        if (!isInliningVariableInitializer(defToInline)) {
          defToInline.getParent().delete();
        } else {
          defToInline.delete();
        }

        if (ReferencesSearch.search(local).findFirst() == null) {
          QuickFixFactory.getInstance().createRemoveUnusedVariableFix(local).invoke(project, editor, local.getContainingFile());
        }

        if (editor != null && !project.getApplication().isUnitTestMode()) {
          highlightManager.addOccurrenceHighlights(editor, exprs, EditorColors.SEARCH_RESULT_ATTRIBUTES, true, null);
        }

        for (final PsiExpression expr : exprs) {
          InlineUtil.tryToInlineArrayCreationForVarargs(expr);
        }
      } catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    };

    CommandProcessor.getInstance().executeCommand(
      project,
      () -> Application.get().runWriteAction(runnable),
      RefactoringLocalize.inlineCommand(localName).get(),
      null
    );
  }

  @Nullable
  public static PsiElement checkRefsInAugmentedAssignmentOrUnaryModified(final PsiElement[] refsToInline) {
    for (PsiElement element : refsToInline) {

      PsiElement parent = element.getParent();
      if (parent instanceof PsiArrayAccessExpression arrayAccessExpression) {
        if (arrayAccessExpression.getIndexExpression() == element) continue;
        element = parent;
        parent = parent.getParent();
      }

      if (parent instanceof PsiAssignmentExpression assignment && element == assignment.getLExpression()
          || isUnaryWriteExpression(parent)) {

        return element;
      }
    }
    return null;
  }

  private static boolean isUnaryWriteExpression(PsiElement parent) {
    IElementType tokenType = null;
    if (parent instanceof PsiPrefixExpression prefixExpression) {
      tokenType = prefixExpression.getOperationTokenType();
    }
    if (parent instanceof PsiPostfixExpression postfixExpression) {
      tokenType = postfixExpression.getOperationTokenType();
    }
    return tokenType == JavaTokenType.PLUSPLUS || tokenType == JavaTokenType.MINUSMINUS;
  }

  private static boolean isSameDefinition(final PsiElement def, final PsiExpression defToInline) {
    if (def instanceof PsiLocalVariable localVar) return defToInline.equals(localVar.getInitializer());
    final PsiElement parent = def.getParent();
    return parent instanceof PsiAssignmentExpression assignment && defToInline.equals(assignment.getRExpression());
  }

  private static boolean isInliningVariableInitializer(final PsiExpression defToInline) {
    return defToInline.getParent() instanceof PsiVariable;
  }

  @Nullable
  private static PsiExpression getDefToInline(
    final PsiLocalVariable local,
    final PsiElement refExpr,
    final PsiCodeBlock block
  ) {
    if (refExpr != null) {
      PsiElement def;
      if (refExpr instanceof PsiReferenceExpression referenceExpression && PsiUtil.isAccessedForWriting(referenceExpression)) {
        def = refExpr;
      } else {
        final PsiElement[] defs = DefUseUtil.getDefs(block, local, refExpr);
        if (defs.length == 1) {
          def = defs[0];
        } else {
          return null;
        }
      }

      if (def instanceof PsiReferenceExpression && def.getParent() instanceof PsiAssignmentExpression assignmentExpression) {
        if (assignmentExpression.getOperationTokenType() != JavaTokenType.EQ) return null;
        final PsiExpression rExpr = assignmentExpression.getRExpression();
        if (rExpr != null) return rExpr;
      }
    }
    return local.getInitializer();
  }
}
