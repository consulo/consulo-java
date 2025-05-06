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

import com.intellij.java.language.impl.psi.impl.PsiClassImplUtil;
import com.intellij.java.language.impl.psi.impl.PsiConstantEvaluationHelperImpl;
import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import com.intellij.java.language.impl.psi.impl.PsiVariableEx;
import com.intellij.java.language.impl.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.java.language.impl.psi.impl.java.stubs.PsiFieldStub;
import com.intellij.java.language.impl.psi.impl.source.resolve.JavaResolveCache;
import com.intellij.java.language.impl.psi.impl.source.tree.ChildRole;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaSharedImplUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.augment.PsiAugmentProvider;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import consulo.application.util.Queryable;
import consulo.content.scope.SearchScope;
import consulo.java.language.module.util.JavaClassNames;
import consulo.language.ast.ASTNode;
import consulo.language.file.light.LightVirtualFile;
import consulo.language.impl.ast.CompositeElement;
import consulo.language.impl.ast.Factory;
import consulo.language.impl.ast.TreeElement;
import consulo.language.impl.psi.CheckUtil;
import consulo.language.impl.psi.CodeEditUtil;
import consulo.language.impl.psi.SourceTreeToPsiMap;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiInvalidElementAccessException;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.stub.IStubElementType;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.navigation.ItemPresentation;
import consulo.navigation.ItemPresentationProvider;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.SoftReference;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.lang.ref.Reference;
import java.util.*;

public class PsiFieldImpl extends JavaStubPsiElement<PsiFieldStub> implements PsiField, PsiVariableEx, Queryable {
  private volatile Reference<PsiType> myCachedType;

  public PsiFieldImpl(final PsiFieldStub stub) {
    this(stub, JavaStubElementTypes.FIELD);
  }

  protected PsiFieldImpl(final PsiFieldStub stub, final IStubElementType type) {
    super(stub, type);
  }

  public PsiFieldImpl(final ASTNode node) {
    super(node);
  }

  @Override
  public void subtreeChanged() {
    super.subtreeChanged();
    dropCached();
  }

  private void dropCached() {
    myCachedType = null;
  }

  @Override
  protected Object clone() {
    PsiFieldImpl clone = (PsiFieldImpl) super.clone();
    clone.dropCached();
    return clone;
  }

  @Override
  public PsiClass getContainingClass() {
    PsiElement parent = getParent();
    return parent instanceof PsiClass ? (PsiClass) parent : PsiTreeUtil.getParentOfType(this, PsiSyntheticClass.class);
  }

  @Override
  public PsiElement getContext() {
    final PsiClass cc = getContainingClass();
    return cc != null ? cc : super.getContext();
  }

  @Override
  @Nonnull
  public CompositeElement getNode() {
    return (CompositeElement) super.getNode();
  }

  @Override
  @Nonnull
  public PsiIdentifier getNameIdentifier() {
    return (PsiIdentifier) getNode().findChildByRoleAsPsiElement(ChildRole.NAME);
  }

  @Override
  @Nonnull
  public String getName() {
    final PsiFieldStub stub = getGreenStub();
    if (stub != null) {
      return stub.getName();
    }
    return getNameIdentifier().getText();
  }

  @Override
  public PsiElement setName(@Nonnull String name) throws IncorrectOperationException {
    PsiImplUtil.setName(getNameIdentifier(), name);
    return this;
  }

  @Override
  @Nonnull
  @SuppressWarnings("Duplicates")
  public PsiType getType() {
    PsiFieldStub stub = getStub();
    if (stub != null) {
      PsiType type = SoftReference.dereference(myCachedType);
      if (type == null) {
        type = JavaSharedImplUtil.createTypeFromStub(this, stub.getType());
        myCachedType = new SoftReference<>(type);
      }
      return type;
    }

    myCachedType = null;
    PsiTypeElement typeElement = getTypeElement();
    assert typeElement != null : Arrays.toString(getChildren());
    return JavaSharedImplUtil.getType(typeElement, getNameIdentifier());
  }

  @Override
  public PsiTypeElement getTypeElement() {
    PsiField firstField = findFirstFieldInDeclaration();
    if (firstField != this) {
      return firstField.getTypeElement();
    }

    return (PsiTypeElement) getNode().findChildByRoleAsPsiElement(ChildRole.TYPE);
  }

  @Override
  @Nonnull
  public PsiModifierList getModifierList() {
    final PsiModifierList selfModifierList = getSelfModifierList();
    if (selfModifierList != null) {
      return selfModifierList;
    }
    PsiField firstField = findFirstFieldInDeclaration();
    if (firstField == this) {
      if (!isValid()) {
        throw new PsiInvalidElementAccessException(this);
      }

      final PsiField lastResort = findFirstFieldByTree();
      if (lastResort == this) {
        throw new IllegalStateException("Missing modifier list for sequence of fields: '" + getText() + "'");
      }

      firstField = lastResort;
    }

    return firstField.getModifierList();
  }

