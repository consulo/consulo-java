/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.language.impl.psi.controlFlow.ControlFlowUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.codeEditor.Editor;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.editor.rawHighlight.HighlightInfo;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.dataholder.Key;
import consulo.util.lang.StringUtil;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import java.util.*;

public class VariableAccessFromInnerClassFix implements SyntheticIntentionAction {
  private static final Logger LOG = Logger.getInstance(VariableAccessFromInnerClassFix.class);
  private final PsiVariable myVariable;
  private final PsiElement myContext;
  private final int myFixType;
  private static final int MAKE_FINAL = 0;
  private static final int MAKE_ARRAY = 1;
  private static final int COPY_TO_FINAL = 2;
  private static final Key<Map<PsiVariable,Boolean>>[] VARS = new Key[] {Key.create("VARS_TO_MAKE_FINAL"), Key.create("VARS_TO_TRANSFORM"), Key.create("???")};

  public VariableAccessFromInnerClassFix(@Nonnull PsiVariable variable, @Nonnull PsiElement element) {
    myVariable = variable;
    myContext = element;
    myFixType = getQuickFixType(variable);
    if (myFixType == -1) return;

    getVariablesToFix().add(variable);
  }

  @Override
  @Nonnull
  public String getText() {
    @NonNls String message;
    switch (myFixType) {
      case MAKE_FINAL:
        message = "make.final.text";
        break;
      case MAKE_ARRAY:
        message = "make.final.transform.to.one.element.array";
        break;
      case COPY_TO_FINAL:
        return JavaQuickFixBundle.message("make.final.copy.to.temp", myVariable.getName());
      default:
        return "";
    }
    Collection<PsiVariable> vars = getVariablesToFix();
    String varNames = vars.size() == 1 ? "'"+myVariable.getName()+"'" : "variables";
    return JavaQuickFixBundle.message(message, varNames);
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    return myContext.isValid() &&
           myContext.getManager().isInProject(myContext) &&
           myVariable.isValid() &&
           myFixType != -1 &&
           !getVariablesToFix().isEmpty() &&
           !inOwnInitializer(myVariable, myContext);
  }

