/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.java.language.impl.psi.impl.source.tree.ChildRole;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.psi.JavaTokenType;
import consulo.language.ast.ASTNode;
import consulo.language.ast.ChildRoleBase;
import consulo.language.ast.IElementType;
import consulo.language.impl.ast.CompositeElement;
import consulo.logging.Logger;
import jakarta.annotation.Nonnull;

public class ParameterElement extends CompositeElement {
  private static final Logger LOG = Logger.getInstance(ParameterElement.class);

  public ParameterElement(@Nonnull IElementType type) {
    super(type);
  }

  @Override
  public int getTextOffset() {
    ASTNode node = findChildByType(JavaTokenType.IDENTIFIER);
    return node != null ? node.getStartOffset() : getStartOffset();
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));

    if (role == ChildRole.MODIFIER_LIST) {
      return findChildByType(JavaElementType.MODIFIER_LIST);
    }
    else if (role == ChildRole.NAME) {
      return findChildByType(JavaTokenType.IDENTIFIER);
    }
    else if (role == ChildRole.TYPE) {
      return findChildByType(JavaElementType.TYPE);
    }
    else {
      return null;
    }
  }

  @Override
  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);

    IElementType i = child.getElementType();
    if (i == JavaElementType.MODIFIER_LIST) {
      return ChildRole.MODIFIER_LIST;
    }
    else if (i == JavaElementType.TYPE) {
      return getChildRole(child, ChildRole.TYPE);
    }
    else if (i == JavaTokenType.IDENTIFIER) {
      return getChildRole(child, ChildRole.NAME);
    }
    else {
      return ChildRoleBase.NONE;
    }
  }
}
