// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.impl.psi.impl.java.stubs.impl;

import com.intellij.java.language.impl.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.java.language.impl.psi.impl.java.stubs.PsiPackageStatementStub;
import com.intellij.java.language.psi.PsiPackageStatement;
import consulo.language.psi.stub.StubBase;
import consulo.language.psi.stub.StubElement;
import consulo.util.lang.ObjectUtil;

public class PsiPackageStatementStubImpl extends StubBase<PsiPackageStatement> implements PsiPackageStatementStub {
    private final String myPackageName;

    public PsiPackageStatementStubImpl(StubElement parent, String packageName) {
        super(parent, JavaStubElementTypes.PACKAGE_STATEMENT);
        myPackageName = ObjectUtil.notNull(packageName, "");
    }

    @Override
    public String getPackageName() {
        return myPackageName;
    }

    @Override
    public String toString() {
        return "PsiPackageStatementStub[" + myPackageName + "]";
    }
}
