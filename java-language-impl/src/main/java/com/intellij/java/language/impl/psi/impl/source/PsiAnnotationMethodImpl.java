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

import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.language.impl.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.java.language.impl.psi.impl.java.stubs.PsiMethodStub;
import com.intellij.java.language.impl.psi.impl.source.tree.ChildRole;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiFileFactory;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.SoftReference;
import jakarta.annotation.Nonnull;

/**
 * @author ven
 */
public class PsiAnnotationMethodImpl extends PsiMethodImpl implements PsiAnnotationMethod {
    private SoftReference<PsiAnnotationMemberValue> myCachedDefaultValue = null;

    public PsiAnnotationMethodImpl(PsiMethodStub stub) {
        super(stub, JavaStubElementTypes.ANNOTATION_METHOD);
    }

    public PsiAnnotationMethodImpl(ASTNode node) {
        super(node);
    }

    @Override
    public boolean hasModifierProperty(@Nonnull String name) {
        return PsiModifier.ABSTRACT.equals(name) || PsiModifier.PUBLIC.equals(name) || super.hasModifierProperty(name);
    }

    @Override
    protected void dropCached() {
        myCachedDefaultValue = null;
    }

    @Override
    @RequiredReadAction
    public PsiAnnotationMemberValue getDefaultValue() {
        PsiMethodStub stub = getStub();
        if (stub != null) {
            String text = stub.getDefaultValueText();
            if (StringUtil.isEmpty(text)) {
                return null;
            }

            if (myCachedDefaultValue != null) {
                PsiAnnotationMemberValue value = myCachedDefaultValue.get();
                if (value != null) {
                    return value;
                }
            }

            String annoText = "@interface _Dummy_ { Class foo() default " + text + "; }";
            PsiFileFactory factory = PsiFileFactory.getInstance(getProject());
            PsiJavaFile file = (PsiJavaFile)factory.createFileFromText("a.java", JavaFileType.INSTANCE, annoText);
            PsiAnnotationMemberValue value = ((PsiAnnotationMethod)file.getClasses()[0].getMethods()[0]).getDefaultValue();
            myCachedDefaultValue = new SoftReference<>(value);
            return value;
        }

        myCachedDefaultValue = null;

        ASTNode node = getNode().findChildByRole(ChildRole.ANNOTATION_DEFAULT_VALUE);
        if (node == null) {
            return null;
        }
        return (PsiAnnotationMemberValue)node.getPsi();
    }

    @Override
    @RequiredReadAction
    public String toString() {
        return "PsiAnnotationMethod:" + getName();
    }

    @Override
    public final void accept(@Nonnull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor elemVisitor) {
            elemVisitor.visitAnnotationMethod(this);
        }
        else {
            visitor.visitElement(this);
        }
    }
}