// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.impl.psi.impl.light;

import consulo.language.impl.psi.LightElement;

import com.intellij.java.language.psi.JavaElementVisitor;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiMirrorElement;
import com.intellij.java.language.psi.PsiParameter;
import com.intellij.java.language.psi.PsiParameterList;
import com.intellij.java.language.psi.PsiSubstitutor;
import consulo.util.collection.ContainerUtil;

public class LightParameterListWrapper extends LightElement implements PsiParameterList, PsiMirrorElement {

  private final PsiParameterList myDelegate;
  private final PsiSubstitutor mySubstitutor;

  public LightParameterListWrapper(PsiParameterList delegate, PsiSubstitutor substitutor) {
    super(delegate.getManager(), delegate.getLanguage());
    myDelegate = delegate;
    mySubstitutor = substitutor;
  }

  @Override
  public void accept(PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitParameterList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public PsiParameterList getPrototype() {
    return myDelegate;
  }

  @Override
  public PsiParameter [] getParameters() {
    return mySubstitutor == PsiSubstitutor.EMPTY
           ? myDelegate.getParameters()
           : ContainerUtil.map2Array(myDelegate.getParameters(), PsiParameter.class,
                                     parameter -> new LightParameterWrapper(parameter, mySubstitutor));
  }

  @Override
  public int getParameterIndex(PsiParameter parameter) {
    if (parameter instanceof LightParameterWrapper) {
      parameter = ((LightParameterWrapper)parameter).getPrototype();
    }
    return myDelegate.getParameterIndex(parameter);
  }

  @Override
  public int getParametersCount() {
    return myDelegate.getParametersCount();
  }

  @Override
  public String toString() {
    return "List PSI parameter list wrapper: " + myDelegate;
  }
}
