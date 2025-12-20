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
package com.intellij.java.impl.codeInspection.varScopeCanBeNarrowed;

import com.intellij.java.impl.codeInsight.CodeInsightUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.Application;
import consulo.codeEditor.Editor;
import consulo.codeEditor.ScrollType;
import consulo.fileEditor.FileEditorManager;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.IJSwingUtilities;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * refactored from {@link FieldCanBeLocalInspection.ConvertFieldToLocalQuickFix}
 *
 * @author Danila Ponomarenko
 */
public abstract class BaseConvertToLocalQuickFix<V extends PsiVariable> implements LocalQuickFix {
  private static final Logger LOG = Logger.getInstance(BaseConvertToLocalQuickFix.class);

  @Override
  @Nonnull
  public final LocalizeValue getName() {
    return InspectionLocalize.inspectionConvertToLocalQuickfix();
  }

  @Override
  @RequiredUIAccess
  public final void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
    V variable = getVariable(descriptor);
    PsiFile myFile = variable.getContainingFile();
    if (variable == null || !variable.isValid()) {
      return; //weird. should not get here when field becomes invalid
    }

    try {
      PsiElement newDeclaration = moveDeclaration(project, variable);
      if (newDeclaration == null) return;

      positionCaretToDeclaration(project, myFile, newDeclaration);
    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @Nullable
  protected abstract V getVariable(@Nonnull ProblemDescriptor descriptor);

  protected static void positionCaretToDeclaration(@Nonnull Project project, @Nonnull PsiFile psiFile, @Nonnull PsiElement declaration) {
    Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
    if (editor != null && (IJSwingUtilities.hasFocus(editor.getComponent()) || Application.get().isUnitTestMode())) {
      PsiFile openedFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (openedFile == psiFile) {
        editor.getCaretModel().moveToOffset(declaration.getTextOffset());
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      }
    }
  }

  protected void beforeDelete(@Nonnull Project project, @Nonnull V variable, @Nonnull PsiElement newDeclaration) {
  }

  @RequiredUIAccess
  @Nullable
  private PsiElement moveDeclaration(@Nonnull Project project, @Nonnull V variable) {
    Collection<PsiReference> references = ReferencesSearch.search(variable).findAll();
    if (references.isEmpty()) return null;

    PsiCodeBlock anchorBlock = findAnchorBlock(references);
    if (anchorBlock == null)
      return null; //was assert, but need to fix the case when obsolete inspection highlighting is left
    if (!CodeInsightUtil.preparePsiElementsForWrite(anchorBlock)) return null;

    PsiElement firstElement = getLowestOffsetElement(references);
    String localName = suggestLocalName(project, variable, anchorBlock);

    PsiElement anchor = getAnchorElement(anchorBlock, firstElement);

    PsiAssignmentExpression anchorAssignmentExpression = searchAssignmentExpression(anchor);
    if (anchorAssignmentExpression != null && isVariableAssignment(anchorAssignmentExpression, variable)) {
      Set<PsiReference> refsSet = new HashSet<>(references);
      refsSet.remove(anchorAssignmentExpression.getLExpression());
      return applyChanges(
          project,
          localName,
          anchorAssignmentExpression.getRExpression(),
          variable,
          refsSet,
          declaration -> {
            if (!mayBeFinal(firstElement, references)) {
              PsiUtil.setModifierProperty((PsiModifierListOwner) declaration.getDeclaredElements()[0], PsiModifier.FINAL, false);
            }
            return anchor.replace(declaration);
          }
      );
    }

    return applyChanges(
        project,
        localName,
        variable.getInitializer(),
        variable,
        references,
        declaration -> anchorBlock.addBefore(declaration, anchor)
    );
  }

  @RequiredUIAccess
  protected PsiElement applyChanges(
    @Nonnull Project project,
    @Nonnull String localName,
    @Nullable PsiExpression initializer,
    @Nonnull V variable,
    @Nonnull Collection<PsiReference> references,
    @Nonnull Function<PsiDeclarationStatement, PsiElement> action
  ) {
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);

    return project.getApplication().runWriteAction(
      (Supplier<PsiElement>)() -> {
        PsiElement newDeclaration = moveDeclaration(elementFactory, localName, variable, initializer, action, references);
        beforeDelete(project, variable, newDeclaration);
        variable.normalizeDeclaration();
        variable.delete();
        return newDeclaration;
      }
    );
  }

