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
package com.intellij.java.impl.codeInsight.highlighting;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.ApplicationManager;
import consulo.codeEditor.Editor;
import consulo.externalService.statistic.FeatureUsageTracker;
import consulo.ide.impl.idea.codeInsight.highlighting.HighlightUsagesHandler;
import consulo.language.editor.CodeInsightBundle;
import consulo.language.editor.highlight.usage.HighlightUsagesHandlerBase;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.navigation.ItemPresentation;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class HighlightOverridingMethodsHandler extends HighlightUsagesHandlerBase<PsiClass> {
    private final PsiElement myTarget;
    private final PsiClass myClass;

    public HighlightOverridingMethodsHandler(Editor editor, PsiFile file, PsiElement target, PsiClass psiClass) {
        super(editor, file);
        myTarget = target;
        myClass = psiClass;
    }

    @Override
    @RequiredReadAction
    public List<PsiClass> getTargets() {
        PsiReferenceList list = PsiKeyword.EXTENDS.equals(myTarget.getText()) ? myClass.getExtendsList() : myClass.getImplementsList();
        if (list == null) {
            return Collections.emptyList();
        }
        PsiClassType[] classTypes = list.getReferencedTypes();
        return ChooseClassAndDoHighlightRunnable.resolveClasses(classTypes);
    }

    @Override
    protected void selectTargets(final List<PsiClass> targets, final Consumer<List<PsiClass>> selectionConsumer) {
        new ChooseClassAndDoHighlightRunnable(targets, myEditor, CodeInsightLocalize.highlightOverriddenClassesChooserTitle().get()) {
            @Override
            protected void selected(PsiClass... classes) {
                selectionConsumer.accept(Arrays.asList(classes));
            }
        }.run();
    }

    @Override
    @RequiredReadAction
    public void computeUsages(List<PsiClass> classes) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed("codeassists.highlight.implements");
        for (PsiMethod method : myClass.getMethods()) {
            List<HierarchicalMethodSignature> superSignatures = method.getHierarchicalMethodSignature().getSuperSignatures();
            for (HierarchicalMethodSignature superSignature : superSignatures) {
                PsiClass containingClass = superSignature.getMethod().getContainingClass();
                if (containingClass == null) {
                    continue;
                }
                for (PsiClass classToAnalyze : classes) {
                    if (InheritanceUtil.isInheritorOrSelf(classToAnalyze, containingClass, true)) {
                        PsiIdentifier identifier = method.getNameIdentifier();
                        if (identifier != null) {
                            addOccurrence(identifier);
                            break;
                        }
                    }
                }
            }
        }
        if (myReadUsages.isEmpty()) {
            if (myClass.getApplication().isUnitTestMode()) {
                return;
            }
            String name;
            if (classes.size() == 1) {
                ItemPresentation presentation = classes.get(0).getPresentation();
                name = presentation != null ? presentation.getPresentableText() : "";
            }
            else {
                name = "";
            }
            myHintText = CodeInsightBundle.message("no.methods.overriding.0.are.found", classes.size(), name);
        }
        else {
            addOccurrence(myTarget);
            int methodCount = myReadUsages.size() - 1;  // exclude 'target' keyword
            myStatusText = CodeInsightLocalize.statusBarOverriddenMethodsHighlightedMessage(
                methodCount,
                HighlightUsagesHandler.getShortcutText()
            ).get();
        }
    }
}
