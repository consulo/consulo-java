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

/*
 * @author max
 */
package com.intellij.java.language.impl.psi.impl.java.stubs.impl;

import com.intellij.java.language.psi.PsiClassInitializer;
import com.intellij.java.language.impl.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.java.language.impl.psi.impl.java.stubs.PsiClassInitializerStub;
import consulo.language.psi.stub.StubBase;
import consulo.language.psi.stub.StubElement;

public class PsiClassInitializerStubImpl extends StubBase<PsiClassInitializer> implements PsiClassInitializerStub {
  public PsiClassInitializerStubImpl(final StubElement parent) {
    super(parent, JavaStubElementTypes.CLASS_INITIALIZER);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "PsiClassInitializerStub";
  }
}