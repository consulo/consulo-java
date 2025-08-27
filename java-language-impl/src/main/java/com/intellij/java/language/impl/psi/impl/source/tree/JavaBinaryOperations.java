package com.intellij.java.language.impl.psi.impl.source.tree;

import com.intellij.java.language.impl.parser.ExpressionParser;
import consulo.language.ast.TokenSet;

/**
 * @author VISTALL
 * @since 2025-08-27
 */
public interface JavaBinaryOperations {
    TokenSet ASSIGNMENT_OPS = ExpressionParser.ASSIGNMENT_OPS;
    TokenSet SHIFT_OPS = ExpressionParser.SHIFT_OPS;
    TokenSet ADDITIVE_OPS = ExpressionParser.ADDITIVE_OPS;
    TokenSet MULTIPLICATIVE_OPS = ExpressionParser.MULTIPLICATIVE_OPS;
}
