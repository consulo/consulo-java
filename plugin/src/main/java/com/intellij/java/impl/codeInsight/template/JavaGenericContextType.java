package com.intellij.java.impl.codeInsight.template;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.template.context.EverywhereContextType;
import consulo.language.psi.PsiElement;

import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class JavaGenericContextType extends JavaCodeContextType implements JavaLikeCodeContextType {
  public JavaGenericContextType() {
    super("JAVA_CODE", LocalizeValue.localizeTODO("Java"), EverywhereContextType.class);
  }

  @Override
  protected boolean isInContext(@Nonnull PsiElement element) {
    return true;
  }
}
