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
package com.intellij.java.debugger.engine.evaluation;

import com.intellij.java.debugger.engine.evaluation.expression.EvaluatorBuilder;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.language.file.LanguageFileType;
import consulo.project.Project;
import com.intellij.java.language.psi.JavaCodeFragment;
import consulo.language.psi.PsiElement;

@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class CodeFragmentFactory {
  public abstract JavaCodeFragment createCodeFragment(TextWithImports item, PsiElement context, Project project);

  public abstract JavaCodeFragment createPresentationCodeFragment(TextWithImports item, PsiElement context, Project project);

  public abstract boolean isContextAccepted(PsiElement contextElement);

  public abstract LanguageFileType getFileType();

  /**
   * In case if createCodeFragment returns java code use
   * com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilderImpl#getInstance()
   * @return builder, which can evaluate expression for your code fragment
   */
  public abstract EvaluatorBuilder getEvaluatorBuilder();
}
