// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.impl.psi.impl.compiled;

import com.intellij.java.language.impl.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.java.language.impl.psi.impl.java.stubs.PsiPackageStatementStub;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import consulo.language.impl.ast.TreeElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.stub.StubElement;
import org.jspecify.annotations.Nullable;

public class ClsPackageStatementImpl extends ClsRepositoryPsiElement<PsiPackageStatementStub> implements PsiPackageStatement {

    private final String myPackageName;

    public ClsPackageStatementImpl(PsiPackageStatementStub stub) {
        super(stub);
        myPackageName = stub.getPackageName();
    }

    @Override
    public PsiJavaCodeReferenceElement getPackageReference() {
        throw new UnsupportedOperationException("Method not implemented");
    }

    @Override
    public @Nullable PsiDocComment getDocComment() {
        return null;
    }

    @Override
    public PsiModifierList getAnnotationList() {
        @SuppressWarnings("unchecked") final StubElement<PsiModifierList> child =
            (StubElement<PsiModifierList>) getStub().findChildStubByType(JavaStubElementTypes.MODIFIER_LIST);
        return child == null ? null : child.getPsi();
    }

    @Override
    public PsiElement[] getChildren() {
        PsiModifierList list = getAnnotationList();
        return list == null ? PsiElement.EMPTY_ARRAY : new PsiElement[]{list};
    }

    @Override
    public String getPackageName() {
        return myPackageName;
    }

    @Override
    public void appendMirrorText(final int indentLevel, final StringBuilder buffer) {
        if (!myPackageName.isEmpty()) { // an empty package name should not happen for a well-formed class file
            PsiModifierList list = getAnnotationList();
            if (list != null) {
                for (PsiAnnotation annotation : list.getAnnotations()) {
                    appendText(annotation, indentLevel, buffer);
                    buffer.append("\n");
                }
            }
            buffer.append("package ").append(getPackageName()).append(';');
        }
    }

    @Override
    public void setMirror(TreeElement element) throws InvalidMirrorException {
        setMirrorCheckingType(element, JavaElementType.PACKAGE_STATEMENT);
    }

    @Override
    public void accept(PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor) {
            ((JavaElementVisitor) visitor).visitPackageStatement(this);
        }
        else {
            visitor.visitElement(this);
        }
    }

    @Override
    public String toString() {
        return "PsiPackageStatement:" + getPackageName();
    }
}
