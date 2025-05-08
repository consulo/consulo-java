/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.completion;

import com.intellij.java.impl.codeInsight.completion.scope.CompletionElement;
import com.intellij.java.impl.codeInsight.completion.scope.JavaCompletionProcessor;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.editor.completion.lookup.*;
import consulo.language.psi.PsiElement;
import consulo.language.psi.filter.ElementFilter;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.function.Condition;

import jakarta.annotation.Nonnull;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * @author peter
 */
class SuperCalls {
    @RequiredReadAction
    static Set<LookupElement> suggestQualifyingSuperCalls(
        PsiElement element,
        PsiJavaReference javaReference,
        ElementFilter elementFilter,
        JavaCompletionProcessor.Options options,
        Predicate<String> nameCondition
    ) {
        Set<LookupElement> set = new LinkedHashSet<>();
        for (String className : getContainingClassNames(element)) {
            PsiReferenceExpression fakeSuper = JavaCompletionUtil.createReference(className + ".super.rulez", element);
            PsiElement leaf = ObjectUtil.assertNotNull(fakeSuper.getReferenceNameElement());

            JavaCompletionProcessor superProcessor = new JavaCompletionProcessor(leaf, elementFilter, options, nameCondition);
            fakeSuper.processVariants(superProcessor);

            for (CompletionElement completionElement : superProcessor.getResults()) {
                for (LookupElement item : JavaCompletionUtil.createLookupElements(completionElement, javaReference)) {
                    set.add(withQualifiedSuper(className, item));
                }
            }
        }
        return set;
    }

    @Nonnull
    @RequiredReadAction
    private static LookupElement withQualifiedSuper(final String className, LookupElement item) {
        return PrioritizedLookupElement.withExplicitProximity(
            new LookupElementDecorator<LookupElement>(item) {

                @Override
                public void renderElement(LookupElementPresentation presentation) {
                    super.renderElement(presentation);
                    presentation.setItemText(className + ".super." + presentation.getItemText());
                }

                @Override
                public void handleInsert(InsertionContext context) {
                    context.commitDocument();
                    PsiJavaCodeReferenceElement ref = PsiTreeUtil.findElementOfClassAtOffset(
                        context.getFile(),
                        context.getStartOffset(),
                        PsiJavaCodeReferenceElement.class,
                        false
                    );
                    if (ref != null) {
                        context.getDocument().insertString(ref.getTextRange().getStartOffset(), className + ".");
                    }

                    super.handleInsert(context);
                }
            },
            -1
        );
    }

    @RequiredReadAction
    private static Set<String> getContainingClassNames(PsiElement position) {
        Set<String> result = new LinkedHashSet<>();
        boolean add = false;
        while (position != null) {
            if (position instanceof PsiAnonymousClass) {
                add = true;
            }
            else if (add && position instanceof PsiClass psiClass) {
                ContainerUtil.addIfNotNull(result, psiClass.getName());
            }
            position = position.getParent();
        }
        return result;
    }
}
