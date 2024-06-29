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
package com.intellij.java.impl.codeInsight.generation;

import com.intellij.java.language.impl.codeInsight.generation.EncapsulatableClassMember;
import com.intellij.java.language.impl.codeInsight.generation.GenerationInfo;
import com.intellij.java.language.impl.codeInsight.generation.PropertyClassMember;
import com.intellij.java.language.psi.PsiClass;
import consulo.java.analysis.codeInsight.JavaCodeInsightBundle;
import consulo.language.editor.generation.ClassMember;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import jakarta.annotation.Nullable;

import javax.swing.*;

public class GenerateSetterHandler extends GenerateGetterSetterHandlerBase {

  public GenerateSetterHandler() {
    super(CodeInsightLocalize.generateSetterFieldsChooserTitle().get());
  }

  @Nullable
  @Override
  protected JComponent getHeaderPanel(final Project project) {
    return getHeaderPanel(project, SetterTemplatesManager.getInstance(), JavaCodeInsightBundle.message("generate.equals.hashcode.template"));
  }

  @Override
  protected GenerationInfo[] generateMemberPrototypes(PsiClass aClass, ClassMember original) throws IncorrectOperationException {
    if (original instanceof PropertyClassMember propertyClassMember) {
      final GenerationInfo[] getters = propertyClassMember.generateSetters(aClass);
      if (getters != null) {
        return getters;
      }
    } else if (original instanceof EncapsulatableClassMember encapsulatableClassMember) {
      final GenerationInfo setter = encapsulatableClassMember.generateSetter();
      if (setter != null) {
        return new GenerationInfo[]{setter};
      }
    }
    return GenerationInfo.EMPTY_ARRAY;
  }

  @Override
  protected String getNothingFoundMessage() {
    return "No fields have been found to generate setters for";
  }

  @Override
  protected String getNothingAcceptedMessage() {
    return "No fields without setter were found";
  }
}
