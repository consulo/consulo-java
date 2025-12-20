/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.numeric;

import com.intellij.java.language.psi.PsiLiteralExpression;
import com.intellij.java.language.psi.PsiType;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.intellij.lang.annotations.Pattern;

@ExtensionImpl
public class OctalLiteralInspection extends BaseInspection {
    @Pattern(VALID_ID_PATTERN)
    @Override
    @Nonnull
    public String getID() {
        return "OctalInteger";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.octalLiteralDisplayName();
    }

    @Override
    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.octalLiteralProblemDescriptor().get();
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Nonnull
    @Override
    protected InspectionGadgetsFix[] buildFixes(Object... infos) {
        return new InspectionGadgetsFix[]{
            new ConvertOctalLiteralToDecimalFix(),
            new RemoveLeadingZeroFix()
        };
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new OctalLiteralVisitor();
    }

    private static class OctalLiteralVisitor extends BaseInspectionVisitor {
        @Override
        public void visitLiteralExpression(@Nonnull PsiLiteralExpression literal) {
            super.visitLiteralExpression(literal);
            PsiType type = literal.getType();
            if (type == null) {
                return;
            }
            if (!(type.equals(PsiType.INT) || type.equals(PsiType.LONG))) {
                return;
            }
            String text = literal.getText();
            if (text.length() == 1) {
                return;
            }
            if (text.charAt(0) != '0') {
                return;
            }
            char c1 = text.charAt(1);
            if (c1 != '_' && (c1 < '0' || c1 > '7')) {
                return;
            }
            if (literal.getValue() == null) {
                return;
            }
            registerError(literal);
        }
    }
}