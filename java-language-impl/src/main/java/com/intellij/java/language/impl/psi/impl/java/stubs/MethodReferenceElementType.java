/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.java.language.impl.psi.impl.java.stubs;

import com.intellij.java.language.impl.psi.impl.source.tree.ChildRole;
import com.intellij.java.language.impl.psi.impl.source.tree.ElementType;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaSourceUtil;
import com.intellij.java.language.impl.psi.impl.source.tree.java.PsiMethodReferenceExpressionImpl;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiMethodReferenceExpression;
import consulo.language.ast.*;
import consulo.language.impl.ast.CompositeElement;
import consulo.language.impl.ast.TreeElement;
import consulo.util.lang.lazy.LazyValue;

import javax.annotation.Nonnull;
import java.util.function.Supplier;

public class MethodReferenceElementType extends FunctionalExpressionElementType<PsiMethodReferenceExpression> {
  //prevents cyclic static variables initialization
  private final static Supplier<TokenSet> EXCLUDE_FROM_PRESENTABLE_TEXT = LazyValue.notNull(() -> TokenSet.orSet(ElementType.JAVA_COMMENT_OR_WHITESPACE_BIT_SET, TokenSet.create(JavaElementType.REFERENCE_PARAMETER_LIST)));

  public MethodReferenceElementType() {
    super("METHOD_REF_EXPRESSION");
  }

  @Override
  public PsiMethodReferenceExpression createPsi(@Nonnull ASTNode node) {
    return new PsiMethodReferenceExpressionImpl(node);
  }

  @Override
  public PsiMethodReferenceExpression createPsi(@Nonnull FunctionalExpressionStub<PsiMethodReferenceExpression> stub) {
    return new PsiMethodReferenceExpressionImpl(stub);
  }

  @Nonnull
  @Override
  public ASTNode createCompositeNode() {
    return new CompositeElement(this) {
      @Override
      public void replaceChildInternal(@Nonnull ASTNode child, @Nonnull TreeElement newElement) {
        super.replaceChildInternal(child, JavaSourceUtil.addParenthToReplacedChild(child, newElement, getManager()));
      }


      @Override
      public int getChildRole(ASTNode child) {
        final IElementType elType = child.getElementType();
        if (elType == JavaTokenType.DOUBLE_COLON) {
          return ChildRole.DOUBLE_COLON;
        } else if (elType == JavaTokenType.IDENTIFIER) {
          return ChildRole.REFERENCE_NAME;
        } else if (elType == JavaElementType.REFERENCE_EXPRESSION) {
          return ChildRole.CLASS_REFERENCE;
        }
        return ChildRole.EXPRESSION;
      }

    };
  }

  @Nonnull
  @Override
  protected String getPresentableText(@Nonnull LighterAST tree, @Nonnull LighterASTNode funExpr) {
    return LightTreeUtil.toFilteredString(tree, funExpr, EXCLUDE_FROM_PRESENTABLE_TEXT.get());
  }
}
