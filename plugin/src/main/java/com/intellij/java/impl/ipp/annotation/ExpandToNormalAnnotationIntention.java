/*
 * Copyright 2010-2011 Bas Leijdekkers
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
package com.intellij.java.impl.ipp.annotation;

import com.intellij.java.impl.ipp.base.MutablyNamedIntention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.language.psi.*;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.CaretModel;
import consulo.codeEditor.Editor;
import consulo.fileEditor.FileEditorManager;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ExpandToNormalAnnotationIntention", fileExtensions = "java", categories = {
    "Java",
    "Annotations"
})
public class ExpandToNormalAnnotationIntention extends MutablyNamedIntention {

    @Override
    protected LocalizeValue getTextForElement(PsiElement element) {
        final PsiNameValuePair annotation = (PsiNameValuePair) element;
        final String text = buildReplacementText(annotation);
        return IntentionPowerPackLocalize.expandToNormalAnnotationName(text);
    }

    @Nonnull
    @Override
    public LocalizeValue getNeutralText() {
        return IntentionPowerPackLocalize.expandToNormalAnnotationIntentionFamilyName();
    }

    @Nonnull
    @Override
    protected PsiElementPredicate getElementPredicate() {
        return new ExpandToNormalAnnotationPredicate();
    }

    public static String buildReplacementText(PsiNameValuePair attribute) {
        final StringBuilder text = new StringBuilder();
        final PsiAnnotationMemberValue value = attribute.getValue();
        text.append("value = ");
        if (value != null) {
            text.append(value.getText());
        }
        return text.toString();
    }

    @Override
    protected void processIntention(@Nonnull PsiElement element) throws IncorrectOperationException {
        final PsiNameValuePair attribute = (PsiNameValuePair) element;
        final int textOffset = attribute.getTextOffset();
        final Project project = attribute.getProject();
        final String text = buildReplacementText(attribute);
        final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        final PsiAnnotation newAnnotation = factory.createAnnotationFromText("@A(" + text + " )", attribute);
        attribute.replace(newAnnotation.getParameterList().getAttributes()[0]);
        final FileEditorManager editorManager = FileEditorManager.getInstance(project);
        final Editor editor = editorManager.getSelectedTextEditor();
        if (editor == null) {
            return;
        }
        final CaretModel caretModel = editor.getCaretModel();
        caretModel.moveToOffset(textOffset + text.length() - 1);
    }
}
