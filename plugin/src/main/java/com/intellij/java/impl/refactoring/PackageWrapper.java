/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring;

import jakarta.annotation.Nonnull;

import consulo.util.lang.StringUtil;
import com.intellij.java.language.psi.JavaPsiFacade;
import consulo.language.psi.PsiDirectory;
import com.intellij.java.language.psi.PsiJavaPackage;
import consulo.language.psi.PsiManager;

/**
 * Represents a package. 
 *  @author dsl
 */
public class PackageWrapper {
  private final PsiManager myManager;
  @Nonnull
  private final String myQualifiedName;

  public PackageWrapper(PsiManager manager, @jakarta.annotation.Nonnull String qualifiedName) {
    myManager = manager;
    myQualifiedName = qualifiedName;
  }

  public PackageWrapper(PsiJavaPackage aPackage) {
    myManager = aPackage.getManager();
    myQualifiedName = aPackage.getQualifiedName();
  }

  public PsiManager getManager() { return myManager; }

  public PsiDirectory[] getDirectories() {
    String qName = myQualifiedName;
    while (qName.endsWith(".")) {
      qName = StringUtil.trimEnd(qName, ".");
    }
    final PsiJavaPackage aPackage = JavaPsiFacade.getInstance(myManager.getProject()).findPackage(qName);
    if (aPackage != null) {
      return aPackage.getDirectories();
    } else {
      return PsiDirectory.EMPTY_ARRAY;
    }
  }

  public boolean exists() {
    return JavaPsiFacade.getInstance(myManager.getProject()).findPackage(myQualifiedName) != null;
  }

  @jakarta.annotation.Nonnull
  public String getQualifiedName() {
    return myQualifiedName;
  }

  public boolean equalToPackage(PsiJavaPackage aPackage) {
    return aPackage != null && myQualifiedName.equals(aPackage.getQualifiedName());
  }

  public static PackageWrapper create(PsiJavaPackage aPackage) {
    return new PackageWrapper(aPackage);
  }
}
