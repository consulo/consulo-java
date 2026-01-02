/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.editorActions;

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiClassOwner;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.Application;
import consulo.codeEditor.Editor;
import consulo.document.Document;
import consulo.document.RangeMarker;
import consulo.document.util.TextRange;
import consulo.ide.impl.idea.codeInsight.editorActions.CopyPastePostProcessor;
import consulo.language.editor.CodeInsightSettings;
import consulo.language.editor.util.CollectHighlightsUtil;
import consulo.language.psi.*;
import consulo.logging.Logger;
import consulo.project.DumbService;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.Comparing;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public abstract class CopyPasteReferenceProcessor<TRef extends PsiElement> extends CopyPastePostProcessor<ReferenceTransferableData> {
    private static final Logger LOG = Logger.getInstance(CopyPasteReferenceProcessor.class);

    @Nonnull
    @Override
    @RequiredReadAction
    public List<ReferenceTransferableData> collectTransferableData(
        PsiFile file,
        Editor editor,
        int[] startOffsets,
        int[] endOffsets
    ) {
        if (CodeInsightSettings.getInstance().ADD_IMPORTS_ON_PASTE == CodeInsightSettings.NO) {
            return Collections.emptyList();
        }

        if (file instanceof PsiCompiledFile) {
            file = ((PsiCompiledFile)file).getDecompiledPsiFile();
        }
        if (!(file instanceof PsiClassOwner)) {
            return Collections.emptyList();
        }

        ArrayList<JavaReferenceData> array = new ArrayList<>();
        for (int j = 0; j < startOffsets.length; j++) {
            int startOffset = startOffsets[j];
            for (PsiElement element : CollectHighlightsUtil.getElementsInRange(file, startOffset, endOffsets[j])) {
                addReferenceData(file, startOffset, element, array);
            }
        }

        if (array.isEmpty()) {
            return Collections.emptyList();
        }

        return Collections.singletonList(new ReferenceTransferableData(array.toArray(new JavaReferenceData[array.size()])));
    }

    protected abstract void addReferenceData(PsiFile file, int startOffset, PsiElement element, ArrayList<JavaReferenceData> to);

    @Nonnull
    @Override
    public List<ReferenceTransferableData> extractTransferableData(Transferable content) {
        ReferenceTransferableData referenceData = null;
        if (CodeInsightSettings.getInstance().ADD_IMPORTS_ON_PASTE != CodeInsightSettings.NO) {
            try {
                DataFlavor flavor = JavaReferenceData.getDataFlavor();
                if (flavor != null) {
                    referenceData = (ReferenceTransferableData)content.getTransferData(flavor);
                }
            }
            catch (UnsupportedFlavorException | IOException ignored) {
            }
        }

        if (referenceData != null) { // copy to prevent changing of original by convertLineSeparators
            return Collections.singletonList(referenceData.clone());
        }

        return Collections.emptyList();
    }

    @Override
    @RequiredUIAccess
    public void processTransferableData(
        Project project,
        Editor editor,
        RangeMarker bounds,
        int caretOffset,
        SimpleReference<Boolean> indented,
        List<ReferenceTransferableData> values
    ) {
        if (DumbService.getInstance(project).isDumb()) {
            return;
        }
        Document document = editor.getDocument();
        PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);

        if (!(file instanceof PsiClassOwner)) {
            return;
        }

        PsiDocumentManager.getInstance(project).commitAllDocuments();
        assert values.size() == 1;
        JavaReferenceData[] referenceData = values.get(0).getData();
        TRef[] refs = findReferencesToRestore(file, bounds, referenceData);
        if (CodeInsightSettings.getInstance().ADD_IMPORTS_ON_PASTE == CodeInsightSettings.ASK) {
            askReferencesToRestore(project, refs, referenceData);
        }
        PsiDocumentManager.getInstance(project).commitAllDocuments();
        Application.get().runWriteAction(() -> restoreReferences(referenceData, refs));
    }

    @RequiredReadAction
    protected static void addReferenceData(
        PsiElement element,
        ArrayList<JavaReferenceData> array,
        int startOffset,
        String qClassName,
        @Nullable String staticMemberName
    ) {
        TextRange range = element.getTextRange();
        array.add(new JavaReferenceData(
            range.getStartOffset() - startOffset,
            range.getEndOffset() - startOffset,
            qClassName,
            staticMemberName
        ));
    }

    protected abstract TRef[] findReferencesToRestore(PsiFile file, RangeMarker bounds, JavaReferenceData[] referenceData);

    @RequiredReadAction
    protected PsiElement resolveReferenceIgnoreOverriding(PsiPolyVariantReference reference) {
        PsiElement referent = reference.resolve();
        if (referent == null) {
            ResolveResult[] results = reference.multiResolve(true);
            if (results.length > 0) {
                referent = results[0].getElement();
            }
        }
        return referent;
    }

    protected abstract void restoreReferences(JavaReferenceData[] referenceData, TRef[] refs);

    @RequiredUIAccess
    private static void askReferencesToRestore(Project project, PsiElement[] refs, JavaReferenceData[] referenceData) {
        PsiManager manager = PsiManager.getInstance(project);

        ArrayList<Object> array = new ArrayList<>();
        Object[] refObjects = new Object[refs.length];
        for (int i = 0; i < referenceData.length; i++) {
            PsiElement ref = refs[i];
            if (ref != null) {
                LOG.assertTrue(ref.isValid());
                JavaReferenceData data = referenceData[i];
                PsiClass refClass = JavaPsiFacade.getInstance(manager.getProject()).findClass(data.qClassName, ref.getResolveScope());
                if (refClass == null) {
                    continue;
                }

                Object refObject = refClass;
                if (data.staticMemberName != null) {
                    //Show static members as Strings
                    refObject = refClass.getQualifiedName() + "." + data.staticMemberName;
                }
                refObjects[i] = refObject;

                if (!array.contains(refObject)) {
                    array.add(refObject);
                }
            }
        }
        if (array.isEmpty()) {
            return;
        }

        Object[] selectedObjects = ArrayUtil.toObjectArray(array);
        Arrays.sort(
            selectedObjects,
            (o1, o2) -> {
                String fqName1 = getFQName(o1);
                String fqName2 = getFQName(o2);
                return fqName1.compareToIgnoreCase(fqName2);
            }
        );

        RestoreReferencesDialog dialog = new RestoreReferencesDialog(project, selectedObjects);
        dialog.show();
        selectedObjects = dialog.getSelectedElements();

        for (int i = 0; i < referenceData.length; i++) {
            PsiElement ref = refs[i];
            if (ref != null) {
                LOG.assertTrue(ref.isValid());
                Object refObject = refObjects[i];
                boolean found = false;
                for (Object selected : selectedObjects) {
                    if (Comparing.equal(refObject, selected)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    refs[i] = null;
                }
            }
        }
    }

    private static String getFQName(Object element) {
        return element instanceof PsiClass psiClass ? psiClass.getQualifiedName() : (String)element;
    }
}
