package com.intellij.java.language.impl.psi;

import com.intellij.java.language.impl.psi.impl.source.resolve.reference.impl.providers.GenericReferenceProvider;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;


/**
 * @author VISTALL
 * @since 20/12/2022
 */
public abstract class JavaClassPsiReferenceProvider extends GenericReferenceProvider {
  public abstract PsiReference[] getReferencesByString(String str, PsiElement position, int offsetInPosition);
}
