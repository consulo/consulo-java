/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.hint.api.impls;

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.language.Language;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.parameterInfo.*;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.util.lang.CharArrayUtil;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;

/**
 * @author Maxim.Mossienko
 */
@ExtensionImpl
public class AnnotationParameterInfoHandler implements ParameterInfoHandler<PsiAnnotationParameterList, PsiAnnotationMethod>, DumbAware {
  @Override
  public
  @Nullable
  Object[] getParametersForLookup(LookupElement item, ParameterInfoContext context) {
    return null;
  }

  @Override
  public Object[] getParametersForDocumentation(final PsiAnnotationMethod p, final ParameterInfoContext context) {
    return new Object[]{p};
  }

  @Override
  public boolean couldShowInLookup() {
    return false;
  }

  @Override
  public PsiAnnotationParameterList findElementForParameterInfo(final CreateParameterInfoContext context) {
    final PsiAnnotation annotation = ParameterInfoUtils.findParentOfType(context.getFile(), context.getOffset(), PsiAnnotation.class);

    if (annotation != null) {
      final PsiJavaCodeReferenceElement nameReference = annotation.getNameReferenceElement();

      if (nameReference != null) {
        final PsiElement resolved = nameReference.resolve();

        if (resolved instanceof PsiClass) {
          final PsiClass aClass = (PsiClass) resolved;

          if (aClass.isAnnotationType()) {
            final PsiMethod[] methods = aClass.getMethods();

            if (methods.length != 0) {
              context.setItemsToShow(methods);

              final PsiAnnotationMethod annotationMethod = findAnnotationMethod(context.getFile(), context.getOffset());
              if (annotationMethod != null) context.setHighlightedElement(annotationMethod);

              return annotation.getParameterList();
            }
          }
        }
      }
    }

    return null;
  }

  @Override
  public void showParameterInfo(@Nonnull final PsiAnnotationParameterList element, final CreateParameterInfoContext context) {
    context.showHint(element, element.getTextRange().getStartOffset() + 1, this);
  }

  @Override
  public PsiAnnotationParameterList findElementForUpdatingParameterInfo(final UpdateParameterInfoContext context) {
    final PsiAnnotation annotation = ParameterInfoUtils.findParentOfType(context.getFile(), context.getOffset(), PsiAnnotation.class);
    return annotation != null ? annotation.getParameterList() : null;
  }

  @Override
  public void updateParameterInfo(@Nonnull final PsiAnnotationParameterList o, final UpdateParameterInfoContext context) {
    CharSequence chars = context.getEditor().getDocument().getCharsSequence();
    int offset1 = CharArrayUtil.shiftForward(chars, context.getEditor().getCaretModel().getOffset(), " \t");
    final char c = chars.charAt(offset1);
    if (c == ',' || c == ')') {
      offset1 = CharArrayUtil.shiftBackward(chars, offset1 - 1, " \t");
    }
    context.setHighlightedParameter(findAnnotationMethod(context.getFile(), offset1));
  }

  @Override
  public String getParameterCloseChars() {
    return ParameterInfoUtils.DEFAULT_PARAMETER_CLOSE_CHARS;
  }

  @Override
  public boolean tracksParameterIndex() {
    return true;
  }

  @Override
  public void updateUI(final PsiAnnotationMethod p, final ParameterInfoUIContext context) {
    @NonNls StringBuffer buffer = new StringBuffer();
    int highlightStartOffset;
    int highlightEndOffset;
    buffer.append(p.getReturnType().getPresentableText());
    buffer.append(" ");
    highlightStartOffset = buffer.length();
    buffer.append(p.getName());
    highlightEndOffset = buffer.length();
    buffer.append("()");

    if (p.getDefaultValue() != null) {
      buffer.append(" default ");
      buffer.append(p.getDefaultValue().getText());
    }

    context.setupUIComponentPresentation(buffer.toString(), highlightStartOffset, highlightEndOffset, false, p.isDeprecated(),
        false, context.getDefaultParameterColor());
  }

  private static PsiAnnotationMethod findAnnotationMethod(PsiFile file, int offset) {
    PsiNameValuePair pair = ParameterInfoUtils.findParentOfType(file, offset, PsiNameValuePair.class);
    if (pair == null) return null;
    final PsiReference reference = pair.getReference();
    final PsiElement resolved = reference != null ? reference.resolve() : null;
    return PsiUtil.isAnnotationMethod(resolved) ? (PsiAnnotationMethod) resolved : null;
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }
}
