package com.intellij.java.analysis.refactoring;

import com.intellij.java.language.psi.PsiExpression;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.project.Project;

/**
 * @author VISTALL
 * @since 20/12/2022
 */
public interface IntroduceConstantHandler extends RefactoringActionHandler {
  public void invoke(Project project, PsiExpression[] expressions);
}
