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

import com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import com.intellij.java.language.psi.util.PsiFormatUtilBase;
import com.intellij.java.language.psi.util.PsiTypesUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.codeEditor.Editor;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

import java.util.Collection;
import java.util.HashMap;

/**
 * @author ven
 */
public class BringVariableIntoScopeFix implements SyntheticIntentionAction {
  private static final Logger LOG = Logger.getInstance(BringVariableIntoScopeFix.class);
  private final PsiReferenceExpression myUnresolvedReference;
  private PsiLocalVariable myOutOfScopeVariable;

  public BringVariableIntoScopeFix(PsiReferenceExpression unresolvedReference) {
    myUnresolvedReference = unresolvedReference;
  }

  @Override
  @Nonnull
  public LocalizeValue getText() {
    PsiLocalVariable variable = myOutOfScopeVariable;

    String varText = variable == null ? "" : PsiFormatUtil.formatVariable(variable, PsiFormatUtilBase.SHOW_NAME |
                                                                                    PsiFormatUtilBase.SHOW_TYPE, PsiSubstitutor.EMPTY);
    return JavaQuickFixLocalize.bringVariableToScopeText(varText);
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PsiJavaFile)) return false;
    if (myUnresolvedReference.isQualified()) return false;
    final String referenceName = myUnresolvedReference.getReferenceName();
    if (referenceName == null) return false;

    PsiManager manager = file.getManager();
    if (!myUnresolvedReference.isValid() || !manager.isInProject(myUnresolvedReference)) return false;

    PsiElement container = PsiTreeUtil.getParentOfType(myUnresolvedReference, PsiCodeBlock.class, PsiClass.class);
    if (!(container instanceof PsiCodeBlock)) return false;

    myOutOfScopeVariable = null;
    while(container.getParent() instanceof PsiStatement || container.getParent() instanceof PsiCatchSection) container = container.getParent();
    container.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override public void visitReferenceExpression(PsiReferenceExpression expression) {}

      @Override public void visitExpression(PsiExpression expression) {
        //Don't look inside expressions
      }

      @Override public void visitLocalVariable(PsiLocalVariable variable) {
        if (referenceName.equals(variable.getName())) {
          if (myOutOfScopeVariable == null) {
            myOutOfScopeVariable = variable;
          }
          else {
            myOutOfScopeVariable = null; //2 conflict variables
          }
        }
      }
    });

    return myOutOfScopeVariable != null;
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    LOG.assertTrue(myOutOfScopeVariable != null);
    PsiManager manager = file.getManager();
    myOutOfScopeVariable.normalizeDeclaration();
    PsiUtil.setModifierProperty(myOutOfScopeVariable, PsiModifier.FINAL, false);
    PsiElement commonParent = PsiTreeUtil.findCommonParent(myOutOfScopeVariable, myUnresolvedReference);
    LOG.assertTrue(commonParent != null);
    PsiElement child = myOutOfScopeVariable.getTextRange().getStartOffset() < myUnresolvedReference.getTextRange().getStartOffset() ? myOutOfScopeVariable
                                                                                                                                    : myUnresolvedReference;

    while(child.getParent() != commonParent) child = child.getParent();
    PsiDeclarationStatement newDeclaration = (PsiDeclarationStatement)JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createStatementFromText("int i = 0", null);
    PsiVariable variable = (PsiVariable)newDeclaration.getDeclaredElements()[0].replace(myOutOfScopeVariable);
    if (variable.getInitializer() != null) {
      variable.getInitializer().delete();
    }

    while(!(child instanceof PsiStatement) || !(child.getParent() instanceof PsiCodeBlock)) {
      child = child.getParent();
      commonParent = commonParent.getParent();
    }
    LOG.assertTrue(commonParent != null);
    PsiDeclarationStatement added = (PsiDeclarationStatement)commonParent.addBefore(newDeclaration, child);
    PsiLocalVariable addedVar = (PsiLocalVariable)added.getDeclaredElements()[0];
    CodeStyleManager.getInstance(manager.getProject()).reformat(commonParent);

    //Leave initializer assignment
    PsiExpression initializer = myOutOfScopeVariable.getInitializer();
    if (initializer != null) {
      PsiExpressionStatement assignment = (PsiExpressionStatement)JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createStatementFromText(myOutOfScopeVariable
                                                                                                                                                                .getName() + "= e;", null);
      ((PsiAssignmentExpression)assignment.getExpression()).getRExpression().replace(initializer);
      assignment = (PsiExpressionStatement)CodeStyleManager.getInstance(manager.getProject()).reformat(assignment);
      PsiDeclarationStatement declStatement = PsiTreeUtil.getParentOfType(myOutOfScopeVariable, PsiDeclarationStatement.class);
      LOG.assertTrue(declStatement != null);
      PsiElement parent = declStatement.getParent();
      if (parent instanceof PsiForStatement) {
        declStatement.replace(assignment);
      }
      else {
        parent.addAfter(assignment, declStatement);
      }
    }

    if (myOutOfScopeVariable.isValid()) {
      myOutOfScopeVariable.delete();
    }

    if (HighlightControlFlowUtil.checkVariableInitializedBeforeUsage(myUnresolvedReference, addedVar, new HashMap<PsiElement, Collection<PsiReferenceExpression>>(),file) != null) {
      initialize(addedVar);
    }
  }

  private static void initialize(final PsiLocalVariable variable) throws IncorrectOperationException {
    PsiType type = variable.getType();
    String init = PsiTypesUtil.getDefaultValueOfType(type);
    PsiElementFactory factory = JavaPsiFacade.getInstance(variable.getProject()).getElementFactory();
    PsiExpression initializer = factory.createExpressionFromText(init, variable);
    variable.setInitializer(initializer);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
