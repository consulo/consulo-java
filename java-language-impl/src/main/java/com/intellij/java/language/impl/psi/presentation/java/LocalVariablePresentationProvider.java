package com.intellij.java.language.impl.psi.presentation.java;

import com.intellij.java.language.psi.PsiLocalVariable;
import consulo.annotation.component.ExtensionImpl;


/**
 * @author VISTALL
 * @since 03-Sep-22
 */
@ExtensionImpl
public class LocalVariablePresentationProvider extends VariablePresentationProvider<PsiLocalVariable> {
  @Override
  public Class<PsiLocalVariable> getItemClass() {
    return PsiLocalVariable.class;
  }
}
