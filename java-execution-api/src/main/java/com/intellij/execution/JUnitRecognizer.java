package com.intellij.execution;

import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiMethod;

/**
 * @author Sergey Evdokimov
 */
public abstract class JUnitRecognizer {

  public static final ExtensionPointName<JUnitRecognizer> EP_NAME = ExtensionPointName.create("consulo.java.junitRecognizer");

  public abstract boolean isTestAnnotated(@NotNull PsiMethod method);

  public static boolean willBeAnnotatedAfterCompilation(@NotNull PsiMethod method) {
    for (JUnitRecognizer jUnitRecognizer : EP_NAME.getExtensions()) {
      if (jUnitRecognizer.isTestAnnotated(method)) {
        return true;
      }
    }

    return false;
  }

}
