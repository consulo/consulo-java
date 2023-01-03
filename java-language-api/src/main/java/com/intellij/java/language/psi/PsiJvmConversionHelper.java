/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.language.psi;

import com.intellij.java.language.jvm.JvmClassKind;
import com.intellij.java.language.jvm.JvmModifier;
import com.intellij.java.language.jvm.JvmTypeParameter;
import com.intellij.java.language.jvm.types.JvmReferenceType;
import com.intellij.java.language.jvm.types.JvmSubstitutor;
import com.intellij.java.language.jvm.types.JvmType;
import consulo.java.language.module.util.JavaClassNames;
import consulo.logging.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import static com.intellij.java.language.psi.PsiType.getJavaLangObject;
import static com.intellij.java.language.psi.PsiType.getTypeByName;

class PsiJvmConversionHelper {
  private static final Logger LOG = Logger.getInstance(PsiJvmConversionHelper.class);

  private static final Map<JvmModifier, String> MODIFIERS;

  static {
    Map<JvmModifier, String> modifiers = new EnumMap<>(JvmModifier.class);
    modifiers.put(JvmModifier.PUBLIC, PsiModifier.PUBLIC);
    modifiers.put(JvmModifier.PROTECTED, PsiModifier.PROTECTED);
    modifiers.put(JvmModifier.PRIVATE, PsiModifier.PRIVATE);
    modifiers.put(JvmModifier.PACKAGE_LOCAL, PsiModifier.PACKAGE_LOCAL);
    modifiers.put(JvmModifier.STATIC, PsiModifier.STATIC);
    modifiers.put(JvmModifier.ABSTRACT, PsiModifier.ABSTRACT);
    modifiers.put(JvmModifier.FINAL, PsiModifier.FINAL);
    modifiers.put(JvmModifier.NATIVE, PsiModifier.NATIVE);
    modifiers.put(JvmModifier.SYNCHRONIZED, PsiModifier.SYNCHRONIZED);
    modifiers.put(JvmModifier.STRICTFP, PsiModifier.STRICTFP);
    modifiers.put(JvmModifier.TRANSIENT, PsiModifier.TRANSIENT);
    modifiers.put(JvmModifier.VOLATILE, PsiModifier.VOLATILE);
    modifiers.put(JvmModifier.TRANSITIVE, PsiModifier.TRANSITIVE);
    MODIFIERS = Collections.unmodifiableMap(modifiers);
  }

  @Nonnull
  static PsiAnnotation[] getListAnnotations(@Nonnull PsiModifierListOwner modifierListOwner) {
    PsiModifierList list = modifierListOwner.getModifierList();
    return list == null ? PsiAnnotation.EMPTY_ARRAY : list.getAnnotations();
  }

  static boolean hasListModifier(@Nonnull PsiModifierListOwner modifierListOwner, @Nonnull JvmModifier modifier) {
    return modifierListOwner.hasModifierProperty(MODIFIERS.get(modifier));
  }

  @Nonnull
  static JvmClassKind getJvmClassKind(@Nonnull PsiClass psiClass) {
    if (psiClass.isAnnotationType()) {
      return JvmClassKind.ANNOTATION;
    }
    if (psiClass.isInterface()) {
      return JvmClassKind.INTERFACE;
    }
    if (psiClass.isEnum()) {
      return JvmClassKind.ENUM;
    }
    return JvmClassKind.CLASS;
  }

  @Nullable
  static JvmReferenceType getClassSuperType(@Nonnull PsiClass psiClass) {
    if (psiClass.isInterface()) {
      return null;
    }
    if (psiClass.isEnum()) {
      return getTypeByName(JavaClassNames.JAVA_LANG_ENUM, psiClass.getProject(), psiClass.getResolveScope());
    }
    if (psiClass instanceof PsiAnonymousClass) {
      PsiClassType baseClassType = ((PsiAnonymousClass) psiClass).getBaseClassType();
      PsiClass baseClass = baseClassType.resolve();
      if (baseClass == null || !baseClass.isInterface()) {
        return baseClassType;
      } else {
        return getJavaLangObject(psiClass.getManager(), psiClass.getResolveScope());
      }
    }
    if (JavaClassNames.JAVA_LANG_OBJECT.equals(psiClass.getQualifiedName())) {
      return null;
    }

    PsiClassType[] extendsTypes = psiClass.getExtendsListTypes();
    if (extendsTypes.length != 1) {
      return getJavaLangObject(psiClass.getManager(), psiClass.getResolveScope());
    }
    return extendsTypes[0];
  }

  @Nonnull
  static JvmReferenceType[] getClassInterfaces(@Nonnull PsiClass psiClass) {
    if (psiClass instanceof PsiAnonymousClass) {
      PsiClassType baseClassType = ((PsiAnonymousClass) psiClass).getBaseClassType();
      PsiClass baseClass = baseClassType.resolve();
      if (baseClass != null && baseClass.isInterface()) {
        return new JvmReferenceType[]{baseClassType};
      } else {
        return JvmReferenceType.EMPTY_ARRAY;
      }
    }

    PsiReferenceList referenceList = psiClass.isInterface() ? psiClass.getExtendsList() : psiClass.getImplementsList();
    if (referenceList == null) {
      return JvmReferenceType.EMPTY_ARRAY;
    }
    return referenceList.getReferencedTypes();
  }


  @Nullable
  static PsiAnnotation getListAnnotation(@Nonnull PsiModifierListOwner modifierListOwner, @Nonnull String fqn) {
    PsiModifierList list = modifierListOwner.getModifierList();
    return list == null ? null : list.findAnnotation(fqn);
  }

  static boolean hasListAnnotation(@Nonnull PsiModifierListOwner modifierListOwner, @Nonnull String fqn) {
    PsiModifierList list = modifierListOwner.getModifierList();
    return list != null && list.hasAnnotation(fqn);
  }

  static class PsiJvmSubstitutor implements JvmSubstitutor {

    private final
    @Nonnull
    PsiSubstitutor mySubstitutor;

    PsiJvmSubstitutor(@Nonnull PsiSubstitutor substitutor) {
      mySubstitutor = substitutor;
    }

    @Nullable
    @Override
    public JvmType substitute(@Nonnull JvmTypeParameter typeParameter) {
      if (!(typeParameter instanceof PsiTypeParameter)) {
        return null;
      }
      PsiTypeParameter psiTypeParameter = ((PsiTypeParameter) typeParameter);
      return mySubstitutor.substitute(psiTypeParameter);
    }
  }
}
