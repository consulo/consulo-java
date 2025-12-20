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

import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.document.Document;
import consulo.document.RangeMarker;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.editor.template.Template;
import consulo.language.editor.template.TemplateOptionalProcessor;
import consulo.language.editor.util.PsiUtilBase;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.dataholder.KeyWithDefaultValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class ShortenFQNamesProcessor implements TemplateOptionalProcessor {
    private static final Logger LOG = Logger.getInstance(ShortenFQNamesProcessor.class);

    public static final KeyWithDefaultValue<Boolean> KEY = KeyWithDefaultValue.create("java-shorted-fq-names", true);

    @Nonnull
    @Override
    public KeyWithDefaultValue<Boolean> getKey() {
        return KEY;
    }

    @Override
    public void processText(Project project,
                            Template template,
                            Document document,
                            RangeMarker templateRange,
                            Editor editor) {
        try {
            PsiDocumentManager.getInstance(project).commitDocument(document);
            JavaCodeStyleManager javaStyle = JavaCodeStyleManager.getInstance(project);
            PsiFile file = PsiUtilBase.getPsiFileInEditor(editor, project);
            assert file != null;
            javaStyle.shortenClassReferences(file, templateRange.getStartOffset(), templateRange.getEndOffset());
            PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document);
        }
        catch (IncorrectOperationException e) {
            LOG.error(e);
        }
    }

    @Nonnull
    @Override
    public LocalizeValue getOptionText() {
        return CodeInsightLocalize.dialogEditTemplateCheckboxShortenFqNames();
    }
}