  @Nullable
  private PsiModifierList getSelfModifierList() {
    return getStubOrPsiChild(JavaStubElementTypes.MODIFIER_LIST);
  }

  @Override
  public boolean hasModifierProperty(@Nonnull String name) {
    return getModifierList().hasModifierProperty(name);
  }

  private PsiField findFirstFieldInDeclaration() {
    if (getSelfModifierList() != null) {
      return this;
    }

    final PsiFieldStub stub = getGreenStub();
    if (stub != null) {
      final List siblings = stub.getParentStub().getChildrenStubs();
      final int idx = siblings.indexOf(stub);
      assert idx >= 0;
      for (int i = idx - 1; i >= 0; i--) {
        if (!(siblings.get(i) instanceof PsiFieldStub)) {
          break;
        }
        PsiFieldStub prevField = (PsiFieldStub) siblings.get(i);
        final PsiFieldImpl prevFieldPsi = (PsiFieldImpl) prevField.getPsi();
        if (prevFieldPsi.getSelfModifierList() != null) {
          return prevFieldPsi;
        }
      }
    }

    return findFirstFieldByTree();
  }

  private PsiField findFirstFieldByTree() {
    CompositeElement treeElement = getNode();

    ASTNode modifierList = treeElement.findChildByRole(ChildRole.MODIFIER_LIST);
    if (modifierList == null) {
      ASTNode prevField = treeElement.getTreePrev();
      while (prevField != null && prevField.getElementType() != JavaElementType.FIELD) {
        prevField = prevField.getTreePrev();
      }
      if (prevField == null) {
        return this;
      }
      return ((PsiFieldImpl) SourceTreeToPsiMap.treeElementToPsi(prevField)).findFirstFieldInDeclaration();
    } else {
      return this;
    }
  }

  @Override
  public PsiExpression getInitializer() {
    return (PsiExpression) getNode().findChildByRoleAsPsiElement(ChildRole.INITIALIZER);
  }

  // avoids stub-to-AST switch if possible,
  // returns the light generated initializer literal expression if stored in stubs, the regular initializer if wasn't
  public PsiExpression getDetachedInitializer() {
    final PsiFieldStub stub = getGreenStub();
    PsiExpression initializer;
    if (stub == null) {
      initializer = getInitializer();
    } else {
      String initializerText = stub.getInitializerText();

      if (StringUtil.isEmpty(initializerText) || PsiFieldStub.INITIALIZER_NOT_STORED.equals(initializerText) || PsiFieldStub.INITIALIZER_TOO_LONG.equals(initializerText)) {
        initializer = getInitializer();
      } else {
        PsiJavaParserFacade parserFacade = JavaPsiFacade.getInstance(getProject()).getParserFacade();
        initializer = parserFacade.createExpressionFromText(initializerText, this);
        ((LightVirtualFile) initializer.getContainingFile().getViewProvider().getVirtualFile()).setWritable(false);
      }
    }

    return initializer;
  }

  /**
   * Avoids stub-to-AST switch if possible.
   *
   * @return Light generated initializer literal expression if it was stored in stubs, the regular initializer otherwise
   */
  @Nullable
  public static PsiExpression getDetachedInitializer(@Nonnull PsiVariable variable) {
    return variable instanceof PsiFieldImpl ? ((PsiFieldImpl) variable).getDetachedInitializer() : variable.getInitializer();
  }

  @Override
  public boolean hasInitializer() {
    PsiFieldStub stub = getGreenStub();
    if (stub != null) {
      return stub.getInitializerText() != null;
    }

    return getInitializer() != null;
  }

  private static class OurConstValueComputer implements JavaResolveCache.ConstValueComputer {
    private static final OurConstValueComputer INSTANCE = new OurConstValueComputer();

    @Override
    public Object execute(@Nonnull PsiVariable variable, Set<PsiVariable> visitedVars) {
      return ((PsiFieldImpl) variable)._computeConstantValue(visitedVars);
    }
  }

  @Nullable
  private Object _computeConstantValue(Set<PsiVariable> visitedVars) {
    PsiType type = getType();
    // javac rejects all non primitive and non String constants, although JLS states constants "variables whose initializers are constant expressions"
    if (!(type instanceof PsiPrimitiveType) && !type.equalsToText(JavaClassNames.JAVA_LANG_STRING)) {
      return null;
    }

    PsiExpression initializer = getDetachedInitializer();
    if (initializer == null) return null;
    if (!PsiAugmentProvider.canTrustFieldInitializer(this)) return null;
    return PsiConstantEvaluationHelperImpl.computeCastTo(initializer, type, visitedVars);
  }

