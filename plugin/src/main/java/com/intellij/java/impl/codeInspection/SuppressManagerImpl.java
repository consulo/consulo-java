/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 24-Dec-2007
 */
package com.intellij.java.impl.codeInspection;

import com.intellij.java.analysis.codeInspection.SuppressManager;
import com.intellij.java.analysis.impl.codeInspection.JavaSuppressionUtil;
import consulo.annotation.component.ServiceImpl;
import consulo.language.editor.inspection.SuppressIntentionActionFromFix;
import consulo.language.editor.inspection.SuppressQuickFix;
import consulo.language.editor.intention.SuppressIntentionAction;
import consulo.language.editor.rawHighlight.HighlightDisplayKey;
import consulo.language.psi.PsiElement;
import jakarta.inject.Singleton;

import jakarta.annotation.Nonnull;

@Singleton
@ServiceImpl
public class SuppressManagerImpl extends SuppressManager {
  @Override
  @Nonnull
  public SuppressIntentionAction[] createSuppressActions(@Nonnull final HighlightDisplayKey displayKey) {
    SuppressQuickFix[] batchSuppressActions = createBatchSuppressActions(displayKey);
    return SuppressIntentionActionFromFix.convertBatchToSuppressIntentionActions(batchSuppressActions);
  }

//  @Override
//  public SuppressQuickFix[] getSuppressActions(@Nonnull PsiElement element, String toolShortName) {
//    return createBatchSuppressActions(HighlightDisplayKey.find(toolShortName));
//  }
//
  @Override
  public boolean isSuppressedFor(@jakarta.annotation.Nonnull final PsiElement element, final String toolId) {
    return JavaSuppressionUtil.getElementToolSuppressedIn(element, toolId) != null;
  }
//
//  @Override
//  @Nullable
//  public PsiElement getElementMemberSuppressedIn(@Nonnull final PsiDocCommentOwner owner, final String inspectionToolID) {
//    return JavaSuppressionUtil.getElementMemberSuppressedIn(owner, inspectionToolID);
//  }
//
//  @Override
//  @Nullable
//  public PsiElement getAnnotationMemberSuppressedIn(@Nonnull final PsiModifierListOwner owner, final String inspectionToolID) {
//    return JavaSuppressionUtil.getAnnotationMemberSuppressedIn(owner, inspectionToolID);
//  }
//
//  @Override
//  @Nullable
//  public PsiElement getDocCommentToolSuppressedIn(@Nonnull final PsiDocCommentOwner owner, final String inspectionToolID) {
//    return JavaSuppressionUtil.getDocCommentToolSuppressedIn(owner, inspectionToolID);
//  }
//
//  @Override
//  @Nonnull
//  public Collection<String> getInspectionIdsSuppressedInAnnotation(@Nonnull final PsiModifierListOwner owner) {
//    return JavaSuppressionUtil.getInspectionIdsSuppressedInAnnotation(owner);
//  }
//
//  @Override
//  @Nullable
//  public String getSuppressedInspectionIdsIn(@Nonnull PsiElement element) {
//    return JavaSuppressionUtil.getSuppressedInspectionIdsIn(element);
//  }
//
//  @Override
//  @Nullable
//  public PsiElement getElementToolSuppressedIn(@Nonnull final PsiElement place, final String toolId) {
//    return JavaSuppressionUtil.getElementToolSuppressedIn(place, toolId);
//  }
//
//  @Override
//  public boolean canHave15Suppressions(@Nonnull final PsiElement file) {
//    return JavaSuppressionUtil.canHave15Suppressions(file);
//  }
//
//  @Override
//  public boolean alreadyHas14Suppressions(@Nonnull final PsiDocCommentOwner commentOwner) {
//    return JavaSuppressionUtil.alreadyHas14Suppressions(commentOwner);
//  }
}