  private static boolean inOwnInitializer(PsiVariable variable, PsiElement context) {
    return PsiTreeUtil.isAncestor(variable, context, false);
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) {
    if (!FileModificationService.getInstance().preparePsiElementsForWrite(myContext, myVariable)) return;
    try {
      switch (myFixType) {
        case MAKE_FINAL:
          makeFinal();
          break;
        case MAKE_ARRAY:
          makeArray();
          break;
        case COPY_TO_FINAL:
          copyToFinal();
          break;
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    finally {
      getVariablesToFix().clear();
    }
  }

  private void makeArray() {
    for (PsiVariable var : getVariablesToFix()) {
      makeArray(var);
    }
  }

  @Nonnull
  private Collection<PsiVariable> getVariablesToFix() {
    Map<PsiVariable, Boolean> vars = myContext.getUserData(VARS[myFixType]);
    if (vars == null) myContext.putUserData(VARS[myFixType], vars = ContainerUtil.createConcurrentWeakMap());
    final Map<PsiVariable, Boolean> finalVars = vars;
    return new AbstractCollection<PsiVariable>() {
      @Override
      public boolean add(PsiVariable psiVariable) {
        return finalVars.put(psiVariable, Boolean.TRUE) == null;
      }

      @Nonnull
      @Override
      public Iterator<PsiVariable> iterator() {
        return finalVars.keySet().iterator();
      }

      @Override
      public int size() {
        return finalVars.size();
      }
    };
  }

  private void makeFinal() {
    for (PsiVariable var : getVariablesToFix()) {
      if (var.isValid()) {
        PsiUtil.setModifierProperty(var, PsiModifier.FINAL, true);
      }
    }
  }

  private void makeArray(PsiVariable variable) throws IncorrectOperationException {
    PsiType type = variable.getType();

    PsiElementFactory factory = JavaPsiFacade.getInstance(myContext.getProject()).getElementFactory();
    PsiType newType = type.createArrayType();

    PsiDeclarationStatement variableDeclarationStatement;
    PsiExpression initializer = variable.getInitializer();
    if (initializer == null) {
      String expression = "[1]";
      while (type instanceof PsiArrayType) {
        expression += "[1]";
        type = ((PsiArrayType) type).getComponentType();
      }
      PsiExpression init = factory.createExpressionFromText("new " + type.getCanonicalText() + expression, variable);
      variableDeclarationStatement = factory.createVariableDeclarationStatement(variable.getName(), newType, init);
    }
    else {
      PsiExpression init = factory.createExpressionFromText("{ " + initializer.getText() + " }", variable);
      variableDeclarationStatement = factory.createVariableDeclarationStatement(variable.getName(), newType, init);
    }
    PsiVariable newVariable = (PsiVariable)variableDeclarationStatement.getDeclaredElements()[0];
    PsiUtil.setModifierProperty(newVariable, PsiModifier.FINAL, true);
    PsiElement newExpression = factory.createExpressionFromText(variable.getName() + "[0]", variable);

    PsiElement outerCodeBlock = PsiUtil.getVariableCodeBlock(variable, null);
    if (outerCodeBlock == null) return;
    List<PsiReferenceExpression> outerReferences = new ArrayList<PsiReferenceExpression>();
    collectReferences(outerCodeBlock, variable, outerReferences);
    replaceReferences(outerReferences, newExpression);
    variable.replace(newVariable);
  }

  private void copyToFinal() throws IncorrectOperationException {
    PsiManager psiManager = myContext.getManager();
    PsiElementFactory factory = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory();
    PsiExpression initializer = factory.createExpressionFromText(myVariable.getName(), myContext);
    String newName = suggestNewName(psiManager.getProject(), myVariable);
    PsiType type = myVariable.getType();
    PsiDeclarationStatement copyDecl = factory.createVariableDeclarationStatement(newName, type, initializer);
    PsiVariable newVariable = (PsiVariable)copyDecl.getDeclaredElements()[0];
    PsiUtil.setModifierProperty(newVariable, PsiModifier.FINAL, true);
    PsiElement statement = getStatementToInsertBefore();
    if (statement == null) return;
    PsiExpression newExpression = factory.createExpressionFromText(newName, myVariable);
    replaceReferences(myContext, myVariable, newExpression);
    if (RefactoringUtil.isLoopOrIf(statement.getParent())) {
      RefactoringUtil.putStatementInLoopBody(copyDecl, statement.getParent(), statement);
    } else {
      statement.getParent().addBefore(copyDecl, statement);
    }
  }

  private PsiElement getStatementToInsertBefore() {
    PsiElement declarationScope = myVariable instanceof PsiParameter
                                  ? ((PsiParameter)myVariable).getDeclarationScope() : PsiUtil.getVariableCodeBlock(myVariable, null);
    if (declarationScope == null) return null;

    PsiElement statement = myContext;
    nextInnerClass:
    do {
      statement = RefactoringUtil.getParentStatement(statement, false);

      if (statement == null || statement.getParent() == null) {
        return null;
      }
      PsiElement element = statement;
      while (element != declarationScope && !(element instanceof PsiFile)) {
        if (element instanceof PsiClass) {
          statement = statement.getParent();
          continue nextInnerClass;
        }
        element = element.getParent();
      }
      return statement;
    }
    while (true);
  }

  private static String suggestNewName(Project project, PsiVariable variable) {
    // new name should not conflict with another variable at the variable declaration level and usage level
    String name = variable.getName();
    // trim last digit to suggest variable names like i1,i2, i3...
    if (name.length() > 1 && Character.isDigit(name.charAt(name.length()-1))) {
      name = name.substring(0,name.length()-1);
    }
    name = "final" + StringUtil.capitalize(StringUtil.trimStart(name, "final"));
    return JavaCodeStyleManager.getInstance(project).suggestUniqueVariableName(name, variable, true);
  }


  private static void replaceReferences(PsiElement context, final PsiVariable variable, final PsiElement newExpression) {
    context.accept(new JavaRecursiveElementVisitor() {
      @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
        if (expression.resolve() == variable)
          try {
            expression.replace(newExpression);
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        super.visitReferenceExpression(expression);
      }
    });
  }

  private static void replaceReferences(List<PsiReferenceExpression> references, PsiElement newExpression) throws IncorrectOperationException {
    for (PsiReferenceExpression reference : references) {
      reference.replace(newExpression);
    }
  }

  private static void collectReferences(PsiElement context, final PsiVariable variable, final List<PsiReferenceExpression> references) {
    context.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
        if (expression.resolve() == variable) references.add(expression);
        super.visitReferenceExpression(expression);
      }
    });
  }

  private static int getQuickFixType(@Nonnull PsiVariable variable) {
    PsiElement outerCodeBlock = PsiUtil.getVariableCodeBlock(variable, null);
    if (outerCodeBlock == null) return -1;
    List<PsiReferenceExpression> outerReferences = new ArrayList<PsiReferenceExpression>();
    collectReferences(outerCodeBlock, variable, outerReferences);

    int type = MAKE_FINAL;
    for (PsiReferenceExpression expression : outerReferences) {
      // if it happens that variable referenced from another inner class, make sure it can be make final from there
      PsiElement innerScope = HighlightControlFlowUtil.getInnerClassVariableReferencedFrom(variable, expression);

      if (innerScope != null) {
        int thisType = MAKE_FINAL;
        if (writtenInside(variable, innerScope)) {
          // cannot make parameter array
          if (variable instanceof PsiParameter) return -1;
          thisType = MAKE_ARRAY;
        }
        if (thisType == MAKE_FINAL
            && !canBeFinal(variable, outerReferences)) {
          thisType = COPY_TO_FINAL;
        }
        type = Math.max(type, thisType);
      }
    }
    return type;
  }

  private static boolean canBeFinal(@Nonnull PsiVariable variable, @Nonnull List<PsiReferenceExpression> references) {
    // if there is at least one assignment to this variable, it cannot be final
    Map<PsiElement, Collection<PsiReferenceExpression>> uninitializedVarProblems = new HashMap<PsiElement, Collection<PsiReferenceExpression>>();
    Map<PsiElement, Collection<ControlFlowUtil.VariableInfo>> finalVarProblems = new HashMap<PsiElement, Collection<ControlFlowUtil.VariableInfo>>();
    for (PsiReferenceExpression expression : references) {
      if (ControlFlowUtil.isVariableAssignedInLoop(expression, variable)) return false;
      HighlightInfo highlightInfo = HighlightControlFlowUtil.checkVariableInitializedBeforeUsage(expression, variable, uninitializedVarProblems,
                                                                                                 variable.getContainingFile());
      if (highlightInfo != null) return false;
      highlightInfo = HighlightControlFlowUtil.checkFinalVariableMightAlreadyHaveBeenAssignedTo(variable, expression, finalVarProblems);
      if (highlightInfo != null) return false;
      if (variable instanceof PsiParameter && PsiUtil.isAccessedForWriting(expression)) return false;
    }
    return true;
  }

  private static boolean writtenInside(PsiVariable variable, PsiElement element) {
    if (element instanceof PsiAssignmentExpression) {
      PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)element;
      PsiExpression lExpression = assignmentExpression.getLExpression();
      if (lExpression instanceof PsiReferenceExpression
          && ((PsiReferenceExpression) lExpression).resolve() == variable)
        return true;
    }
    else if (PsiUtil.isIncrementDecrementOperation(element)) {
      PsiElement operand = element instanceof PsiPostfixExpression ?
                           ((PsiPostfixExpression) element).getOperand() :
                           ((PsiPrefixExpression) element).getOperand();
      if (operand instanceof PsiReferenceExpression
          && ((PsiReferenceExpression) operand).resolve() == variable)
        return true;
    }
    PsiElement[] children = element.getChildren();
    for (PsiElement child : children) {
      if (writtenInside(variable, child)) return true;
    }
    return false;
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
