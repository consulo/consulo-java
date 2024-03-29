/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.java.language.impl.psi.impl.source.tree.java;

import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import com.intellij.java.language.impl.psi.impl.source.Constants;
import com.intellij.java.language.impl.psi.impl.source.tree.ChildRole;
import com.intellij.java.language.psi.*;
import consulo.language.ast.ASTNode;
import consulo.language.ast.ChildRoleBase;
import consulo.language.ast.IElementType;
import consulo.language.impl.psi.CompositePsiElement;
import consulo.language.impl.psi.SourceTreeToPsiMap;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.logging.Logger;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;

public class PsiTryStatementImpl extends CompositePsiElement implements PsiTryStatement, Constants {
  private static final Logger LOG = Logger.getInstance(PsiTryStatementImpl.class);

  private volatile PsiParameter[] myCachedCatchParameters = null;

  public PsiTryStatementImpl() {
    super(TRY_STATEMENT);
  }

  @Override
  public void clearCaches() {
    super.clearCaches();
    myCachedCatchParameters = null;
  }

  @Override
  public PsiCodeBlock getTryBlock() {
    return (PsiCodeBlock)findChildByRoleAsPsiElement(ChildRole.TRY_BLOCK);
  }

  @Override
  @Nonnull
  public PsiCodeBlock[] getCatchBlocks() {
    ASTNode tryBlock = SourceTreeToPsiMap.psiElementToTree(getTryBlock());
    if (tryBlock != null) {
      PsiCatchSection[] catchSections = getCatchSections();
      if (catchSections.length == 0) return PsiCodeBlock.EMPTY_ARRAY;
      boolean lastIncomplete = catchSections[catchSections.length - 1].getCatchBlock() == null;
      PsiCodeBlock[] blocks = new PsiCodeBlock[lastIncomplete ? catchSections.length - 1 : catchSections.length];
      for (int i = 0; i < blocks.length; i++) {
        blocks[i] = catchSections[i].getCatchBlock();
      }
      return blocks;
    }
    return PsiCodeBlock.EMPTY_ARRAY;
  }

  @Override
  @Nonnull
  public PsiParameter[] getCatchBlockParameters() {
    PsiParameter[] catchParameters = myCachedCatchParameters;
    if (catchParameters == null) {
      PsiCatchSection[] catchSections = getCatchSections();
      if (catchSections.length == 0) return PsiParameter.EMPTY_ARRAY;
      boolean lastIncomplete = catchSections[catchSections.length - 1].getCatchBlock() == null;
      int limit = lastIncomplete ? catchSections.length - 1 : catchSections.length;
      ArrayList<PsiParameter> parameters = new ArrayList<PsiParameter>();
      for (int i = 0; i < limit; i++) {
        PsiParameter parameter = catchSections[i].getParameter();
        if (parameter != null) parameters.add(parameter);
      }
      myCachedCatchParameters = catchParameters = parameters.toArray(new PsiParameter[parameters.size()]);
    }
    return catchParameters;
  }

  @Override
  @Nonnull
  public PsiCatchSection[] getCatchSections() {
    return getChildrenAsPsiElements(CATCH_SECTION_BIT_SET, PsiCatchSection.ARRAY_FACTORY);
  }

  @Override
  public PsiCodeBlock getFinallyBlock() {
    return (PsiCodeBlock)findChildByRoleAsPsiElement(ChildRole.FINALLY_BLOCK);
  }

  @Override
  public PsiResourceList getResourceList() {
    return PsiTreeUtil.getChildOfType(this, PsiResourceList.class);
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.TRY_KEYWORD:
        return findChildByType(TRY_KEYWORD);

      case ChildRole.TRY_BLOCK:
        return findChildByType(CODE_BLOCK);

      case ChildRole.FINALLY_KEYWORD:
        return findChildByType(FINALLY_KEYWORD);

      case ChildRole.FINALLY_BLOCK:
        {
          ASTNode finallyKeyword = findChildByRole(ChildRole.FINALLY_KEYWORD);
          if (finallyKeyword == null) return null;
          for(ASTNode child = finallyKeyword.getTreeNext(); child != null; child = child.getTreeNext()){
            if (child.getElementType() == CODE_BLOCK){
              return child;
            }
          }
          return null;
        }
    }
  }

  @Override
  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == TRY_KEYWORD) {
      return ChildRole.TRY_KEYWORD;
    }
    else if (i == FINALLY_KEYWORD) {
      return ChildRole.FINALLY_KEYWORD;
    }
    else if (i == CATCH_SECTION) {
      return ChildRole.CATCH_SECTION;
    }
    else {
      if (child.getElementType() == CODE_BLOCK) {
        int role = getChildRole(child, ChildRole.TRY_BLOCK);
        if (role != ChildRoleBase.NONE) return role;
        return getChildRole(child, ChildRole.FINALLY_BLOCK);
      }
      else {
        return ChildRoleBase.NONE;
      }
    }
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitTryStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public boolean processDeclarations(@Nonnull final PsiScopeProcessor processor,
                                     @Nonnull final ResolveState state,
                                     final PsiElement lastParent,
                                     @Nonnull final PsiElement place) {
    final PsiResourceList resourceList = getResourceList();
    if (resourceList != null && lastParent instanceof PsiCodeBlock && lastParent == getTryBlock()) {
      return PsiImplUtil.processDeclarationsInResourceList(resourceList, processor, state, lastParent);
    }

    return true;
  }

  public String toString() {
    return "PsiTryStatement";
  }
}
