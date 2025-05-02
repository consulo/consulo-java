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
package com.intellij.java.language.impl.psi.impl.compiled;

import com.intellij.java.language.impl.psi.impl.PsiClassImplUtil;
import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import com.intellij.java.language.impl.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.java.language.impl.psi.impl.cache.TypeInfo;
import com.intellij.java.language.impl.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.java.language.impl.psi.impl.java.stubs.PsiMethodStub;
import com.intellij.java.language.impl.psi.scope.util.PsiScopesUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.MethodSignature;
import com.intellij.java.language.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.java.language.psi.util.MethodSignatureUtil;
import consulo.application.dumb.IndexNotReadyException;
import consulo.application.util.AtomicNotNullLazyValue;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.NotNullLazyValue;
import consulo.component.extension.Extensions;
import consulo.content.scope.SearchScope;
import consulo.language.content.FileIndexFacade;
import consulo.language.impl.ast.TreeElement;
import consulo.language.impl.psi.SourceTreeToPsiMap;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.navigation.ItemPresentation;
import consulo.navigation.ItemPresentationProvider;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;

public class ClsMethodImpl extends ClsMemberImpl<PsiMethodStub> implements PsiAnnotationMethod {
    private final NotNullLazyValue<PsiTypeElement> myReturnType;
    private final NotNullLazyValue<PsiAnnotationMemberValue> myDefaultValue;

    public ClsMethodImpl(final PsiMethodStub stub) {
        super(stub);

        myReturnType = isConstructor() ? null : new AtomicNotNullLazyValue<PsiTypeElement>() {
            @Nonnull
            @Override
            protected PsiTypeElement compute() {
                PsiMethodStub stub = getStub();
                String typeText = TypeInfo.createTypeText(stub.getReturnTypeText());
                assert typeText != null : stub;
                return new ClsTypeElementImpl(ClsMethodImpl.this, typeText, ClsTypeElementImpl.VARIANCE_NONE);
            }
        };

        final String text = getStub().getDefaultValueText();
        myDefaultValue = StringUtil.isEmptyOrSpaces(text) ? null : new AtomicNotNullLazyValue<PsiAnnotationMemberValue>() {
            @Nonnull
            @Override
            protected PsiAnnotationMemberValue compute() {
                return ClsParsingUtil.createMemberValueFromText(text, getManager(), ClsMethodImpl.this);
            }
        };
    }

    @Override
    @Nonnull
    public PsiElement[] getChildren() {
        return getChildren(
            getDocComment(),
            getModifierList(),
            getReturnTypeElement(),
            getNameIdentifier(),
            getParameterList(),
            getThrowsList(),
            getDefaultValue()
        );
    }

