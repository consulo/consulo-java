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
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.codeStyle.AlignmentStrategy;

import jakarta.annotation.Nonnull;
import java.util.List;

public class SubsequentOneLineMethodsAligner extends ChildAlignmentStrategyProvider {
  private AlignmentStrategy myAlignmentStrategy;


  public SubsequentOneLineMethodsAligner() {
    myAlignmentStrategy = newAlignmentStrategy();
  }

  @Override
  public AlignmentStrategy getNextChildStrategy(@Nonnull ASTNode child) {
    IElementType childType = child.getElementType();

    if (childType != JavaElementType.METHOD || child.textContains('\n')) {
      myAlignmentStrategy = newAlignmentStrategy();
      return AlignmentStrategy.getNullStrategy();
    }

    return myAlignmentStrategy;
  }

  private static AlignmentStrategy newAlignmentStrategy() {
    List<IElementType> types = List.of((IElementType) JavaElementType.CODE_BLOCK);
    return AlignmentStrategy.createAlignmentPerTypeStrategy(types, JavaElementType.METHOD, true);
  }

}
