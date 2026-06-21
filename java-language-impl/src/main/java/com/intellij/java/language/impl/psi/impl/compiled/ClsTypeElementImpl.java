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
import consulo.language.impl.psi.SourceTreeToPsiMap;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.util.PsiTreeUtil;
import one.util.streamex.StreamEx;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public class ClsTypeElementImpl extends ClsElementImpl implements PsiTypeElement {
    static final char VARIANCE_NONE = '\0';
    static final char VARIANCE_EXTENDS = '+';
    static final char VARIANCE_SUPER = '-';
    static final char VARIANCE_INVARIANT = '*';

    private final PsiElement myParent;
    private final String myTypeText;
    private final char myVariance;
    private final TypeAnnotationContainer myAnnotations;
    private final NullableLazyValue<ClsElementImpl> myChild;
    private final NotNullLazyValue<PsiType> myCachedType;

    public ClsTypeElementImpl(PsiElement parent, String typeText, char variance) {
        this(parent, typeText, variance, TypeAnnotationContainer.EMPTY);
    }

    ClsTypeElementImpl(@Nullable PsiElement parent, TypeInfo typeInfo) {
        this(parent, Objects.requireNonNull(typeInfo.text()), VARIANCE_NONE, typeInfo.getTypeAnnotations());
    }

    ClsTypeElementImpl(
        @Nullable PsiElement parent,
        String typeText,
        char variance,
        TypeAnnotationContainer annotations
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
    public void appendMirrorText(int indentLevel, StringBuilder buffer) {
        buffer.append(getType().getCanonicalText(true));
    }

    @Override
    public void setMirror(TreeElement element) throws InvalidMirrorException {
        setMirrorCheckingType(element, JavaElementType.TYPE);

        PsiTypeElement mirror = SourceTreeToPsiMap.treeToPsiNotNull(element);
        ClsElementImpl childValue = myChild.getValue();
        if (childValue instanceof ClsTypeElementImpl) {
            setMirror(childValue, PsiTreeUtil.getChildOfType(mirror, PsiTypeElement.class));
        }
        else if (childValue instanceof ClsJavaCodeReferenceElementImpl) {
            setMirror(childValue, PsiTreeUtil.getChildOfType(mirror, PsiJavaCodeReferenceElement.class));
        }
    }

    private boolean isArray() {
        return myTypeText.endsWith("[]");
    }

    private boolean isVarArgs() {
        return myTypeText.endsWith("...");
    }

    @Override
    public PsiType getType() {
        return myCachedType.getValue();
    }

    @Override
    public PsiJavaCodeReferenceElement getInnermostComponentReferenceElement() {
        ClsElementImpl child = myChild.getValue();
        if (child instanceof ClsTypeElementImpl typeElement) {
            return typeElement.getInnermostComponentReferenceElement();
        }
        else {
            return (PsiJavaCodeReferenceElement) child;
        }
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
        switch (myVariance) {
            case VARIANCE_INVARIANT:
                return null;
            case VARIANCE_NONE:
                return new ClsJavaCodeReferenceElementImpl(this, myTypeText, myAnnotations);
            default:
                return new ClsTypeElementImpl(this, myTypeText, VARIANCE_NONE, myAnnotations.forBound());
        }
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

    private ClsElementImpl getDeepestArrayElement() {
        int depth = getArrayDepth();
        int bracketPos = myTypeText.length() - depth * 2 - (isVarArgs() ? 1 : 0);
        TypeAnnotationContainer container = myAnnotations;
        for (int i = 0; i < depth; i++) {
            container = container.forArrayElement();
        }
        return new ClsTypeElementImpl(this, myTypeText.substring(0, bracketPos), myVariance, container);
    }


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

    @RequiredReadAction
    private PsiType calculateType() {
        return calculateBaseType().annotate(myAnnotations.getProvider(this));
    }

    @RequiredReadAction
    private PsiType calculateBaseType() {
        PsiType result = PsiJavaParserFacadeImpl.getPrimitiveType(myTypeText);
        if (result != null) {
            return result;
        }

        ClsElementImpl childElement = myChild.getValue();
        if (childElement instanceof ClsTypeElementImpl typeElement) {
            if (myVariance == VARIANCE_EXTENDS) {
                return PsiWildcardType.createExtends(getManager(), typeElement.getType());
            }
            if (myVariance == VARIANCE_SUPER) {
                return PsiWildcardType.createSuper(getManager(), typeElement.getType());
            }
            assert isArray() || isVarArgs() : this;
            assert myVariance == VARIANCE_NONE : this + "(" + myVariance + ")";
            return createArrayType(typeElement);
        }
        if (childElement instanceof ClsJavaCodeReferenceElementImpl codeRefElem) {
            assert myVariance == VARIANCE_NONE : this + "(" + myVariance + ")";
            return new PsiClassReferenceType(codeRefElem, null);
        }
        assert childElement == null : this;
        assert myVariance == VARIANCE_INVARIANT : this + "(" + myVariance + ")";
        return PsiWildcardType.createUnbounded(getManager());
    }

    @Override
    public void accept(PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor elemVisitor) {
            elemVisitor.visitTypeElement(this);
        }
        else {
            visitor.visitElement(this);
        }
    }

    @Override
    public PsiAnnotation[] getAnnotations() {
        throw new UnsupportedOperationException();//todo
    }

    @Override
    public PsiAnnotation findAnnotation(String qualifiedName) {
        return PsiImplUtil.findAnnotation(this, qualifiedName);
    }

    @Override
    public PsiAnnotation addAnnotation(String qualifiedName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PsiAnnotation[] getApplicableAnnotations() {
        return getType().getAnnotations();
    }

    @Override
    @RequiredReadAction
    public String toString() {
        return "PsiTypeElement:" + getText();
    }
}