    @Override
    public PsiClass getContainingClass() {
        return (PsiClass)getParent();
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
    public HierarchicalMethodSignature getHierarchicalMethodSignature() {
        return PsiSuperMethodImplUtil.getHierarchicalMethodSignature(this);
    }

    @Override
    public PsiTypeElement getReturnTypeElement() {
        return myReturnType != null ? myReturnType.getValue() : null;
    }

    @Override
    public PsiType getReturnType() {
        PsiTypeElement typeElement = getReturnTypeElement();
        return typeElement == null ? null : typeElement.getType();
    }

    @Override
    @Nonnull
    public PsiModifierList getModifierList() {
        return getStub().findChildStubByType(JavaStubElementTypes.MODIFIER_LIST).getPsi();
    }

    @Override
    public boolean hasModifierProperty(@Nonnull String name) {
        return getModifierList().hasModifierProperty(name);
    }

    @Override
    @Nonnull
    public PsiParameterList getParameterList() {
        return getStub().findChildStubByType(JavaStubElementTypes.PARAMETER_LIST).getPsi();
    }

    @Override
    @Nonnull
    public PsiReferenceList getThrowsList() {
        return getStub().findChildStubByType(JavaStubElementTypes.THROWS_LIST).getPsi();
    }

    @Override
    public PsiTypeParameterList getTypeParameterList() {
        return getStub().findChildStubByType(JavaStubElementTypes.TYPE_PARAMETER_LIST).getPsi();
    }

    @Override
    public PsiCodeBlock getBody() {
        return null;
    }

    @Override
    public boolean isDeprecated() {
        return getStub().isDeprecated() || PsiImplUtil.isDeprecatedByAnnotation(this);
    }

    @Override
    public PsiAnnotationMemberValue getDefaultValue() {
        return myDefaultValue != null ? myDefaultValue.getValue() : null;
    }

    @Override
    public boolean isConstructor() {
        return getStub().isConstructor();
    }

    @Override
    public boolean isVarArgs() {
        return getStub().isVarArgs();
    }

    @Override
    @Nonnull
    public MethodSignature getSignature(@Nonnull PsiSubstitutor substitutor) {
        return MethodSignatureBackedByPsiMethod.create(this, substitutor);
    }

    @Override
    public void appendMirrorText(int indentLevel, @Nonnull StringBuilder buffer) {
        appendText(getDocComment(), indentLevel, buffer, NEXT_LINE);
        appendText(getModifierList(), indentLevel, buffer, "");
        appendText(getTypeParameterList(), indentLevel, buffer, " ");
        if (!isConstructor()) {
            appendText(getReturnTypeElement(), indentLevel, buffer, " ");
        }
        appendText(getNameIdentifier(), indentLevel, buffer, "");
        appendText(getParameterList(), indentLevel, buffer);

        PsiReferenceList throwsList = getThrowsList();
        if (throwsList.getReferencedTypes().length > 0) {
            buffer.append(' ');
            appendText(throwsList, indentLevel, buffer);
        }

        PsiAnnotationMemberValue defaultValue = getDefaultValue();
        if (defaultValue != null) {
            buffer.append(" default ");
            appendText(defaultValue, indentLevel, buffer);
        }

        if (hasModifierProperty(PsiModifier.ABSTRACT) || hasModifierProperty(PsiModifier.NATIVE)) {
            buffer.append(";");
        }
        else {
            buffer.append(" { /* compiled code */ }");
        }
    }

    @Override
    public void setMirror(@Nonnull TreeElement element) throws InvalidMirrorException {
        setMirrorCheckingType(element, null);

        PsiMethod mirror = SourceTreeToPsiMap.treeToPsiNotNull(element);

        setMirrorIfPresent(getDocComment(), mirror.getDocComment());
        setMirror(getModifierList(), mirror.getModifierList());
        setMirror(getTypeParameterList(), mirror.getTypeParameterList());
        if (!isConstructor()) {
            setMirror(getReturnTypeElement(), mirror.getReturnTypeElement());
        }
        setMirror(getNameIdentifier(), mirror.getNameIdentifier());
        setMirror(getParameterList(), mirror.getParameterList());
        setMirror(getThrowsList(), mirror.getThrowsList());

        PsiAnnotationMemberValue defaultValue = getDefaultValue();
        if (defaultValue != null) {
            assert mirror instanceof PsiAnnotationMethod : this;
            setMirror(defaultValue, ((PsiAnnotationMethod)mirror).getDefaultValue());
        }
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
    public boolean processDeclarations(
        @Nonnull PsiScopeProcessor processor,
        @Nonnull ResolveState state,
        PsiElement lastParent,
        @Nonnull PsiElement place
    ) {
        processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, this);
        if (lastParent == null) {
            return true;
        }

        if (!PsiScopesUtil.walkChildrenScopes(this, processor, state, lastParent, place)) {
            return false;
        }

        final PsiParameter[] parameters = getParameterList().getParameters();
        for (PsiParameter parameter : parameters) {
            if (!processor.execute(parameter, state)) {
                return false;
            }
        }

        return true;
    }

    @Nullable
    public PsiMethod getSourceMirrorMethod() {
        return LanguageCachedValueUtil.getCachedValue(
            this,
            () -> CachedValueProvider.Result.create(
                calcSourceMirrorMethod(),
                getContainingFile(),
                getContainingFile().getNavigationElement(),
                FileIndexFacade.getInstance(getProject()).getRootModificationTracker()
            )
        );
    }

    @Nullable
    private PsiMethod calcSourceMirrorMethod() {
        PsiClass sourceClassMirror = ((ClsClassImpl)getParent()).getSourceMirrorClass();
        if (sourceClassMirror == null) {
            return null;
        }
        for (PsiMethod sourceMethod : sourceClassMirror.findMethodsByName(getName(), false)) {
            if (MethodSignatureUtil.areParametersErasureEqual(this, sourceMethod)) {
                return sourceMethod;
            }
        }
        return null;
    }

    @Override
    @Nonnull
    public PsiElement getNavigationElement() {
        for (ClsCustomNavigationPolicy customNavigationPolicy : Extensions.getExtensions(ClsCustomNavigationPolicy.EP_NAME)) {
            try {
                PsiElement navigationElement = customNavigationPolicy.getNavigationElement(this);
                if (navigationElement != null) {
                    return navigationElement;
                }
            }
            catch (IndexNotReadyException ignore) {
            }
        }

        try {
            final PsiMethod method = getSourceMirrorMethod();
            return method != null ? method.getNavigationElement() : this;
        }
        catch (IndexNotReadyException e) {
            return this;
        }
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
        return PsiImplUtil.getMemberUseScope(this);
    }

    @Override
    public String toString() {
        return "PsiMethod:" + getName();
    }
}
