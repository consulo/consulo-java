/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.completion;

import com.intellij.java.impl.codeInsight.ExpectedTypesProvider;
import com.intellij.java.impl.codeInsight.lookup.VariableLookupItem;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiSuperMethodUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import consulo.application.AllIcons;
import consulo.language.editor.completion.CompletionParameters;
import consulo.language.editor.completion.CompletionProvider;
import consulo.language.editor.completion.CompletionResultSet;
import consulo.language.editor.completion.CompletionUtilCore;
import consulo.language.editor.completion.lookup.*;
import consulo.language.pattern.PsiElementPattern;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ProcessingContext;
import consulo.ui.image.Image;
import consulo.ui.image.ImageEffects;
import consulo.util.lang.Comparing;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static consulo.language.pattern.PlatformPatterns.psiElement;

/**
 * @author peter
 */
class SameSignatureCallParametersProvider implements CompletionProvider {
  static final PsiElementPattern.Capture<PsiElement> IN_CALL_ARGUMENT = psiElement().beforeLeaf(psiElement(JavaTokenType.RPARENTH)).afterLeaf("(").withParent(psiElement(PsiReferenceExpression
      .class).withParent(psiElement(PsiExpressionList.class).withParent(PsiCall.class)));

  @Override
  public void addCompletions(@Nonnull CompletionParameters parameters, ProcessingContext context, @Nonnull CompletionResultSet result) {
    addSignatureItems(parameters, result);
  }

  void addSignatureItems(@Nonnull CompletionParameters parameters, @Nonnull Consumer<LookupElement> result) {
    final PsiCall methodCall = PsiTreeUtil.getParentOfType(parameters.getPosition(), PsiCall.class);
    assert methodCall != null;
    Set<Pair<PsiMethod, PsiSubstitutor>> candidates = getCallCandidates(methodCall);

    PsiMethod container = PsiTreeUtil.getParentOfType(methodCall, PsiMethod.class);
    while (container != null) {
      for (final Pair<PsiMethod, PsiSubstitutor> candidate : candidates) {
        if (container.getParameterList().getParametersCount() > 1 && candidate.first.getParameterList().getParametersCount() > 1) {
          PsiMethod from = getMethodToTakeParametersFrom(container, candidate.first, candidate.second);
          if (from != null) {
            result.accept(createParametersLookupElement(from, methodCall, candidate.first));
          }
        }
      }

      container = PsiTreeUtil.getParentOfType(container, PsiMethod.class);

    }
  }

  private static LookupElement createParametersLookupElement(final PsiMethod takeParametersFrom, PsiElement call, PsiMethod invoked) {
    final PsiParameter[] parameters = takeParametersFrom.getParameterList().getParameters();
    final String lookupString = StringUtil.join(parameters, PsiNamedElement::getName, ", ");

    // TODO [VISTALL] new icon
    Image ppIcon = ImageEffects.transparent(AllIcons.Nodes.Parameter);
    LookupElementBuilder element = LookupElementBuilder.create(lookupString).withIcon(ppIcon);
    if (PsiTreeUtil.isAncestor(takeParametersFrom, call, true)) {
      element = element.withInsertHandler(new InsertHandler<LookupElement>() {
        @Override
        public void handleInsert(InsertionContext context, LookupElement item) {
          context.commitDocument();
          for (PsiParameter parameter : CompletionUtilCore.getOriginalOrSelf(takeParametersFrom).getParameterList().getParameters()) {
            VariableLookupItem.makeFinalIfNeeded(context, parameter);
          }
        }
      });
    }
    element.putUserData(JavaCompletionUtil.SUPER_METHOD_PARAMETERS, Boolean.TRUE);

    return TailTypeDecorator.withTail(element, ExpectedTypesProvider.getFinalCallParameterTailType(call, invoked.getReturnType(), invoked));
  }

  private static Set<Pair<PsiMethod, PsiSubstitutor>> getCallCandidates(PsiCall expression) {
    Set<Pair<PsiMethod, PsiSubstitutor>> candidates = new LinkedHashSet<>();
    JavaResolveResult[] results;
    if (expression instanceof PsiMethodCallExpression) {
      results = ((PsiMethodCallExpression) expression).getMethodExpression().multiResolve(false);
    } else {
      results = new JavaResolveResult[]{expression.resolveMethodGenerics()};
    }

    PsiMethod toExclude = ExpressionUtils.isConstructorInvocation(expression) ? PsiTreeUtil.getParentOfType(expression, PsiMethod.class) : null;

    for (final JavaResolveResult candidate : results) {
      final PsiElement element = candidate.getElement();
      if (element instanceof PsiMethod) {
        final PsiClass psiClass = ((PsiMethod) element).getContainingClass();
        if (psiClass != null) {
          for (Pair<PsiMethod, PsiSubstitutor> overload : psiClass.findMethodsAndTheirSubstitutorsByName(((PsiMethod) element).getName(), true)) {
            if (overload.first != toExclude) {
              candidates.add(Pair.create(overload.first, candidate.getSubstitutor().putAll(overload.second)));
            }
          }
          break;
        }
      }
    }
    return candidates;
  }


  @Nullable
  private static PsiMethod getMethodToTakeParametersFrom(PsiMethod place, PsiMethod invoked, PsiSubstitutor substitutor) {
    if (PsiSuperMethodUtil.isSuperMethod(place, invoked)) {
      return place;
    }

    Map<String, PsiType> requiredNames = new HashMap<>();
    final PsiParameter[] parameters = place.getParameterList().getParameters();
    final PsiParameter[] callParams = invoked.getParameterList().getParameters();
    if (callParams.length > parameters.length) {
      return null;
    }

    final boolean checkNames = invoked.isConstructor();
    boolean sameTypes = true;
    for (int i = 0; i < callParams.length; i++) {
      PsiParameter callParam = callParams[i];
      PsiParameter parameter = parameters[i];
      requiredNames.put(callParam.getName(), substitutor.substitute(callParam.getType()));
      if (checkNames && !Comparing.equal(parameter.getName(), callParam.getName()) || !Comparing.equal(parameter.getType(), substitutor.substitute(callParam.getType()))) {
        sameTypes = false;
      }
    }

    if (sameTypes && callParams.length == parameters.length) {
      return place;
    }

    for (PsiParameter parameter : parameters) {
      PsiType type = requiredNames.remove(parameter.getName());
      if (type != null && !parameter.getType().equals(type)) {
        return null;
      }
    }

    return requiredNames.isEmpty() ? invoked : null;
  }
}
