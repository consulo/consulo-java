/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.java.impl.psi.impl;

import com.intellij.java.impl.psi.impl.source.PsiExpressionCodeFragmentImpl;
import com.intellij.java.impl.psi.impl.source.PsiJavaCodeReferenceCodeFragmentImpl;
import com.intellij.java.language.impl.psi.impl.source.PsiCodeFragmentImpl;
import com.intellij.java.language.impl.psi.impl.source.PsiTypeCodeFragmentImpl;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.psi.*;
import consulo.annotation.component.ServiceImpl;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jspecify.annotations.Nullable;

@Singleton
@ServiceImpl
public class JavaCodeFragmentFactoryImpl extends JavaCodeFragmentFactory {
  private final Project myProject;

  @Inject
  public JavaCodeFragmentFactoryImpl(Project project) {
    myProject = project;
  }

  @Override
  public PsiExpressionCodeFragment createExpressionCodeFragment(String text,
                                                                @Nullable PsiElement context,
                                                                @Nullable PsiType expectedType,
                                                                boolean isPhysical) {
    return new PsiExpressionCodeFragmentImpl(myProject, isPhysical, "fragment.java", text, expectedType, context);
  }

  @Override
  public JavaCodeFragment createCodeBlockCodeFragment(String text,
                                                      @Nullable PsiElement context,
                                                      boolean isPhysical) {
    return new PsiCodeFragmentImpl(myProject, JavaElementType.STATEMENTS, isPhysical, "fragment.java", text, context);
  }

  @Override
  public PsiTypeCodeFragment createTypeCodeFragment(String text,
                                                    @Nullable PsiElement context,
                                                    boolean isPhysical) {
    return createTypeCodeFragment(text, context, isPhysical, 0);
  }

  @Override
  public PsiTypeCodeFragment createTypeCodeFragment(String text,
                                                    @Nullable PsiElement context,
                                                    boolean isPhysical,
                                                    int flags) {
    return new PsiTypeCodeFragmentImpl(myProject, isPhysical, "fragment.java", text, flags, context);
  }

  @Override
  public PsiJavaCodeReferenceCodeFragment createReferenceCodeFragment(String text,
                                                                      @Nullable PsiElement context,
                                                                      boolean isPhysical,
                                                                      boolean isClassesAccepted) {
    return new PsiJavaCodeReferenceCodeFragmentImpl(myProject, isPhysical, "fragment.java", text, isClassesAccepted, context);
  }
}
