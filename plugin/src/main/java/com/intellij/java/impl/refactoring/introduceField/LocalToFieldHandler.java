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

import com.intellij.java.impl.codeInsight.CodeInsightUtil;
import com.intellij.java.impl.refactoring.util.EnumConstantsUtil;
import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.language.codeInsight.TestFrameworks;
import com.intellij.java.language.impl.codeInsight.PsiClassListCellRenderer;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.util.VisibilityUtil;
import consulo.application.ApplicationManager;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorPopupHelper;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.introduce.inplace.AbstractInplaceIntroducer;
import consulo.language.editor.ui.PopupNavigationUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.resolve.PsiElementProcessor;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.ex.popup.JBPopup;
import consulo.undoRedo.CommandProcessor;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.java.impl.refactoring.introduceField.BaseExpressionToFieldHandler.InitializationPlace.IN_CONSTRUCTOR;
import static com.intellij.java.impl.refactoring.introduceField.BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION;

public abstract class LocalToFieldHandler {
  private static final Logger LOG = Logger.getInstance(LocalToFieldHandler.class);

  private static final String REFACTORING_NAME = RefactoringBundle.message("convert.local.to.field.title");
  private final Project myProject;
  private final boolean myIsConstant;

  public LocalToFieldHandler(Project project, boolean isConstant) {
    myProject = project;
    myIsConstant = isConstant;
  }

  protected abstract BaseExpressionToFieldHandler.Settings showRefactoringDialog(PsiClass aClass, PsiLocalVariable local, PsiExpression[] occurences, boolean isStatic);

  public boolean convertLocalToField(final PsiLocalVariable local, final Editor editor) {
    boolean tempIsStatic = myIsConstant;
    PsiElement parent = local.getParent();
    List<PsiClass> classes = new ArrayList<PsiClass>();
    while (parent != null && parent.getContainingFile() != null) {
      if (parent instanceof PsiClass && !(myIsConstant && parent instanceof PsiAnonymousClass)) {
        classes.add((PsiClass) parent);
      }
      /*if (parent instanceof PsiFile && JspPsiUtil.isInJspFile(parent)) {
        String message = RefactoringBundle.message("error.not.supported.for.jsp", REFACTORING_NAME);
        CommonRefactoringUtil.showErrorHint(myProject, editor, message, REFACTORING_NAME, HelpID.LOCAL_TO_FIELD);
        return false;
      }   */
      if (parent instanceof PsiModifierListOwner && ((PsiModifierListOwner) parent).hasModifierProperty(PsiModifier.STATIC)) {
        tempIsStatic = true;
      }
      parent = parent.getParent();
    }

    if (classes.isEmpty()) return false;
    final AbstractInplaceIntroducer activeIntroducer = AbstractInplaceIntroducer.getActiveIntroducer(editor);
    final boolean shouldSuggestDialog = activeIntroducer instanceof InplaceIntroduceConstantPopup &&
        activeIntroducer.startsOnTheSameElement(null, local);
    if (classes.size() == 1 || ApplicationManager.getApplication().isUnitTestMode() || shouldSuggestDialog) {
      if (convertLocalToField(local, classes.get(getChosenClassIndex(classes)), editor, tempIsStatic)) return false;
    } else {
      final boolean isStatic = tempIsStatic;
      JBPopup popup = PopupNavigationUtil.getPsiElementPopup(classes.toArray(new PsiClass[classes.size()]), new PsiClassListCellRenderer(), "Choose class to introduce " + (myIsConstant ? "constant" : "field"), new PsiElementProcessor<PsiClass>() {
        @Override
        public boolean execute(@Nonnull PsiClass aClass) {
          convertLocalToField(local, aClass, editor, isStatic);
          return false;
        }
      });
      EditorPopupHelper.getInstance().showPopupInBestPositionFor(editor, popup);
    }

    return true;
  }

  protected int getChosenClassIndex(List<PsiClass> classes) {
    return classes.size() - 1;
  }

