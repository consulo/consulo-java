/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.java.language.psi.PsiAnnotationParameterList;
import com.intellij.java.language.impl.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.java.language.impl.psi.impl.java.stubs.PsiAnnotationParameterListStub;
import consulo.language.psi.stub.StubBase;
import consulo.language.psi.stub.StubElement;

/**
 * @author Dmitry Avdeev
 *         Date: 7/27/12
 */
public class PsiAnnotationParameterListStubImpl extends StubBase<PsiAnnotationParameterList> implements PsiAnnotationParameterListStub {

  public PsiAnnotationParameterListStubImpl(StubElement parent) {
    super(parent, JavaStubElementTypes.ANNOTATION_PARAMETER_LIST);
  }
}
