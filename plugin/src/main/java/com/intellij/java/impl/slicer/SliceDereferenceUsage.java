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
package com.intellij.java.impl.slicer;

import com.intellij.java.language.psi.PsiSubstitutor;
import consulo.application.util.function.Processor;
import consulo.language.psi.PsiElement;
import consulo.ui.image.Image;
import consulo.usage.TextChunk;
import consulo.usage.UsagePresentation;

import jakarta.annotation.Nonnull;

/**
 * User: cdr
 */
public class SliceDereferenceUsage extends SliceUsage {
  public SliceDereferenceUsage(@Nonnull PsiElement element, @Nonnull SliceUsage parent, @Nonnull PsiSubstitutor substitutor) {
    super(element, parent, substitutor, 0, "");
  }

  @Override
  public void processChildren(@Nonnull Processor<SliceUsage> processor) {
    // no children
  }

  @Nonnull
  @Override
  public UsagePresentation getPresentation() {
    final UsagePresentation presentation = super.getPresentation();

    return new UsagePresentation() {
      @Override
      @Nonnull
      public TextChunk[] getText() {
        return presentation.getText();
      }

      @Override
      @Nonnull
      public String getPlainText() {
        return presentation.getPlainText();
      }

      @Override
      public Image getIcon() {
        return presentation.getIcon();
      }

      @Override
      public String getTooltipText() {
        return "Variable dereferenced";
      }
    };
  }
}
