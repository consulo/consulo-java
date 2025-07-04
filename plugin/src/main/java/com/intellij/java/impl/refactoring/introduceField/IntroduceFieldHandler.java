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

import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.impl.refactoring.introduceParameter.AbstractJavaInplaceIntroducer;
import com.intellij.java.impl.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.java.impl.refactoring.util.occurrences.*;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.codeEditor.Editor;
import consulo.dataContext.DataContext;
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

import java.util.List;

public class IntroduceFieldHandler extends BaseExpressionToFieldHandler implements JavaIntroduceFieldHandlerBase {

  public static final String REFACTORING_NAME = RefactoringBundle.message("introduce.field.title");
  private static final MyOccurrenceFilter MY_OCCURRENCE_FILTER = new MyOccurrenceFilter();
  private InplaceIntroduceFieldPopup myInplaceIntroduceFieldPopup;

  public IntroduceFieldHandler() {
    super(false);
  }

  protected String getRefactoringName() {
    return REFACTORING_NAME;
  }

  protected boolean validClass(PsiClass parentClass, Editor editor) {
    if (parentClass.isInterface()) {
      LocalizeValue message = RefactoringLocalize.cannotPerformRefactoringWithReason(RefactoringLocalize.cannotIntroduceFieldInInterface());
      CommonRefactoringUtil.showErrorHint(parentClass.getProject(), editor, message.get(), REFACTORING_NAME, getHelpID());
      return false;
    }
    else {
      return true;
    }
  }

  protected String getHelpID() {
    return HelpID.INTRODUCE_FIELD;
  }

