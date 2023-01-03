package com.intellij.java.language.impl.psi;

import com.intellij.java.language.impl.psi.impl.source.resolve.reference.impl.providers.GenericReferenceProvider;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;

import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 20/12/2022
 */
public abstract class JavaClassPsiReferenceProvider extends GenericReferenceProvider {
  @Nonnull
  public abstract PsiReference[] getReferencesByString(String str, @Nonnull PsiElement position, int offsetInPosition);
}
