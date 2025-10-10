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
package com.intellij.java.impl.ipp.exceptions;

import com.intellij.java.impl.ipp.base.Intention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.language.psi.*;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

import java.util.*;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.DetailExceptionsIntention", fileExtensions = "java", categories = {"Java", "Other"})
public class DetailExceptionsIntention extends Intention {
    @Nonnull
    @Override
    public LocalizeValue getText() {
        return IntentionPowerPackLocalize.detailExceptionsIntentionName();
    }

    @Override
    @Nonnull
    public PsiElementPredicate getElementPredicate() {
        return new DetailExceptionsPredicate();
    }

    @Override
    public void processIntention(@Nonnull PsiElement element) throws IncorrectOperationException {
        final PsiJavaToken token = (PsiJavaToken) element;
        PsiElement parent = token.getParent();
        if (parent instanceof PsiCatchSection) {
            parent = parent.getParent();
        }
        if (!(parent instanceof PsiTryStatement)) {
            return;
        }
        final PsiTryStatement tryStatement = (PsiTryStatement) parent;
        @NonNls final StringBuilder newTryStatement = new StringBuilder("try");
        final Set<PsiType> exceptionsThrown = new HashSet<PsiType>();
        final PsiResourceList resourceList = tryStatement.getResourceList();
        if (resourceList != null) {
            newTryStatement.append(resourceList.getText());
            ExceptionUtils.calculateExceptionsThrownForResourceList(resourceList, exceptionsThrown);
        }
        final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
        if (tryBlock == null) {
            return;
        }
        final String tryBlockText = tryBlock.getText();
        newTryStatement.append(tryBlockText);
        ExceptionUtils.calculateExceptionsThrownForCodeBlock(tryBlock, exceptionsThrown);
        final Comparator<PsiType> comparator = new HierarchicalTypeComparator();
        final List<PsiType> exceptionsAlreadyEmitted = new ArrayList<PsiType>();
        final PsiCatchSection[] catchSections = tryStatement.getCatchSections();
        for (PsiCatchSection catchSection : catchSections) {
            final PsiParameter parameter = catchSection.getParameter();
            final PsiCodeBlock block = catchSection.getCatchBlock();
            if (parameter != null && block != null) {
                final PsiType caughtType = parameter.getType();
                final List<PsiType> exceptionsToExpand = new ArrayList<PsiType>(10);
                for (Object aExceptionsThrown : exceptionsThrown) {
                    final PsiType thrownType = (PsiType) aExceptionsThrown;
                    if (caughtType.isAssignableFrom(thrownType)) {
                        exceptionsToExpand.add(thrownType);
                    }
                }
                exceptionsToExpand.removeAll(exceptionsAlreadyEmitted);
                Collections.sort(exceptionsToExpand, comparator);
                for (PsiType thrownType : exceptionsToExpand) {
                    newTryStatement.append("catch(").append(thrownType.getCanonicalText()).append(' ').append(parameter.getName()).append(')');
                    newTryStatement.append(block.getText());
                    exceptionsAlreadyEmitted.add(thrownType);
                }
            }
        }
        final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
        if (finallyBlock != null) {
            newTryStatement.append("finally").append(finallyBlock.getText());
        }
        final String newStatement = newTryStatement.toString();
        replaceStatementAndShorten(newStatement, tryStatement);
    }
}