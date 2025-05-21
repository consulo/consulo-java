// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.impl.compiled;

import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import com.intellij.java.language.impl.psi.impl.PsiJavaParserFacadeImpl;
import com.intellij.java.language.impl.psi.impl.cache.TypeAnnotationContainer;
import com.intellij.java.language.impl.psi.impl.cache.TypeInfo;
import com.intellij.java.language.impl.psi.impl.source.PsiClassReferenceType;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.util.AtomicNotNullLazyValue;
import consulo.application.util.AtomicNullableLazyValue;
import consulo.application.util.NotNullLazyValue;
import consulo.application.util.NullableLazyValue;
import consulo.language.impl.ast.TreeElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import one.util.streamex.StreamEx;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.Objects;

public class ClsTypeElementImpl extends ClsElementImpl implements PsiTypeElement {
    static final char VARIANCE_NONE = '\0';
    static final char VARIANCE_EXTENDS = '+';
    static final char VARIANCE_SUPER = '-';
    static final char VARIANCE_INVARIANT = '*';

    private final PsiElement myParent;
    @Nonnull
    private final String myTypeText;
    private final char myVariance;
    @Nonnull
    private final TypeAnnotationContainer myAnnotations;
    @Nonnull
    private final NullableLazyValue<ClsElementImpl> myChild;
    @Nonnull
    private final NotNullLazyValue<PsiType> myCachedType;

    public ClsTypeElementImpl(@Nonnull PsiElement parent, @Nonnull String typeText, char variance) {
        this(parent, typeText, variance, TypeAnnotationContainer.EMPTY);
    }

    ClsTypeElementImpl(@Nullable PsiElement parent, @Nonnull TypeInfo typeInfo) {
        this(parent, Objects.requireNonNull(TypeInfo.createTypeText(typeInfo)), VARIANCE_NONE, typeInfo.getTypeAnnotations());
    }

