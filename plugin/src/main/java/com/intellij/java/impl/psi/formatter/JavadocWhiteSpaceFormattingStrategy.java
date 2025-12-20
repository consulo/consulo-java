/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.java.impl.psi.formatter;

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.JavaDocTokenType;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.ast.ASTNode;
import consulo.language.codeStyle.WhiteSpaceFormattingStrategyAdapter;

import jakarta.annotation.Nonnull;

/**
 * @author max
 */
@ExtensionImpl
public class JavadocWhiteSpaceFormattingStrategy extends WhiteSpaceFormattingStrategyAdapter {
  @Override
  public boolean containsWhitespacesOnly(@Nonnull ASTNode node) {
    return node.getElementType() == JavaDocTokenType.DOC_COMMENT_DATA && node.textContains('\n') && node.getText().trim().length() == 0;
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }
}
