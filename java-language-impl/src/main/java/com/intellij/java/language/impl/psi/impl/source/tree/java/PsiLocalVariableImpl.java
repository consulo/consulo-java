/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.java.language.impl.psi.impl.PsiConstantEvaluationHelperImpl;
import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import com.intellij.java.language.impl.psi.impl.PsiVariableEx;
import com.intellij.java.language.impl.psi.impl.source.Constants;
import com.intellij.java.language.impl.psi.impl.source.JavaDummyHolder;
import com.intellij.java.language.impl.psi.impl.source.tree.ChildRole;
import com.intellij.java.language.impl.psi.impl.source.tree.ElementType;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaSharedImplUtil;
import com.intellij.java.language.psi.*;
import consulo.content.scope.SearchScope;
import consulo.language.ast.ASTNode;
import consulo.language.ast.ChildRoleBase;
import consulo.language.ast.IElementType;
import consulo.language.ast.TokenType;
import consulo.language.impl.ast.CompositeElement;
import consulo.language.impl.ast.Factory;
import consulo.language.impl.ast.SharedImplUtil;
import consulo.language.impl.ast.TreeUtil;
import consulo.language.impl.psi.*;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.CharTable;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.navigation.ItemPresentation;
import consulo.navigation.ItemPresentationProvider;
import jakarta.annotation.Nonnull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class PsiLocalVariableImpl extends CompositePsiElement implements PsiLocalVariable, PsiVariableEx, Constants {
  private static final Logger LOG = Logger.getInstance(PsiLocalVariableImpl.class);

  private volatile String myCachedName = null;

  @SuppressWarnings({"UnusedDeclaration"})
  public PsiLocalVariableImpl() {
    this(LOCAL_VARIABLE);
  }

  protected PsiLocalVariableImpl(final IElementType type) {
    super(type);
  }

  @Override
  public void clearCaches() {
    super.clearCaches();
    myCachedName = null;
  }

  @Override
  @Nonnull
  public final PsiIdentifier getNameIdentifier() {
    final PsiElement element = findChildByRoleAsPsiElement(ChildRole.NAME);
    assert element instanceof PsiIdentifier : getText();
    return (PsiIdentifier)element;
  }

  @Override
  @Nonnull
  public final String getName() {
    String cachedName = myCachedName;
    if (cachedName == null){
      myCachedName = cachedName = getNameIdentifier().getText();
    }
    return cachedName;
  }

  @Override
  public void setInitializer(PsiExpression initializer) throws IncorrectOperationException {
    JavaSharedImplUtil.setInitializer(this, initializer);
  }

  @Override
  public PsiElement setName(@Nonnull String name) throws IncorrectOperationException {
    PsiImplUtil.setName(getNameIdentifier(), name);
    return this;
  }

  @Override
  @Nonnull
  public PsiType getType() {
    return JavaSharedImplUtil.getType(getTypeElement(), getNameIdentifier());
  }

  @Override
  @Nonnull
  public PsiTypeElement getTypeElement() {
    PsiTypeElement typeElement = PsiTreeUtil.getChildOfType(this, PsiTypeElement.class);
    if (typeElement != null) return typeElement;

    final PsiElement parent = getParent();
    assert parent != null : "no parent; " + this + "; [" + getText() + "]";
    final PsiLocalVariable localVariable = PsiTreeUtil.getChildOfType(parent, PsiLocalVariable.class);
    assert localVariable != null : "no local variable in " + Arrays.toString(parent.getChildren());
    typeElement = PsiTreeUtil.getChildOfType(localVariable, PsiTypeElement.class);
    assert typeElement != null : "no type element in " + Arrays.toString(localVariable.getChildren());
    return typeElement;
  }

  @Override
  public PsiModifierList getModifierList() {
    final CompositeElement parent = getTreeParent();
    if (parent == null) return null;
    final CompositeElement first = (CompositeElement)parent.findChildByType(LOCAL_VARIABLE);
    return first != null ? (PsiModifierList)first.findChildByRoleAsPsiElement(ChildRole.MODIFIER_LIST) : null;
  }

  @Override
  public boolean hasModifierProperty(@Nonnull String name) {
    final PsiModifierList modifierList = getModifierList();
    return modifierList != null && modifierList.hasModifierProperty(name);
  }

  @Override
  public PsiExpression getInitializer() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.INITIALIZER);
  }

  @Override
  public boolean hasInitializer() {
    return getInitializer() != null;
  }

  @Override
  public Object computeConstantValue() {
    return computeConstantValue(new HashSet<PsiVariable>());
  }

  @Override
  public Object computeConstantValue(Set<PsiVariable> visitedVars) {
    if (!hasModifierProperty(PsiModifier.FINAL)) return null;

    PsiType type = getType();
    // javac rejects all non primitive and non String constants, although JLS states constants "variables whose initializers are constant expressions"
    if (!(type instanceof PsiPrimitiveType) && !type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) return null;

    PsiExpression initializer = getInitializer();
    if (initializer == null) return null;
    return PsiConstantEvaluationHelperImpl.computeCastTo(initializer, getType(), visitedVars);
  }

  @Override
  public int getTextOffset() {
    return getNameIdentifier().getTextOffset();
  }

  @Override
  public void normalizeDeclaration() throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
    final CharTable treeCharTab = SharedImplUtil.findCharTableByTree(this);

    final CompositeElement statement = getTreeParent();
    final PsiElement psiElement = SourceTreeToPsiMap.treeElementToPsi(statement);
    final PsiElement[] variables = psiElement instanceof  PsiDeclarationStatement
                                   ? ((PsiDeclarationStatement)psiElement).getDeclaredElements() : PsiElement.EMPTY_ARRAY;
    if (variables.length > 1) {
      final PsiModifierList modifierList = getModifierList();
      final PsiTypeElement typeElement = getTypeElement();
      assert modifierList != null : getText();
      ASTNode last = statement;
      for (int i = 1; i < variables.length; i++) {
        ASTNode typeCopy = typeElement.copy().getNode();
        ASTNode modifierListCopy = modifierList.copy().getNode();
        CompositeElement variable = (CompositeElement)SourceTreeToPsiMap.psiToTreeNotNull(variables[i]);

        ASTNode comma = PsiImplUtil.skipWhitespaceAndCommentsBack(variable.getTreePrev());
        if (comma != null && comma.getElementType() == JavaTokenType.COMMA) {
          CodeEditUtil.removeChildren(statement, comma, variable.getTreePrev());
        }

        CodeEditUtil.removeChild(statement, variable);
        final CharTable charTableByTree = SharedImplUtil.findCharTableByTree(statement);
        CompositeElement statement1 = Factory.createCompositeElement(DECLARATION_STATEMENT, charTableByTree, getManager());
        statement1.addChild(variable, null);

        ASTNode space = Factory.createSingleLeafElement(TokenType.WHITE_SPACE, " ", 0, 1, treeCharTab, getManager());
        variable.addChild(space, variable.getFirstChildNode());

        variable.addChild(typeCopy, variable.getFirstChildNode());

        if (modifierListCopy.getTextLength() > 0) {
          space = Factory.createSingleLeafElement(TokenType.WHITE_SPACE, " ", 0, 1, treeCharTab, getManager());
          variable.addChild(space, variable.getFirstChildNode());
        }

        variable.addChild(modifierListCopy, variable.getFirstChildNode());

        ASTNode semicolon = Factory.createSingleLeafElement(JavaTokenType.SEMICOLON, ";", 0, 1, treeCharTab, getManager());
        SourceTreeToPsiMap.psiToTreeNotNull(variables[i - 1]).addChild(semicolon, null);

        CodeEditUtil.addChild(statement.getTreeParent(), statement1, last.getTreeNext());

        last = statement1;
      }
    }

    JavaSharedImplUtil.normalizeBrackets(this);
  }

  @Override
  public void deleteChildInternal(@Nonnull ASTNode child) {
    if (getChildRole(child) == ChildRole.INITIALIZER){
      ASTNode eq = findChildByRole(ChildRole.INITIALIZER_EQ);
      if (eq != null){
        deleteChildInternal(eq);
      }
    }
    super.deleteChildInternal(child);
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.MODIFIER_LIST:
        return findChildByType(MODIFIER_LIST);

      case ChildRole.TYPE:
        return findChildByType(TYPE);

      case ChildRole.NAME:
        return findChildByType(JavaTokenType.IDENTIFIER);

      case ChildRole.INITIALIZER_EQ:
        return findChildByType(JavaTokenType.EQ);

      case ChildRole.INITIALIZER:
        return findChildByType(ElementType.EXPRESSION_BIT_SET);

      case ChildRole.CLOSING_SEMICOLON:
        return TreeUtil.findChildBackward(this, JavaTokenType.SEMICOLON);
    }
  }

  @Override
  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == MODIFIER_LIST) {
      return ChildRole.MODIFIER_LIST;
    }
    else if (i == TYPE) {
      return getChildRole(child, ChildRole.TYPE);
    }
    else if (i == JavaTokenType.IDENTIFIER) {
      return getChildRole(child, ChildRole.NAME);
    }
    else if (i == JavaTokenType.EQ) {
      return getChildRole(child, ChildRole.INITIALIZER_EQ);
    }
    else if (i == JavaTokenType.SEMICOLON) {
      return getChildRole(child, ChildRole.CLOSING_SEMICOLON);
    }
    else {
      if (ElementType.EXPRESSION_BIT_SET.contains(child.getElementType())) {
        return ChildRole.INITIALIZER;
      }
      return ChildRoleBase.NONE;
    }
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitLocalVariable(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public boolean processDeclarations(@Nonnull PsiScopeProcessor processor, @Nonnull ResolveState state, PsiElement lastParent, @Nonnull PsiElement place) {
    if (lastParent == null) return true;
    if (lastParent.getContext() instanceof JavaDummyHolder) {
      return processor.execute(this, state);
    }

    if (lastParent.getParent() != this) return true;
    final ASTNode lastParentTree = SourceTreeToPsiMap.psiElementToTree(lastParent);

    return getChildRole(lastParentTree) != ChildRole.INITIALIZER ||
           processor.execute(this, state);
  }

  @Override
  public ItemPresentation getPresentation() {
    return ItemPresentationProvider.getItemPresentation(this);
  }

  public String toString() {
    return "PsiLocalVariable:" + getName();
  }

  @Override
  @Nonnull
  public SearchScope getUseScope() {
    final PsiElement parentElement = getParent();
    if (parentElement instanceof PsiDeclarationStatement) {
      return new LocalSearchScope(parentElement.getParent());
    }
    else {
      return ResolveScopeManager.getElementUseScope(this);
    }
  }
}
