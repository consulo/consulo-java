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
package com.intellij.java.language.impl.psi.impl.source;

import com.intellij.java.language.impl.psi.impl.java.stubs.JavaPackageAccessibilityStatementElementType;
import com.intellij.java.language.impl.psi.impl.java.stubs.PsiPackageAccessibilityStatementStub;
import com.intellij.java.language.psi.*;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import static consulo.language.psi.SyntaxTraverser.psiTraverser;

public class PsiPackageAccessibilityStatementImpl extends JavaStubPsiElement<PsiPackageAccessibilityStatementStub> implements PsiPackageAccessibilityStatement {
  public PsiPackageAccessibilityStatementImpl(@jakarta.annotation.Nonnull PsiPackageAccessibilityStatementStub stub) {
    super(stub, stub.getStubType());
  }

  public PsiPackageAccessibilityStatementImpl(@Nonnull ASTNode node) {
    super(node);
  }

  @jakarta.annotation.Nonnull
  @Override
  public Role getRole() {
    return JavaPackageAccessibilityStatementElementType.typeToRole(getElementType());
  }

  @Nullable
  @Override
  public PsiJavaCodeReferenceElement getPackageReference() {
    return PsiTreeUtil.getChildOfType(this, PsiJavaCodeReferenceElement.class);
  }

  @jakarta.annotation.Nullable
  @Override
  public String getPackageName() {
    PsiPackageAccessibilityStatementStub stub = getGreenStub();
    if (stub != null) {
      return StringUtil.nullize(stub.getPackageName());
    } else {
      PsiJavaCodeReferenceElement ref = getPackageReference();
      return ref != null ? PsiNameHelper.getQualifiedClassName(ref.getText(), true) : null;
    }
  }

  @Nonnull
  @Override
  public Iterable<PsiJavaModuleReferenceElement> getModuleReferences() {
    return psiTraverser().children(this).filter(PsiJavaModuleReferenceElement.class);
  }

  @Nonnull
  @Override
  public List<String> getModuleNames() {
    PsiPackageAccessibilityStatementStub stub = getGreenStub();
    if (stub != null) {
      return stub.getTargets();
    } else {
      List<String> targets = new ArrayList<>();
      for (PsiJavaModuleReferenceElement refElement : getModuleReferences()) {
        targets.add(refElement.getReferenceText());
      }
      return targets;
    }
  }

  @Override
  public void accept(@jakarta.annotation.Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor) visitor).visitPackageAccessibilityStatement(this);
    } else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiPackageAccessibilityStatement";
  }
}