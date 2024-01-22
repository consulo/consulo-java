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
package com.intellij.java.analysis.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.infos.CandidateInfo;
import com.intellij.java.language.psi.infos.MethodCandidateInfo;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.intention.QuickFixAction;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.editor.rawHighlight.HighlightInfo;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;

import jakarta.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * @author cdr
 */
public class PermuteArgumentsFix implements SyntheticIntentionAction {
  private static final Logger LOG = Logger.getInstance(PermuteArgumentsFix.class);
  private final PsiCall myCall;
  private final PsiCall myPermutation;

  private PermuteArgumentsFix(@Nonnull PsiCall call, @Nonnull PsiCall permutation) {
    myCall = call;
    myPermutation = permutation;
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }


  @Override
  @Nonnull
  public String getText() {
    return JavaQuickFixBundle.message("permute.arguments");
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    return !project.isDisposed() && myCall.isValid() && myCall.getManager().isInProject(myCall);
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;
    myCall.getArgumentList().replace(myPermutation.getArgumentList());
  }

  public static void registerFix(HighlightInfo info, PsiCall callExpression, final CandidateInfo[] candidates, final TextRange fixRange) {
    PsiExpression[] expressions = callExpression.getArgumentList().getExpressions();
    if (expressions.length < 2) return;
    List<PsiCall> permutations = new ArrayList<PsiCall>();

    for (CandidateInfo candidate : candidates) {
      if (candidate instanceof MethodCandidateInfo) {
        MethodCandidateInfo methodCandidate = (MethodCandidateInfo) candidate;
        PsiMethod method = methodCandidate.getElement();
        PsiSubstitutor substitutor = methodCandidate.getSubstitutor();

        PsiParameter[] parameters = method.getParameterList().getParameters();
        if (expressions.length != parameters.length || parameters.length == 0) continue;
        int minIncompatibleIndex = parameters.length;
        int maxIncompatibleIndex = 0;
        int incompatibilitiesCount = 0;
        for (int i = 0; i < parameters.length; i++) {
          PsiParameter parameter = parameters[i];
          PsiType type = substitutor.substitute(parameter.getType());
          if (TypeConversionUtil.areTypesAssignmentCompatible(type, expressions[i])) continue;
          if (minIncompatibleIndex == parameters.length) minIncompatibleIndex = i;
          maxIncompatibleIndex = i;
          incompatibilitiesCount++;
        }

        try {
          registerSwapFixes(expressions, callExpression, permutations, methodCandidate, incompatibilitiesCount, minIncompatibleIndex, maxIncompatibleIndex);
          registerShiftFixes(expressions, callExpression, permutations, methodCandidate, minIncompatibleIndex, maxIncompatibleIndex);
        } catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    }
    if (permutations.size() == 1) {
      PermuteArgumentsFix fix = new PermuteArgumentsFix(callExpression, permutations.get(0));
      QuickFixAction.registerQuickFixAction(info, fixRange, fix);
    }
  }

  private static void registerShiftFixes(final PsiExpression[] expressions, final PsiCall callExpression, final List<PsiCall> permutations,
                                         final MethodCandidateInfo methodCandidate, final int minIncompatibleIndex, final int maxIncompatibleIndex)
      throws IncorrectOperationException {
    PsiMethod method = methodCandidate.getElement();
    PsiSubstitutor substitutor = methodCandidate.getSubstitutor();
    // shift range should include both incompatible indexes
    for (int i = 0; i <= minIncompatibleIndex; i++) {
      for (int j = Math.max(i + 2, maxIncompatibleIndex); j < expressions.length; j++) { // if j=i+1 the shift is equal to swap
        {
          ArrayUtil.rotateLeft(expressions, i, j);
          if (PsiUtil.isApplicable(method, substitutor, expressions)) {
            PsiCall copy = (PsiCall) callExpression.copy();
            PsiExpression[] copyExpressions = copy.getArgumentList().getExpressions();
            for (int k = i; k < copyExpressions.length; k++) {
              copyExpressions[k].replace(expressions[k]);
            }

            JavaResolveResult result = copy.resolveMethodGenerics();
            if (result.getElement() != null && result.isValidResult()) {
              permutations.add(copy);
              if (permutations.size() > 1) return;
            }
          }
          ArrayUtil.rotateRight(expressions, i, j);
        }

        {
          ArrayUtil.rotateRight(expressions, i, j);
          if (PsiUtil.isApplicable(method, substitutor, expressions)) {
            PsiCall copy = (PsiCall) callExpression.copy();
            PsiExpression[] copyExpressions = copy.getArgumentList().getExpressions();
            for (int k = i; k < copyExpressions.length; k++) {
              copyExpressions[k].replace(expressions[k]);
            }

            JavaResolveResult result = copy.resolveMethodGenerics();
            if (result.getElement() != null && result.isValidResult()) {
              permutations.add(copy);
              if (permutations.size() > 1) return;
            }
          }
          ArrayUtil.rotateLeft(expressions, i, j);
        }
      }
    }
  }

  private static void registerSwapFixes(final PsiExpression[] expressions, final PsiCall callExpression, final List<PsiCall> permutations,
                                        MethodCandidateInfo candidate, final int incompatibilitiesCount, final int minIncompatibleIndex,
                                        final int maxIncompatibleIndex) throws IncorrectOperationException {
    PsiMethod method = candidate.getElement();
    PsiSubstitutor substitutor = candidate.getSubstitutor();
    if (incompatibilitiesCount >= 3) return; // no way we can fix it by swapping

    for (int i = minIncompatibleIndex; i < maxIncompatibleIndex; i++) {
      for (int j = i + 1; j <= maxIncompatibleIndex; j++) {
        ArrayUtil.swap(expressions, i, j);
        if (PsiUtil.isApplicable(method, substitutor, expressions)) {
          PsiCall copy = (PsiCall) callExpression.copy();
          PsiExpression[] copyExpressions = copy.getArgumentList().getExpressions();
          copyExpressions[i].replace(expressions[i]);
          copyExpressions[j].replace(expressions[j]);
          JavaResolveResult result = copy.resolveMethodGenerics();
          if (result.getElement() != null && result.isValidResult()) {
            permutations.add(copy);
            if (permutations.size() > 1) return;
          }
        }
        ArrayUtil.swap(expressions, i, j);
      }
    }
  }
}
