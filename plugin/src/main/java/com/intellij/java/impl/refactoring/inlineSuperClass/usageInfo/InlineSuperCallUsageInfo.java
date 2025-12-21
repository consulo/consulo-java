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
package com.intellij.java.impl.refactoring.inlineSuperClass.usageInfo;

import com.intellij.java.impl.refactoring.inline.InlineMethodProcessor;
import com.intellij.java.impl.refactoring.inline.ReferencedElementsCollector;
import com.intellij.java.impl.refactoring.util.FixableUsageInfo;
import com.intellij.java.impl.refactoring.util.InlineUtil;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.usage.UsageInfo;
import consulo.util.collection.MultiMap;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;

/**
 * @author anna
 * @since 2009-10-10
 */
public class InlineSuperCallUsageInfo extends FixableUsageInfo {
    private PsiCodeBlock myConstructorBody;

    @RequiredReadAction
    public InlineSuperCallUsageInfo(PsiMethodCallExpression methodCallExpression) {
        super(methodCallExpression);
    }

    @RequiredReadAction
    public InlineSuperCallUsageInfo(PsiMethodCallExpression methodCallExpression, PsiCodeBlock constructorBody) {
        super(methodCallExpression);
        myConstructorBody = constructorBody;
    }

    @Override
    @RequiredWriteAction
    public void fixUsage() throws IncorrectOperationException {
        PsiElement element = getElement();
        if (element != null && myConstructorBody != null) {
            assert !element.isPhysical();
            PsiStatement statement = JavaPsiFacade.getElementFactory(getProject()).createStatementFromText("super();", myConstructorBody);
            element = ((PsiExpressionStatement) myConstructorBody.addBefore(statement, myConstructorBody.getFirstBodyElement())).getExpression();
        }
        if (element instanceof PsiMethodCallExpression call) {
            PsiReferenceExpression methodExpression = call.getMethodExpression();
            PsiMethod superConstructor = (PsiMethod) methodExpression.resolve();
            if (superConstructor != null) {
                PsiMethod methodCopy = JavaPsiFacade.getElementFactory(getProject()).createMethod("toInline", PsiType.VOID);
                PsiCodeBlock constructorBody = superConstructor.getBody();
                if (constructorBody != null) {
                    PsiCodeBlock methodBody = methodCopy.getBody();
                    assert methodBody != null;
                    methodBody.replace(constructorBody);

                    methodCopy.getParameterList().replace(superConstructor.getParameterList());
                    methodCopy.getThrowsList().replace(superConstructor.getThrowsList());

                    methodExpression = (PsiReferenceExpression) methodExpression.replace(JavaPsiFacade.getElementFactory(getProject())
                        .createExpressionFromText(methodCopy.getName(), methodExpression));
                    PsiClass inliningClass = superConstructor.getContainingClass();
                    assert inliningClass != null;
                    methodCopy = (PsiMethod) inliningClass.add(methodCopy);
                    InlineMethodProcessor inlineMethodProcessor =
                        new InlineMethodProcessor(getProject(), methodCopy, methodExpression, null, true);
                    inlineMethodProcessor.inlineMethodCall(methodExpression);
                    methodCopy.delete();
                }
            }
        }
    }

    @Override
    @RequiredReadAction
    public LocalizeValue getConflictMessage() {
        MultiMap<PsiElement, LocalizeValue> conflicts = new MultiMap<>();
        if (getElement() instanceof PsiMethodCallExpression methodCallExpression) {
            final PsiMethod superConstructor = methodCallExpression.resolveMethod();
            if (superConstructor != null) {
                InlineMethodProcessor.addInaccessibleMemberConflicts(
                    superConstructor,
                    new UsageInfo[]{new UsageInfo(methodCallExpression.getMethodExpression())},
                    new ReferencedElementsCollector() {
                        @Override
                        protected void checkAddMember(@Nonnull PsiMember member) {
                            if (!PsiTreeUtil.isAncestor(superConstructor.getContainingClass(), member, false)) {
                                super.checkAddMember(member);
                            }
                        }
                    },
                    conflicts
                );
                if (InlineMethodProcessor.checkBadReturns(superConstructor) && !InlineUtil.allUsagesAreTailCalls(superConstructor)) {
                    conflicts.putValue(
                        superConstructor,
                        LocalizeValue.localizeTODO(StringUtil.capitalize(
                            RefactoringLocalize.refactoringIsNotSupportedWhenReturnStatementInterruptsTheExecutionFlow("") +
                                " of super constructor"
                        ))
                    );
                }
            }
        }
        return conflicts.isEmpty() ? null : conflicts.values().iterator().next(); //todo
    }
}