  private boolean convertLocalToField(PsiLocalVariable local, PsiClass aClass, Editor editor, boolean isStatic) {
    final PsiExpression[] occurences = CodeInsightUtil.findReferenceExpressions(RefactoringUtil.getVariableScope(local), local);
    if (editor != null) {
      RefactoringUtil.highlightAllOccurrences(myProject, occurences, editor);
    }

    final BaseExpressionToFieldHandler.Settings settings = showRefactoringDialog(aClass, local, occurences, isStatic);
    if (settings == null) return true;
    //LocalToFieldDialog dialog = new LocalToFieldDialog(project, aClass, local, isStatic);
    final PsiClass destinationClass = settings.getDestinationClass();
    boolean rebindNeeded = false;
    if (destinationClass != null) {
      aClass = destinationClass;
      rebindNeeded = true;
    }

    final PsiClass aaClass = aClass;
    final boolean rebindNeeded1 = rebindNeeded;
    final Runnable runnable =
        new IntroduceFieldRunnable(rebindNeeded1, local, aaClass, settings, isStatic, occurences);
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(runnable);
      }
    }, REFACTORING_NAME, null);
    return false;
  }

  private static PsiField createField(PsiLocalVariable local, PsiType forcedType, String fieldName, boolean includeInitializer) {
    @NonNls StringBuilder pattern = new StringBuilder();
    pattern.append("private int ");
    pattern.append(fieldName);
    if (local.getInitializer() == null) {
      includeInitializer = false;
    }
    if (includeInitializer) {
      pattern.append("=0");
    }
    pattern.append(";");
    final Project project = local.getProject();
    PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    try {
      PsiField field = factory.createFieldFromText(pattern.toString(), null);

      field.getTypeElement().replace(factory.createTypeElement(forcedType));
      if (includeInitializer) {
        PsiExpression initializer =
            RefactoringUtil.convertInitializerToNormalExpression(local.getInitializer(), forcedType);
        field.getInitializer().replace(initializer);
      }

      for (PsiAnnotation annotation : local.getModifierList().getAnnotations()) {
        field.getModifierList().add(annotation.copy());
      }
      return field;
    } catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
  }

  private static PsiStatement createAssignment(PsiLocalVariable local, String fieldname, PsiElementFactory factory) {
    try {
      String pattern = fieldname + "=0;";
      PsiExpressionStatement statement = (PsiExpressionStatement) factory.createStatementFromText(pattern, null);
      statement = (PsiExpressionStatement) CodeStyleManager.getInstance(local.getProject()).reformat(statement);

      PsiAssignmentExpression expr = (PsiAssignmentExpression) statement.getExpression();
      final PsiExpression initializer = RefactoringUtil.convertInitializerToNormalExpression(local.getInitializer(), local.getType());
      expr.getRExpression().replace(initializer);

      return statement;
    } catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
  }

  private static PsiStatement addInitializationToSetUp(final PsiLocalVariable local, final PsiField field, final PsiElementFactory factory)
      throws IncorrectOperationException {
    PsiMethod inClass = TestFrameworks.getInstance().findOrCreateSetUpMethod(field.getContainingClass());
    assert inClass != null;
    PsiStatement assignment = createAssignment(local, field.getName(), factory);
    final PsiCodeBlock body = inClass.getBody();
    assert body != null;
    if (PsiTreeUtil.isAncestor(body, local, false)) {
      assignment = (PsiStatement) body.addBefore(assignment, PsiTreeUtil.getParentOfType(local, PsiStatement.class));
    } else {
      assignment = (PsiStatement) body.add(assignment);
    }
    local.delete();
    return assignment;
  }

  private static PsiStatement addInitializationToConstructors(PsiLocalVariable local, PsiField field, PsiMethod enclosingConstructor,
                                                              PsiElementFactory factory) throws IncorrectOperationException {
    PsiClass aClass = field.getContainingClass();
    PsiMethod[] constructors = aClass.getConstructors();
    PsiStatement assignment = createAssignment(local, field.getName(), factory);
    boolean added = false;
    for (PsiMethod constructor : constructors) {
      if (constructor == enclosingConstructor) continue;
      PsiCodeBlock body = constructor.getBody();
      if (body == null) continue;
      PsiStatement[] statements = body.getStatements();
      if (statements.length > 0) {
        PsiStatement first = statements[0];
        if (first instanceof PsiExpressionStatement) {
          PsiExpression expression = ((PsiExpressionStatement) first).getExpression();
          if (expression instanceof PsiMethodCallExpression) {
            @NonNls String text = ((PsiMethodCallExpression) expression).getMethodExpression().getText();
            if ("this".equals(text)) {
              continue;
            }
            if ("super".equals(text) && enclosingConstructor == null && PsiTreeUtil.isAncestor(constructor, local, false)) {
              local.delete();
              return (PsiStatement) body.addAfter(assignment, first);
            }
          }
        }
        if (enclosingConstructor == null && PsiTreeUtil.isAncestor(constructor, local, false)) {
          local.delete();
          return (PsiStatement) body.addBefore(assignment, first);
        }
      }

      assignment = (PsiStatement) body.add(assignment);
      added = true;
    }
    if (!added && enclosingConstructor == null) {
      if (aClass instanceof PsiAnonymousClass) {
        final PsiClassInitializer classInitializer = (PsiClassInitializer) aClass.addAfter(factory.createClassInitializer(), field);
        assignment = (PsiStatement) classInitializer.getBody().add(assignment);
      } else {
        PsiMethod constructor = (PsiMethod) aClass.add(factory.createConstructor());
        assignment = (PsiStatement) constructor.getBody().add(assignment);
      }
    }

    if (enclosingConstructor == null) local.delete();
    return assignment;
  }

  static class IntroduceFieldRunnable implements Runnable {
    private final String myVariableName;
    private final String myFieldName;
    private final boolean myRebindNeeded;
    private final PsiLocalVariable myLocal;
    private final Project myProject;
    private final PsiClass myDestinationClass;
    private final BaseExpressionToFieldHandler.Settings mySettings;
    private final BaseExpressionToFieldHandler.InitializationPlace myInitializerPlace;
    private final PsiExpression[] myOccurences;
    private PsiField myField;
    private PsiStatement myAssignmentStatement;

    public IntroduceFieldRunnable(boolean rebindNeeded,
                                  PsiLocalVariable local,
                                  PsiClass aClass,
                                  BaseExpressionToFieldHandler.Settings settings,
                                  boolean isStatic,
                                  PsiExpression[] occurrences) {
      myVariableName = local.getName();
      myFieldName = settings.getFieldName();
      myRebindNeeded = rebindNeeded;
      myLocal = local;
      myProject = local.getProject();
      myDestinationClass = aClass;
      mySettings = settings;
      myInitializerPlace = settings.getInitializerPlace();
      myOccurences = occurrences;
    }

    public void run() {
      try {
        final boolean rebindNeeded2 = !myVariableName.equals(myFieldName) || myRebindNeeded;
        final PsiReference[] refs;
        if (rebindNeeded2) {
          refs = ReferencesSearch.search(myLocal, GlobalSearchScope.projectScope(myProject), false).toArray(new PsiReference[0]);
        } else {
          refs = null;
        }

        final PsiMethod enclosingConstructor = BaseExpressionToFieldHandler.getEnclosingConstructor(myDestinationClass, myLocal);
        myField = mySettings.isIntroduceEnumConstant() ? EnumConstantsUtil.createEnumConstant(myDestinationClass, myLocal, myFieldName)
            : createField(myLocal, mySettings.getForcedType(), myFieldName, myInitializerPlace == IN_FIELD_DECLARATION);
        myField = (PsiField) myDestinationClass.add(myField);
        BaseExpressionToFieldHandler.setModifiers(myField, mySettings);
        if (!mySettings.isIntroduceEnumConstant()) {
          VisibilityUtil.fixVisibility(myOccurences, myField, mySettings.getFieldVisibility());
        }

        myLocal.normalizeDeclaration();
        PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement) myLocal.getParent();
        final BaseExpressionToFieldHandler.InitializationPlace finalInitializerPlace;
        if (myLocal.getInitializer() == null) {
          finalInitializerPlace = IN_FIELD_DECLARATION;
        } else {
          finalInitializerPlace = myInitializerPlace;
        }
        final PsiElementFactory factory = JavaPsiFacade.getInstance(myProject).getElementFactory();

        switch (finalInitializerPlace) {
          case IN_FIELD_DECLARATION:
            declarationStatement.delete();
            break;

          case IN_CURRENT_METHOD:
            PsiStatement statement = createAssignment(myLocal, myFieldName, factory);
            myAssignmentStatement = (PsiStatement) declarationStatement.replace(statement);
            break;

          case IN_CONSTRUCTOR:
            myAssignmentStatement = addInitializationToConstructors(myLocal, myField, enclosingConstructor, factory);
            break;
          case IN_SETUP_METHOD:
            myAssignmentStatement = addInitializationToSetUp(myLocal, myField, factory);
        }

        if (enclosingConstructor != null && myInitializerPlace == IN_CONSTRUCTOR) {
          PsiStatement statement = createAssignment(myLocal, myFieldName, factory);
          myAssignmentStatement = (PsiStatement) declarationStatement.replace(statement);
        }

        if (rebindNeeded2) {
          for (final PsiReference reference : refs) {
            if (reference != null) {
              //expr = RefactoringUtil.outermostParenthesizedExpression(expr);
              RefactoringUtil.replaceOccurenceWithFieldRef((PsiExpression) reference, myField, myDestinationClass);
              //replaceOccurenceWithFieldRef((PsiExpression)reference, field, aaClass);
            }
          }
          //RefactoringUtil.renameVariableReferences(local, pPrefix + fieldName, GlobalSearchScope.projectScope(myProject));
        }
      } catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }

    public PsiField getField() {
      return myField;
    }

    public PsiStatement getAssignmentStatement() {
      return myAssignmentStatement;
    }
  }
}
