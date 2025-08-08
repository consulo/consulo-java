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
package com.intellij.java.impl.javadoc.actions;

import com.intellij.java.impl.javadoc.JavadocConfigurable;
import com.intellij.java.impl.javadoc.JavadocGenerationManager;
import consulo.java.language.localize.JavadocLocalize;
import consulo.java.localize.JavaLocalize;
import consulo.language.editor.impl.action.BaseAnalysisAction;
import consulo.language.editor.scope.AnalysisScope;
import consulo.language.editor.ui.awt.scope.BaseAnalysisActionDialog;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.event.DocumentAdapter;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.layout.VerticalLayout;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;

public final class GenerateJavadocAction extends BaseAnalysisAction {
    private JavadocConfigurable myConfigurable;

    public GenerateJavadocAction() {
        super(
            JavaLocalize.actionGeneratejavadocText(),
            JavaLocalize.actionGeneratejavadocDescription(),
            JavadocLocalize.javadocGenerateTitle(),
            JavadocLocalize.javadocGenerateTitle()
        );
    }

    @Override
    protected void analyze(@Nonnull Project project, @Nonnull AnalysisScope scope) {
        myConfigurable.apply();
        JavadocGenerationManager.getInstance(project).generateJavadoc(scope);
        dispose();
    }

    @RequiredUIAccess
    @Override
    protected void extendMainLayout(BaseAnalysisActionDialog dialog, VerticalLayout layout, Project project) {
        myConfigurable = new JavadocConfigurable(JavadocGenerationManager.getInstance(project).getConfiguration());
        JComponent component = myConfigurable.createComponent();
        myConfigurable.reset();
        myConfigurable.getOutputDirField().getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(DocumentEvent e) {
                updateAvailability(dialog);
            }
        });
        updateAvailability(dialog);

        layout.add(TargetAWT.wrap(component));
    }

    private void updateAvailability(BaseAnalysisActionDialog dialog) {
        dialog.setOKActionEnabled(!myConfigurable.getOutputDir().isEmpty());
    }

    @Override
    protected void canceled() {
        super.canceled();
        dispose();
    }

    private void dispose() {
        if (myConfigurable != null) {
            myConfigurable.disposeUIResources();
            myConfigurable = null;
        }
    }

    @Override
    protected String getHelpTopic() {
        return "reference.dialogs.generate.javadoc";
    }
}
