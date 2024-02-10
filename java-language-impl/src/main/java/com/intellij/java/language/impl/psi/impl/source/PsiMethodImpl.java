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
package com.intellij.java.language.impl.psi.impl.source;

import com.intellij.java.language.impl.psi.impl.PsiClassImplUtil;
import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import com.intellij.java.language.impl.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.java.language.impl.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.java.language.impl.psi.impl.java.stubs.PsiMethodStub;
import com.intellij.java.language.impl.psi.impl.light.LightCompactConstructorParameter;
import com.intellij.java.language.impl.psi.impl.light.LightParameterListBuilder;
import com.intellij.java.language.impl.psi.impl.source.tree.ChildRole;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaSharedImplUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.java.language.psi.util.MethodSignature;
import com.intellij.java.language.psi.util.MethodSignatureBackedByPsiMethod;
import consulo.application.ApplicationManager;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.Queryable;
import consulo.application.util.function.Computable;
import consulo.content.scope.SearchScope;
import consulo.language.ast.ASTNode;
import consulo.language.impl.ast.CompositeElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.stub.IStubElementType;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.navigation.ItemPresentation;
import consulo.navigation.ItemPresentationProvider;
import consulo.util.lang.ref.SoftReference;
import jakarta.annotation.Nonnull;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PsiMethodImpl extends JavaStubPsiElement<PsiMethodStub> implements PsiMethod, Queryable {
  private SoftReference<PsiType> myCachedType;

  public PsiMethodImpl(final PsiMethodStub stub) {
    this(stub, JavaStubElementTypes.METHOD);
  }

  protected PsiMethodImpl(final PsiMethodStub stub, final IStubElementType type) {
    super(stub, type);
  }

  public PsiMethodImpl(final ASTNode node) {
    super(node);
  }

  @Override
  public void subtreeChanged() {
    super.subtreeChanged();
    dropCached();
  }

  protected void dropCached() {
    myCachedType = null;
  }

  @Override
  protected Object clone() {
    PsiMethodImpl clone = (PsiMethodImpl)super.clone();
    clone.dropCached();
    return clone;
  }

  @Override
  public PsiClass getContainingClass() {
    PsiElement parent = getParent();
    return parent instanceof PsiClass ? (PsiClass)parent : PsiTreeUtil.getParentOfType(this, PsiSyntheticClass.class);
  }

  @Override
  public PsiElement getContext() {
    final PsiClass cc = getContainingClass();
    return cc != null ? cc : super.getContext();
  }

  @Override
  public PsiIdentifier getNameIdentifier() {
    return (PsiIdentifier)getNode().findChildByRoleAsPsiElement(ChildRole.NAME);
  }

  @Override
  @Nonnull
  public PsiMethod[] findSuperMethods() {
    return PsiSuperMethodImplUtil.findSuperMethods(this);
  }

  @Override
  @Nonnull
  public PsiMethod[] findSuperMethods(boolean checkAccess) {
    return PsiSuperMethodImplUtil.findSuperMethods(this, checkAccess);
  }

  @Override
  @Nonnull
  public PsiMethod[] findSuperMethods(PsiClass parentClass) {
    return PsiSuperMethodImplUtil.findSuperMethods(this, parentClass);
  }

  @Override
  @Nonnull
  public List<MethodSignatureBackedByPsiMethod> findSuperMethodSignaturesIncludingStatic(boolean checkAccess) {
    return PsiSuperMethodImplUtil.findSuperMethodSignaturesIncludingStatic(this, checkAccess);
  }

  @Override
  public PsiMethod findDeepestSuperMethod() {
    return PsiSuperMethodImplUtil.findDeepestSuperMethod(this);
  }

  @Override
  @Nonnull
  public PsiMethod[] findDeepestSuperMethods() {
    return PsiSuperMethodImplUtil.findDeepestSuperMethods(this);
  }

  @Override
  @Nonnull
  public String getName() {
    final String name;
    final PsiMethodStub stub = getGreenStub();
    if (stub != null) {
      name = stub.getName();
    }
    else {
      final PsiIdentifier nameIdentifier = getNameIdentifier();
      name = nameIdentifier == null ? null : nameIdentifier.getText();
    }

    return name != null ? name : "<unnamed>";
  }

  @Override
  @Nonnull
  public HierarchicalMethodSignature getHierarchicalMethodSignature() {
    return PsiSuperMethodImplUtil.getHierarchicalMethodSignature(this);
  }

  @Override
  public PsiElement setName(@Nonnull String name) throws IncorrectOperationException {
    final PsiIdentifier identifier = getNameIdentifier();
    if (identifier == null) {
      throw new IncorrectOperationException("Empty name: " + this);
    }
    PsiImplUtil.setName(identifier, name);
    return this;
  }

  @Override
  public PsiTypeElement getReturnTypeElement() {
    if (isConstructor()) {
      return null;
    }
    return (PsiTypeElement)getNode().findChildByRoleAsPsiElement(ChildRole.TYPE);
  }

  @Override
  public PsiTypeParameterList getTypeParameterList() {
    return getRequiredStubOrPsiChild(JavaStubElementTypes.TYPE_PARAMETER_LIST);
  }

  @Override
  public boolean hasTypeParameters() {
    return PsiImplUtil.hasTypeParameters(this);
  }

  @Override
  @Nonnull
  public PsiTypeParameter[] getTypeParameters() {
    return PsiImplUtil.getTypeParameters(this);
  }

  @Override
  public PsiType getReturnType() {
    if (isConstructor()) {
      return null;
    }

    PsiMethodStub stub = getStub();
    if (stub != null) {
      PsiType type = SoftReference.dereference(myCachedType);
      if (type == null) {
        type = JavaSharedImplUtil.createTypeFromStub(this, stub.getReturnTypeText());
        myCachedType = new SoftReference<>(type);
      }
      return type;
    }

    myCachedType = null;
    PsiTypeElement typeElement = getReturnTypeElement();
    return typeElement != null ? JavaSharedImplUtil.getType(typeElement, getParameterList()) : null;
  }

  @Override
  @Nonnull
  public PsiModifierList getModifierList() {
    return getRequiredStubOrPsiChild(JavaStubElementTypes.MODIFIER_LIST);
  }

  @Override
  public boolean hasModifierProperty(@Nonnull String name) {
    return getModifierList().hasModifierProperty(name);
  }

  @Override
  @Nonnull
  public PsiParameterList getParameterList() {
    PsiParameterList list = getStubOrPsiChild(JavaStubElementTypes.PARAMETER_LIST);
    if (list == null) {
      return LanguageCachedValueUtil.getCachedValue(this, () -> {
        LightParameterListBuilder lightList = new LightParameterListBuilder(getManager(), getLanguage()) {
          @Override
          public String getText() {
            return null;
          }
        };
        PsiClass aClass = getContainingClass();
        if (aClass != null) {
          PsiRecordComponent[] recordComponents = aClass.getRecordComponents();
          for (PsiRecordComponent component : recordComponents) {
            String name = component.getName();
            lightList.addParameter(new LightCompactConstructorParameter(name, component.getType(), this, component));
          }
        }

        return CachedValueProvider.Result.create(lightList, this, PsiModificationTracker.MODIFICATION_COUNT);
      });
    }
    return list;
  }

  @Override
  @Nonnull
  public PsiReferenceList getThrowsList() {
    PsiReferenceList child = getStubOrPsiChild(JavaStubElementTypes.THROWS_LIST);
    if (child != null) {
      return child;
    }

    PsiMethodStub stub = getStub();
    Stream<String> children =
      stub != null ? stub.getChildrenStubs().stream().map(s -> s.getClass().getSimpleName() + " : " + s.getStubType()) : Stream.of(
        getChildren()).map(e -> e.getClass()
                                 .getSimpleName() + " : " + e.getNode().getElementType());
    throw new AssertionError("Missing throws list, file=" + getContainingFile() + " children:\n" + children.collect(Collectors.joining("\n")));
  }

  @Override
  public PsiCodeBlock getBody() {
    return (PsiCodeBlock)getNode().findChildByRoleAsPsiElement(ChildRole.METHOD_BODY);
  }

  @Override
  @Nonnull
  public CompositeElement getNode() {
    return (CompositeElement)super.getNode();
  }

  @Override
  public boolean isDeprecated() {
    final PsiMethodStub stub = getGreenStub();
    if (stub != null) {
      return stub.isDeprecated() || stub.hasDeprecatedAnnotation() && PsiImplUtil.isDeprecatedByAnnotation(this);
    }

    return PsiImplUtil.isDeprecatedByDocTag(this) || PsiImplUtil.isDeprecatedByAnnotation(this);
  }

  @Override
  public PsiDocComment getDocComment() {
    final PsiMethodStub stub = getGreenStub();
    if (stub != null && !stub.hasDocComment()) {
      return null;
    }

    return (PsiDocComment)getNode().findChildByRoleAsPsiElement(ChildRole.DOC_COMMENT);
  }

  @Override
  public boolean isConstructor() {
    final PsiMethodStub stub = getGreenStub();
    if (stub != null) {
      return stub.isConstructor();
    }

    return getNode().findChildByRole(ChildRole.TYPE) == null;
  }

  @Override
  public boolean isVarArgs() {
    final PsiMethodStub stub = getGreenStub();
    if (stub != null) {
      return stub.isVarArgs();
    }

    return PsiImplUtil.isVarArgs(this);
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitMethod(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiMethod:" + getName();
  }

  @Override
  public boolean processDeclarations(@Nonnull PsiScopeProcessor processor,
                                     @Nonnull ResolveState state,
                                     PsiElement lastParent,
                                     @Nonnull PsiElement place) {
    return PsiImplUtil.processDeclarationsInMethod(this, processor, state, lastParent, place);

  }

  @Override
  @Nonnull
  public MethodSignature getSignature(@Nonnull PsiSubstitutor substitutor) {
    if (substitutor == PsiSubstitutor.EMPTY) {
      return LanguageCachedValueUtil.getCachedValue(this, () ->
      {
        MethodSignature signature = MethodSignatureBackedByPsiMethod.create(this, PsiSubstitutor.EMPTY);
        return CachedValueProvider.Result.create(signature, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
      });
    }
    return MethodSignatureBackedByPsiMethod.create(this, substitutor);
  }

  @Override
  public PsiElement getOriginalElement() {
    final PsiClass containingClass = getContainingClass();
    if (containingClass != null) {
      PsiElement original = containingClass.getOriginalElement();
      if (original != containingClass) {
        final PsiMethod originalMethod = ((PsiClass)original).findMethodBySignature(this, false);
        if (originalMethod != null) {
          return originalMethod;
        }
      }
    }
    return this;
  }

  @Override
  public ItemPresentation getPresentation() {
    return ItemPresentationProvider.getItemPresentation(this);
  }

  @Override
  public boolean isEquivalentTo(final PsiElement another) {
    return PsiClassImplUtil.isMethodEquivalentTo(this, another);
  }

  @Override
  @Nonnull
  public SearchScope getUseScope() {
    return ApplicationManager.getApplication().runReadAction((Computable<SearchScope>)() -> PsiImplUtil.getMemberUseScope(this));
  }

  @Override
  public void putInfo(@Nonnull Map<String, String> info) {
    info.put("methodName", getName());
  }
}