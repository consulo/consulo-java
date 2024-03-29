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
package com.intellij.java.language.impl.psi.impl.source.tree.java;

import com.intellij.java.language.impl.psi.impl.source.tree.ChildRole;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiKeyword;
import consulo.language.ast.ASTNode;
import consulo.language.ast.ChildRoleBase;
import consulo.language.ast.IElementType;

public class TypeParameterExtendsBoundsListElement extends ReferenceListElement {
  public TypeParameterExtendsBoundsListElement() {
    super(JavaElementType.EXTENDS_BOUND_LIST, JavaTokenType.EXTENDS_KEYWORD, PsiKeyword.EXTENDS, JavaTokenType.AND, "&");
  }

  @Override
  public int getChildRole(ASTNode child) {
    assert child.getTreeParent() == this : child;
    IElementType childType = child.getElementType();
    if (childType == JavaTokenType.AND) {
      return ChildRole.AMPERSAND_IN_BOUNDS_LIST;
    }
    if (childType == JavaElementType.JAVA_CODE_REFERENCE) {
      return ChildRole.BASE_CLASS_REFERENCE;
    }
    return ChildRoleBase.NONE;
  }
}