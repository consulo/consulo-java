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
package com.intellij.java.language.impl.psi.impl.java.stubs.impl;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.impl.psi.impl.java.stubs.*;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaFile;
import com.intellij.java.language.psi.PsiJavaModule;
import consulo.language.psi.stub.IStubFileElementType;
import consulo.language.psi.stub.PsiFileStubImpl;
import consulo.language.psi.stub.StubElement;

/**
 * @author max
 */
public class PsiJavaFileStubImpl extends PsiFileStubImpl<PsiJavaFile> implements PsiJavaFileStub {
    private final LanguageLevel myLanguageLevel;
    private final boolean myCompiled;
    private final StubPsiFactory myFactory;

    public PsiJavaFileStubImpl(boolean compiled) {
        this(null, null, compiled);
    }

    public PsiJavaFileStubImpl(PsiJavaFile file, LanguageLevel languageLevel, boolean compiled) {
        super(file);
        myLanguageLevel = languageLevel;
        myCompiled = compiled;
        myFactory = compiled ? ClsStubPsiFactory.INSTANCE : SourceStubPsiFactory.INSTANCE;
    }

    @Override
    public IStubFileElementType getType() {
        return JavaStubElementTypes.JAVA_FILE;
    }

    @Override
    public PsiClass[] getClasses() {
        return getChildrenByType(JavaStubElementTypes.CLASS, PsiClass.ARRAY_FACTORY);
    }

    @Override
    public PsiJavaModule getModule() {
        StubElement<PsiJavaModule> moduleStub = findChildStubByType(JavaStubElementTypes.MODULE);
        return moduleStub != null ? moduleStub.getPsi() : null;
    }

    @Override
    public String getPackageName() {
        PsiPackageStatementStub stub = (PsiPackageStatementStub) findChildStubByType(JavaStubElementTypes.PACKAGE_STATEMENT);
        return stub == null ? "" : stub.getPackageName();
    }

    @Override
    public LanguageLevel getLanguageLevel() {
        return myLanguageLevel;
    }

    @Override
    public boolean isCompiled() {
        return myCompiled;
    }

    @Override
    public StubPsiFactory getPsiFactory() {
        return myFactory;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        PsiJavaFileStubImpl stub = (PsiJavaFileStubImpl) o;

        if (myCompiled != stub.myCompiled) {
            return false;
        }

        if (myLanguageLevel != stub.myLanguageLevel) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + (myLanguageLevel != null ? myLanguageLevel.hashCode() : 0);
        result = 31 * result + (myCompiled ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return "PsiJavaFileStub [" + getPackageName() + "]";
    }
}