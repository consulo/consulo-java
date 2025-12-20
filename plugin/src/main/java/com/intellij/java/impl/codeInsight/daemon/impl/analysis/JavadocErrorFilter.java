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
package com.intellij.java.impl.codeInsight.daemon.impl.analysis;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.HighlightErrorFilter;
import consulo.language.psi.PsiErrorElement;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import consulo.language.psi.util.PsiTreeUtil;
import jakarta.annotation.Nonnull;

/**
 * @author yole
 */
@ExtensionImpl
public class JavadocErrorFilter extends HighlightErrorFilter {

  @Override
  public boolean shouldHighlightErrorElement(@Nonnull PsiErrorElement element) {
    return !value(element);
  }

  public static boolean value(PsiErrorElement element) {
    return PsiTreeUtil.getParentOfType(element, PsiDocComment.class) != null;
  }
}
