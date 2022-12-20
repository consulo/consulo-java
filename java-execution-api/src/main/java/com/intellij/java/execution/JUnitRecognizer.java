package com.intellij.java.execution;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import com.intellij.java.language.psi.PsiMethod;

import javax.annotation.Nonnull;

/**
 * @author Sergey Evdokimov
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class JUnitRecognizer {

  public static final ExtensionPointName<JUnitRecognizer> EP_NAME = ExtensionPointName.create(JUnitRecognizer.class);

  public abstract boolean isTestAnnotated(@Nonnull PsiMethod method);

  public static boolean willBeAnnotatedAfterCompilation(@Nonnull PsiMethod method) {
    for (JUnitRecognizer jUnitRecognizer : EP_NAME.getExtensionList()) {
      if (jUnitRecognizer.isTestAnnotated(method)) {
        return true;
      }
    }

    return false;
  }

}