    ClsTypeElementImpl(
        @Nullable PsiElement parent,
        @Nonnull String typeText,
        char variance,
        @Nonnull TypeAnnotationContainer annotations
    ) {
        myParent = parent;
        myTypeText = TypeInfo.internFrequentType(typeText);
        myVariance = variance;
        myAnnotations = annotations;
        myChild = new AtomicNullableLazyValue<>() {
            @Override
            protected ClsElementImpl compute() {
                return calculateChild();
            }
        };
        myCachedType = AtomicNotNullLazyValue.createValue(this::calculateType);
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public PsiElement[] getChildren() {
        ClsElementImpl child = myChild.getValue();
        return child != null ? new PsiElement[]{child} : PsiElement.EMPTY_ARRAY;
    }

    @Override
    public PsiElement getParent() {
        return myParent;
    }

    @Override
    @RequiredReadAction
    public String getText() {
        String shortClassName = PsiNameHelper.getShortClassName(myTypeText);
        return decorateTypeText(shortClassName);
    }

    private String decorateTypeText(String shortClassName) {
        switch (myVariance) {
            case VARIANCE_NONE:
                return shortClassName;
            case VARIANCE_EXTENDS:
                return PsiWildcardType.EXTENDS_PREFIX + shortClassName;
            case VARIANCE_SUPER:
                return PsiWildcardType.SUPER_PREFIX + shortClassName;
            case VARIANCE_INVARIANT:
                return "?";
            default:
                assert false : myVariance;
                return null;
        }
    }

    public String getCanonicalText() {
        return decorateTypeText(myTypeText);
    }

    @Override
    public void appendMirrorText(int indentLevel, @Nonnull StringBuilder buffer) {
        buffer.append(getType().getCanonicalText(true));
    }

    @Override
    public void setMirror(@Nonnull TreeElement element) throws InvalidMirrorException {
        setMirrorCheckingType(element, JavaElementType.TYPE);

        ClsElementImpl child = myChild.getValue();
        if (child != null) {
            child.setMirror(element.getFirstChildNode());
        }
    }

    private boolean isArray() {
        return myTypeText.endsWith("[]");
    }

    private boolean isVarArgs() {
        return myTypeText.endsWith("...");
    }

    @Override
    @Nonnull
    public PsiType getType() {
        return myCachedType.getValue();
    }

    @Override
    public PsiJavaCodeReferenceElement getInnermostComponentReferenceElement() {
        return null;
    }

    private ClsElementImpl calculateChild() {
        if (PsiJavaParserFacadeImpl.getPrimitiveType(myTypeText) != null) {
            return null;
        }
        if (isArray()) {
            if (myVariance == VARIANCE_NONE) {
                return getDeepestArrayElement();
            }
            return new ClsTypeElementImpl(this, myTypeText, VARIANCE_NONE, myAnnotations.forBound());
        }
        if (isVarArgs()) {
            return getDeepestArrayElement();
        }
        return myVariance == VARIANCE_INVARIANT ? null :
            new ClsJavaCodeReferenceElementImpl(this, myTypeText, myVariance == VARIANCE_NONE ? myAnnotations : myAnnotations.forBound());
    }

    int getArrayDepth() {
        boolean varArgs = isVarArgs();
        if (!varArgs && !isArray()) {
            return 0;
        }
        int bracketPos = myTypeText.length() - (varArgs ? 3 : 2);
        int depth = 1;
        while (bracketPos > 2 && myTypeText.startsWith("[]", bracketPos - 2)) {
            bracketPos -= 2;
            depth++;
        }
        return depth;
    }

    @Nonnull
    private ClsElementImpl getDeepestArrayElement() {
        int depth = getArrayDepth();
        int bracketPos = myTypeText.length() - depth * 2 - (isVarArgs() ? 1 : 0);
        TypeAnnotationContainer container = myAnnotations;
        for (int i = 0; i < depth; i++) {
            container = container.forArrayElement();
        }
        return new ClsTypeElementImpl(this, myTypeText.substring(0, bracketPos), myVariance, container);
    }


    @Nonnull
    private PsiType createArrayType(PsiTypeElement deepestChild) {
        int depth = getArrayDepth();
        List<TypeAnnotationContainer> containers =
            StreamEx.iterate(myAnnotations, TypeAnnotationContainer::forArrayElement).limit(depth).toList();
        PsiType type = deepestChild.getType();
        for (int i = depth - 1; i >= 0; i--) {
            if (i == 0 && isVarArgs()) {
                type = new PsiEllipsisType(type);
            }
            else {
                type = type.createArrayType();
            }
            type = type.annotate(containers.get(i).getProvider(this));
        }
        return type;
    }

    @Nonnull
    @RequiredReadAction
    private PsiType calculateType() {
        return calculateBaseType().annotate(myAnnotations.getProvider(this));
    }

    @Nonnull
    @RequiredReadAction
    private PsiType calculateBaseType() {
        PsiType result = PsiJavaParserFacadeImpl.getPrimitiveType(myTypeText);
        if (result != null) {
            return result;
        }

        ClsElementImpl childElement = myChild.getValue();
        if (childElement instanceof ClsTypeElementImpl typeElement) {
            if (isArray()) {
                return switch (myVariance) {
                    case VARIANCE_NONE -> createArrayType(typeElement);
                    case VARIANCE_EXTENDS -> PsiWildcardType.createExtends(getManager(), typeElement.getType());
                    case VARIANCE_SUPER -> PsiWildcardType.createSuper(getManager(), typeElement.getType());
                    default -> {
                        assert false : myVariance;
                        yield null;
                    }
                };
            }
            assert isVarArgs() : this;
            return createArrayType(typeElement);
        }
        if (childElement instanceof ClsJavaCodeReferenceElementImpl codeRefElem) {
            PsiClassReferenceType psiClassReferenceType = new PsiClassReferenceType(codeRefElem, null);
            return switch (myVariance) {
                case VARIANCE_NONE -> psiClassReferenceType;
                case VARIANCE_EXTENDS -> PsiWildcardType.createExtends(
                    getManager(),
                    psiClassReferenceType.annotate(myAnnotations.forBound().getProvider(codeRefElem))
                );
                case VARIANCE_SUPER -> PsiWildcardType.createSuper(
                    getManager(),
                    psiClassReferenceType.annotate(myAnnotations.forBound().getProvider(codeRefElem))
                );
                case VARIANCE_INVARIANT -> PsiWildcardType.createUnbounded(getManager());
                default -> {
                    assert false : myVariance;
                    yield null;
                }
            };
        }
        assert childElement == null : this;
        return PsiWildcardType.createUnbounded(getManager());
    }

    @Override
    public void accept(@Nonnull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor elemVisitor) {
            elemVisitor.visitTypeElement(this);
        }
        else {
            visitor.visitElement(this);
        }
    }

    @Override
    @Nonnull
    public PsiAnnotation[] getAnnotations() {
        throw new UnsupportedOperationException();//todo
    }

    @Override
    public PsiAnnotation findAnnotation(@Nonnull String qualifiedName) {
        return PsiImplUtil.findAnnotation(this, qualifiedName);
    }

    @Override
    @Nonnull
    public PsiAnnotation addAnnotation(@Nonnull String qualifiedName) {
        throw new UnsupportedOperationException();
    }

    @Override
    @Nonnull
    public PsiAnnotation[] getApplicableAnnotations() {
        return getType().getAnnotations();
    }

    @Override
    @RequiredReadAction
    public String toString() {
        return "PsiTypeElement:" + getText();
    }
}