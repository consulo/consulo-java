package com.intellij.java.impl.codeInspection.inheritance.search;

import javax.annotation.Nonnull;

import com.intellij.java.language.psi.PsiClass;

/**
 * @author Dmitry Batkovich
 */
public class InheritorsStatisticsSearchResult {

  @Nonnull
  private final PsiClass myClass;
  private final int myPercent;

  InheritorsStatisticsSearchResult(final @Nonnull PsiClass aClass, final int percent) {
    myClass = aClass;
    myPercent = percent;
  }

  public PsiClass getPsiClass() {
    return myClass;
  }

  public int getPercent() {
    return myPercent;
  }

}
