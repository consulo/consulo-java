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

import com.intellij.java.language.psi.PsiNameValuePair;
import com.intellij.java.language.impl.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.java.language.impl.psi.impl.java.stubs.PsiNameValuePairStub;
import consulo.language.psi.stub.StubBase;
import consulo.language.psi.stub.StubElement;
import consulo.index.io.StringRef;
import jakarta.annotation.Nullable;

/**
 * @author Dmitry Avdeev
 *         Date: 7/27/12
 */
public class PsiNameValuePairStubImpl extends StubBase<PsiNameValuePair> implements PsiNameValuePairStub {

  @Nullable
  private final StringRef myName;
  @Nullable
  private final StringRef myValue;

  public PsiNameValuePairStubImpl(StubElement parent, @Nullable StringRef name, @Nullable StringRef value) {
    super(parent, JavaStubElementTypes.NAME_VALUE_PAIR);
    myName = name;
    myValue = value;
  }

  @Override
  public String getName() {
    return myName == null ? "value" : myName.getString();
  }

  @Override
  public String getValue() {
    return myValue == null ? null : myValue.getString();
  }
}
