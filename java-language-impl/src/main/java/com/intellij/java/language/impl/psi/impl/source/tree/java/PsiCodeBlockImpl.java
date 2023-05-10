/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import com.intellij.java.language.impl.psi.impl.source.tree.ChildRole;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.impl.psi.scope.ElementClassHint;
import com.intellij.java.language.impl.psi.scope.NameHint;
import com.intellij.java.language.impl.psi.scope.PatternResolveState;
import com.intellij.java.language.impl.psi.scope.util.PsiScopesUtil;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.ast.ASTNode;
import consulo.language.ast.ChildRoleBase;
import consulo.language.ast.IElementType;
import consulo.language.impl.ast.TreeElement;
import consulo.language.impl.ast.TreeUtil;
import consulo.language.impl.psi.LazyParseablePsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.logging.Logger;
import consulo.util.lang.Pair;
import consulo.util.lang.ref.Ref;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class PsiCodeBlockImpl extends LazyParseablePsiElement implements PsiCodeBlock {
  private static final Logger LOG = Logger.getInstance(PsiCodeBlockImpl.class);

  public PsiCodeBlockImpl(CharSequence text) {
    super(JavaElementType.CODE_BLOCK, text);
  }

  @Override
  public void clearCaches() {
    super.clearCaches();
    myVariablesSet = null;
    myClassesSet = null;
    myConflict = false;
  }

  @Override
  @Nonnull
  public PsiStatement[] getStatements() {
    return PsiImplUtil.getChildStatements(this);
  }

  @Override
  public PsiElement getFirstBodyElement() {
    final PsiJavaToken lBrace = getLBrace();
    if (lBrace == null) return null;
    final PsiElement nextSibling = lBrace.getNextSibling();
    return nextSibling == getRBrace() ? null : nextSibling;
  }

  @Override
  public PsiElement getLastBodyElement() {
    final PsiJavaToken rBrace = getRBrace();
    if (rBrace != null) {
      final PsiElement prevSibling = rBrace.getPrevSibling();
      return prevSibling == getLBrace() ? null : prevSibling;
    }
    return getLastChild();
  }

  @Override
  public PsiJavaToken getLBrace() {
    return (PsiJavaToken) findChildByRoleAsPsiElement(ChildRole.LBRACE);
  }

  @Override
  public PsiJavaToken getRBrace() {
    return (PsiJavaToken) findChildByRoleAsPsiElement(ChildRole.RBRACE);
  }

  private volatile Set<String> myVariablesSet = null;
  private volatile Set<String> myClassesSet = null;
  private volatile boolean myConflict = false;

  // return Pair(classes, locals) or null if there was conflict
  @Nullable
  @RequiredReadAction
  private Pair<Set<String>, Set<String>> buildMaps() {
    Set<String> set1 = myClassesSet;
    Set<String> set2 = myVariablesSet;
    boolean wasConflict = myConflict;
    if (set1 == null || set2 == null) {
      final Set<String> localsSet = new HashSet<String>();
      final Set<String> classesSet = new HashSet<String>();
      final Ref<Boolean> conflict = new Ref<Boolean>(Boolean.FALSE);
      PsiScopesUtil.walkChildrenScopes(this, (element, state) -> {
        if (element instanceof PsiLocalVariable || element instanceof PsiPatternVariable) {
          final PsiVariable variable = (PsiVariable) element;
          final String name = variable.getName();
          if (!localsSet.add(name)) {
            conflict.set(Boolean.TRUE);
            localsSet.clear();
            classesSet.clear();
          }
        } else if (element instanceof PsiClass) {
          final PsiClass psiClass = (PsiClass) element;
          final String name = psiClass.getName();
          if (!classesSet.add(name)) {
            conflict.set(Boolean.TRUE);
            localsSet.clear();
            classesSet.clear();
          }
        }
        return !conflict.get();
      }, PatternResolveState.WHEN_BOTH.putInto(ResolveState.initial()), this, this);

      myClassesSet = set1 = classesSet.isEmpty() ? Collections.<String>emptySet() : classesSet;
      myVariablesSet = set2 = localsSet.isEmpty() ? Collections.<String>emptySet() : localsSet;
      myConflict = wasConflict = conflict.get();
    }
    return wasConflict ? null : Pair.create(set1, set2);
  }

  @Override
  public TreeElement addInternal(TreeElement first, ASTNode last, ASTNode anchor, Boolean before) {
    if (anchor == null) {
      if (before == null || before.booleanValue()) {
        anchor = findChildByRole(ChildRole.RBRACE);
        before = Boolean.TRUE;
      } else {
        anchor = findChildByRole(ChildRole.LBRACE);
        before = Boolean.FALSE;
      }
    }

    if (before == Boolean.TRUE) {
      while (isNonJavaStatement(anchor)) {
        anchor = anchor.getTreePrev();
        before = Boolean.FALSE;
      }
    } else if (before == Boolean.FALSE) {
      while (isNonJavaStatement(anchor)) {
        anchor = anchor.getTreeNext();
        before = Boolean.TRUE;
      }
    }

    return super.addInternal(first, last, anchor, before);
  }

  private static boolean isNonJavaStatement(ASTNode anchor) {
    final PsiElement psi = anchor.getPsi();
    return psi instanceof PsiStatement && psi.getLanguage() != JavaLanguage.INSTANCE;
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch (role) {
      default:
        return null;

      case ChildRole.LBRACE:
        return findChildByType(JavaTokenType.LBRACE);

      case ChildRole.RBRACE:
        return TreeUtil.findChildBackward(this, JavaTokenType.RBRACE);
    }
  }

  @Override
  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == JavaTokenType.LBRACE) {
      return getChildRole(child, ChildRole.LBRACE);
    } else if (i == JavaTokenType.RBRACE) {
      return getChildRole(child, ChildRole.RBRACE);
    } else {
      return ChildRoleBase.NONE;
    }
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor) visitor).visitCodeBlock(this);
    } else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiCodeBlock";
  }


  @Override
  public boolean processDeclarations(@Nonnull PsiScopeProcessor processor, @Nonnull ResolveState state, PsiElement lastParent, @Nonnull PsiElement place) {
    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, this);
    if (lastParent == null) {
      // Parent element should not see our vars
      return true;
    }
    Pair<Set<String>, Set<String>> pair = buildMaps();
    boolean conflict = pair == null;
    final Set<String> classesSet = conflict ? null : pair.getFirst();
    final Set<String> variablesSet = conflict ? null : pair.getSecond();
    final NameHint hint = processor.getHint(NameHint.KEY);
    if (hint != null && !conflict) {
      final ElementClassHint elementClassHint = processor.getHint(ElementClassHint.KEY);
      final String name = hint.getName(state);
      if ((elementClassHint == null || elementClassHint.shouldProcess(ElementClassHint.DeclarationKind.CLASS)) && classesSet.contains(name)) {
        return PsiScopesUtil.walkChildrenScopes(this, processor, state, lastParent, place);
      }
      if ((elementClassHint == null || elementClassHint.shouldProcess(ElementClassHint.DeclarationKind.VARIABLE)) && variablesSet.contains(name)) {
        return PsiScopesUtil.walkChildrenScopes(this, processor, state, lastParent, place);
      }
    } else {
      return PsiScopesUtil.walkChildrenScopes(this, processor, state, lastParent, place);
    }
    return true;
  }

  @Override
  public boolean shouldChangeModificationCount(PsiElement place) {
    PsiElement parent = getParent();
    return !(parent instanceof PsiMethod || parent instanceof PsiClassInitializer);
  }
}
