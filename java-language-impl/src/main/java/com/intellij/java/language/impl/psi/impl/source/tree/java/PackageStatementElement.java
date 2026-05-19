// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.impl.psi.impl.source.tree.java;

import com.intellij.java.language.impl.psi.impl.source.Constants;
import com.intellij.java.language.impl.psi.impl.source.tree.ChildRole;
import consulo.language.ast.ASTNode;
import consulo.language.ast.ChildRoleBase;
import consulo.language.ast.IElementType;
import consulo.language.impl.ast.CompositeElement;
import consulo.language.impl.ast.TreeUtil;
import consulo.logging.Logger;

public class PackageStatementElement extends CompositeElement implements Constants {
    private static final Logger LOG = Logger.getInstance(PackageStatementElement.class);

    public PackageStatementElement() {
        super(PACKAGE_STATEMENT);
    }

    @Override
    public ASTNode findChildByRole(int role) {
        LOG.assertTrue(ChildRole.isUnique(role));
        switch (role) {
            case ChildRole.PACKAGE_KEYWORD:
                return findChildByType(PACKAGE_KEYWORD);

            case ChildRole.PACKAGE_REFERENCE:
                return findChildByType(JAVA_CODE_REFERENCE);

            case ChildRole.CLOSING_SEMICOLON:
                return TreeUtil.findChildBackward(this, SEMICOLON);

            case ChildRole.MODIFIER_LIST:
                return findChildByType(MODIFIER_LIST);

            default:
                return null;
        }
    }

    @Override
    public int getChildRole(ASTNode child) {
        LOG.assertTrue(child.getTreeParent() == this);
        IElementType i = child.getElementType();
        if (i == PACKAGE_KEYWORD) {
            return ChildRole.PACKAGE_KEYWORD;
        }
        else if (i == JAVA_CODE_REFERENCE) {
            return ChildRole.PACKAGE_REFERENCE;
        }
        else if (i == SEMICOLON) {
            return ChildRole.CLOSING_SEMICOLON;
        }
        else if (i == MODIFIER_LIST) {
            return ChildRole.MODIFIER_LIST;
        }
        else {
            return ChildRoleBase.NONE;
        }
    }
}
