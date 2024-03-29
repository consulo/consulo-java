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

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.impl.codeInsight.ExceptionUtil;
import com.intellij.java.language.impl.psi.impl.source.Constants;
import com.intellij.java.language.impl.psi.impl.source.tree.ChildRole;
import com.intellij.java.language.impl.psi.scope.util.PsiScopesUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.application.util.CachedValue;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.CachedValuesManager;
import consulo.language.ast.ASTNode;
import consulo.language.ast.ChildRoleBase;
import consulo.language.ast.IElementType;
import consulo.language.impl.psi.CompositePsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.logging.Logger;
import consulo.util.collection.ContainerUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * @author ven
 */
public class PsiCatchSectionImpl extends CompositePsiElement implements PsiCatchSection, Constants {
  private static final Logger LOG = Logger.getInstance(PsiCatchSectionImpl.class);

  private final Object myTypesCacheLock = new Object();
  private CachedValue<List<PsiType>> myTypesCache = null;

  public PsiCatchSectionImpl() {
    super(CATCH_SECTION);
  }

  @Override
  public PsiParameter getParameter() {
    return (PsiParameter) findChildByRoleAsPsiElement(ChildRole.PARAMETER);
  }

  @Override
  public PsiCodeBlock getCatchBlock() {
    return (PsiCodeBlock) findChildByRoleAsPsiElement(ChildRole.CATCH_BLOCK);
  }

  @Override
  public PsiType getCatchType() {
    PsiParameter parameter = getParameter();
    if (parameter == null) return null;
    return parameter.getType();
  }

  @Override
  @Nonnull
  public List<PsiType> getPreciseCatchTypes() {
    final PsiParameter parameter = getParameter();
    if (parameter == null) return Collections.emptyList();

    return getTypesCache().getValue();
  }

  @Override
  public void clearCaches() {
    super.clearCaches();
    synchronized (myTypesCacheLock) {
      myTypesCache = null;
    }
  }

  private CachedValue<List<PsiType>> getTypesCache() {
    synchronized (myTypesCacheLock) {
      if (myTypesCache == null) {
        final CachedValuesManager cacheManager = CachedValuesManager.getManager(getProject());
        myTypesCache = cacheManager.createCachedValue(new CachedValueProvider<List<PsiType>>() {
          @Override
          public Result<List<PsiType>> compute() {
            final List<PsiType> types = computePreciseCatchTypes(getParameter());
            return Result.create(types, PsiModificationTracker.MODIFICATION_COUNT);
          }
        }, false);
      }
      return myTypesCache;
    }
  }

  private List<PsiType> computePreciseCatchTypes(@Nullable final PsiParameter parameter) {
    if (parameter == null) {
      return List.of();
    }

    PsiType declaredType = parameter.getType();

    // When the thrown expression is an ... exception parameter Ej (parameter) of a catch clause Cj (this) ...
    if (PsiUtil.getLanguageLevel(parameter).isAtLeast(LanguageLevel.JDK_1_7) &&
        isCatchParameterEffectivelyFinal(parameter, getCatchBlock())) {
      PsiTryStatement statement = getTryStatement();
      // ... and the try block of the try statement which declares Cj (tryBlock) can throw T ...
      Collection<PsiClassType> thrownTypes = getThrownTypes(statement);
      if (thrownTypes.isEmpty()) return Collections.emptyList();
      // ... and for all exception parameters Ei declared by any catch clauses Ci, 1 <= i < j,
      //     declared to the left of Cj for the same try statement, T is not assignable to Ei ...
      final PsiParameter[] parameters = statement.getCatchBlockParameters();
      List<PsiType> uncaughtTypes = ContainerUtil.mapNotNull(thrownTypes, new Function<PsiClassType, PsiType>() {
        @Override
        public PsiType apply(final PsiClassType thrownType) {
          for (int i = 0; i < parameters.length && parameters[i] != parameter; i++) {
            final PsiType catchType = parameters[i].getType();
            if (catchType.isAssignableFrom(thrownType)) return null;
          }
          return thrownType;
        }
      });
      // ... and T is assignable to Ej ...
      boolean passed = true;
      for (PsiType type : uncaughtTypes) {
        if (!declaredType.isAssignableFrom(type)) {
          passed = false;
          break;
        }
      }
      // ... the throw statement throws precisely the set of exception types T.
      if (passed) return uncaughtTypes;
    }

    return Collections.singletonList(declaredType);
  }

