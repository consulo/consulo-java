/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugins.intelliLang.inject.java;

import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.Language;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiLiteralExpression;
import consulo.ide.impl.idea.util.Function;
import consulo.util.collection.ContainerUtil;
import org.intellij.plugins.intelliLang.inject.InjectedLanguage;
import org.intellij.plugins.intelliLang.util.StringLiteralReference;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Provides completion for available Language-IDs in
 * <pre>@Language("[ctrl-space]")</pre>
 */
final class LanguageReference extends StringLiteralReference {

  public LanguageReference(PsiLiteralExpression value) {
    super(value);
  }

  @Nullable
  public PsiElement resolve() {
    return InjectedLanguage.findLanguageById(getValue()) != null ? myValue : null;
  }

  public boolean isSoft() {
    return false;
  }

  @Nonnull
  public Object[] getVariants() {
    final String[] ids = InjectedLanguage.getAvailableLanguageIDs();
    return ContainerUtil.map2Array(ids, LookupElement.class, new Function<String, LookupElement>() {
      public LookupElement fun(String s) {
        final Language l = InjectedLanguage.findLanguageById(s);
        assert l != null;

        final FileType ft = l.getAssociatedFileType();
        if (ft != null) {
          return LookupElementBuilder.create(s).withIcon(ft.getIcon()).withTypeText(ft.getDescription().get());
//                } else if (l == StdLanguages.EL) {
//                    // IDEA-10012
//                    return new LanguageLookupValue(s, StdFileTypes.JSP.getIcon(), "Expression Language");
        }
        return LookupElementBuilder.create(s);
      }
    });
  }

}
