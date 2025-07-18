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
package com.intellij.java.impl.refactoring.introduceField;

import com.intellij.java.analysis.refactoring.IntroduceConstantHandler;
import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.impl.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.impl.refactoring.util.classMembers.ClassMemberReferencesVisitor;
import com.intellij.java.impl.refactoring.util.occurrences.ExpressionOccurrenceManager;
import com.intellij.java.impl.refactoring.util.occurrences.OccurrenceManager;
import com.intellij.java.language.impl.codeInsight.ExceptionUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorColors;
import consulo.codeEditor.markup.RangeHighlighter;
import consulo.colorScheme.EditorColorsManager;
import consulo.colorScheme.TextAttributes;
import consulo.dataContext.DataContext;
import consulo.document.util.TextRange;
import consulo.language.editor.highlight.HighlightManager;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.introduce.inplace.AbstractInplaceIntroducer;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.project.ui.wm.WindowManager;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class IntroduceConstantHandlerImpl extends BaseExpressionToFieldHandler implements IntroduceConstantHandler {
  public static final String REFACTORING_NAME = RefactoringBundle.message("introduce.constant.title");
  protected InplaceIntroduceConstantPopup myInplaceIntroduceConstantPopup;

  public IntroduceConstantHandlerImpl() {
    super(true);
  }

  protected String getHelpID() {
    return HelpID.INTRODUCE_CONSTANT;
  }

  public void invoke(Project project, PsiExpression[] expressions) {
    for (PsiExpression expression : expressions) {
      final PsiFile file = expression.getContainingFile();
      if (!CommonRefactoringUtil.checkReadOnlyStatus(project, file)) return;
    }
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    super.invoke(project, expressions, null);
  }

  public void invoke(@Nonnull final Project project, final Editor editor, PsiFile file, DataContext dataContext) {
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, file)) return;

    PsiDocumentManager.getInstance(project).commitAllDocuments();
    ElementToWorkOn.processElementToWorkOn(editor, file, REFACTORING_NAME, getHelpID(), project, getElementProcessor(project, editor));
  }

  protected boolean invokeImpl(final Project project, final PsiLocalVariable localVariable, final Editor editor) {
    final PsiElement parent = localVariable.getParent();
    if (!(parent instanceof PsiDeclarationStatement)) {
      LocalizeValue message =
          RefactoringLocalize.cannotPerformRefactoringWithReason(RefactoringLocalize.errorWrongCaretPositionLocalOrExpressionName());
      CommonRefactoringUtil.showErrorHint(project, editor, message.get(), REFACTORING_NAME, getHelpID());
      return false;
    }
    final LocalToFieldHandler localToFieldHandler = new LocalToFieldHandler(project, true){
      @Override
      protected Settings showRefactoringDialog(PsiClass aClass,
                                               PsiLocalVariable local,
                                               PsiExpression[] occurences,
                                               boolean isStatic) {
        return IntroduceConstantHandlerImpl.this.showRefactoringDialog(project, editor, aClass, local.getInitializer(), local.getType(), occurences, local, null);
      }
    };
    return localToFieldHandler.convertLocalToField(localVariable, editor);
  }


  protected Settings showRefactoringDialog(Project project,
                                           final Editor editor,
                                           PsiClass parentClass,
                                           PsiExpression expr,
                                           PsiType type,
                                           PsiExpression[] occurrences,
                                           PsiElement anchorElement,
                                           PsiElement anchorElementIfAll) {
    final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(expr != null ? expr : anchorElement, PsiMethod.class);
    
    PsiLocalVariable localVariable = null;
    if (expr instanceof PsiReferenceExpression) {
      PsiElement ref = ((PsiReferenceExpression)expr).resolve();
      if (ref instanceof PsiLocalVariable) {
        localVariable = (PsiLocalVariable)ref;
      }
    } else if (anchorElement instanceof PsiLocalVariable) {
      localVariable = (PsiLocalVariable)anchorElement;
    }

    String enteredName = null;
    boolean replaceAllOccurrences = true;

    final AbstractInplaceIntroducer activeIntroducer = AbstractInplaceIntroducer.getActiveIntroducer(editor);
    if (activeIntroducer != null) {
      activeIntroducer.stopIntroduce(editor);
      expr = (PsiExpression)activeIntroducer.getExpr();
      localVariable = (PsiLocalVariable)activeIntroducer.getLocalVariable();
      occurrences = (PsiExpression[])activeIntroducer.getOccurrences();
      enteredName = activeIntroducer.getInputName();
      replaceAllOccurrences = activeIntroducer.isReplaceAllOccurrences();
      type = ((InplaceIntroduceConstantPopup)activeIntroducer).getType();
    }
    
    for (PsiExpression occurrence : occurrences) {
      if (RefactoringUtil.isAssignmentLHS(occurrence)) {
        LocalizeValue message =
            RefactoringLocalize.cannotPerformRefactoringWithReason(LocalizeValue.localizeTODO("Selected expression is used for write"));
        CommonRefactoringUtil.showErrorHint(project, editor, message.get(), REFACTORING_NAME, getHelpID());
        highlightError(project, editor, occurrence);
        return null;
      }
    }
    
    if (localVariable == null) {
      final PsiElement errorElement = isStaticFinalInitializer(expr);
      if (errorElement != null) {
        LocalizeValue message =
            RefactoringLocalize.cannotPerformRefactoringWithReason(RefactoringLocalize.selectedExpressionCannotBeAConstantInitializer());
        CommonRefactoringUtil.showErrorHint(project, editor, message.get(), REFACTORING_NAME, getHelpID());
        highlightError(project, editor, errorElement);
        return null;
      }
    }
    else {
      final PsiExpression initializer = localVariable.getInitializer();
      if (initializer == null) {
        LocalizeValue message = RefactoringLocalize.cannotPerformRefactoringWithReason(
          RefactoringLocalize.variableDoesNotHaveAnInitializer(localVariable.getName())
        );
        CommonRefactoringUtil.showErrorHint(project, editor, message.get(), REFACTORING_NAME, getHelpID());
        return null;
      }
      final PsiElement errorElement = isStaticFinalInitializer(initializer);
      if (errorElement != null) {
        LocalizeValue message = RefactoringLocalize.cannotPerformRefactoringWithReason(
          RefactoringLocalize.initializerForVariableCannotBeAConstantInitializer(localVariable.getName()));
        CommonRefactoringUtil.showErrorHint(project, editor, message.get(), REFACTORING_NAME, getHelpID());
        highlightError(project, editor, errorElement);
        return null;
      }
    }

    final TypeSelectorManagerImpl typeSelectorManager = new TypeSelectorManagerImpl(project, type, containingMethod, expr, occurrences);
    if (editor != null && editor.getSettings().isVariableInplaceRenameEnabled() &&
        (expr == null || expr.isPhysical()) && activeIntroducer == null) {
      myInplaceIntroduceConstantPopup =
        new InplaceIntroduceConstantPopup(project, editor, parentClass, expr, localVariable, occurrences,
                                          typeSelectorManager,
                                          anchorElement, anchorElementIfAll,
                                          expr != null ? createOccurrenceManager(expr, parentClass) : null);
      if (myInplaceIntroduceConstantPopup.startInplaceIntroduceTemplate()) {
        return null;
      }
    }


    final IntroduceConstantDialog dialog =
      new IntroduceConstantDialog(project, parentClass, expr, localVariable, localVariable != null, occurrences, getParentClass(),
                                  typeSelectorManager, enteredName);
    dialog.setReplaceAllOccurrences(replaceAllOccurrences);
    dialog.show();
    if (!dialog.isOK()) {
      return null;
    }
    return new Settings(dialog.getEnteredName(), expr, occurrences, dialog.isReplaceAllOccurrences(), true, true,
                                           InitializationPlace.IN_FIELD_DECLARATION, dialog.getFieldVisibility(), localVariable,
                                           dialog.getSelectedType(), dialog.isDeleteVariable(), dialog.getDestinationClass(),
                                           dialog.isAnnotateAsNonNls(),
                                           dialog.introduceEnumConstant());
  }

  private static void highlightError(Project project, Editor editor, PsiElement errorElement) {
    if (editor != null) {
      final TextRange textRange = errorElement.getTextRange();
      HighlightManager.getInstance(project).addRangeHighlight(editor, textRange.getStartOffset(), textRange.getEndOffset(), EditorColors.SEARCH_RESULT_ATTRIBUTES, true, new ArrayList<RangeHighlighter>());
    }
  }

  protected String getRefactoringName() {
    return REFACTORING_NAME;
  }

  @Override
  public AbstractInplaceIntroducer getInplaceIntroducer() {
    return myInplaceIntroduceConstantPopup;
  }

  @Nullable
  private PsiElement isStaticFinalInitializer(PsiExpression expr) {
    PsiClass parentClass = expr != null ? getParentClass(expr) : null;
    if (parentClass == null) return null;
    IsStaticFinalInitializerExpression visitor = new IsStaticFinalInitializerExpression(parentClass, expr);
    expr.accept(visitor);
    return visitor.getElementReference();
  }

  protected OccurrenceManager createOccurrenceManager(final PsiExpression selectedExpr, final PsiClass parentClass) {
    return new ExpressionOccurrenceManager(selectedExpr, parentClass, null);
  }

  private static class IsStaticFinalInitializerExpression extends ClassMemberReferencesVisitor {
    private PsiElement myElementReference = null;
    private final PsiExpression myInitializer;

    public IsStaticFinalInitializerExpression(PsiClass aClass, PsiExpression initializer) {
      super(aClass);
      myInitializer = initializer;
    }

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      final PsiElement psiElement = expression.resolve();
      if ((psiElement instanceof PsiLocalVariable || psiElement instanceof PsiParameter) &&
          !PsiTreeUtil.isAncestor(myInitializer, psiElement, false)) {
        myElementReference = expression;
      }
      else {
        super.visitReferenceExpression(expression);
      }
    }

    @Override
    public void visitCallExpression(PsiCallExpression callExpression) {
      super.visitCallExpression(callExpression);
      final List<PsiClassType> checkedExceptions = ExceptionUtil.getThrownCheckedExceptions(new PsiElement[]{callExpression});
      if (!checkedExceptions.isEmpty()) {
        myElementReference = callExpression;
      }
    }

    protected void visitClassMemberReferenceElement(PsiMember classMember, PsiJavaCodeReferenceElement classMemberReference) {
      if (!classMember.hasModifierProperty(PsiModifier.STATIC)) {
        myElementReference = classMemberReference;
      }
    }

    @Override
    public void visitElement(PsiElement element) {
      if (myElementReference != null) return;
      super.visitElement(element);
    }

    @Nullable
    public PsiElement getElementReference() {
      return myElementReference;
    }
  }

  public PsiClass getParentClass(@Nonnull PsiExpression initializerExpression) {
    final PsiType type = initializerExpression.getType();

    if (type != null && PsiUtil.isConstantExpression(initializerExpression)) {
      if (type instanceof PsiPrimitiveType ||
          PsiType.getJavaLangString(initializerExpression.getManager(), initializerExpression.getResolveScope()).equals(type)) {
        return super.getParentClass(initializerExpression);
      }
    }

    PsiElement parent = initializerExpression.getUserData(ElementToWorkOn.PARENT);
    if (parent == null) parent = initializerExpression;
    PsiClass aClass = PsiTreeUtil.getParentOfType(parent, PsiClass.class);
    while (aClass != null) {
      if (aClass.hasModifierProperty(PsiModifier.STATIC)) return aClass;
      if (aClass.getParent() instanceof PsiJavaFile) return aClass;
      aClass = PsiTreeUtil.getParentOfType(aClass, PsiClass.class);
    }
    return null;
  }

  @Override
  protected boolean accept(ElementToWorkOn elementToWorkOn) {
    final PsiExpression expr = elementToWorkOn.getExpression();
    if (expr != null) {
      return isStaticFinalInitializer(expr) == null;
    }
    final PsiLocalVariable localVariable = elementToWorkOn.getLocalVariable();
    final PsiExpression initializer = localVariable.getInitializer();
    return initializer != null && isStaticFinalInitializer(initializer) == null;
  }

  protected boolean validClass(PsiClass parentClass, Editor editor) {
    return true;
  }

}
