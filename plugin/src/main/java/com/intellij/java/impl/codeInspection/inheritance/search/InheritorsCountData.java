package com.intellij.java.impl.codeInspection.inheritance.search;

import javax.annotation.Nonnull;

import com.intellij.java.language.psi.PsiClass;

/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
class InheritorsCountData implements Comparable<InheritorsCountData> {
  @Nonnull
  private final PsiClass myPsiClass;
  private final int myInheritorsCount;

  public InheritorsCountData(@Nonnull final PsiClass psiClass, final int inheritorsCount) {
    myPsiClass = psiClass;
    myInheritorsCount = inheritorsCount;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || !(o instanceof InheritorsCountData)) return false;

    final InheritorsCountData data = (InheritorsCountData)o;
    return myInheritorsCount == data.myInheritorsCount && myPsiClass.equals(data.myPsiClass);
  }

  @Nonnull
  public PsiClass getPsiClass() {
    return myPsiClass;
  }

  public int getInheritorsCount() {
    return myInheritorsCount;
  }

  @Override
  public int hashCode() {
    final String name = myPsiClass.getName();
    int result = name != null ? name.hashCode() : 0;
    return 31 * result + myInheritorsCount;
  }

  @Override
  public int compareTo(@Nonnull final InheritorsCountData that) {
    final int sub = -this.myInheritorsCount + that.myInheritorsCount;
    if (sub != 0) return sub;
    return String.CASE_INSENSITIVE_ORDER.compare(this.myPsiClass.getName(), that.myPsiClass.getName());
  }

  public String toString() {
    return String.format("%s:%d", myPsiClass, myInheritorsCount);
  }
}