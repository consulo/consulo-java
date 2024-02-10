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
package com.intellij.java.analysis.impl.codeInspection.redundantCast;

import com.intellij.java.analysis.codeInspection.GroupNames;
import com.intellij.java.analysis.impl.codeInspection.miscGenerics.GenericsInspectionToolBase;
import com.intellij.java.analysis.impl.codeInspection.miscGenerics.SuspiciousMethodCallUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiExpressionTrimRenderer;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.RedundantCastUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.*;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElement;
import consulo.project.Project;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author max
 */
@ExtensionImpl
public class RedundantCastInspection extends GenericsInspectionToolBase<RedundantCastInspectionState> {
  private static final String SHORT_NAME = "RedundantCast";

  private final LocalQuickFix myQuickFixAction;

  public RedundantCastInspection() {
    myQuickFixAction = new AcceptSuggested();
  }

  @Nonnull
  @Override
  public InspectionToolState<? extends RedundantCastInspectionState> createStateProvider() {
    return new RedundantCastInspectionState();
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Nonnull
  @Override
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.WARNING;
  }

  @Override
  @Nullable
  public ProblemDescriptor[] getDescriptions(@Nonnull PsiElement where,
                                             @Nonnull InspectionManager manager,
                                             boolean isOnTheFly,
                                             RedundantCastInspectionState state) {
    List<PsiTypeCastExpression> redundantCasts = RedundantCastUtil.getRedundantCastsInside(where);
    if (redundantCasts.isEmpty()) {
      return null;
    }
    List<ProblemDescriptor> descriptions = new ArrayList<>(redundantCasts.size());
    for (PsiTypeCastExpression redundantCast : redundantCasts) {
      ProblemDescriptor descriptor = createDescription(redundantCast, manager, isOnTheFly, state);
      if (descriptor != null) {
        descriptions.add(descriptor);
      }
    }
    if (descriptions.isEmpty()) {
      return null;
    }
    return descriptions.toArray(ProblemDescriptor.EMPTY_ARRAY);
  }

  @Override
  public ProblemDescriptor[] checkField(@Nonnull PsiField field,
                                        @Nonnull InspectionManager manager,
                                        boolean isOnTheFly,
                                        RedundantCastInspectionState state) {
    return getDescriptions(field, manager, isOnTheFly, state);
  }

  @Nullable
  private ProblemDescriptor createDescription(@Nonnull PsiTypeCastExpression cast,
                                              @Nonnull InspectionManager manager,
                                              boolean onTheFly,
                                              RedundantCastInspectionState state) {
    PsiExpression operand = cast.getOperand();
    PsiTypeElement castType = cast.getCastType();
    if (operand == null || castType == null) {
      return null;
    }
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(cast.getParent());
    if (parent instanceof PsiExpressionList) {
      final PsiElement gParent = parent.getParent();
      if (gParent instanceof PsiMethodCallExpression && state.IGNORE_SUSPICIOUS_METHOD_CALLS) {
        final String message = SuspiciousMethodCallUtil
          .getSuspiciousMethodCallMessage((PsiMethodCallExpression)gParent, operand, operand.getType(), true, new ArrayList<>(), 0);
        if (message != null) {
          return null;
        }
      }
    }

    String message = InspectionsBundle.message("inspection.redundant.cast.problem.descriptor",
                                               "<code>" + PsiExpressionTrimRenderer.render(operand) + "</code>", "<code>#ref</code> #loc");
    return manager.createProblemDescriptor(castType, message, myQuickFixAction, ProblemHighlightType.LIKE_UNUSED_SYMBOL, onTheFly);
  }


  private static class AcceptSuggested implements LocalQuickFix {
    @Override
    @Nonnull
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.redundant.cast.remove.quickfix");
    }

    @Override
    public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
      PsiElement castTypeElement = descriptor.getPsiElement();
      PsiTypeCastExpression cast = castTypeElement == null ? null : (PsiTypeCastExpression)castTypeElement.getParent();
      if (cast != null) {
        RemoveRedundantCastUtil.removeCast(cast);
      }
    }
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.redundant.cast.display.name");
  }

  @Override
  @Nonnull
  public String getGroupDisplayName() {
    return GroupNames.VERBOSE_GROUP_NAME;
  }

  @Override
  @Nonnull
  public String getShortName() {
    return SHORT_NAME;
  }
}
