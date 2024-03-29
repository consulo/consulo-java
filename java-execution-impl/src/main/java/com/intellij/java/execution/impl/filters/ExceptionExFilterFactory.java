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
package com.intellij.java.execution.impl.filters;

import com.intellij.java.execution.filters.ExceptionFilterFactory;
import com.intellij.java.execution.filters.ExceptionInfoCache;
import com.intellij.java.execution.filters.ExceptionWorker;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiCodeBlock;
import com.intellij.java.language.psi.PsiTryStatement;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.AccessToken;
import consulo.application.ApplicationManager;
import consulo.colorScheme.EffectType;
import consulo.colorScheme.TextAttributes;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.execution.ui.console.FileHyperlinkInfo;
import consulo.execution.ui.console.Filter;
import consulo.execution.ui.console.FilterMixin;
import consulo.execution.ui.console.HyperlinkInfo;
import consulo.language.psi.PsiCompiledFile;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.navigation.OpenFileDescriptor;
import consulo.ui.color.ColorValue;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.util.lang.Trinity;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @author gregsh
 */
@ExtensionImpl
public class ExceptionExFilterFactory implements ExceptionFilterFactory {
  @Nonnull
  @Override
  public Filter create(@Nonnull GlobalSearchScope searchScope) {
    return new MyFilter(searchScope);
  }

  private static class MyFilter implements Filter, FilterMixin {
    private final ExceptionInfoCache myCache;

    public MyFilter(@Nonnull final GlobalSearchScope scope) {
      myCache = new ExceptionInfoCache(scope);
    }

    @Override
    public Result applyFilter(final String line, final int textEndOffset) {
      return null;
    }

    @Override
    public boolean shouldRunHeavy() {
      return true;
    }

    @Override
    public void applyHeavyFilter(@Nonnull final Document copiedFragment, final int startOffset, int startLineNumber, @Nonnull final Consumer<? super AdditionalHighlight> consumer) {
      Map<String, Trinity<TextRange, TextRange, TextRange>> visited = new HashMap<String, Trinity<TextRange, TextRange, TextRange>>();
      final Trinity<TextRange, TextRange, TextRange> emptyInfo = Trinity.create(null, null, null);

      final ExceptionWorker worker = new ExceptionWorker(myCache);
      for (int i = 0; i < copiedFragment.getLineCount(); i++) {
        final int lineStartOffset = copiedFragment.getLineStartOffset(i);
        final int lineEndOffset = copiedFragment.getLineEndOffset(i);

        String text = copiedFragment.getText(new TextRange(lineStartOffset, lineEndOffset));
        if (!text.contains(".java:")) {
          continue;
        }
        Trinity<TextRange, TextRange, TextRange> info = visited.get(text);
        if (info == emptyInfo) {
          continue;
        }

        if (info == null) {
          info = emptyInfo;
          AccessToken token = ApplicationManager.getApplication().acquireReadActionLock();
          try {
            worker.execute(text, lineEndOffset);
            Result result = worker.getResult();
            if (result == null) {
              continue;
            }
            HyperlinkInfo hyperlinkInfo = result.getHyperlinkInfo();
            if (!(hyperlinkInfo instanceof FileHyperlinkInfo)) {
              continue;
            }

            OpenFileDescriptor descriptor = ((FileHyperlinkInfo) hyperlinkInfo).getDescriptor();
            if (descriptor == null) {
              continue;
            }

            PsiFile psiFile = worker.getFile();
            if (psiFile == null || psiFile instanceof PsiCompiledFile) {
              continue;
            }
            int offset = descriptor.getOffset();
            if (offset <= 0) {
              continue;
            }

            PsiElement element = psiFile.findElementAt(offset);
            PsiTryStatement parent = PsiTreeUtil.getParentOfType(element, PsiTryStatement.class, true, PsiClass.class);
            PsiCodeBlock tryBlock = parent != null ? parent.getTryBlock() : null;
            if (tryBlock == null || !tryBlock.getTextRange().contains(offset)) {
              continue;
            }
            info = worker.getInfo();
          } finally {
            token.finish();
            visited.put(text, info);
          }
        }
        int off = startOffset + lineStartOffset;
        final ColorValue color = TargetAWT.from(UIUtil.getInactiveTextColor());
        consumer.accept(new AdditionalHighlight(off + info.first.getStartOffset(), off + info.second.getEndOffset()) {
          @Nonnull
          @Override
          public TextAttributes getTextAttributes(@Nullable TextAttributes source) {
            return new TextAttributes(null, null, color, EffectType.BOLD_DOTTED_LINE, Font.PLAIN);
          }
        });
      }
    }

    @Nonnull
    @Override
    public String getUpdateMessage() {
      return "Highlighting try blocks...";
    }
  }
}
