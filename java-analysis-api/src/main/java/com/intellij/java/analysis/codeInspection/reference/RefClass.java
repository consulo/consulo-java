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
package com.intellij.java.analysis.codeInspection.reference;

import consulo.language.editor.inspection.reference.RefElement;
import com.intellij.java.language.psi.PsiClass;
import jakarta.annotation.Nonnull;

import java.util.List;
import java.util.Set;

/**
 * User: anna
 * Date: 27-Dec-2005
 */
public interface RefClass extends RefJavaElement {

  @Nonnull
  Set<RefClass> getBaseClasses();

  @Nonnull
  Set<RefClass> getSubClasses();

  @Nonnull
  List<RefMethod> getConstructors();

  @Nonnull
  Set<RefElement> getInTypeReferences();

  @Nonnull
  Set<RefElement> getInstanceReferences();

  RefMethod getDefaultConstructor();

  @Nonnull
  List<RefMethod> getLibraryMethods();

  boolean isAnonymous();

  boolean isInterface();

  boolean isUtilityClass();

  boolean isAbstract();

  boolean isApplet();

  boolean isServlet();

  boolean isTestCase();

  boolean isLocalClass();

  boolean isSelfInheritor(PsiClass psiClass);

  @Override
  PsiClass getElement();
}
