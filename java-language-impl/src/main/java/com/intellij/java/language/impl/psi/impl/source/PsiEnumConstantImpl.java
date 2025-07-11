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
package com.intellij.java.language.impl.psi.impl.source;

import com.intellij.java.language.impl.psi.impl.PsiClassImplUtil;
import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import com.intellij.java.language.impl.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.java.language.impl.psi.impl.java.stubs.PsiFieldStub;
import com.intellij.java.language.impl.psi.impl.source.tree.ChildRole;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiReference;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.navigation.ItemPresentation;
import consulo.navigation.ItemPresentationProvider;
import jakarta.annotation.Nonnull;

/**
 * @author dsl
 */
public class PsiEnumConstantImpl extends JavaStubPsiElement<PsiFieldStub> implements PsiEnumConstant {
    private static final Logger LOG = Logger.getInstance(PsiEnumConstantImpl.class);
    private final MyReference myReference = new MyReference();

    public PsiEnumConstantImpl(final PsiFieldStub stub) {
        super(stub, JavaStubElementTypes.ENUM_CONSTANT);
    }

    public PsiEnumConstantImpl(final ASTNode node) {
        super(node);
    }

    @Override
    public String toString() {
        return "PsiEnumConstant:" + getName();
    }

    @Override
    public void accept(@Nonnull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor) {
            ((JavaElementVisitor) visitor).visitEnumConstant(this);
        }
        else {
            visitor.visitElement(this);
        }
    }

    @Override
    public PsiExpressionList getArgumentList() {
        return (PsiExpressionList) calcTreeElement().findChildByRoleAsPsiElement(ChildRole.ARGUMENT_LIST);
    }

    @Override
    public PsiEnumConstantInitializer getInitializingClass() {
        return (PsiEnumConstantInitializer) getStubOrPsiChild(JavaStubElementTypes.ENUM_CONSTANT_INITIALIZER);
    }

    @Nonnull
    @Override
    public PsiEnumConstantInitializer getOrCreateInitializingClass() {
        final PsiEnumConstantInitializer initializingClass = getInitializingClass();
        if (initializingClass != null) {
            return initializingClass;
        }

        final PsiElementFactory factory = JavaPsiFacade.getElementFactory(getProject());
        final PsiEnumConstantInitializer initializer = factory.createEnumConstantFromText("foo{}", null).getInitializingClass();
        LOG.assertTrue(initializer != null);

        final PsiExpressionList argumentList = getArgumentList();
        if (argumentList != null) {
            return (PsiEnumConstantInitializer) addAfter(initializer, argumentList);
        }
        else {
            return (PsiEnumConstantInitializer) addAfter(initializer, getNameIdentifier());
        }
    }

    @Override
    public PsiClass getContainingClass() {
        PsiElement parent = getParent();
        return parent instanceof PsiClass ? (PsiClass) parent : null;
    }

    @Override
    public PsiElement getContext() {
        final PsiClass cc = getContainingClass();
        return cc != null ? cc : super.getContext();
    }

    @Override
    public PsiModifierList getModifierList() {
        return getStubOrPsiChild(JavaStubElementTypes.MODIFIER_LIST);
    }

    @Override
    public boolean hasModifierProperty(@Nonnull String name) {
        return PsiModifier.PUBLIC.equals(name) || PsiModifier.STATIC.equals(name) || PsiModifier.FINAL.equals(name);
    }

    @Override
    @Nonnull
    public PsiType getType() {
        return JavaPsiFacade.getInstance(getProject()).getElementFactory().createType(getContainingClass());
    }

    @Override
    public PsiTypeElement getTypeElement() {
        return null;
    }

    @Override
    public PsiExpression getInitializer() {
        return null;
    }

    @Override
    public boolean hasInitializer() {
        return true;
    }

    @Override
    public void normalizeDeclaration() throws IncorrectOperationException {
    }

    @Override
    public Object computeConstantValue() {
        return this;
    }

    @Override
    public PsiMethod resolveMethod() {
        PsiClass containingClass = getContainingClass();
        LOG.assertTrue(containingClass != null);
        final JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
        JavaResolveResult resolveResult = facade.getResolveHelper().resolveConstructor(facade.getElementFactory().createType(containingClass),
            getArgumentList(), this);
        return (PsiMethod) resolveResult.getElement();
    }

    @Override
    @Nonnull
    public JavaResolveResult[] multiResolve(boolean incompleteCode) {
        return myReference.multiResolve(incompleteCode);
    }

    @Override
    @Nonnull
    public JavaResolveResult resolveMethodGenerics() {
        PsiClass containingClass = getContainingClass();
        LOG.assertTrue(containingClass != null);
        final JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
        return facade.getResolveHelper().resolveConstructor(facade.getElementFactory().createType(containingClass), getArgumentList(), this);
    }

    @Override
    @Nonnull
    public PsiIdentifier getNameIdentifier() {
        return (PsiIdentifier) calcTreeElement().findChildByRoleAsPsiElement(ChildRole.NAME);
    }

    @Override
    @Nonnull
    public String getName() {
        final PsiFieldStub stub = getStub();
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
    public PsiDocComment getDocComment() {
        return (PsiDocComment) calcTreeElement().findChildByRoleAsPsiElement(ChildRole.DOC_COMMENT);
    }

    @Override
    public boolean isDeprecated() {
        final PsiFieldStub stub = getStub();
        if (stub != null) {
            return stub.isDeprecated();
        }

        PsiDocComment docComment = getDocComment();
        return docComment != null && docComment.findTagByName("deprecated") != null
            || getModifierList().findAnnotation(CommonClassNames.JAVA_LANG_DEPRECATED) != null;
    }

    @Override
    public PsiReference getReference() {
        return myReference;
    }

    @Override
    public PsiMethod resolveConstructor() {
        return resolveMethod();
    }

    private class MyReference implements PsiJavaReference {
        @Override
        public PsiElement getElement() {
            return PsiEnumConstantImpl.this;
        }

        @Override
        public TextRange getRangeInElement() {
            PsiIdentifier nameIdentifier = getNameIdentifier();
            int startOffsetInParent = nameIdentifier.getStartOffsetInParent();
            return new TextRange(startOffsetInParent, startOffsetInParent + nameIdentifier.getTextLength());
        }

        @Override
        public boolean isSoft() {
            return false;
        }

        @Override
        public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
            return getElement();
        }

        @Override
        public PsiElement bindToElement(@Nonnull PsiElement element) throws IncorrectOperationException {
            throw new IncorrectOperationException("Invalid operation");
        }

        @Override
        public void processVariants(@Nonnull PsiScopeProcessor processor) {
        }

        @Override
        @Nonnull
        public JavaResolveResult[] multiResolve(boolean incompleteCode) {
            final JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
            PsiClassType type = facade.getElementFactory().createType(getContainingClass());
            return facade.getResolveHelper().multiResolveConstructor(type, getArgumentList(), getElement());
        }

        @Override
        @Nonnull
        public JavaResolveResult advancedResolve(boolean incompleteCode) {
            final JavaResolveResult[] results = multiResolve(incompleteCode);
            if (results.length == 1) {
                return results[0];
            }
            return JavaResolveResult.EMPTY;
        }

        @Override
        public PsiElement resolve() {
            return advancedResolve(false).getElement();
        }

        @Override
        @Nonnull
        public String getCanonicalText() {
            return getContainingClass().getName();
        }

        @Override
        public boolean isReferenceTo(PsiElement element) {
            return element instanceof PsiMethod && ((PsiMethod) element).isConstructor() && ((PsiMethod) element).getContainingClass() ==
                getContainingClass() && getManager().areElementsEquivalent(resolve(), element);
        }
    }

    @Override
    public ItemPresentation getPresentation() {
        return ItemPresentationProvider.getItemPresentation(this);
    }

    @Override
    public void setInitializer(PsiExpression initializer) throws IncorrectOperationException {
        throw new IncorrectOperationException();
    }

    @Override
    public boolean isEquivalentTo(final PsiElement another) {
        return PsiClassImplUtil.isFieldEquivalentTo(this, another);
    }
}
