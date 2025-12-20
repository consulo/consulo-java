/*
 * User: anna
 * Date: 29-Oct-2008
 */
package com.intellij.refactoring;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiType;
import consulo.codeEditor.Editor;
import consulo.project.Project;
import com.intellij.java.impl.refactoring.introduceField.IntroduceConstantHandlerImpl;

public class MockIntroduceConstantHandler extends IntroduceConstantHandlerImpl {
  private final PsiClass myTargetClass;

  public MockIntroduceConstantHandler(PsiClass targetClass) {
    myTargetClass = targetClass;
  }

  @Override
  protected Settings showRefactoringDialog(Project project, Editor editor, PsiClass parentClass, PsiExpression expr,
                                           PsiType type, PsiExpression[] occurrences, PsiElement anchorElement,
                                           PsiElement anchorElementIfAll) {
    return new Settings("xxx", expr, occurrences, true, true, true, InitializationPlace.IN_FIELD_DECLARATION, getVisibility(), null, null, false,
                        myTargetClass != null ? myTargetClass : parentClass, false, false);
  }

  protected String getVisibility() {
    return PsiModifier.PUBLIC;
  }
}
