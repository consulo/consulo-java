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
package com.intellij.java.impl.refactoring.wrapreturnvalue;

import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.impl.refactoring.RefactorJBundle;
import com.intellij.java.impl.refactoring.psi.MyUsageViewUtil;
import consulo.usage.UsageInfo;
import consulo.usage.UsageViewDescriptor;
import jakarta.annotation.Nonnull;

class WrapReturnValueUsageViewDescriptor implements UsageViewDescriptor {

    @Nonnull
    private final PsiMethod method;

    WrapReturnValueUsageViewDescriptor(@Nonnull PsiMethod method,
                                       UsageInfo[] usages){
        super();
        this.method = method;
    }

    public PsiElement[] getElements(){
        return new PsiElement[]{method};
    }

    public String getProcessedElementsHeader(){
        return RefactorJBundle.message("method.whose.return.are.to.wrapped");
    }

    public String getCodeReferencesText(int usagesCount, int filesCount){
        return RefactorJBundle.message("references.to.be.modified.usage.view",
                MyUsageViewUtil.getUsageCountInfo(usagesCount, filesCount, RefactorJBundle.message("reference")));
    }

  public String getCommentReferencesText(int usagesCount, int filesCount) {
        return null;
    }
}
