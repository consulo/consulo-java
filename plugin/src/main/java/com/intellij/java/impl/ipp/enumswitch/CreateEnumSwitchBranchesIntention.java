/*
 * Copyright 2003-2009 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ipp.enumswitch;

import com.intellij.java.impl.ipp.base.Intention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.language.psi.*;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.CreateEnumSwitchBranchesIntention", fileExtensions = "java", categories = {"Java", "Other"})
public class CreateEnumSwitchBranchesIntention extends Intention {
    @Nonnull
    @Override
    public LocalizeValue getText() {
        return IntentionPowerPackLocalize.createEnumSwitchBranchesIntentionName();
    }

    @Nonnull
    protected PsiElementPredicate getElementPredicate() {
        return new EnumSwitchPredicate();
    }

    public void processIntention(@Nonnull PsiElement element)
        throws IncorrectOperationException {
        if (element instanceof PsiWhiteSpace) {
            element = element.getPrevSibling();
        }
        final PsiSwitchStatement switchStatement = (PsiSwitchStatement) element;
        final PsiCodeBlock body = switchStatement.getBody();
        final PsiExpression switchExpression = switchStatement.getExpression();
        if (switchExpression == null) {
            return;
        }
        final PsiClassType switchType =
            (PsiClassType) switchExpression.getType();
        if (switchType == null) {
            return;
        }
        final PsiClass enumClass = switchType.resolve();
        if (enumClass == null) {
            return;
        }
        final PsiField[] fields = enumClass.getFields();
        final List<String> missingEnumElements =
            new ArrayList<String>(fields.length);
        for (final PsiField field : fields) {
            if (field instanceof PsiEnumConstant) {
                missingEnumElements.add(field.getName());
            }
        }
        if (body != null) {
            final PsiStatement[] statements = body.getStatements();
            for (final PsiStatement statement : statements) {
                if (!(statement instanceof PsiSwitchLabelStatement)) {
                    continue;
                }
                final PsiSwitchLabelStatement labelStatement =
                    (PsiSwitchLabelStatement) statement;
                final PsiExpression value = labelStatement.getCaseValue();
                if (!(value instanceof PsiReferenceExpression)) {
                    continue;
                }
                final PsiReferenceExpression reference =
                    (PsiReferenceExpression) value;
                final PsiElement resolved = reference.resolve();
                if (!(resolved instanceof PsiEnumConstant)) {
                    continue;
                }
                final PsiEnumConstant enumConstant = (PsiEnumConstant) resolved;
                missingEnumElements.remove(enumConstant.getName());
            }
        }
        @NonNls final StringBuilder buffer = new StringBuilder();
        buffer.append("switch(");
        buffer.append(switchExpression.getText());
        buffer.append("){");
        if (body != null) {
            final PsiElement[] children = body.getChildren();
            for (int i = 1; i < children.length - 1; i++) {
                buffer.append(children[i].getText());
            }
        }
        for (String missingEnumElement : missingEnumElements) {
            buffer.append("case ");
            buffer.append(missingEnumElement);
            buffer.append(": break;");
        }
        buffer.append('}');
        final String newStatement = buffer.toString();
        replaceStatement(newStatement, switchStatement);
    }
}