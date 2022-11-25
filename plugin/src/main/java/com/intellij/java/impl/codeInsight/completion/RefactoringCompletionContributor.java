/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.java.impl.refactoring.ui.ClassNameReferenceEditor;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.editor.completion.CompletionContributor;
import consulo.language.editor.completion.CompletionParameters;
import consulo.language.editor.completion.CompletionResult;
import consulo.language.editor.completion.CompletionResultSet;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementDecorator;
import consulo.language.editor.completion.lookup.LookupElementPresentation;
import consulo.language.editor.completion.lookup.LookupElementRenderer;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;

import java.util.function.Consumer;

/**
 * @author peter
 */
public abstract class RefactoringCompletionContributor extends CompletionContributor {
  @Override
  public void fillCompletionVariants(CompletionParameters parameters, final CompletionResultSet resultSet) {
    if (parameters.getOriginalFile().getUserData(ClassNameReferenceEditor.CLASS_NAME_REFERENCE_FRAGMENT) == null) {
      return;
    }

    resultSet.runRemainingContributors(parameters, new Consumer<CompletionResult>() {
      @Override
      public void accept(CompletionResult result) {
        LookupElement element = result.getLookupElement();
        Object object = element.getObject();
        if (object instanceof PsiClass) {
          Module module = ModuleUtilCore.findModuleForPsiElement((PsiClass) object);
          if (module != null) {
            resultSet.accept(LookupElementDecorator.withRenderer(element, new AppendModuleName(module)));
            return;
          }
        }
        resultSet.passResult(result);
      }
    });
  }

  private static class AppendModuleName extends LookupElementRenderer<LookupElementDecorator<LookupElement>> {
    private final Module myModule;

    public AppendModuleName(Module module) {
      myModule = module;
    }

    @Override
    public void renderElement(LookupElementDecorator<LookupElement> element, LookupElementPresentation presentation) {
      element.getDelegate().renderElement(presentation);
      presentation.appendTailText(" [" + myModule.getName() + "]", true);
    }
  }
}
