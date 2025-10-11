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
package com.intellij.java.impl.ig.errorhandling;

import com.intellij.java.impl.ig.ui.UiUtils;
import com.intellij.java.language.psi.CommonClassNames;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiThrowStatement;
import com.intellij.java.language.psi.PsiType;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ui.ExternalizableStringSet;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import consulo.ui.ex.awt.table.ListTable;
import consulo.ui.ex.awt.table.ListWrappingTableModel;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import org.intellij.lang.annotations.Pattern;

import javax.swing.*;
import java.util.List;

@ExtensionImpl
public class BadExceptionThrownInspection extends BaseInspection {
    /**
     * @noinspection PublicField
     */
    public String exceptionsString = "";

    @SuppressWarnings("PublicField")
    public final ExternalizableStringSet exceptions = new ExternalizableStringSet(
        CommonClassNames.JAVA_LANG_THROWABLE,
        CommonClassNames.JAVA_LANG_EXCEPTION,
        CommonClassNames.JAVA_LANG_ERROR,
        CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION,
        CommonClassNames.JAVA_LANG_NULL_POINTER_EXCEPTION,
        CommonClassNames.JAVA_LANG_CLASS_CAST_EXCEPTION,
        CommonClassNames.JAVA_LANG_ARRAY_INDEX_OUT_OF_BOUNDS_EXCEPTION
    );

    public BadExceptionThrownInspection() {
        if (exceptionsString.length() != 0) {
            exceptions.clear();
            final List<String> strings = StringUtil.split(exceptionsString, ",");
            for (String string : strings) {
                exceptions.add(string);
            }
            exceptionsString = "";
        }
    }

    @Nonnull
    @Override
    @Pattern(VALID_ID_PATTERN)
    public String getID() {
        return "ProhibitedExceptionThrown";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.badExceptionThrownDisplayName();
    }

    @Override
    public JComponent createOptionsPanel() {
        final ListTable table =
            new ListTable(new ListWrappingTableModel(exceptions, InspectionGadgetsLocalize.exceptionClassColumnName().get()));
        return UiUtils.createAddRemoveTreeClassChooserPanel(
            table,
            InspectionGadgetsLocalize.chooseExceptionClass().get(),
            CommonClassNames.JAVA_LANG_THROWABLE
        );
    }

    @Override
    @Nonnull
    public String buildErrorString(Object... infos) {
        final PsiType type = (PsiType) infos[0];
        final String exceptionName = type.getPresentableText();
        return InspectionGadgetsLocalize.badExceptionThrownProblemDescriptor(exceptionName).get();
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new BadExceptionThrownVisitor();
    }

    private class BadExceptionThrownVisitor extends BaseInspectionVisitor {

        @Override
        public void visitThrowStatement(PsiThrowStatement statement) {
            super.visitThrowStatement(statement);
            final PsiExpression exception = statement.getException();
            if (exception == null) {
                return;
            }
            final PsiType type = exception.getType();
            if (type == null) {
                return;
            }
            final String text = type.getCanonicalText();
            if (exceptions.contains(text)) {
                registerStatementError(statement, type);
            }
        }
    }
}