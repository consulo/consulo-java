/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.java.language.impl.psi.impl.source.tree;

import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import com.intellij.java.language.impl.psi.impl.source.SourceJavaCodeReference;
import com.intellij.java.language.impl.psi.impl.source.tree.java.ReplaceExpressionUtil;
import com.intellij.java.language.psi.*;
import consulo.language.ast.*;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.impl.ast.*;
import consulo.language.impl.psi.DummyHolder;
import consulo.language.impl.psi.SourceTreeToPsiMap;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.util.CharTable;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;

import jakarta.annotation.Nonnull;

public class JavaSourceUtil {
  private static final Logger LOG = Logger.getInstance(JavaSourceUtil.class);

  private static final TokenSet REF_FILTER = TokenSet.orSet(ElementType.JAVA_COMMENT_OR_WHITESPACE_BIT_SET,
      TokenSet.create(JavaElementType.ANNOTATION));

  private JavaSourceUtil() {
  }

  public static void fullyQualifyReference(@Nonnull CompositeElement reference, @Nonnull PsiClass targetClass) {
    if (((SourceJavaCodeReference) reference).isQualified()) { // qualified reference
      final PsiClass parentClass = targetClass.getContainingClass();
      if (parentClass == null) {
        return;
      }
      final ASTNode qualifier = reference.findChildByRole(ChildRole.QUALIFIER);
      if (qualifier instanceof SourceJavaCodeReference) {
        ((SourceJavaCodeReference) qualifier).fullyQualify(parentClass);
      }
    } else { // unqualified reference, need to qualify with package name
      final String qName = targetClass.getQualifiedName();
      if (qName == null) {
        return; // todo: local classes?
      }
      final int i = qName.lastIndexOf('.');
      if (i > 0) {
        final String prefix = qName.substring(0, i);
        final PsiManager manager = reference.getManager();
        final PsiJavaParserFacade parserFacade = JavaPsiFacade.getInstance(manager.getProject())
            .getParserFacade();

        final TreeElement qualifier;
        if (reference instanceof PsiReferenceExpression) {
          qualifier = (TreeElement) parserFacade.createExpressionFromText(prefix, null).getNode();
        } else {
          qualifier = (TreeElement) parserFacade.createReferenceFromText(prefix, null).getNode();
        }

        if (qualifier != null) {
          final CharTable systemCharTab = SharedImplUtil.findCharTableByTree(qualifier);
          final LeafElement dot = Factory.createSingleLeafElement(JavaTokenType.DOT, ".", 0, 1,
              systemCharTab, manager);
          qualifier.rawInsertAfterMe(dot);
          reference.addInternal(qualifier, dot, null, Boolean.FALSE);
        }
      }
    }
  }

  @Nonnull
  public static String getReferenceText(@Nonnull PsiJavaCodeReferenceElement ref) {
    final StringBuilder buffer = new StringBuilder();

    ((TreeElement) ref.getNode()).acceptTree(new RecursiveTreeElementWalkingVisitor() {
      @Override
      public void visitLeaf(LeafElement leaf) {
        if (!REF_FILTER.contains(leaf.getElementType())) {
          String leafText = leaf.getText();
          if (buffer.length() > 0 && !leafText.isEmpty() && Character.isJavaIdentifierPart(leafText.charAt
              (0))) {
            char lastInBuffer = buffer.charAt(buffer.length() - 1);
            if (lastInBuffer == '?' || Character.isJavaIdentifierPart(lastInBuffer)) {
              buffer.append(" ");
            }
          }

          buffer.append(leafText);
        }
      }

      @Override
      public void visitComposite(CompositeElement composite) {
        if (!REF_FILTER.contains(composite.getElementType())) {
          super.visitComposite(composite);
        }
      }
    });

    return buffer.toString();
  }

  @Nonnull
  public static String getReferenceText(@Nonnull LighterAST tree, @Nonnull LighterASTNode node) {
    return LightTreeUtil.toFilteredString(tree, node, REF_FILTER);
  }

