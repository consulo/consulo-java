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
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.impl.refactoring.changeClassSignature.ChangeClassSignatureDialog;
import com.intellij.java.impl.refactoring.changeClassSignature.TypeParameterInfo;
import com.intellij.java.language.psi.*;
import consulo.codeEditor.Editor;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import consulo.language.editor.intention.BaseIntentionAction;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * @author Danila Ponomarenko
 */
public class ChangeClassSignatureFromUsageFix extends BaseIntentionAction implements SyntheticIntentionAction {
  private final PsiClass myClass;
  private final PsiReferenceParameterList myParameterList;

  public ChangeClassSignatureFromUsageFix(@Nonnull PsiClass aClass,
                                          @Nonnull PsiReferenceParameterList parameterList) {
    myClass = aClass;
    myParameterList = parameterList;
    setText(JavaQuickFixBundle.message("change.class.signature.family"));
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
      result.put(new TypeParameterInfo(suggester.suggest(type), type),
                 factory.createTypeCodeFragment(type.getClassName(), typeElement, true));
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