  @Override
  public Object computeConstantValue() {
    return computeConstantValue(new HashSet<>(2));
  }

  @Override
  public Object computeConstantValue(Set<PsiVariable> visitedVars) {
    if (!hasModifierProperty(PsiModifier.FINAL)) {
      return null;
    }

    return JavaResolveCache.getInstance(getProject()).computeConstantValueWithCaching(this, OurConstValueComputer.INSTANCE, visitedVars);
  }

  @Override
  public boolean isDeprecated() {
    final PsiFieldStub stub = getGreenStub();
    if (stub != null) {
      return stub.isDeprecated() || stub.hasDeprecatedAnnotation() && PsiImplUtil.isDeprecatedByAnnotation(this);
    }

    return PsiImplUtil.isDeprecatedByDocTag(this) || PsiImplUtil.isDeprecatedByAnnotation(this);
  }

  @Override
  public PsiDocComment getDocComment() {
    final PsiFieldStub stub = getGreenStub();
    if (stub != null && !stub.hasDocComment()) {
      return null;
    }

    CompositeElement treeElement = getNode();
    if (getTypeElement() != null) {
      PsiElement element = treeElement.findChildByRoleAsPsiElement(ChildRole.DOC_COMMENT);
      return element instanceof PsiDocComment ? (PsiDocComment) element : null;
    } else {
      ASTNode prevField = treeElement.getTreePrev();
      while (prevField.getElementType() != JavaElementType.FIELD) {
        prevField = prevField.getTreePrev();
      }
      return ((PsiField) SourceTreeToPsiMap.treeElementToPsi(prevField)).getDocComment();
    }
  }

  @Override
  public void normalizeDeclaration() throws IncorrectOperationException {
    CheckUtil.checkWritable(this);

    final PsiTypeElement type = getTypeElement();
    PsiElement modifierList = getModifierList();
    ASTNode field = SourceTreeToPsiMap.psiElementToTree(type.getParent());
    while (true) {
      ASTNode comma = PsiImplUtil.skipWhitespaceAndComments(field.getTreeNext());
      if (comma == null || comma.getElementType() != JavaTokenType.COMMA) {
        break;
      }
      ASTNode nextField = PsiImplUtil.skipWhitespaceAndComments(comma.getTreeNext());
      if (nextField == null || nextField.getElementType() != JavaElementType.FIELD) {
        break;
      }

      TreeElement semicolon = Factory.createSingleLeafElement(JavaTokenType.SEMICOLON, ";", 0, 1, null, getManager());
      CodeEditUtil.addChild(field, semicolon, null);

      CodeEditUtil.removeChild(comma.getTreeParent(), comma);

      PsiElement typeClone = type.copy();
      CodeEditUtil.addChild(nextField, SourceTreeToPsiMap.psiElementToTree(typeClone), nextField.getFirstChildNode());

      PsiElement modifierListClone = modifierList.copy();
      CodeEditUtil.addChild(nextField, SourceTreeToPsiMap.psiElementToTree(modifierListClone), nextField.getFirstChildNode());

      field = nextField;
    }

    JavaSharedImplUtil.normalizeBrackets(this);
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor) visitor).visitField(this);
    } else {
      visitor.visitElement(this);
    }
  }

  @Override
  public boolean processDeclarations(@Nonnull PsiScopeProcessor processor, @Nonnull ResolveState state, PsiElement lastParent, @Nonnull PsiElement place) {
    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, this);
    return true;
  }

  @Override
  public String toString() {
    return "PsiField:" + getName();
  }

  @Override
  public PsiElement getOriginalElement() {
    PsiClass containingClass = getContainingClass();
    if (containingClass != null) {
      PsiField originalField = ((PsiClass) containingClass.getOriginalElement()).findFieldByName(getName(), false);
      if (originalField != null) {
        return originalField;
      }
    }
    return this;
  }

  @Override
  public ItemPresentation getPresentation() {
    return ItemPresentationProvider.getItemPresentation(this);
  }

  @Override
  public void setInitializer(PsiExpression initializer) throws IncorrectOperationException {
    JavaSharedImplUtil.setInitializer(this, initializer);
  }

  @Override
  public boolean isEquivalentTo(final PsiElement another) {
    return PsiClassImplUtil.isFieldEquivalentTo(this, another);
  }

  @Override
  @Nonnull
  public SearchScope getUseScope() {
    return PsiImplUtil.getMemberUseScope(this);
  }

  @Override
  public void putInfo(@Nonnull Map<String, String> info) {
    info.put("fieldName", getName());
  }
}