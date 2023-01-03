package com.intellij.java.language.codeInsight;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;

import javax.annotation.Nonnull;

@ExtensionAPI(ComponentScope.APPLICATION)
public interface MemberImplementorExplorer {
  @Nonnull
  PsiMethod[] getMethodsToImplement(PsiClass aClass);
}
