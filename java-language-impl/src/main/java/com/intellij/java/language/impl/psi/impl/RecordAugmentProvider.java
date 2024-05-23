// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.impl;

import com.intellij.java.language.impl.psi.impl.light.LightMethod;
import com.intellij.java.language.impl.psi.impl.light.LightRecordCanonicalConstructor;
import com.intellij.java.language.impl.psi.impl.light.LightRecordField;
import com.intellij.java.language.impl.psi.impl.light.LightRecordMethod;
import com.intellij.java.language.impl.psi.impl.source.PsiExtensibleClass;
import com.intellij.java.language.impl.psi.util.AccessModifier;
import com.intellij.java.language.impl.psi.util.JavaPsiRecordUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.augment.PsiAugmentProvider;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiCompiledElement;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ExtensionImpl
public class RecordAugmentProvider extends PsiAugmentProvider {
  @Override
  protected
  @Nonnull
  <Psi extends PsiElement> List<Psi> getAugments(@Nonnull PsiElement element,
                                                 @Nonnull Class<Psi> type,
                                                 @Nullable String nameHint) {
    if (element instanceof PsiExtensibleClass) {
      PsiExtensibleClass aClass = (PsiExtensibleClass)element;
      if (!aClass.isRecord()) {
        return Collections.emptyList();
      }
      if (type == PsiMethod.class && !(element instanceof PsiCompiledElement)) {
        // We do not remove constructor and accessors in compiled records, so no need to augment
        return getAccessorsAugments(aClass);
      }
      if (type == PsiField.class) {
        return getFieldAugments(aClass);
      }
    }
    return Collections.emptyList();
  }

  @Nonnull
  private static <Psi extends PsiElement> List<Psi> getAccessorsAugments(PsiExtensibleClass aClass) {
    PsiRecordHeader header = aClass.getRecordHeader();
    if (header == null) {
      return Collections.emptyList();
    }
    PsiRecordComponent[] components = aClass.getRecordComponents();
    PsiElementFactory factory = JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory();
    ArrayList<Psi> methods = new ArrayList<>(components.length);
    List<PsiMethod> ownMethods = aClass.getOwnMethods();
    for (PsiRecordComponent component : components) {
      if (!shouldGenerateMethod(component, ownMethods)) {
        continue;
      }
      PsiMethod recordMethod = createRecordMethod(component, factory);
      if (recordMethod == null) {
        continue;
      }
      LightMethod method = new LightRecordMethod(aClass.getManager(), recordMethod, aClass, component);
      //noinspection unchecked
      methods.add((Psi)method);
    }
    PsiMethod constructor = getCanonicalConstructor(aClass, ownMethods, header);
    if (constructor != null) {
      //noinspection unchecked
      methods.add((Psi)constructor);
    }
    return methods;
  }

  @Nullable
  private static PsiMethod getCanonicalConstructor(PsiExtensibleClass aClass,
                                                   List<PsiMethod> ownMethods,
                                                   @Nonnull PsiRecordHeader recordHeader) {
    String className = aClass.getName();
    if (className == null) {
      return null;
    }
    for (PsiMethod method : ownMethods) {
      if (JavaPsiRecordUtil.isCompactConstructor(method) || JavaPsiRecordUtil.isExplicitCanonicalConstructor(method)) {
        return null;
      }
    }
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(recordHeader.getProject());
    String sb = className + recordHeader.getText() + "{"
      + StringUtil.join(recordHeader.getRecordComponents(), c -> "this." + c.getName() + "=" + c.getName() + ";", "\n")
      + "}";
    PsiMethod nonPhysical = factory.createMethodFromText(sb, recordHeader.getContainingClass());
    PsiModifierList classModifierList = aClass.getModifierList();
    AccessModifier modifier = classModifierList == null ? AccessModifier.PUBLIC : AccessModifier.fromModifierList(classModifierList);
    nonPhysical.getModifierList().setModifierProperty(modifier.toPsiModifier(), true);
    return new LightRecordCanonicalConstructor(nonPhysical, aClass);
  }

  private static boolean shouldGenerateMethod(PsiRecordComponent component, List<PsiMethod> ownMethods) {
    String componentName = component.getName();
    if (componentName == null) {
      return false;
    }
    for (PsiMethod method : ownMethods) {
      // Return type is not checked to avoid unnecessary warning about clashing signatures in case of different return types
      if (componentName.equals(method.getName()) && method.getParameterList().isEmpty()) {
        return false;
      }
    }
    return true;
  }

  @Nonnull
  public static <Psi extends PsiElement> List<Psi> getFieldAugments(PsiClass aClass) {
    PsiRecordComponent[] components = aClass.getRecordComponents();
    PsiElementFactory factory = JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory();
    ArrayList<Psi> fields = new ArrayList<>(components.length);
    for (PsiRecordComponent component : components) {
      PsiField recordField = createRecordField(component, factory);
      if (recordField == null) {
        continue;
      }
      LightRecordField field = new LightRecordField(aClass.getManager(), recordField, aClass, component);
      //noinspection unchecked
      fields.add((Psi)field);
    }
    return fields;
  }

  @Nullable
  private static PsiField createRecordField(@Nonnull PsiRecordComponent component, @Nonnull PsiElementFactory factory) {
    String name = component.getName();
    if (hasForbiddenType(component)) {
      return null;
    }
    String typeText = getTypeText(component);
    if (typeText == null) {
      return null;
    }
    try {
      return factory.createFieldFromText("private final " + typeText + " " + name + ";", component.getContainingClass());
    }
    catch (IncorrectOperationException e) {
      // typeText could be unparseable, like '@int'
      return null;
    }
  }

  @Nullable
  private static PsiMethod createRecordMethod(@Nonnull PsiRecordComponent component, @Nonnull PsiElementFactory factory) {
    String name = component.getName();
    if (name == null) {
      return null;
    }
    if (hasForbiddenType(component)) {
      return null;
    }
    String typeText = getTypeText(component);
    if (typeText == null) {
      return null;
    }
    try {
      return factory.createMethodFromText("public " + typeText + " " + name + "(){ return " + name + "; }", component.getContainingClass());
    }
    catch (IncorrectOperationException e) {
      // typeText could be unparseable, like '@int'
      return null;
    }
  }

  private static boolean hasForbiddenType(@Nonnull PsiRecordComponent component) {
    PsiTypeElement typeElement = component.getTypeElement();
    return typeElement == null || typeElement.getText().equals(PsiKeyword.RECORD);
  }

  @Nullable
  private static String getTypeText(@Nonnull PsiRecordComponent component) {
    PsiTypeElement typeElement = component.getTypeElement();
    if (typeElement == null) {
      return null;
    }
    String typeText = typeElement.getText();
    if (typeText.endsWith("...")) {
      typeText = typeText.substring(0, typeText.length() - 3) + "[]";
    }
    return typeText;
  }
}
