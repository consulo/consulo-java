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
package com.intellij.psi.impl.source.javadoc;

import org.jetbrains.annotations.NonNls;
import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.java.language.psi.PsiJavaCodeReferenceElement;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.java.language.psi.javadoc.JavadocTagInfo;
import com.intellij.java.language.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * @author mike
 */
class ExceptionTagInfo implements JavadocTagInfo {
  private final String myName;

  public ExceptionTagInfo(@NonNls String name) {
    myName = name;
  }

  @Override
  public String checkTagValue(PsiDocTagValue value) {
    if (value == null) return JavaErrorBundle.message("javadoc.exception.tag.exception.class.expected");
    final PsiElement firstChild = value.getFirstChild();
    if (firstChild == null) return JavaErrorBundle.message("javadoc.exception.tag.exception.class.expected");

    final PsiElement psiElement = firstChild.getFirstChild();
    if (!(psiElement instanceof PsiJavaCodeReferenceElement)) {
      return JavaErrorBundle.message("javadoc.exception.tag.wrong.tag.value");
    }

    final PsiJavaCodeReferenceElement ref = ((PsiJavaCodeReferenceElement)psiElement);
    final PsiElement element = ref.resolve();
    if (!(element instanceof PsiClass)) return null;

    final PsiClass exceptionClass = (PsiClass)element;


    final PsiClass throwable = JavaPsiFacade.getInstance(value.getProject()).findClass("java.lang.Throwable", value.getResolveScope());

    if (throwable != null) {
      if (!exceptionClass.equals(throwable) && !exceptionClass.isInheritor(throwable, true)) {
        return JavaErrorBundle.message("javadoc.exception.tag.class.is.not.throwable", exceptionClass.getQualifiedName());
      }
    }

    final PsiClass runtimeException =
      JavaPsiFacade.getInstance(value.getProject()).findClass("java.lang.RuntimeException", value.getResolveScope());

    if (runtimeException != null &&
        (exceptionClass.isInheritor(runtimeException, true) || exceptionClass.equals(runtimeException))) {
      return null;
    }

    final PsiClass errorException = JavaPsiFacade.getInstance(value.getProject()).findClass("java.lang.Error", value.getResolveScope());

    if (errorException != null &&
        (exceptionClass.isInheritor(errorException, true) || exceptionClass.equals(errorException))) {
      return null;
    }

    PsiMethod method = PsiTreeUtil.getParentOfType(value, PsiMethod.class);
    if (method == null) {
      return null;
    }
    final PsiClassType[] references = method.getThrowsList().getReferencedTypes();

    for (PsiClassType reference : references) {
      final PsiClass psiClass = reference.resolve();
      if (psiClass == null) continue;
      if (exceptionClass.isInheritor(psiClass, true) || exceptionClass.equals(psiClass)) return null;
    }

    return JavaErrorBundle.message("javadoc.exception.tag.exception.is.not.thrown", exceptionClass.getName(), method.getName());
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public PsiReference getReference(PsiDocTagValue value) {
    return null;
  }

  @Override
  public boolean isValidInContext(PsiElement element) {
    if (!(element instanceof PsiMethod)) return false;
    return true;
  }

  @Override
  public boolean isInline() {
    return false;
  }
}
