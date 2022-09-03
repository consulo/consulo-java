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
package com.intellij.java.impl.codeInsight.template.impl;

import consulo.language.editor.CodeInsightBundle;
import consulo.language.editor.template.Template;
import consulo.language.editor.template.TemplateOptionalProcessor;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import consulo.document.Document;
import consulo.codeEditor.Editor;
import consulo.document.RangeMarker;
import consulo.project.Project;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiFile;
import consulo.language.editor.util.PsiUtilBase;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;

public class ShortenFQNamesProcessor implements TemplateOptionalProcessor {
  private static final Logger LOG = Logger.getInstance(ShortenFQNamesProcessor.class);

  @Override
  public void processText(final Project project, final Template template, final Document document, final RangeMarker templateRange,
                          final Editor editor) {
    if (!template.isToShortenLongNames()) return;

    try {
      PsiDocumentManager.getInstance(project).commitDocument(document);
      JavaCodeStyleManager javaStyle = JavaCodeStyleManager.getInstance(project);
      final PsiFile file = PsiUtilBase.getPsiFileInEditor(editor, project);
      assert file != null;
      javaStyle.shortenClassReferences(file, templateRange.getStartOffset(), templateRange.getEndOffset());
      PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document);
    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @Override
  public String getOptionName() {
    return CodeInsightBundle.message("dialog.edit.template.checkbox.shorten.fq.names");
  }

  @Override
  public boolean isEnabled(final Template template) {
    return template.isToShortenLongNames();
  }

  @Override
  public void setEnabled(final Template template, final boolean value) {
    template.setToShortenLongNames(value);
  }

  @Override
  public boolean isVisible(Template template) {
    return true;
  }
}
