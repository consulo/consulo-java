/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.impl.lang.java;

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.PsiClass;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.impl.psi.impl.source.tree.java.ImportStatementElement;
import consulo.usage.UsageToPsiElementProvider;

/**
 * @author Konstantin Bulenkov
 */
@ExtensionImpl
public class JavaUsageToPsiElementProvider extends UsageToPsiElementProvider {
  private static final int MAX_HOPES = 17;

  @Override
  public PsiElement getAppropriateParentFrom(PsiElement element) {
    if (element.getLanguage() == JavaLanguage.INSTANCE) {
      int hopes = 0;
      while (hopes++ < MAX_HOPES && element != null) {
        if (element instanceof PsiField ||
            element instanceof PsiMethod ||
            element instanceof ImportStatementElement ||
            element instanceof PsiClass
          ) return element;

        element = element.getParent();
      }
    }
    return null;
  }
}
