package com.intellij.java.impl.codeInspection.javaDoc;

import com.intellij.java.language.psi.PsiMethod;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;

/**
 * @author VISTALL
 * @since 20/12/2022
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface JavaDocNotNecessaryFilter {
  @RequiredReadAction
  boolean isJavaDocNotNecessary(PsiMethod method);
}
