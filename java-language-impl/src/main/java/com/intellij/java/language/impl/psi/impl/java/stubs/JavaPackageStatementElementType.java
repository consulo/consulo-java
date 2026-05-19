/*
 * Copyright 2013-2026 consulo.io
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

package com.intellij.java.language.impl.psi.impl.java.stubs;

import com.intellij.java.language.impl.psi.impl.java.stubs.impl.PsiPackageStatementStubImpl;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaSourceUtil;
import com.intellij.java.language.impl.psi.impl.source.tree.java.PackageStatementElement;
import com.intellij.java.language.impl.psi.impl.source.tree.java.PsiPackageStatementImpl;
import com.intellij.java.language.psi.PsiPackageStatement;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.ast.LighterAST;
import consulo.language.ast.LighterASTNode;
import consulo.language.psi.stub.IndexSink;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;

import java.io.IOException;

/**
 * @author VISTALL
 * @since 2026-05-18
 */
public class JavaPackageStatementElementType extends JavaStubElementType<PsiPackageStatementStub, PsiPackageStatement> {
    public JavaPackageStatementElementType() {
        super("PACKAGE_STATEMENT");
    }

    @Override
    public PsiPackageStatement createPsi(ASTNode node) {
        return new PsiPackageStatementImpl(node);
    }

    @Override
    public ASTNode createCompositeNode() {
        return new PackageStatementElement();
    }

    @Override
    public PsiPackageStatementStub createStub(LighterAST tree, LighterASTNode node, StubElement parentStub) {
        String refText = null;
        for (LighterASTNode child : tree.getChildren(node)) {
            IElementType type = child.getTokenType();
            if (type == JavaElementType.JAVA_CODE_REFERENCE) {
                refText = JavaSourceUtil.getReferenceText(tree, child);
            }
        }

        return new PsiPackageStatementStubImpl(parentStub, refText);
    }

    @Override
    public PsiPackageStatement createPsi(PsiPackageStatementStub stub) {
        return getPsiFactory(stub).createPackageStatement(stub);
    }

    @Override
    public void serialize(PsiPackageStatementStub stub, StubOutputStream stubOutputStream) throws IOException {
        stubOutputStream.writeName(stub.getPackageName());
    }

    @Override
    public PsiPackageStatementStub deserialize(StubInputStream stubInputStream, StubElement parent) throws IOException {
        String packageName = stubInputStream.readNameString();
        return new PsiPackageStatementStubImpl(parent, packageName);
    }

    @Override
    public void indexStub(PsiPackageStatementStub psiPackageStatementStub, IndexSink indexSink) {

    }
}
