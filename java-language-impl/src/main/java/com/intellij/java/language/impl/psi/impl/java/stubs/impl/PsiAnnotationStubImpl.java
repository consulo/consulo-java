/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.language.impl.psi.impl.java.stubs.impl;

import com.intellij.java.language.codeInsight.AnnotationUtil;
import consulo.logging.Logger;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiAnnotation;
import com.intellij.java.language.psi.PsiJavaParserFacade;
import com.intellij.java.language.impl.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.java.language.impl.psi.impl.java.stubs.PsiAnnotationStub;
import consulo.language.impl.ast.CharTableImpl;
import consulo.language.psi.stub.StubBase;
import consulo.language.psi.stub.StubElement;
import consulo.util.lang.ref.SoftReference;
import consulo.language.util.IncorrectOperationException;

/**
 * @author max
 */
public class PsiAnnotationStubImpl extends StubBase<PsiAnnotation> implements PsiAnnotationStub {
  private static final Logger LOG = Logger.getInstance(PsiAnnotationStubImpl.class);

  private final String myText;
  private SoftReference<PsiAnnotation> myParsedFromRepository;

  public PsiAnnotationStubImpl(final StubElement parent, final String text) {
    super(parent, JavaStubElementTypes.ANNOTATION);
    CharSequence interned = CharTableImpl.getStaticInterned(text);
    myText = interned == null ? text : interned.toString();
  }

  static {
    CharTableImpl.addStringsFromClassToStatics(AnnotationUtil.class);
    CharTableImpl.staticIntern("@NotNull");
    CharTableImpl.staticIntern("@Nullable");
    CharTableImpl.staticIntern("@Override");
  }

  @Override
  public String getText() {
    return myText;
  }

  @Override
  public PsiAnnotation getPsiElement() {
    if (myParsedFromRepository != null) {
      PsiAnnotation annotation = myParsedFromRepository.get();
      if (annotation != null) {
        return annotation;
      }
    }

    final String text = getText();
    try {
      PsiJavaParserFacade facade = JavaPsiFacade.getInstance(getProject()).getParserFacade();
      PsiAnnotation annotation = facade.createAnnotationFromText(text, getPsi());
      myParsedFromRepository = new SoftReference<PsiAnnotation>(annotation);
      return annotation;
    }
    catch (IncorrectOperationException e) {
      LOG.error("Bad annotation in repository!", e);
      return null;
    }
  }

  @Override
  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "PsiAnnotationStub[" + myText + "]";
  }
}
