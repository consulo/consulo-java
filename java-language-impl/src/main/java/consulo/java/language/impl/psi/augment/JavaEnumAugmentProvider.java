/*
 * Copyright 2013-2015 must-be.org
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

package consulo.java.language.impl.psi.augment;

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.augment.PsiAugmentProvider;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.impl.psi.impl.light.LightMethodBuilder;
import com.intellij.java.language.impl.psi.impl.source.PsiImmediateClassType;
import consulo.java.language.module.util.JavaClassNames;
import consulo.util.dataholder.Key;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author VISTALL
 * @since 30.04.2015
 */
@ExtensionImpl
public class JavaEnumAugmentProvider extends PsiAugmentProvider {
  public static final Key<Boolean> FLAG = Key.create("enum.method.flags");
  public static final String VALUES_METHOD_NAME = "values";
  public static final String VALUE_OF_METHOD_NAME = "valueOf";

  @Nonnull
  @Override
  @SuppressWarnings("unchecked")
  public <Psi extends PsiElement> List<Psi> getAugments(@Nonnull PsiElement element, @Nonnull Class<Psi> type) {
    if (type == PsiMethod.class && element instanceof PsiClass && element.getUserData(FLAG) == Boolean.TRUE && ((PsiClass) element).isEnum()) {
      List<Psi> list = new ArrayList<Psi>(2);

      LightMethodBuilder valuesMethod = new LightMethodBuilder(element.getManager(), JavaLanguage.INSTANCE, VALUES_METHOD_NAME);
      valuesMethod.setContainingClass((PsiClass) element);
      valuesMethod.setMethodReturnType(new PsiArrayType(new PsiImmediateClassType((PsiClass) element, PsiSubstitutor.EMPTY)));
      valuesMethod.addModifiers(PsiModifier.PUBLIC, PsiModifier.STATIC);
      list.add((Psi) valuesMethod);

      LightMethodBuilder valueOfMethod = new LightMethodBuilder(element.getManager(), JavaLanguage.INSTANCE, VALUE_OF_METHOD_NAME);
      valueOfMethod.setContainingClass((PsiClass) element);
      valueOfMethod.setMethodReturnType(new PsiImmediateClassType((PsiClass) element, PsiSubstitutor.EMPTY));
      valueOfMethod.addModifiers(PsiModifier.PUBLIC, PsiModifier.STATIC);
      valueOfMethod.addParameter("name", JavaClassNames.JAVA_LANG_STRING);
      valueOfMethod.addException(JavaClassNames.JAVA_LANG_ILLEGAL_ARGUMENT_EXCEPTION);
      list.add((Psi) valueOfMethod);
      return list;
    }
    return Collections.emptyList();
  }
}
