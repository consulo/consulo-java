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
package com.intellij.java.impl.refactoring.util;

import consulo.project.Project;
import consulo.util.lang.ref.Ref;
import consulo.language.psi.PsiElement;
import consulo.language.editor.refactoring.BaseRefactoringProcessor;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.usage.UsageInfo;
import consulo.language.util.IncorrectOperationException;
import consulo.util.collection.MultiMap;
import com.intellij.xml.util.XmlUtil;
import consulo.logging.Logger;

import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class FixableUsagesRefactoringProcessor extends BaseRefactoringProcessor {
    private static final Logger LOG = Logger.getInstance(FixableUsagesRefactoringProcessor.class);

    protected FixableUsagesRefactoringProcessor(Project project) {
        super(project);
    }

    protected void performRefactoring(UsageInfo[] usageInfos) {
        CommonRefactoringUtil.sortDepthFirstRightLeftOrder(usageInfos);
        for (UsageInfo usageInfo : usageInfos) {
            if (usageInfo instanceof FixableUsageInfo) {
                try {
                    ((FixableUsageInfo)usageInfo).fixUsage();
                }
                catch (IncorrectOperationException e) {
                    LOG.info(e);
                }
            }
        }
    }

    @Nonnull
    protected final UsageInfo[] findUsages() {
        final List<FixableUsageInfo> usages = Collections.synchronizedList(new ArrayList<FixableUsageInfo>());
        findUsages(usages);
        final int numUsages = usages.size();
        final FixableUsageInfo[] usageArray = usages.toArray(new FixableUsageInfo[numUsages]);
        return usageArray;
    }

    protected abstract void findUsages(@Nonnull List<FixableUsageInfo> usages);

    protected static void checkConflicts(final Ref<UsageInfo[]> refUsages, final MultiMap<PsiElement, String> conflicts) {
        for (UsageInfo info : refUsages.get()) {
            final String conflict = ((FixableUsageInfo)info).getConflictMessage();
            if (conflict != null) {
                conflicts.putValue(info.getElement(), XmlUtil.escape(conflict));
            }
        }
    }
}
