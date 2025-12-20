package com.intellij.java.impl.codeInspection.inheritance.search;

import jakarta.annotation.Nonnull;

import com.intellij.java.language.psi.PsiClass;

/**
 * @author Dmitry Batkovich
 */
public class InheritorsStatisticsSearchResult {

  @Nonnull
  private final PsiClass myClass;
  private final int myPercent;

  InheritorsStatisticsSearchResult(@Nonnull PsiClass aClass, int percent) {
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
