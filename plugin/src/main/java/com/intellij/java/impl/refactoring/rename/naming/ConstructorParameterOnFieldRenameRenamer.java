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
package com.intellij.java.impl.refactoring.rename.naming;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiParameter;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.codeStyle.VariableKind;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.util.lang.Comparing;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiMirrorElement;
import consulo.language.psi.PsiNamedElement;
import consulo.language.impl.psi.LightElement;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.rename.AutomaticRenamer;
import org.jetbrains.annotations.NonNls;

import java.util.HashSet;
import java.util.Set;

/**
 * @author ven
 */
public class ConstructorParameterOnFieldRenameRenamer extends AutomaticRenamer {
  @NonNls
  protected String canonicalNameToName(@NonNls final String canonicalName, final PsiNamedElement element) {
    return JavaCodeStyleManager.getInstance(element.getProject()).propertyNameToVariableName(canonicalName, VariableKind.PARAMETER);
  }

  protected String nameToCanonicalName(@NonNls final String name, final PsiNamedElement element) {
    return JavaCodeStyleManager.getInstance(element.getProject()).variableNameToPropertyName(name, VariableKind.FIELD);
  }

  public ConstructorParameterOnFieldRenameRenamer(PsiField aField, String newFieldName) {
    final JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(aField.getProject());
    final String propertyName = styleManager.variableNameToPropertyName(aField.getName(), VariableKind.FIELD);
    if (!Comparing.strEqual(propertyName, styleManager.variableNameToPropertyName(newFieldName, VariableKind.FIELD))) {
      final String paramName = styleManager.propertyNameToVariableName(propertyName, VariableKind.PARAMETER);
      final PsiClass aClass = aField.getContainingClass();

      Set<PsiParameter> toRename = new HashSet<PsiParameter>();
      for (PsiMethod constructor : aClass.getConstructors()) {
        if (constructor instanceof PsiMirrorElement) {
          final PsiElement prototype = ((PsiMirrorElement) constructor).getPrototype();
          if (prototype instanceof PsiMethod && ((PsiMethod) prototype).isConstructor()) {
            constructor = (PsiMethod) prototype;
          } else {
            continue;
          }
        }
        if (constructor instanceof LightElement) continue;
        final PsiParameter[] parameters = constructor.getParameterList().getParameters();
        for (final PsiParameter parameter : parameters) {
          final String parameterName = parameter.getName();
          if (paramName.equals(parameterName) ||
              propertyName.equals(styleManager.variableNameToPropertyName(parameterName, VariableKind.PARAMETER))) {
            toRename.add(parameter);
          }
        }
      }
      myElements.addAll(toRename);

      suggestAllNames(aField.getName(), newFieldName);
    }
  }

  public String getDialogTitle() {
    return RefactoringLocalize.renameConstructorParametersTitle().get();
  }

  public String getDialogDescription() {
    return RefactoringLocalize.renameConstructorParametersWithTheFollowingNamesTo().get();
  }

  public String entityName() {
    return RefactoringLocalize.entityNameConstructorParameter().get();
  }
}