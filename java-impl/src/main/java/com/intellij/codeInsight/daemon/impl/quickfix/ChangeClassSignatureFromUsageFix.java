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
package com.intellij.codeInsight.daemon.impl.quickfix;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;

import consulo.java.JavaQuickFixBundle;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.changeClassSignature.ChangeClassSignatureDialog;
import com.intellij.refactoring.changeClassSignature.TypeParameterInfo;
import com.intellij.util.IncorrectOperationException;

/**
 * @author Danila Ponomarenko
 */
public class ChangeClassSignatureFromUsageFix extends BaseIntentionAction {
  private final PsiClass myClass;
  private final PsiReferenceParameterList myParameterList;

  public ChangeClassSignatureFromUsageFix(@Nonnull PsiClass aClass,
                                          @Nonnull PsiReferenceParameterList parameterList) {
    myClass = aClass;
    myParameterList = parameterList;
  }

  @Nonnull
  @Override
  public String getFamilyName() {
    return JavaQuickFixBundle.message("change.class.signature.family");
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    if (!myClass.isValid() || !myParameterList.isValid()) {
      return false;
    }

    if (myClass.getTypeParameters().length >= myParameterList.getTypeArguments().length) {
      return false;
    }

    final PsiTypeParameterList classTypeParameterList = myClass.getTypeParameterList();
    if (classTypeParameterList == null) {
      return false;
    }

    setText(JavaQuickFixBundle.message("change.class.signature.text", myClass.getName(), myParameterList.getText()));

    return true;
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiTypeParameterList classTypeParameterList = myClass.getTypeParameterList();
    if (classTypeParameterList == null) {
      return;
    }

    ChangeClassSignatureDialog dialog = new ChangeClassSignatureDialog(
      myClass,
      createTypeParameters(
        JavaCodeFragmentFactory.getInstance(project),
        Arrays.asList(classTypeParameterList.getTypeParameters()),
        Arrays.asList(myParameterList.getTypeParameterElements())
      ),
      false
    );
    dialog.show();
  }

  @Nonnull
  private static Map<TypeParameterInfo, PsiTypeCodeFragment> createTypeParameters(@Nonnull JavaCodeFragmentFactory factory,
                                                                                  @Nonnull List<PsiTypeParameter> classTypeParameters,
                                                                                  @Nonnull List<PsiTypeElement> typeElements) {
    final LinkedHashMap<TypeParameterInfo, PsiTypeCodeFragment> result = new LinkedHashMap<TypeParameterInfo, PsiTypeCodeFragment>();
    final TypeParameterNameSuggester suggester = new TypeParameterNameSuggester(classTypeParameters);

    int listIndex = 0;
    for (PsiTypeElement typeElement : typeElements) {
      if (listIndex < classTypeParameters.size()) {
        final PsiTypeParameter typeParameter = classTypeParameters.get(listIndex);

        if (isAssignable(typeParameter, typeElement.getType())) {
          result.put(new TypeParameterInfo(listIndex++), null);
          continue;
        }
      }

      final PsiClassType type = (PsiClassType)typeElement.getType();
      result.put(new TypeParameterInfo(suggester.suggest(type), type), factory.createTypeCodeFragment(type.getClassName(), typeElement, true));
    }
    return result;
  }

  private static boolean isAssignable(@Nonnull PsiTypeParameter typeParameter, @Nonnull PsiType type) {
    for (PsiClassType t : typeParameter.getExtendsListTypes()) {
      if (!t.isAssignableFrom(type)) {
        return false;
      }
    }

    return true;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }


  private static class TypeParameterNameSuggester {
    private final Set<String> usedNames = new HashSet<String>();

    public TypeParameterNameSuggester(@Nonnull PsiTypeParameter... typeParameters) {
      this(Arrays.asList(typeParameters));
    }

    public TypeParameterNameSuggester(@Nonnull Collection<PsiTypeParameter> typeParameters) {
      for (PsiTypeParameter p : typeParameters) {
        usedNames.add(p.getName());
      }
    }

    @Nonnull
    private String suggestUnusedName(@Nonnull String name) {
      String unusedName = name;
      int i = 0;
      while (true) {
        if (usedNames.add(unusedName)) {
          return unusedName;
        }
        unusedName = name + ++i;
      }
    }

    @Nonnull
    public String suggest(@Nonnull PsiClassType type) {
      return suggestUnusedName(type.getClassName().substring(0, 1).toUpperCase());
    }
  }
}
