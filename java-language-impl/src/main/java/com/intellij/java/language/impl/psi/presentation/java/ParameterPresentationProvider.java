package com.intellij.java.language.impl.psi.presentation.java;

import com.intellij.java.language.psi.PsiParameter;
import consulo.annotation.component.ExtensionImpl;

import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 03-Sep-22
 */
@ExtensionImpl
public class ParameterPresentationProvider extends VariablePresentationProvider<PsiParameter> {
  @Nonnull
  @Override
  public Class<PsiParameter> getItemClass() {
    return PsiParameter.class;
  }
}
