/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ipp.initialization;

import com.intellij.java.impl.ipp.base.Intention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.impl.ipp.psiutils.HighlightUtil;
import com.intellij.java.language.psi.*;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiParserFacade;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.SplitDeclarationAndInitializationIntention", fileExtensions = "java", categories = {"Java", "Declaration"})
public class SplitDeclarationAndInitializationIntention extends Intention {
    @Nonnull
    @Override
    public LocalizeValue getText() {
        return IntentionPowerPackLocalize.splitDeclarationAndInitializationIntentionName();
    }

    @Override
    @Nonnull
    protected PsiElementPredicate getElementPredicate() {
        return new SplitDeclarationAndInitializationPredicate();
    }

    @Override
    public void processIntention(@Nonnull PsiElement element)
        throws IncorrectOperationException {
        final PsiField field = (PsiField) element.getParent();
        field.normalizeDeclaration();
        final PsiExpression initializer = field.getInitializer();
        if (initializer == null) {
            return;
        }
        final String initializerText;
        if (initializer instanceof PsiArrayInitializerExpression) {
            final PsiType type = initializer.getType();
            if (type == null) {
                initializerText = initializer.getText();
            }
            else {
                initializerText = "new " + type.getCanonicalText() +
                    initializer.getText();
            }
        }
        else {
            initializerText = initializer.getText();
        }
        final PsiClass containingClass = field.getContainingClass();
        if (containingClass == null) {
            return;
        }
        final boolean fieldIsStatic =
            field.hasModifierProperty(PsiModifier.STATIC);
        final PsiClassInitializer[] classInitializers =
            containingClass.getInitializers();
        PsiClassInitializer classInitializer = null;
        final int fieldOffset = field.getTextOffset();
        for (PsiClassInitializer existingClassInitializer : classInitializers) {
            final int initializerOffset =
                existingClassInitializer.getTextOffset();
            if (initializerOffset <= fieldOffset) {
                continue;
            }
            final boolean initializerIsStatic =
                existingClassInitializer.hasModifierProperty(
                    PsiModifier.STATIC);
            if (initializerIsStatic == fieldIsStatic) {
                classInitializer = existingClassInitializer;
                break;
            }
        }
        final PsiManager manager = field.getManager();
        final Project project = manager.getProject();
        final PsiElementFactory elementFactory =
            JavaPsiFacade.getInstance(project).getElementFactory();
        if (classInitializer == null) {
            classInitializer = elementFactory.createClassInitializer();
            classInitializer = (PsiClassInitializer)
                containingClass.addAfter(classInitializer, field);

            // add some whitespace between the field and the class initializer
            final PsiElement whitespace =
                PsiParserFacade.SERVICE.getInstance(project).createWhiteSpaceFromText("\n");
            containingClass.addAfter(whitespace, field);
        }
        final PsiCodeBlock body = classInitializer.getBody();
        @NonNls final String initializationStatementText =
            field.getName() + " = " + initializerText + ';';
        final PsiExpressionStatement statement =
            (PsiExpressionStatement) elementFactory.createStatementFromText(
                initializationStatementText, body);
        final PsiElement addedElement = body.add(statement);
        if (fieldIsStatic) {
            final PsiModifierList modifierList =
                classInitializer.getModifierList();
            if (modifierList != null) {
                modifierList.setModifierProperty(PsiModifier.STATIC, true);
            }
        }
        initializer.delete();
        final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(manager.getProject());
        codeStyleManager.reformat(field);
        codeStyleManager.reformat(classInitializer);
        HighlightUtil.highlightElement(addedElement);
    }
}