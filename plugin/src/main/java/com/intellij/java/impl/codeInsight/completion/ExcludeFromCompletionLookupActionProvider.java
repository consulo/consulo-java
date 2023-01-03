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
package com.intellij.java.impl.codeInsight.completion;

import com.intellij.java.impl.codeInsight.daemon.impl.actions.AddImportAction;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.completion.lookup.Lookup;
import consulo.language.editor.completion.lookup.LookupActionProvider;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementAction;
import consulo.project.Project;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * @author peter
 */
@ExtensionImpl(id = "excludeFromCompletion", order = "last")
public class ExcludeFromCompletionLookupActionProvider implements LookupActionProvider {
  @Override
  public void fillActions(LookupElement element, Lookup lookup, Consumer<LookupElementAction> consumer) {
    Object o = element.getObject();
    if (o instanceof PsiClassObjectAccessExpression) {
      o = PsiUtil.resolveClassInType(((PsiClassObjectAccessExpression) o).getOperand().getType());
    }

    if (o instanceof PsiClass) {
      PsiClass clazz = (PsiClass) o;
      addExcludes(consumer, clazz, clazz.getQualifiedName());
    } else if (o instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod) o;
      if (method.hasModifierProperty(PsiModifier.STATIC)) {
        addExcludes(consumer, method, PsiUtil.getMemberQualifiedName(method));
      }
    } else if (o instanceof PsiField) {
      final PsiField field = (PsiField) o;
      if (field.hasModifierProperty(PsiModifier.STATIC)) {
        addExcludes(consumer, field, PsiUtil.getMemberQualifiedName(field));
      }
    }
  }

  private static void addExcludes(Consumer<LookupElementAction> consumer, PsiMember element, @Nullable String qname) {
    if (qname == null) {
      return;
    }
    final Project project = element.getProject();
    for (final String s : AddImportAction.getAllExcludableStrings(qname)) {
      consumer.accept(new ExcludeFromCompletionAction(project, s));
    }
  }

  private static class ExcludeFromCompletionAction extends LookupElementAction {
    private final Project myProject;
    private final String myToExclude;

    public ExcludeFromCompletionAction(Project project, String s) {
      super(null, "Exclude '" + s + "' from completion");
      myProject = project;
      myToExclude = s;
    }

    @Override
    public Result performLookupAction() {
      AddImportAction.excludeFromImport(myProject, myToExclude);
      return Result.HIDE_LOOKUP;
    }
  }
}