  private static Collection<PsiClassType> getThrownTypes(@Nonnull PsiTryStatement statement) {
    Collection<PsiClassType> types = ContainerUtil.newArrayList();
    PsiCodeBlock tryBlock = statement.getTryBlock();
    if (tryBlock != null) {
      types.addAll(ExceptionUtil.getThrownExceptions(tryBlock));
    }
    PsiResourceList resourceList = statement.getResourceList();
    if (resourceList != null) {
      types.addAll(ExceptionUtil.getThrownExceptions(resourceList));
    }
    return types;
  }

  // do not use control flow here to avoid dead loop
  private static boolean isCatchParameterEffectivelyFinal(final PsiParameter parameter, @Nullable final PsiCodeBlock catchBlock) {
    final boolean[] result = {true};
    if (catchBlock != null) {
      catchBlock.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitReferenceExpression(PsiReferenceExpression expression) {
          super.visitReferenceExpression(expression);
          if (expression.resolve() == parameter && PsiUtil.isAccessedForWriting(expression)) {
            result[0] = false;
            stopWalking();
          }
        }
      });
    }
    return result[0];
  }

  @Override
  @Nonnull
  public PsiTryStatement getTryStatement() {
    return (PsiTryStatement) getParent();
  }

  @Override
  @Nullable
  public PsiJavaToken getLParenth() {
    return (PsiJavaToken) findChildByRole(ChildRole.CATCH_BLOCK_PARAMETER_LPARENTH);
  }

  @Override
  @Nullable
  public PsiJavaToken getRParenth() {
    return (PsiJavaToken) findChildByRole(ChildRole.CATCH_BLOCK_PARAMETER_RPARENTH);
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor) visitor).visitCatchSection(this);
    } else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiCatchSection";
  }

  @Override
  public ASTNode findChildByRole(int role) {
    switch (role) {
      default:
        return null;

      case ChildRole.PARAMETER:
        return findChildByType(PARAMETER);

      case ChildRole.CATCH_KEYWORD:
        return findChildByType(CATCH_KEYWORD);

      case ChildRole.CATCH_BLOCK_PARAMETER_LPARENTH:
        return findChildByType(LPARENTH);

      case ChildRole.CATCH_BLOCK_PARAMETER_RPARENTH:
        return findChildByType(RPARENTH);

      case ChildRole.CATCH_BLOCK:
        return findChildByType(CODE_BLOCK);
    }
  }

  @Override
  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == PARAMETER) {
      return ChildRole.PARAMETER;
    } else if (i == CODE_BLOCK) {
      return ChildRole.CATCH_BLOCK;
    } else if (i == CATCH_KEYWORD) {
      return ChildRole.CATCH_KEYWORD;
    } else if (i == LPARENTH) {
      return ChildRole.CATCH_BLOCK_PARAMETER_LPARENTH;
    } else if (i == RPARENTH) {
      return ChildRole.CATCH_BLOCK_PARAMETER_RPARENTH;
    }

    return ChildRoleBase.NONE;
  }

  @Override
  public boolean processDeclarations(@Nonnull PsiScopeProcessor processor,
                                     @Nonnull ResolveState state,
                                     PsiElement lastParent,
                                     @Nonnull PsiElement place) {
    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, this);
    if (lastParent == null || lastParent.getParent() != this)
      // Parent element should not see our vars
      return true;

    final PsiParameter catchParameter = getParameter();
    if (catchParameter != null) {
      return processor.execute(catchParameter, state);
    }

    return PsiScopesUtil.walkChildrenScopes(this, processor, state, lastParent, place);
  }
}
