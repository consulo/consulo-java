// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.psi.formatter.java;

import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.psi.JavaTokenType;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.codeStyle.AlignmentStrategy;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;

import java.util.Set;

public final class SubsequentVariablesAlignerConfigurations {

  private final static Set<IElementType> LOCAL_VAR_TYPES_TO_ALIGN = Set.of(
    JavaTokenType.IDENTIFIER,
    JavaTokenType.EQ
  );

  private final static Set<IElementType> ASSIGN_TYPES_TO_ALIGN = Set.of(
    JavaElementType.REFERENCE_EXPRESSION,
    JavaTokenType.EQ
  );

  static final CompositeAligner.AlignerConfiguration subsequentVariableAligner = new SubsequentVariableAligner();
  static final CompositeAligner.AlignerConfiguration subsequentAssignmentAligner = new SubsequentAssignmentAligner();

  private static class SubsequentVariableAligner implements CompositeAligner.AlignerConfiguration {
    @Override
    public boolean shouldAlign(@Nonnull ASTNode child) {
      return child.getElementType() == JavaElementType.DECLARATION_STATEMENT && StringUtil.countNewLines(child.getChars()) == 0;
    }

    @Nonnull
    @Override
    public AlignmentStrategy createStrategy() {
      return AlignmentStrategy.createAlignmentPerTypeStrategy(LOCAL_VAR_TYPES_TO_ALIGN, JavaElementType.LOCAL_VARIABLE, true);
    }
  }

  private static class SubsequentAssignmentAligner implements CompositeAligner.AlignerConfiguration {
    @Override
    public boolean shouldAlign(@Nonnull ASTNode child) {
      IElementType childType = child.getElementType();
      if (childType == JavaElementType.EXPRESSION_STATEMENT) {
        ASTNode expressionNode = child.getFirstChildNode();
        if (expressionNode != null && expressionNode.getElementType() == JavaElementType.ASSIGNMENT_EXPRESSION) {
          return true;
        }
      }
      return false;
    }

    @Nonnull
    @Override
    public AlignmentStrategy createStrategy() {
      return AlignmentStrategy.createAlignmentPerTypeStrategy(ASSIGN_TYPES_TO_ALIGN, JavaElementType.ASSIGNMENT_EXPRESSION, true);
    }
  }
}