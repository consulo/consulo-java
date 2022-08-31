package com.intellij.java.language.codeInsight;

import javax.annotation.Nonnull;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;

public interface MemberImplementorExplorer {
  ExtensionPointName<MemberImplementorExplorer> EXTENSION_POINT_NAME = ExtensionPointName.create("consulo.java.methodImplementor");

  @Nonnull
  PsiMethod[] getMethodsToImplement(PsiClass aClass);
}
