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
package com.intellij.java.language.impl.psi.impl.source;

import com.intellij.java.language.impl.psi.impl.java.stubs.impl.PsiJavaFileStubImpl;
import com.intellij.java.language.impl.psi.impl.source.tree.ElementType;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaDocElementType;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaSourceUtil;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiJavaFile;
import consulo.language.ast.*;
import consulo.language.impl.ast.RecursiveTreeElementWalkingVisitor;
import consulo.language.impl.ast.TreeElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.stub.LightStubBuilder;
import consulo.language.psi.stub.StubElement;

import javax.annotation.Nonnull;

public class JavaLightStubBuilder extends LightStubBuilder {
  @Nonnull
  @Override
  protected StubElement createStubForFile(@Nonnull PsiFile file, @Nonnull LighterAST tree) {
    if (!(file instanceof PsiJavaFile)) {
      return super.createStubForFile(file, tree);
    }

    String refText = "";
    LighterASTNode pkg = LightTreeUtil.firstChildOfType(tree, tree.getRoot(), JavaElementType.PACKAGE_STATEMENT);
    if (pkg != null) {
      LighterASTNode ref = LightTreeUtil.firstChildOfType(tree, pkg, JavaElementType.JAVA_CODE_REFERENCE);
      if (ref != null) {
        refText = JavaSourceUtil.getReferenceText(tree, ref);
      }
    }
    return new PsiJavaFileStubImpl((PsiJavaFile) file, refText, null, false);
  }

  @Override
  public boolean skipChildProcessingWhenBuildingStubs(@Nonnull ASTNode parent, @Nonnull ASTNode node) {
    IElementType parentType = parent.getElementType();
    IElementType nodeType = node.getElementType();

    if (checkByTypes(parentType, nodeType)) {
      return true;
    }

    if (nodeType == JavaElementType.CODE_BLOCK) {
      CodeBlockVisitor visitor = new CodeBlockVisitor();
      ((TreeElement) node).acceptTree(visitor);
      return visitor.result;
    }

    return false;
  }

  @Override
  protected boolean skipChildProcessingWhenBuildingStubs(@Nonnull LighterAST tree, @Nonnull LighterASTNode parent, @Nonnull LighterASTNode node) {
    return checkByTypes(parent.getTokenType(), node.getTokenType()) || isCodeBlockWithoutStubs(node);
  }

  public static boolean isCodeBlockWithoutStubs(@Nonnull LighterASTNode node) {
    if (node.getTokenType() == JavaElementType.CODE_BLOCK && node instanceof LighterLazyParseableNode) {
      CodeBlockVisitor visitor = new CodeBlockVisitor();
      ((LighterLazyParseableNode) node).accept(visitor);
      return visitor.result;
    }

    return false;
  }

  private static boolean checkByTypes(IElementType parentType, IElementType nodeType) {
    if (ElementType.IMPORT_STATEMENT_BASE_BIT_SET.contains(parentType)) {
      return true;
    }
    if (nodeType == JavaElementType.RECEIVER_PARAMETER) {
      return true;
    }
    if (nodeType == JavaElementType.PARAMETER && parentType != JavaElementType.PARAMETER_LIST) {
      return true;
    }
    if (nodeType == JavaElementType.PARAMETER_LIST && parentType == JavaElementType.LAMBDA_EXPRESSION) {
      return true;
    }
    if (nodeType == JavaDocElementType.DOC_COMMENT) {
      return true;
    }

    return false;
  }

  private static class CodeBlockVisitor extends RecursiveTreeElementWalkingVisitor implements LighterLazyParseableNode.Visitor {
    private static final TokenSet BLOCK_ELEMENTS = TokenSet.create(
        JavaElementType.CLASS, JavaElementType.ANONYMOUS_CLASS,
        JavaTokenType.ARROW, JavaTokenType.DOUBLE_COLON, JavaTokenType.AT);

    private boolean result = true;

    @Override
    protected void visitNode(TreeElement element) {
      if (BLOCK_ELEMENTS.contains(element.getElementType())) {
        result = false;
        stopWalking();
        return;
      }
      super.visitNode(element);
    }

    private IElementType preLast;
    private IElementType last;
    private boolean seenNew;
    private boolean seenLParen;
    private boolean seenModifier;

    @Override
    public boolean visit(IElementType type) {
      if (ElementType.JAVA_COMMENT_OR_WHITESPACE_BIT_SET.contains(type)) {
        return true;
      }

      // annotations, method refs & lambdas
      if (type == JavaTokenType.AT || type == JavaTokenType.ARROW || type == JavaTokenType.DOUBLE_COLON) {
        return (result = false);
      }
      // anonymous classes
      else if (type == JavaTokenType.NEW_KEYWORD) {
        seenNew = true;
      } else if (seenNew && type == JavaTokenType.SEMICOLON) {
        seenNew = false;
        seenLParen = false;
      } else if (seenNew && type == JavaTokenType.LBRACE && seenLParen) {
        return (result = false);
      } else if (seenNew && type == JavaTokenType.LPARENTH) {
        seenLParen = true;
      } else if (ElementType.MODIFIER_BIT_SET.contains(type)) {
        seenModifier = true;
      }
      // local classes
      else if (type == JavaTokenType.CLASS_KEYWORD && (last != JavaTokenType.DOT || preLast != JavaTokenType.IDENTIFIER || seenModifier)
          || type == JavaTokenType.ENUM_KEYWORD
          || type == JavaTokenType.INTERFACE_KEYWORD) {
        return (result = false);
      }

      preLast = last;
      last = type;
      return true;
    }
  }
}
