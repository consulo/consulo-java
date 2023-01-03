package com.intellij.java.impl.codeInsight.template;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.template.context.EverywhereContextType;
import consulo.language.psi.PsiElement;

import javax.annotation.Nonnull;

@ExtensionImpl
public class JavaGenericContextType extends JavaCodeContextType {
  public JavaGenericContextType() {
    super("JAVA_CODE", "Java", EverywhereContextType.class);
  }

  @Override
  protected boolean isInContext(@Nonnull PsiElement element) {
    return true;
  }
}
