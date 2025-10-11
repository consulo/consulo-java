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
package com.intellij.java.impl.ig.bugs;

import com.intellij.java.impl.ig.psiutils.ExceptionUtils;
import com.intellij.java.impl.ig.psiutils.IteratorUtils;
import com.intellij.java.language.psi.*;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.intellij.lang.annotations.Pattern;

import java.util.Set;

@ExtensionImpl
public class IteratorNextDoesNotThrowNoSuchElementExceptionInspection extends BaseInspection {
    @Nonnull
    @Override
    @Pattern(VALID_ID_PATTERN)
    public String getID() {
        return "IteratorNextCanNotThrowNoSuchElementException";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.iteratorNextDoesNotThrowNosuchelementexceptionDisplayName();
    }

    @Override
    @Nonnull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.iteratorNextDoesNotThrowNosuchelementexceptionProblemDescriptor().get();
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new IteratorNextDoesNotThrowNoSuchElementExceptionVisitor();
    }

    private static class IteratorNextDoesNotThrowNoSuchElementExceptionVisitor
        extends BaseInspectionVisitor {

        @Override
        public void visitMethod(@Nonnull PsiMethod method) {
            // note: no call to super
            if (!MethodUtils.methodMatches(method, CommonClassNames.JAVA_UTIL_ITERATOR, null, HardcodedMethodConstants.NEXT)) {
                return;
            }
            final Set<PsiClassType> exceptions =
                ExceptionUtils.calculateExceptionsThrown(method);
            for (final PsiType exception : exceptions) {
                if (exception.equalsToText(
                    "java.util.NoSuchElementException")) {
                    return;
                }
            }
            if (IteratorUtils.containsCallToIteratorNext(method, null, false)) {
                return;
            }
            final CalledMethodsVisitor visitor = new CalledMethodsVisitor();
            method.accept(visitor);
            if (visitor.isNoSuchElementExceptionThrown()) {
                return;
            }
            registerMethodError(method);
        }
    }

    private static class CalledMethodsVisitor
        extends JavaRecursiveElementVisitor {

        private boolean noSuchElementExceptionThrown = false;

        @Override
        public void visitMethodCallExpression(
            PsiMethodCallExpression expression
        ) {
            if (noSuchElementExceptionThrown) {
                return;
            }
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression =
                expression.getMethodExpression();
            final PsiElement method = methodExpression.resolve();
            if (method == null) {
                return;
            }
            final Set<PsiClassType> exceptions =
                ExceptionUtils.calculateExceptionsThrown(method);
            for (final PsiType exception : exceptions) {
                if (exception.equalsToText(
                    "java.util.NoSuchElementException")) {
                    noSuchElementExceptionThrown = true;
                }
            }
        }

        public boolean isNoSuchElementExceptionThrown() {
            return noSuchElementExceptionThrown;
        }
    }
}