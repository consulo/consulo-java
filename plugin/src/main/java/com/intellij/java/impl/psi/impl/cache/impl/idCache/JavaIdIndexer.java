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
package com.intellij.java.impl.psi.impl.cache.impl.idCache;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.language.impl.lexer.JavaLexer;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.lexer.Lexer;
import consulo.language.psi.stub.LexerBasedIdIndexer;
import consulo.language.psi.stub.OccurrenceConsumer;
import consulo.virtualFileSystem.fileType.FileType;

import jakarta.annotation.Nonnull;

@ExtensionImpl
public class JavaIdIndexer extends LexerBasedIdIndexer {
  @Override
  public Lexer createLexer(OccurrenceConsumer consumer) {
    return createIndexingLexer(consumer);
  }

  public static Lexer createIndexingLexer(OccurrenceConsumer consumer) {
    Lexer javaLexer = new JavaLexer(LanguageLevel.JDK_1_3);
    return new JavaFilterLexer(javaLexer, consumer);
  }

  @Nonnull
  @Override
  public FileType getFileType() {
    return JavaFileType.INSTANCE;
  }
}