  public void invoke(@Nonnull final Project project, final Editor editor, PsiFile file, DataContext dataContext) {
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, file)) return;
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    ElementToWorkOn.processElementToWorkOn(editor,
                                           file,
                                           REFACTORING_NAME,
                                           HelpID.INTRODUCE_FIELD,
                                           project,
                                           getElementProcessor(project, editor));
  }

  protected Settings showRefactoringDialog(Project project, Editor editor, PsiClass parentClass, PsiExpression expr,
                                           PsiType type,
                                           PsiExpression[] occurrences, PsiElement anchorElement, PsiElement anchorElementIfAll) {
    final AbstractInplaceIntroducer activeIntroducer = AbstractInplaceIntroducer.getActiveIntroducer(editor);

    PsiLocalVariable localVariable = null;
    if (anchorElement instanceof PsiLocalVariable) {
      localVariable = (PsiLocalVariable)anchorElement;
    }
    else if (expr instanceof PsiReferenceExpression) {
      PsiElement ref = ((PsiReferenceExpression)expr).resolve();
      if (ref instanceof PsiLocalVariable) {
        localVariable = (PsiLocalVariable)ref;
      }
    }

    String enteredName = null;
    boolean replaceAll = false;
    if (activeIntroducer != null) {
      activeIntroducer.stopIntroduce(editor);
      expr = (PsiExpression)activeIntroducer.getExpr();
      localVariable = (PsiLocalVariable)activeIntroducer.getLocalVariable();
      occurrences = (PsiExpression[])activeIntroducer.getOccurrences();
      enteredName = activeIntroducer.getInputName();
      replaceAll = activeIntroducer.isReplaceAllOccurrences();
      type = ((AbstractJavaInplaceIntroducer)activeIntroducer).getType();
      IntroduceFieldDialog.ourLastInitializerPlace = ((InplaceIntroduceFieldPopup)activeIntroducer).getInitializerPlace();
    }

    final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(expr != null ? expr : anchorElement, PsiMethod.class);
    final PsiModifierListOwner staticParentElement = PsiUtil.getEnclosingStaticElement(getElement(expr, anchorElement), parentClass);
    boolean declareStatic = staticParentElement != null;

    boolean isInSuperOrThis = false;
    if (!declareStatic) {
      for (int i = 0; !declareStatic && i < occurrences.length; i++) {
        PsiExpression occurrence = occurrences[i];
        isInSuperOrThis = isInSuperOrThis(occurrence);
        declareStatic = isInSuperOrThis;
      }
    }
    int occurrencesNumber = occurrences.length;
    final boolean currentMethodConstructor = containingMethod != null && containingMethod.isConstructor();
    final boolean allowInitInMethod =
      (!currentMethodConstructor || !isInSuperOrThis) && (anchorElement instanceof PsiLocalVariable || anchorElement instanceof PsiStatement);
    final boolean allowInitInMethodIfAll = (!currentMethodConstructor || !isInSuperOrThis) && anchorElementIfAll instanceof PsiStatement;

    if (editor != null && editor.getSettings().isVariableInplaceRenameEnabled() &&
      (expr == null || expr.isPhysical()) && activeIntroducer == null) {
      myInplaceIntroduceFieldPopup =
        new InplaceIntroduceFieldPopup(localVariable, parentClass, declareStatic, currentMethodConstructor, occurrences, expr,
                                       new TypeSelectorManagerImpl(project, type, containingMethod, expr, occurrences), editor,
                                       allowInitInMethod, allowInitInMethodIfAll, anchorElement, anchorElementIfAll,
                                       expr != null ? createOccurrenceManager(expr, parentClass) : null, project);
      if (myInplaceIntroduceFieldPopup.startInplaceIntroduceTemplate()) {
        return null;
      }
    }

    IntroduceFieldDialog dialog = new IntroduceFieldDialog(
      project, parentClass, expr, localVariable,
      currentMethodConstructor,
      localVariable != null, declareStatic, occurrences,
      allowInitInMethod, allowInitInMethodIfAll,
      new TypeSelectorManagerImpl(project, type, containingMethod, expr, occurrences),
      enteredName
    );
    dialog.setReplaceAllOccurrences(replaceAll);
    dialog.show();

    if (!dialog.isOK()) {
      return null;
    }

    if (!dialog.isDeleteVariable()) {
      localVariable = null;
    }


    return new Settings(dialog.getEnteredName(), expr, occurrences, dialog.isReplaceAllOccurrences(),
                        declareStatic, dialog.isDeclareFinal(),
                        dialog.getInitializerPlace(), dialog.getFieldVisibility(),
                        localVariable,
                        dialog.getFieldType(), localVariable != null, (TargetDestination)null, false, false);
  }

  @Override
  protected boolean accept(ElementToWorkOn elementToWorkOn) {
    return true;
  }

  private static PsiElement getElement(PsiExpression expr, PsiElement anchorElement) {
    PsiElement element = null;
    if (expr != null) {
      element = expr.getUserData(ElementToWorkOn.PARENT);
      if (element == null) element = expr;
    }
    if (element == null) element = anchorElement;
    return element;
  }

  @Override
  public AbstractInplaceIntroducer getInplaceIntroducer() {
    return myInplaceIntroduceFieldPopup;
  }

  private static boolean isInSuperOrThis(PsiExpression occurrence) {
    return !NotInSuperCallOccurrenceFilter.INSTANCE.isOK(occurrence) || !NotInThisCallFilter.INSTANCE.isOK(occurrence);
  }

  protected OccurrenceManager createOccurrenceManager(final PsiExpression selectedExpr, final PsiClass parentClass) {
    final OccurrenceFilter occurrenceFilter = isInSuperOrThis(selectedExpr) ? null : MY_OCCURRENCE_FILTER;
    return new ExpressionOccurrenceManager(selectedExpr, parentClass, occurrenceFilter, true);
  }

  protected boolean invokeImpl(final Project project, PsiLocalVariable localVariable, final Editor editor) {
    final PsiElement parent = localVariable.getParent();
    if (!(parent instanceof PsiDeclarationStatement)) {
      LocalizeValue message =
          RefactoringLocalize.cannotPerformRefactoringWithReason(RefactoringLocalize.errorWrongCaretPositionLocalOrExpressionName());
      CommonRefactoringUtil.showErrorHint(project, editor, message.get(), REFACTORING_NAME, getHelpID());
      return false;
    }
    LocalToFieldHandler localToFieldHandler = new LocalToFieldHandler(project, false) {
      @Override
      protected Settings showRefactoringDialog(PsiClass aClass,
                                               PsiLocalVariable local,
                                               PsiExpression[] occurences,
                                               boolean isStatic) {
        final PsiStatement statement = PsiTreeUtil.getParentOfType(local, PsiStatement.class);
        return IntroduceFieldHandler.this.showRefactoringDialog(project,
                                                                editor,
                                                                aClass,
                                                                local.getInitializer(),
                                                                local.getType(),
                                                                occurences,
                                                                local,
                                                                statement);
      }

      @Override
      protected int getChosenClassIndex(List<PsiClass> classes) {
        return IntroduceFieldHandler.this.getChosenClassIndex(classes);
      }
    };
    return localToFieldHandler.convertLocalToField(localVariable, editor);
  }

  public void invoke(@Nonnull Project project, PsiElement element, @Nullable Editor editor) {
    if (element instanceof PsiExpression) {
      invokeImpl(project, (PsiExpression)element, editor);
    }
    else if (element instanceof PsiLocalVariable) {
      invokeImpl(project, (PsiLocalVariable)element, editor);
    }
    else {
      LOG.error("elements[0] should be PsiExpression or PsiLocalVariable; was " + element);
    }
  }

  protected int getChosenClassIndex(List<PsiClass> classes) {
    return classes.size() - 1;
  }

  private static class MyOccurrenceFilter implements OccurrenceFilter {
    public boolean isOK(PsiExpression occurrence) {
      return !isInSuperOrThis(occurrence);
    }
  }
}
