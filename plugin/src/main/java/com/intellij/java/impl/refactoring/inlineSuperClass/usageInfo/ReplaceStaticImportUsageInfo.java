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

/*
 * User: anna
 * Date: 02-Oct-2008
 */
package com.intellij.java.impl.refactoring.inlineSuperClass.usageInfo;

import com.intellij.java.impl.refactoring.util.FixableUsageInfo;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiImportStaticStatement;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.util.lang.StringUtil;

public class ReplaceStaticImportUsageInfo extends FixableUsageInfo {
    private final PsiImportStaticStatement myStaticImportStatement;
    private final PsiClass[] myTargetClasses;

    @RequiredReadAction
    public ReplaceStaticImportUsageInfo(PsiImportStaticStatement staticImportStatement, PsiClass[] targetClass) {
        super(staticImportStatement);
        myStaticImportStatement = staticImportStatement;
        myTargetClasses = targetClass;
    }

    @Override
    @RequiredWriteAction
    public void fixUsage() throws IncorrectOperationException {
        String memberName = myStaticImportStatement.getReferenceName();
        myStaticImportStatement.replace(JavaPsiFacade.getInstance(myStaticImportStatement.getProject())
            .getElementFactory()
            .createImportStaticStatement(myTargetClasses[0], memberName != null ? memberName : "*"));
    }

    @Override
    public LocalizeValue getConflictMessage() {
        if (myTargetClasses.length != 1) {
            return LocalizeValue.localizeTODO(
                "Static import can be replaced with any of " +
                    StringUtil.join(myTargetClasses, psiClass -> psiClass.getQualifiedName(), ", ")
            );
        }
        return super.getConflictMessage();
    }
}