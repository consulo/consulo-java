/*
 * Copyright 2009-2011 Bas Leijdekkers
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
package com.intellij.java.impl.ig.javadoc;

import com.intellij.java.language.psi.JavaDocTokenType;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiSubstitutor;
import com.intellij.java.language.psi.javadoc.*;
import com.intellij.java.language.psi.util.MethodSignature;
import com.intellij.java.language.psi.util.MethodSignatureUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.language.ast.IElementType;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

@ExtensionImpl
public class UnnecessaryJavaDocLinkInspection extends BaseInspection {

  private static final int THIS_METHOD = 1;
  private static final int THIS_CLASS = 2;
  private static final int SUPER_METHOD = 3;

  @SuppressWarnings({"PublicField"})
  public boolean ignoreInlineLinkToSuper = false;

  @Nls
  @Nonnull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsLocalize.unnecessaryJavadocLinkDisplayName().get();
  }

  @Nonnull
  @Override
  protected String buildErrorString(Object... infos) {
    final int n = (Integer)infos[1];
    if (n == THIS_METHOD) {
      return InspectionGadgetsLocalize.unnecessaryJavadocLinkThisMethodProblemDescriptor().get();
    }
    else if (n == THIS_CLASS) {
      return InspectionGadgetsLocalize.unnecessaryJavadocLinkThisClassProblemDescriptor().get();
    }
    else {
      return InspectionGadgetsLocalize.unnecessaryJavadocLinkSuperMethodProblemDescriptor().get();
    }
  }

  @Override
  public JComponent createOptionsPanel() {
    LocalizeValue message = InspectionGadgetsLocalize.unnecessaryJavadocLinkOption();
    return new SingleCheckboxOptionsPanel(message.get(), this, "ignoreInlineLinkToSuper");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new UnnecessaryJavaDocLinkFix((String)infos[0]);
  }

  private static class UnnecessaryJavaDocLinkFix
    extends InspectionGadgetsFix {

    private final String tagName;

    public UnnecessaryJavaDocLinkFix(String tagName) {
      this.tagName = tagName;
    }

    @Nonnull
    public String getName() {
      return InspectionGadgetsLocalize.unnecessaryJavadocLinkQuickfix(tagName).get();
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiDocTag)) {
        return;
      }
      final PsiDocTag docTag = (PsiDocTag)parent;
      final PsiDocComment docComment = docTag.getContainingComment();
      if (docComment != null) {
        if (shouldDeleteEntireComment(docComment)) {
          docComment.delete();
          return;
        }
      }
      docTag.delete();
    }

    private static boolean shouldDeleteEntireComment(
      PsiDocComment docComment) {
      final PsiDocToken[] docTokens = PsiTreeUtil.getChildrenOfType(
        docComment, PsiDocToken.class);
      if (docTokens == null) {
        return false;
      }
      for (PsiDocToken docToken : docTokens) {
        final IElementType tokenType = docToken.getTokenType();
        if (!JavaDocTokenType.DOC_COMMENT_DATA.equals(tokenType)) {
          continue;
        }
        if (!StringUtil.isEmptyOrSpaces(docToken.getText())) {
          return false;
        }
      }
      return true;
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryJavaDocLinkVisitor();
  }

  private class UnnecessaryJavaDocLinkVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitDocTag(PsiDocTag tag) {
      super.visitDocTag(tag);
      @NonNls final String name = tag.getName();
      if ("link".equals(name) || "linkplain".equals(name)) {
        if (!(tag instanceof PsiInlineDocTag)) {
          return;
        }
      }
      else if ("see".equals(name)) {
        if (tag instanceof PsiInlineDocTag) {
          return;
        }
      }
      final PsiReference reference = extractReference(tag);
      if (reference == null) {
        return;
      }
      final PsiElement target = reference.resolve();
      if (target == null) {
        return;
      }
      final PsiMethod containingMethod =
        PsiTreeUtil.getParentOfType(tag, PsiMethod.class);
      if (containingMethod == null) {
        return;
      }
      if (target.equals(containingMethod)) {
        registerError(tag.getNameElement(), '@' + name,
                      Integer.valueOf(THIS_METHOD));
        return;
      }
      final PsiClass containingClass =
        PsiTreeUtil.getParentOfType(tag, PsiClass.class);
      if (target.equals(containingClass)) {
        registerError(tag.getNameElement(), '@' + name,
                      Integer.valueOf(THIS_CLASS));
        return;
      }
      if (!(target instanceof PsiMethod)) {
        return;
      }
      final PsiMethod method = (PsiMethod)target;
      if (!isSuperMethod(method, containingMethod)) {
        return;
      }
      if (ignoreInlineLinkToSuper && tag instanceof PsiInlineDocTag) {
        return;
      }
      registerError(tag.getNameElement(), '@' + name,
                    Integer.valueOf(SUPER_METHOD));
    }

    private PsiReference extractReference(PsiDocTag tag) {
      final PsiDocTagValue valueElement = tag.getValueElement();
      if (valueElement != null) {
        return valueElement.getReference();
      }
      // hack around the fact that a reference to a class is apparently
      // not a PsiDocTagValue
      final PsiElement[] dataElements = tag.getDataElements();
      if (dataElements.length == 0) {
        return null;
      }
      PsiElement salientElement = null;
      for (PsiElement dataElement : dataElements) {
        if (!(dataElement instanceof PsiWhiteSpace)) {
          salientElement = dataElement;
          break;
        }
      }
      if (salientElement == null) {
        return null;
      }
      final PsiElement child = salientElement.getFirstChild();
      if (!(child instanceof PsiReference)) {
        return null;
      }
      return (PsiReference)child;
    }

    public boolean isSuperMethod(PsiMethod superMethodCandidate,
                                 PsiMethod derivedMethod) {
      final PsiClass superClassCandidate =
        superMethodCandidate.getContainingClass();
      final PsiClass derivedClass = derivedMethod.getContainingClass();
      if (derivedClass == null || superClassCandidate == null) {
        return false;
      }
      if (!derivedClass.isInheritor(superClassCandidate, false)) {
        return false;
      }
      final PsiSubstitutor superSubstitutor =
        TypeConversionUtil.getSuperClassSubstitutor(
          superClassCandidate, derivedClass,
          PsiSubstitutor.EMPTY);
      final MethodSignature superSignature =
        superMethodCandidate.getSignature(superSubstitutor);
      final MethodSignature derivedSignature =
        derivedMethod.getSignature(PsiSubstitutor.EMPTY);
      return MethodSignatureUtil.isSubsignature(superSignature,
                                                derivedSignature);
    }
  }
}