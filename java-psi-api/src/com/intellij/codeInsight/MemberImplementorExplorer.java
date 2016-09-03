package com.intellij.codeInsight;

import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;

public interface MemberImplementorExplorer {
  ExtensionPointName<MemberImplementorExplorer> EXTENSION_POINT_NAME = ExtensionPointName.create("consulo.java.methodImplementor");

  @NotNull
  PsiMethod[] getMethodsToImplement(PsiClass aClass);
}
