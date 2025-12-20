package com.intellij.refactoring;

import consulo.project.Project;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiLocalVariable;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.impl.refactoring.introduceField.BaseExpressionToFieldHandler;
import com.intellij.java.impl.refactoring.introduceField.LocalToFieldHandler;

/**
 * @author ven
 */
public class MockLocalToFieldHandler extends LocalToFieldHandler {
  private final boolean myMakeEnumConstant;
  public MockLocalToFieldHandler(Project project, boolean isConstant, boolean makeEnumConstant) {
    super(project, isConstant);
    myMakeEnumConstant = makeEnumConstant;
  }

  @Override
  protected BaseExpressionToFieldHandler.Settings showRefactoringDialog(PsiClass aClass, PsiLocalVariable local, PsiExpression[] occurences,
                                                                        boolean isStatic) {
    return new BaseExpressionToFieldHandler.Settings("xxx", null, occurences, true, isStatic, true, BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION,
                                                     PsiModifier.PRIVATE, local, local.getType(), false, aClass, true, myMakeEnumConstant);
  }
}
