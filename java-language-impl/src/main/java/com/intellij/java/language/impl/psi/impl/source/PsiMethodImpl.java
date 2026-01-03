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
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.application.Application;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.Queryable;
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
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PsiMethodImpl extends JavaStubPsiElement<PsiMethodStub> implements PsiMethod, Queryable {
    private SoftReference<PsiType> myCachedType;

    public PsiMethodImpl(PsiMethodStub stub) {
        this(stub, JavaStubElementTypes.METHOD);
    }

    protected PsiMethodImpl(PsiMethodStub stub, IStubElementType type) {
        super(stub, type);
    }

    public PsiMethodImpl(ASTNode node) {
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
        return getParent() instanceof PsiClass psiClass ? psiClass : PsiTreeUtil.getParentOfType(this, PsiSyntheticClass.class);
    }

    @Override
    public PsiElement getContext() {
        PsiClass cc = getContainingClass();
        return cc != null ? cc : super.getContext();
    }

    @Override
    @RequiredReadAction
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

    @Nonnull
    @Override
    public PsiMethod[] findDeepestSuperMethods() {
        return PsiSuperMethodImplUtil.findDeepestSuperMethods(this);
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public String getName() {
        String name;
        PsiMethodStub stub = getGreenStub();
        if (stub != null) {
            name = stub.getName();
        }
        else {
            PsiIdentifier nameIdentifier = getNameIdentifier();
            name = nameIdentifier == null ? null : nameIdentifier.getText();
        }

        return name != null ? name : "<unnamed>";
    }

    @Nonnull
    @Override
    public HierarchicalMethodSignature getHierarchicalMethodSignature() {
        return PsiSuperMethodImplUtil.getHierarchicalMethodSignature(this);
    }

    @Override
    @RequiredWriteAction
    public PsiElement setName(@Nonnull String name) throws IncorrectOperationException {
        PsiIdentifier identifier = getNameIdentifier();
        if (identifier == null) {
            throw new IncorrectOperationException("Empty name: " + this);
        }
        PsiImplUtil.setName(identifier, name);
        return this;
    }

    @Override
    @RequiredReadAction
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

    @Nonnull
    @Override
    public PsiTypeParameter[] getTypeParameters() {
        return PsiImplUtil.getTypeParameters(this);
    }

    @Override
    @RequiredReadAction
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

    @Nonnull
    @Override
    public PsiModifierList getModifierList() {
        return getRequiredStubOrPsiChild(JavaStubElementTypes.MODIFIER_LIST);
    }

    @Override
    public boolean hasModifierProperty(@Nonnull String name) {
        return getModifierList().hasModifierProperty(name);
    }

    @Nonnull
    @Override
    public PsiParameterList getParameterList() {
        PsiParameterList list = getStubOrPsiChild(JavaStubElementTypes.PARAMETER_LIST);
        if (list == null) {
            return LanguageCachedValueUtil.getCachedValue(
                this,
                () -> {
                    LightParameterListBuilder lightList = new LightParameterListBuilder(getManager(), getLanguage()) {
                        @Override
                        @RequiredReadAction
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
                }
            );
        }
        return list;
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public PsiReferenceList getThrowsList() {
        PsiReferenceList child = getStubOrPsiChild(JavaStubElementTypes.THROWS_LIST);
        if (child != null) {
            return child;
        }

        PsiMethodStub stub = getStub();
        Stream<String> children = stub != null
            ? stub.getChildrenStubs().stream().map(s -> s.getClass().getSimpleName() + " : " + s.getStubType())
            : Stream.of(getChildren()).map(e -> e.getClass().getSimpleName() + " : " + e.getNode().getElementType());
        throw new AssertionError(
            "Missing throws list, file=" + getContainingFile() + " children:\n" +
                children.collect(Collectors.joining("\n"))
        );
    }

    @Override
    @RequiredReadAction
    public PsiCodeBlock getBody() {
        return (PsiCodeBlock)getNode().findChildByRoleAsPsiElement(ChildRole.METHOD_BODY);
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public CompositeElement getNode() {
        return (CompositeElement)super.getNode();
    }

    @Override
    public boolean isDeprecated() {
        PsiMethodStub stub = getGreenStub();
        if (stub != null) {
            return stub.isDeprecated() || stub.hasDeprecatedAnnotation() && PsiImplUtil.isDeprecatedByAnnotation(this);
        }

        return PsiImplUtil.isDeprecatedByDocTag(this) || PsiImplUtil.isDeprecatedByAnnotation(this);
    }

    @Override
    @RequiredReadAction
    public PsiDocComment getDocComment() {
        PsiMethodStub stub = getGreenStub();
        if (stub != null && !stub.hasDocComment()) {
            return null;
        }

        return (PsiDocComment)getNode().findChildByRoleAsPsiElement(ChildRole.DOC_COMMENT);
    }

    @Override
    @RequiredReadAction
    public boolean isConstructor() {
        PsiMethodStub stub = getGreenStub();
        if (stub != null) {
            return stub.isConstructor();
        }

        return getNode().findChildByRole(ChildRole.TYPE) == null;
    }

    @Override
    public boolean isVarArgs() {
        PsiMethodStub stub = getGreenStub();
        if (stub != null) {
            return stub.isVarArgs();
        }

        return PsiImplUtil.isVarArgs(this);
    }

    @Override
    public void accept(@Nonnull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor javaElementVisitor) {
            javaElementVisitor.visitMethod(this);
        }
        else {
            visitor.visitElement(this);
        }
    }

    @Override
    @RequiredReadAction
    public String toString() {
        return "PsiMethod:" + getName();
    }

    @Override
    public boolean processDeclarations(
        @Nonnull PsiScopeProcessor processor,
        @Nonnull ResolveState state,
        PsiElement lastParent,
        @Nonnull PsiElement place
    ) {
        return PsiImplUtil.processDeclarationsInMethod(this, processor, state, lastParent, place);
    }

    @Override
    @Nonnull
    public MethodSignature getSignature(@Nonnull PsiSubstitutor substitutor) {
        if (substitutor == PsiSubstitutor.EMPTY) {
            return LanguageCachedValueUtil.getCachedValue(
                this,
                () -> {
                    MethodSignature signature = MethodSignatureBackedByPsiMethod.create(this, PsiSubstitutor.EMPTY);
                    return CachedValueProvider.Result.create(signature, PsiModificationTracker.MODIFICATION_COUNT);
                }
            );
        }
        return MethodSignatureBackedByPsiMethod.create(this, substitutor);
    }

    @Override
    public PsiElement getOriginalElement() {
        PsiClass containingClass = getContainingClass();
        if (containingClass != null) {
            PsiElement original = containingClass.getOriginalElement();
            if (original != containingClass && original instanceof PsiClass originalClass) {
                PsiMethod originalMethod = originalClass.findMethodBySignature(this, false);
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
    public boolean isEquivalentTo(PsiElement another) {
        return PsiClassImplUtil.isMethodEquivalentTo(this, another);
    }

    @Override
    @Nonnull
    public SearchScope getUseScope() {
        return Application.get().runReadAction((Supplier<SearchScope>)() -> PsiImplUtil.getMemberUseScope(this));
    }

    @Override
    @RequiredReadAction
    public void putInfo(@Nonnull Map<String, String> info) {
        info.put("methodName", getName());
    }
}