  protected PsiElement moveDeclaration(
    PsiElementFactory elementFactory,
    String localName,
    V variable,
    PsiExpression initializer,
    Function<PsiDeclarationStatement, PsiElement> action,
    Collection<PsiReference> references
  ) {
    PsiDeclarationStatement declaration =
      elementFactory.createVariableDeclarationStatement(localName, variable.getType(), initializer);
    PsiElement newDeclaration = action.apply(declaration);
    retargetReferences(elementFactory, localName, references);
    return newDeclaration;
  }

  @Nullable
  private static PsiAssignmentExpression searchAssignmentExpression(@Nullable PsiElement anchor) {
    return anchor instanceof PsiExpressionStatement anchorExpression
      && anchorExpression.getExpression() instanceof PsiAssignmentExpression assignmentExpression ? assignmentExpression : null;
  }

  @RequiredReadAction
  private static boolean isVariableAssignment(@Nonnull PsiAssignmentExpression expression, @Nonnull PsiVariable variable) {
    return expression.getOperationTokenType() == JavaTokenType.EQ
      && expression.getLExpression() instanceof PsiReferenceExpression leftExpression && leftExpression.isReferenceTo(variable);
  }

  @Nonnull
  protected abstract String suggestLocalName(@Nonnull Project project, @Nonnull V variable, @Nonnull PsiCodeBlock scope);

  private static boolean mayBeFinal(PsiElement firstElement, @Nonnull Collection<PsiReference> references) {
    for (PsiReference reference : references) {
      PsiElement element = reference.getElement();
      if (element == firstElement) continue;
      if (element instanceof PsiExpression expression && PsiUtil.isAccessedForWriting(expression)) return false;
    }
    return true;
  }

  private static void retargetReferences(PsiElementFactory elementFactory, String localName, Collection<PsiReference> refs)
    throws IncorrectOperationException {
    PsiReferenceExpression refExpr = (PsiReferenceExpression) elementFactory.createExpressionFromText(localName, null);
    for (PsiReference ref : refs) {
      if (ref instanceof PsiReferenceExpression referenceExpression) {
        referenceExpression.replace(refExpr);
      }
    }
  }

  @Nullable
  private static PsiElement getAnchorElement(PsiCodeBlock anchorBlock, @Nonnull PsiElement firstElement) {
    PsiElement element = firstElement;
    while (element != null && element.getParent() != anchorBlock) {
      element = element.getParent();
    }
    return element;
  }

  @Nullable
  @RequiredReadAction
  private static PsiElement getLowestOffsetElement(@Nonnull Collection<PsiReference> refs) {
    PsiElement firstElement = null;
    for (PsiReference reference : refs) {
      PsiElement element = reference.getElement();
      if (firstElement == null || firstElement.getTextRange().getStartOffset() > element.getTextRange().getStartOffset()) {
        firstElement = element;
      }
    }
    return firstElement;
  }

  @RequiredReadAction
  private static PsiCodeBlock findAnchorBlock(Collection<PsiReference> refs) {
    PsiCodeBlock result = null;
    for (PsiReference psiReference : refs) {
      PsiElement element = psiReference.getElement();
      PsiCodeBlock block = PsiTreeUtil.getParentOfType(element, PsiCodeBlock.class);
      if (result == null || block == null) {
        result = block;
      } else {
        PsiElement commonParent = PsiTreeUtil.findCommonParent(result, block);
        result = PsiTreeUtil.getParentOfType(commonParent, PsiCodeBlock.class, false);
      }
    }
    return result;
  }

  public boolean runForWholeFile() {
    return true;
  }
}
