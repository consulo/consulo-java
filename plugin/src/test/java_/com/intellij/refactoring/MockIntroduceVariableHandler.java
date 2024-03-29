package com.intellij.refactoring;

import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiType;
import consulo.codeEditor.Editor;
import consulo.project.Project;
import consulo.language.editor.refactoring.introduce.inplace.OccurrencesChooser;
import com.intellij.java.impl.refactoring.introduceVariable.InputValidator;
import com.intellij.java.impl.refactoring.introduceVariable.IntroduceVariableBase;
import com.intellij.java.impl.refactoring.introduceVariable.IntroduceVariableSettings;
import com.intellij.java.impl.refactoring.ui.TypeSelectorManagerImpl;
import consulo.util.collection.MultiMap;
import junit.framework.Assert;
import org.jetbrains.annotations.NonNls;

/**
 *  @author dsl
 */
class MockIntroduceVariableHandler extends IntroduceVariableBase {
  private final String myName;
  private final boolean myReplaceAll;
  private final boolean myDeclareFinal;
  private final boolean myReplaceLValues;
  private final String myExpectedTypeCanonicalName;
  private final boolean myLookForType;

  public MockIntroduceVariableHandler(@NonNls final String name, final boolean replaceAll,
                                      final boolean declareFinal, final boolean replaceLValues,
                                      @NonNls final String expectedTypeCanonicalName) {

    this(name, replaceAll, declareFinal, replaceLValues, expectedTypeCanonicalName, false);
  }

  public MockIntroduceVariableHandler(@NonNls final String name, final boolean replaceAll,
                                      final boolean declareFinal, final boolean replaceLValues,
                                      @NonNls final String expectedTypeCanonicalName, boolean lookForType) {

    myName = name;
    myReplaceAll = replaceAll;
    myDeclareFinal = declareFinal;
    myReplaceLValues = replaceLValues;
    myExpectedTypeCanonicalName = expectedTypeCanonicalName;
    myLookForType = lookForType;
  }

  @Override
  public IntroduceVariableSettings getSettings(Project project, Editor editor,
                                               PsiExpression expr, final PsiExpression[] occurrences,
                                               TypeSelectorManagerImpl typeSelectorManager,
                                               final boolean declareFinalIfAll,
                                               boolean anyAssignmentLHS,
                                               InputValidator validator,
                                               PsiElement anchor, final OccurrencesChooser.ReplaceChoice replaceChoice) {
    final PsiType type = myLookForType ? findType(typeSelectorManager.getTypesForAll(), typeSelectorManager.getDefaultType())
                                       : typeSelectorManager.getDefaultType();
    Assert.assertTrue(type.getInternalCanonicalText(), type.getInternalCanonicalText().equals(myExpectedTypeCanonicalName));
    IntroduceVariableSettings introduceVariableSettings = new IntroduceVariableSettings() {
      @Override
      public String getEnteredName() {
        return myName;
      }

      @Override
      public boolean isReplaceAllOccurrences() {
        return myReplaceAll && occurrences.length > 1;
      }

      @Override
      public boolean isDeclareFinal() {
        return myDeclareFinal || isReplaceAllOccurrences() && declareFinalIfAll;
      }

      @Override
      public boolean isReplaceLValues() {
        return myReplaceLValues;
      }

      @Override
      public PsiType getSelectedType() {
        return type;
      }

      @Override
      public boolean isOK() {
        return true;
      }
    };
    final boolean validationResult = validator.isOK(introduceVariableSettings);
    assertValidationResult(validationResult);
    return introduceVariableSettings;
  }

  protected void assertValidationResult(final boolean validationResult) {
    Assert.assertTrue(validationResult);
  }

  @Override
  protected void showErrorMessage(Project project, Editor editor, String message) {
    throw new RuntimeException("Error message:" + message);
  }

  @Override
  protected boolean reportConflicts(final MultiMap<PsiElement,String> conflicts, final Project project, IntroduceVariableSettings dialog) {
    return false;
  }

  private PsiType findType(final PsiType[] candidates, PsiType defaultType) {
    for (PsiType candidate : candidates) {
      if (candidate.equalsToText(myExpectedTypeCanonicalName)) return candidate;
    }
    return defaultType;
  }
}
