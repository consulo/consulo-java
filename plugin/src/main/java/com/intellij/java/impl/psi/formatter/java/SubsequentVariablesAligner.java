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
package com.intellij.java.impl.psi.formatter.java;

import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.psi.JavaTokenType;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.codeStyle.AlignmentStrategy;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nonnull;
import java.util.Set;

public class SubsequentVariablesAligner extends ChildAlignmentStrategyProvider {
  private final static Set<IElementType> TYPES_TO_ALIGN = Set.of(JavaTokenType.IDENTIFIER, JavaTokenType.EQ);

  private AlignmentStrategy myAlignmentStrategy;

  public SubsequentVariablesAligner() {
    updateAlignmentStrategy();
  }

  private void updateAlignmentStrategy() {
    myAlignmentStrategy = AlignmentStrategy.createAlignmentPerTypeStrategy(TYPES_TO_ALIGN, JavaElementType.LOCAL_VARIABLE, true);
  }

  @Override
  public AlignmentStrategy getNextChildStrategy(@Nonnull ASTNode child) {
    IElementType childType = child.getElementType();
    if (childType != JavaElementType.DECLARATION_STATEMENT || StringUtil.countNewLines(child.getChars()) > 0) {
      updateAlignmentStrategy();
      return AlignmentStrategy.getNullStrategy();
    }

    if (isWhiteSpaceWithBlankLines(child.getTreePrev())) {
      updateAlignmentStrategy();
      return myAlignmentStrategy;
    }

    return myAlignmentStrategy;
  }

}