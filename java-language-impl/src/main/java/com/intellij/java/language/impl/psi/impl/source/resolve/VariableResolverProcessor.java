// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.impl.source.resolve;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.impl.psi.scope.ElementClassFilter;
import com.intellij.java.language.impl.psi.scope.ElementClassHint;
import com.intellij.java.language.impl.psi.scope.PsiConflictResolver;
import com.intellij.java.language.impl.psi.scope.conflictResolvers.JavaVariableConflictResolver;
import com.intellij.java.language.impl.psi.scope.processor.ConflictFilterProcessor;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.infos.CandidateInfo;
import com.intellij.java.language.psi.scope.JavaScopeProcessorEvent;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.filter.ElementFilter;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.util.collection.SmartList;
import consulo.util.dataholder.Key;

import jakarta.annotation.Nonnull;

/**
 * @author ik, dsl
 */
public class VariableResolverProcessor extends ConflictFilterProcessor implements ElementClassHint {
  private static final ElementFilter ourFilter = ElementClassFilter.VARIABLE;

  private boolean myStaticScopeFlag;
  private final PsiClass myAccessClass;
  private PsiElement myCurrentFileContext;

  public VariableResolverProcessor(@Nonnull PsiJavaCodeReferenceElement place, @Nonnull PsiFile placeFile) {
    super(place.getReferenceName(), ourFilter, new PsiConflictResolver[]{new JavaVariableConflictResolver()}, new SmartList<>(), place, placeFile);

    PsiClass access = null;
    PsiElement qualifier = place.getQualifier();
    if (qualifier instanceof PsiExpression) {
      final JavaResolveResult accessClass = PsiUtil.getAccessObjectClass((PsiExpression) qualifier);
      final PsiElement element = accessClass.getElement();
      if (element instanceof PsiTypeParameter) {
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(placeFile.getProject());
        final PsiClassType type = factory.createType((PsiTypeParameter) element);
        final PsiType accessType = accessClass.getSubstitutor().substitute(type);
        if (accessType instanceof PsiArrayType) {
          LanguageLevel languageLevel = PsiUtil.getLanguageLevel(placeFile);
          access = factory.getArrayClass(languageLevel);
        } else if (accessType instanceof PsiClassType) {
          access = ((PsiClassType) accessType).resolve();
        }
      } else if (element instanceof PsiClass) {
        access = (PsiClass) element;
      }
    }
    myAccessClass = access;
  }

  @Override
  protected boolean stopAtFoundResult(JavaResolveResult cachedResult) {
    if (super.stopAtFoundResult(cachedResult)) {
      if (myPlaceFile instanceof JavaCodeFragment) {
        JavaCodeFragment.VisibilityChecker visibilityChecker = ((JavaCodeFragment) myPlaceFile).getVisibilityChecker();
        if (visibilityChecker != null) {
          PsiElement element = cachedResult.getElement();
          if (element instanceof PsiMember) {
            PsiMember member = (PsiMember) element;
            return JavaResolveUtil.isAccessible(member, member.getContainingClass(), member.getModifierList(), myPlace, myAccessClass,
                cachedResult.getCurrentFileResolveScope(), null);
          }
        }
      }
      return true;
    }
    return false;
  }

  @Override
  public final void handleEvent(@Nonnull PsiScopeProcessor.Event event, Object associated) {
    super.handleEvent(event, associated);
    if (event == JavaScopeProcessorEvent.START_STATIC) {
      myStaticScopeFlag = true;
    } else if (JavaScopeProcessorEvent.SET_CURRENT_FILE_CONTEXT.equals(event)) {
      myCurrentFileContext = (PsiElement) associated;
    }
  }

  @Override
  public void add(@Nonnull PsiElement element, @Nonnull PsiSubstitutor substitutor) {
    final boolean staticProblem = myStaticScopeFlag &&
        !((PsiModifierListOwner) element).hasModifierProperty(PsiModifier.STATIC) &&
        (element instanceof PsiField || !(element instanceof PsiVariable && PsiUtil.isCompileTimeConstant((PsiVariable) element)));
    add(new CandidateInfo(element, substitutor, myPlace, myAccessClass, staticProblem, myCurrentFileContext));
  }


  @Override
  public boolean shouldProcess(@Nonnull DeclarationKind kind) {
    return kind == DeclarationKind.VARIABLE || kind == DeclarationKind.FIELD || kind == DeclarationKind.ENUM_CONST;
  }

  @Override
  public boolean execute(@Nonnull PsiElement element, @Nonnull ResolveState state) {
    if (!(element instanceof PsiField) && (myName == null || PsiUtil.checkName(element, myName, myPlace))) {
      super.execute(element, state);
      return myResults.isEmpty();
    }

    return super.execute(element, state);
  }

  @Override
  public <T> T getHint(@Nonnull Key<T> hintKey) {
    if (hintKey == ElementClassHint.KEY) {
      //noinspection unchecked
      return (T) this;
    }

    return super.getHint(hintKey);
  }
}
