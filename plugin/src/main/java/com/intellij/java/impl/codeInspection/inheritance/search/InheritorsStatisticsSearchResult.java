package com.intellij.java.impl.codeInspection.inheritance.search;


import com.intellij.java.language.psi.PsiClass;

/**
 * @author Dmitry Batkovich
 */
public class InheritorsStatisticsSearchResult {

  private final PsiClass myClass;
  private final int myPercent;

  InheritorsStatisticsSearchResult(PsiClass aClass, int percent) {
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
