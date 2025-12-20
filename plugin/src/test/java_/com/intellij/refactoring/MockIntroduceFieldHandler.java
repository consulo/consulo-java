package com.intellij.refactoring;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiType;
import consulo.codeEditor.Editor;
import consulo.project.Project;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import consulo.language.editor.refactoring.rename.SuggestedNameInfo;
import com.intellij.java.language.psi.codeStyle.VariableKind;
import com.intellij.java.impl.refactoring.introduceField.IntroduceFieldHandler;

/**
 * @author ven
 */
public class MockIntroduceFieldHandler extends IntroduceFieldHandler {
  private final InitializationPlace myInitializationPlace;
  private final boolean myDeclareStatic;

  public MockIntroduceFieldHandler(InitializationPlace initializationPlace, boolean declareStatic) {
    myInitializationPlace = initializationPlace;
    myDeclareStatic = declareStatic;
  }

  @Override
  protected Settings showRefactoringDialog(Project project, Editor editor, PsiClass parentClass, PsiExpression expr, PsiType type,
                                           PsiExpression[] occurrences, PsiElement anchorElement, PsiElement anchorElementIfAll) {
    SuggestedNameInfo name = JavaCodeStyleManager.getInstance(project).suggestVariableName(VariableKind.FIELD, null, expr, type);
    return new Settings(name.names[0],  expr, occurrences, true, myDeclareStatic, true, myInitializationPlace,
            PsiModifier.PUBLIC,
            null,
            getFieldType(type), true, (TargetDestination)null, false, false);
  }

  protected PsiType getFieldType(PsiType type) {
    return type;
  }
}
