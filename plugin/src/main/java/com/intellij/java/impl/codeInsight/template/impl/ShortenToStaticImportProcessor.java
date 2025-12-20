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
package com.intellij.java.impl.codeInsight.template.impl;

import com.intellij.java.impl.codeInsight.intention.impl.AddOnDemandStaticImportAction;
import com.intellij.java.impl.codeInsight.intention.impl.AddSingleMemberStaticImportAction;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.document.Document;
import consulo.document.RangeMarker;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.editor.template.Template;
import consulo.language.editor.template.TemplateOptionalProcessor;
import consulo.language.editor.util.PsiUtilBase;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiUtilCore;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.util.dataholder.KeyWithDefaultValue;
import consulo.util.lang.Pair;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;

/**
 * @author Denis Zhdanov
 * @since 4/27/11 3:07 PM
 */
@ExtensionImpl
public class ShortenToStaticImportProcessor implements TemplateOptionalProcessor {
    public static final KeyWithDefaultValue<Boolean> KEY = KeyWithDefaultValue.create("java-use-static-import", false);

    private static final List<StaticImporter> IMPORTERS = asList(new SingleMemberStaticImporter(), new OnDemandStaticImporter());

    @Nonnull
    @Override
    public KeyWithDefaultValue<Boolean> getKey() {
        return KEY;
    }

    @Override
    public void processText(Project project, Template template, Document document, RangeMarker templateRange, Editor editor) {
        PsiDocumentManager.getInstance(project).commitDocument(document);
        PsiFile file = PsiUtilBase.getPsiFileInEditor(editor, project);
        if (file == null) {
            return;
        }

        List<Pair<PsiElement, StaticImporter>> staticImportTargets = new ArrayList<Pair<PsiElement, StaticImporter>>();
        for (
            PsiElement element = PsiUtilCore.getElementAtOffset(file, templateRange.getStartOffset());
            element != null && element.getTextRange().getStartOffset() < templateRange.getEndOffset();
            element = getNext(element)) {
            for (StaticImporter importer : IMPORTERS) {
                if (importer.canPerform(element)) {
                    staticImportTargets.add(new Pair<PsiElement, StaticImporter>(element, importer));
                    break;
                }
            }
        }

        for (Pair<PsiElement, StaticImporter> pair : staticImportTargets) {
            if (pair.first.isValid()) {
                pair.second.perform(project, file, editor, pair.first);
            }
        }
    }

    @Nullable
    private static PsiElement getNext(@Nonnull PsiElement element) {
        PsiElement result = element.getNextSibling();
        for (PsiElement current = element; current != null && result == null; current = current.getParent()) {
            result = current.getNextSibling();
        }
        return result;
    }

    @Nonnull
    @Override
    public LocalizeValue getOptionText() {
        return CodeInsightLocalize.dialogEditTemplateCheckboxUseStaticImport();
    }

    private interface StaticImporter {
        boolean canPerform(@Nonnull PsiElement element);

        void perform(Project project, PsiFile file, Editor editor, PsiElement element);
    }

    private static class SingleMemberStaticImporter implements StaticImporter {
        @Override
        public boolean canPerform(@Nonnull PsiElement element) {
            return AddSingleMemberStaticImportAction.getStaticImportClass(element) != null;
        }

        @Override
        public void perform(Project project, PsiFile file, Editor editor, PsiElement element) {
            AddSingleMemberStaticImportAction.invoke(file, element);
        }
    }

    private static class OnDemandStaticImporter implements StaticImporter {
        @Override
        @RequiredReadAction
        public boolean canPerform(@Nonnull PsiElement element) {
            return AddOnDemandStaticImportAction.getClassToPerformStaticImport(element) != null;
        }

        @Override
        @RequiredReadAction
        public void perform(Project project, PsiFile file, Editor editor, PsiElement element) {
            AddOnDemandStaticImportAction.invoke(project, file, editor, element);
        }
    }
}
