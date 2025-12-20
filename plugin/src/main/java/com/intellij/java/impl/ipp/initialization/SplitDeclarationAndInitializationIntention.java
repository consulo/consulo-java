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
        PsiField field = (PsiField) element.getParent();
        field.normalizeDeclaration();
        PsiExpression initializer = field.getInitializer();
        if (initializer == null) {
            return;
        }
        String initializerText;
        if (initializer instanceof PsiArrayInitializerExpression) {
            PsiType type = initializer.getType();
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
        PsiClass containingClass = field.getContainingClass();
        if (containingClass == null) {
            return;
        }
        boolean fieldIsStatic =
            field.hasModifierProperty(PsiModifier.STATIC);
        PsiClassInitializer[] classInitializers =
            containingClass.getInitializers();
        PsiClassInitializer classInitializer = null;
        int fieldOffset = field.getTextOffset();
        for (PsiClassInitializer existingClassInitializer : classInitializers) {
            int initializerOffset =
                existingClassInitializer.getTextOffset();
            if (initializerOffset <= fieldOffset) {
                continue;
            }
            boolean initializerIsStatic =
                existingClassInitializer.hasModifierProperty(
                    PsiModifier.STATIC);
            if (initializerIsStatic == fieldIsStatic) {
                classInitializer = existingClassInitializer;
                break;
            }
        }
        PsiManager manager = field.getManager();
        Project project = manager.getProject();
        PsiElementFactory elementFactory =
            JavaPsiFacade.getInstance(project).getElementFactory();
        if (classInitializer == null) {
            classInitializer = elementFactory.createClassInitializer();
            classInitializer = (PsiClassInitializer)
                containingClass.addAfter(classInitializer, field);

            // add some whitespace between the field and the class initializer
            PsiElement whitespace =
                PsiParserFacade.SERVICE.getInstance(project).createWhiteSpaceFromText("\n");
            containingClass.addAfter(whitespace, field);
        }
        PsiCodeBlock body = classInitializer.getBody();
        @NonNls String initializationStatementText =
            field.getName() + " = " + initializerText + ';';
        PsiExpressionStatement statement =
            (PsiExpressionStatement) elementFactory.createStatementFromText(
                initializationStatementText, body);
        PsiElement addedElement = body.add(statement);
        if (fieldIsStatic) {
            PsiModifierList modifierList =
                classInitializer.getModifierList();
            if (modifierList != null) {
                modifierList.setModifierProperty(PsiModifier.STATIC, true);
            }
        }
        initializer.delete();
        CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(manager.getProject());
        codeStyleManager.reformat(field);
        codeStyleManager.reformat(classInitializer);
        HighlightUtil.highlightElement(addedElement);
    }
}