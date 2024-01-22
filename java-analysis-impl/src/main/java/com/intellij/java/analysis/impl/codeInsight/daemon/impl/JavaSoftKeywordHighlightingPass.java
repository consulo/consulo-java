/*
 * Copyright 2013-2017 must-be.org
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

package com.intellij.java.analysis.impl.codeInsight.daemon.impl;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.impl.lexer.JavaLexer;
import com.intellij.java.language.psi.JavaRecursiveElementVisitor;
import com.intellij.java.language.psi.PsiJavaFile;
import com.intellij.java.language.psi.PsiKeyword;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.progress.ProgressIndicator;
import consulo.document.Document;
import consulo.language.editor.impl.highlight.TextEditorHighlightingPass;
import consulo.language.editor.impl.highlight.UpdateHighlightersUtil;
import consulo.language.editor.rawHighlight.HighlightInfo;
import consulo.util.collection.ContainerUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author VISTALL
 * @since 09-Jan-17
 */
public class JavaSoftKeywordHighlightingPass extends TextEditorHighlightingPass {
  private final PsiJavaFile myFile;
  private final List<HighlightInfo> myResults = new ArrayList<>();

  public JavaSoftKeywordHighlightingPass(@jakarta.annotation.Nonnull PsiJavaFile file, @Nullable Document document) {
    super(file.getProject(), document);
    myFile = file;
  }

  @RequiredReadAction
  @Override
  public void doCollectInformation(@Nonnull ProgressIndicator progressIndicator) {
    LanguageLevel languageLevel = myFile.getLanguageLevel();

    myFile.accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitKeyword(PsiKeyword keyword) {
        if (JavaLexer.isSoftKeyword(keyword.getNode().getChars(), languageLevel)) {
          ContainerUtil.addIfNotNull(myResults, HighlightInfo.newHighlightInfo(JavaHighlightInfoTypes.JAVA_KEYWORD).range(keyword).create());
        }
      }
    });
  }

  @Override
  public void doApplyInformationToEditor() {
    if (!myResults.isEmpty()) {
      UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument, 0, myFile.getTextLength(), myResults, getColorsScheme(), getId());
    }
  }
}