  @Nonnull
  public static TreeElement addParenthToReplacedChild(@Nonnull ASTNode child, @Nonnull TreeElement newChild, @Nonnull PsiManager manager) {
    boolean needParenth = ElementType.EXPRESSION_BIT_SET.contains(child.getElementType()) &&
        ElementType.EXPRESSION_BIT_SET.contains(newChild.getElementType()) &&
        ReplaceExpressionUtil.isNeedParenthesis(child, newChild);
    if (!needParenth)
      return newChild;

    CompositeElement parenthExpr = ASTFactory.composite(JavaElementType.PARENTH_EXPRESSION);

    TreeElement dummyExpr = (TreeElement) newChild.clone();
    CharTable charTableByTree = SharedImplUtil.findCharTableByTree(newChild);
    new DummyHolder(manager, parenthExpr, null, charTableByTree);
    parenthExpr.putUserData(CharTable.CHAR_TABLE_KEY, charTableByTree);
    parenthExpr.rawAddChildren(ASTFactory.leaf(JavaTokenType.LPARENTH, "("));
    parenthExpr.rawAddChildren(dummyExpr);
    parenthExpr.rawAddChildren(ASTFactory.leaf(JavaTokenType.RPARENTH, ")"));

    try {
      CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(manager.getProject());
      PsiElement formatted = codeStyleManager.reformat(SourceTreeToPsiMap.treeToPsiNotNull(parenthExpr));
      parenthExpr = (CompositeElement) SourceTreeToPsiMap.psiToTreeNotNull(formatted);
    } catch (IncorrectOperationException e) {
      LOG.error(e); // should not happen
    }

    newChild.putUserData(CharTable.CHAR_TABLE_KEY, SharedImplUtil.findCharTableByTree(newChild));
    dummyExpr.getTreeParent().replaceChild(dummyExpr, newChild);

    // TODO remove explicit caches drop since this should be ok if we will use ChangeUtil for the modification
    TreeUtil.clearCaches(TreeUtil.getFileElement(parenthExpr));
    return parenthExpr;
  }

  public static void deleteSeparatingComma(@Nonnull CompositeElement element, @Nonnull ASTNode child) {
    assert child.getElementType() != JavaTokenType.COMMA : child;

    ASTNode next = PsiImplUtil.skipWhitespaceAndComments(child.getTreeNext());
    if (next != null && next.getElementType() == JavaTokenType.COMMA) {
      element.deleteChildInternal(next);
    } else {
      ASTNode prev = PsiImplUtil.skipWhitespaceAndCommentsBack(child.getTreePrev());
      if (prev != null && prev.getElementType() == JavaTokenType.COMMA) {
        element.deleteChildInternal(prev);
      }
    }
  }

  public static void addSeparatingComma(@Nonnull CompositeElement element,
                                        @Nonnull ASTNode child,
                                        @Nonnull TokenSet listTypes) {
    assert child.getElementType() != JavaTokenType.COMMA : child;

    scanChildren(element, child, listTypes, true);
    scanChildren(element, child, listTypes, false);
  }

  private static void scanChildren(CompositeElement element, ASTNode node, TokenSet listTypes, boolean forward) {
    ASTNode child = node;
    while (true) {
      child = (forward ? child.getTreeNext() : child.getTreePrev());
      if (child == null || child.getElementType() == JavaTokenType.COMMA) {
        break;
      }
      if (listTypes.contains(child.getElementType())) {
        CharTable charTable = SharedImplUtil.findCharTableByTree(element);
        PsiManager manager = element.getPsi().getManager();
        TreeElement comma = Factory.createSingleLeafElement(JavaTokenType.COMMA, ",", 0, 1, charTable,
            manager);
        element.addInternal(comma, comma, (forward ? node : child), Boolean.FALSE);
        break;
      }
    }
  }
}
