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
package com.intellij.java.language.impl.psi.impl.source.tree.java;

import com.intellij.java.language.impl.psi.impl.source.Constants;
import com.intellij.java.language.impl.psi.impl.source.PsiClassReferenceType;
import com.intellij.java.language.impl.psi.impl.source.PsiImmediateClassType;
import com.intellij.java.language.impl.psi.impl.source.tree.ChildRole;
import com.intellij.java.language.psi.*;
import consulo.language.ast.ASTNode;
import consulo.language.ast.ChildRoleBase;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.logging.Logger;
import jakarta.annotation.Nonnull;

public class PsiThisExpressionImpl extends ExpressionPsiElement implements PsiThisExpression, Constants {
  private static final Logger LOG = Logger.getInstance(PsiThisExpressionImpl.class);

  public PsiThisExpressionImpl() {
    super(THIS_EXPRESSION);
  }

  @Override
  public PsiJavaCodeReferenceElement getQualifier() {
    return (PsiJavaCodeReferenceElement) findChildByRoleAsPsiElement(ChildRole.QUALIFIER);
  }

  @Override
  public PsiType getType() {
    PsiJavaCodeReferenceElement qualifier = getQualifier();
    if (qualifier != null) {
      PsiClass qualifierResolve = (PsiClass) qualifier.resolve();
      if (qualifierResolve != null) return new PsiImmediateClassType(qualifierResolve, PsiSubstitutor.EMPTY);

      return new PsiClassReferenceType(qualifier, null);
    }
    for (PsiElement scope = getContext(); scope != null; scope = scope.getContext()) {
      if (scope instanceof PsiClass) {
        PsiClass aClass = (PsiClass) scope;
        return new PsiImmediateClassType(aClass, PsiSubstitutor.EMPTY);
      } else if (scope instanceof PsiExpressionList && scope.getParent() instanceof PsiAnonymousClass) {
        scope = scope.getParent();
      } else if (scope instanceof JavaCodeFragment) {
        PsiType fragmentThisType = ((JavaCodeFragment) scope).getThisType();
        if (fragmentThisType != null) return fragmentThisType;
      }
    }
    return null;
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch (role) {
      default:
        return null;

      case ChildRole.QUALIFIER:
        if (getFirstChildNode().getElementType() == JAVA_CODE_REFERENCE) {
          return getFirstChildNode();
        } else {
          return null;
        }

      case ChildRole.DOT:
        return findChildByType(DOT);

      case ChildRole.THIS_KEYWORD:
        return getLastChildNode();
    }
  }

  @Override
  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == JAVA_CODE_REFERENCE) {
      return ChildRole.QUALIFIER;
    } else if (i == DOT) {
      return ChildRole.DOT;
    } else if (i == THIS_KEYWORD) {
      return ChildRole.THIS_KEYWORD;
    } else {
      return ChildRoleBase.NONE;
    }
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor) visitor).visitThisExpression(this);
    } else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiThisExpression:" + getText();
  }
}

