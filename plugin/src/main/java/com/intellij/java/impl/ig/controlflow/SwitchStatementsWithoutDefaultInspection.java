/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.controlflow;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

import javax.swing.*;

@ExtensionImpl
public class SwitchStatementsWithoutDefaultInspection extends BaseInspection {
    @SuppressWarnings("PublicField")
    public boolean m_ignoreFullyCoveredEnums = true;

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.switchStatementsWithoutDefaultDisplayName();
    }

    @Nonnull
    public String getID() {
        return "SwitchStatementWithoutDefaultBranch";
    }

    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.switchStatementsWithoutDefaultProblemDescriptor().get();
    }

    public JComponent createOptionsPanel() {
        LocalizeValue message = InspectionGadgetsLocalize.switchStatementWithoutDefaultIgnoreOption();
        return new SingleCheckboxOptionsPanel(message.get(), this, "m_ignoreFullyCoveredEnums");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new SwitchStatementsWithoutDefaultVisitor();
    }

    private class SwitchStatementsWithoutDefaultVisitor extends BaseInspectionVisitor {

        @Override
        public void visitSwitchStatement(@Nonnull PsiSwitchStatement statement) {
            super.visitSwitchStatement(statement);
            if (switchStatementHasDefault(statement)) {
                return;
            }
            if (m_ignoreFullyCoveredEnums && switchStatementIsFullyCoveredEnum(statement)) {
                return;
            }
            registerStatementError(statement);
        }

        private boolean switchStatementHasDefault(PsiSwitchStatement statement) {
            PsiCodeBlock body = statement.getBody();
            if (body == null) {
                return true; // do not warn about incomplete code
            }
            PsiStatement[] statements = body.getStatements();
            if (statements.length == 0) {
                return true; // do not warn when no switch branches are present at all
            }
            for (PsiStatement child : statements) {
                if (!(child instanceof PsiSwitchLabelStatement)) {
                    continue;
                }
                PsiSwitchLabelStatement switchLabelStatement = (PsiSwitchLabelStatement) child;
                if (switchLabelStatement.isDefaultCase()) {
                    return true;
                }
            }
            return false;
        }

        private boolean switchStatementIsFullyCoveredEnum(PsiSwitchStatement statement) {
            PsiExpression expression = statement.getExpression();
            if (expression == null) {
                return false;
            }
            PsiType type = expression.getType();
            if (!(type instanceof PsiClassType)) {
                return false;
            }
            PsiClassType classType = (PsiClassType) type;
            PsiClass aClass = classType.resolve();
            if (aClass == null || !aClass.isEnum()) {
                return false;
            }
            PsiCodeBlock body = statement.getBody();
            if (body == null) {
                return false;
            }
            PsiStatement[] statements = body.getStatements();
            int numCases = 0;
            for (PsiStatement child : statements) {
                if (child instanceof PsiSwitchLabelStatement) {
                    numCases++;
                }
            }
            PsiField[] fields = aClass.getFields();
            int numEnums = 0;
            for (PsiField field : fields) {
                PsiType fieldType = field.getType();
                if (fieldType.equals(type)) {
                    numEnums++;
                }
            }
            return numEnums == numCases;
        }
    }
}