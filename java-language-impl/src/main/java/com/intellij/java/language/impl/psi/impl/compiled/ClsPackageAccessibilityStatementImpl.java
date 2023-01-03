/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.language.impl.psi.impl.compiled;

import com.intellij.java.language.impl.psi.impl.java.stubs.JavaPackageAccessibilityStatementElementType;
import com.intellij.java.language.impl.psi.impl.java.stubs.PsiPackageAccessibilityStatementStub;
import com.intellij.java.language.psi.PsiJavaCodeReferenceElement;
import com.intellij.java.language.psi.PsiJavaModuleReferenceElement;
import com.intellij.java.language.psi.PsiPackageAccessibilityStatement;
import consulo.application.util.AtomicNullableLazyValue;
import consulo.language.impl.ast.TreeElement;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.StringUtil;
import consulo.util.lang.lazy.LazyValue;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import static consulo.util.lang.StringUtil.nullize;

public class ClsPackageAccessibilityStatementImpl extends ClsRepositoryPsiElement<PsiPackageAccessibilityStatementStub> implements PsiPackageAccessibilityStatement {
  private final AtomicNullableLazyValue<PsiJavaCodeReferenceElement> myPackageReference;
  private final Supplier<Iterable<PsiJavaModuleReferenceElement>> myModuleReferences;

  public ClsPackageAccessibilityStatementImpl(PsiPackageAccessibilityStatementStub stub) {
    super(stub);
    myPackageReference = new AtomicNullableLazyValue<PsiJavaCodeReferenceElement>() {
      @Override
      protected PsiJavaCodeReferenceElement compute() {
        String packageName = getPackageName();
        return packageName != null ? new ClsJavaCodeReferenceElementImpl(ClsPackageAccessibilityStatementImpl.this, packageName) : null;
      }
    };
    myModuleReferences = LazyValue.atomicNotNull(() -> {
      return ContainerUtil.map(getStub().getTargets(), target -> new ClsJavaModuleReferenceElementImpl(ClsPackageAccessibilityStatementImpl.this, target));
    });
  }

  @Nonnull
  @Override
  public Role getRole() {
    return JavaPackageAccessibilityStatementElementType.typeToRole(getStub().getStubType());
  }

  @Override
  public PsiJavaCodeReferenceElement getPackageReference() {
    return myPackageReference.getValue();
  }

  @Override
  public String getPackageName() {
    return nullize(getStub().getPackageName());
  }

  @Nonnull
  @Override
  public Iterable<PsiJavaModuleReferenceElement> getModuleReferences() {
    return myModuleReferences.get();
  }

  @Nonnull
  @Override
  public List<String> getModuleNames() {
    return getStub().getTargets();
  }

  @Override
  public void appendMirrorText(int indentLevel, @Nonnull StringBuilder buffer) {
    StringUtil.repeatSymbol(buffer, ' ', indentLevel);
    buffer.append(getRole().toString().toLowerCase(Locale.US)).append(' ').append(getPackageName());
    List<String> targets = getStub().getTargets();
    if (!targets.isEmpty()) {
      buffer.append(" to ");
      for (int i = 0; i < targets.size(); i++) {
        if (i > 0) {
          buffer.append(", ");
        }
        buffer.append(targets.get(i));
      }
    }
    buffer.append(";\n");
  }

  @Override
  public void setMirror(@Nonnull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, getStub().getStubType());
  }

  @Override
  public String toString() {
    return "PsiPackageAccessibilityStatement[" + getRole() + "]";
  }
}