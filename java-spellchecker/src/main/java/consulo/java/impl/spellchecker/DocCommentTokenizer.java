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
package consulo.java.impl.spellchecker;

import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.java.language.psi.javadoc.PsiDocTag;
import consulo.language.psi.PsiElement;
import consulo.language.spellcheker.tokenizer.TokenConsumer;
import consulo.language.spellcheker.tokenizer.Tokenizer;
import consulo.language.spellcheker.tokenizer.splitter.CommentTokenSplitter;

import javax.annotation.Nonnull;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 *
 * @author shkate@jetbrains.com
 */
public class DocCommentTokenizer extends Tokenizer<PsiDocComment>
{
  private static final Set<String> excludedTags = Set.of("author", "see", "by", "link");

  @Override
  public void tokenize(@Nonnull PsiDocComment comment, TokenConsumer consumer) {
    final CommentTokenSplitter splitter = CommentTokenSplitter.getInstance();

    for (PsiElement el : comment.getChildren()) {
      if (el instanceof PsiDocTag) {
        PsiDocTag tag = (PsiDocTag)el;
        if (!excludedTags.contains(tag.getName())) {
          for (PsiElement data : tag.getDataElements()) {
            consumer.consumeToken(data, splitter);
          }
        }
      }
      else {
        consumer.consumeToken(el, splitter);
      }
    }
  }
}
