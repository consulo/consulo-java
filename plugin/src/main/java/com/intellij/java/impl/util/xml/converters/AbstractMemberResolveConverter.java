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

package com.intellij.java.impl.util.xml.converters;

import com.intellij.java.analysis.codeInsight.intention.QuickFixFactory;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PropertyMemberType;
import com.intellij.java.language.psi.util.PropertyUtil;
import consulo.application.presentation.TypePresentationService;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.project.content.scope.ProjectScopes;
import consulo.xml.util.xml.*;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Gregory.Shrago
 */
public abstract class AbstractMemberResolveConverter extends ResolvingConverter<PsiMember> {
  @Nullable
  protected abstract PsiClass getTargetClass(ConvertContext context);

  @Nonnull
  protected abstract PropertyMemberType[] getMemberTypes(ConvertContext context);

  @Nonnull
  protected PsiType getPsiType(ConvertContext context) {
    return PsiType.getJavaLangObject(context.getPsiManager(), (GlobalSearchScope) ProjectScopes.getAllScope(context.getPsiManager().getProject()));
  }

  protected boolean isLookDeep() {
    return true;
  }

  protected String getPropertyName(String s, ConvertContext context) {
    return s;
  }

  public PsiMember fromString(String s, ConvertContext context) {
    if (s == null) return null;
    PsiClass psiClass = getTargetClass(context);
    if (psiClass == null) return null;
    for (PropertyMemberType type : getMemberTypes(context)) {
      switch (type) {
        case FIELD:
          PsiField field = psiClass.findFieldByName(s, isLookDeep());
          if (field != null) return field;
          break;
        case GETTER:
          PsiMethod getter = PropertyUtil.findPropertyGetter(psiClass, getPropertyName(s, context), false, isLookDeep());
          if (getter != null) return getter;
          break;
        case SETTER:
          PsiMethod setter = PropertyUtil.findPropertySetter(psiClass, getPropertyName(s, context), false, isLookDeep());
          if (setter != null) return setter;
          break;
      }
    }
    return null;
  }


  public String toString(PsiMember t, ConvertContext context) {
    return t == null? null : getPropertyName(t.getName(), context);
  }

  public String getErrorMessage(String s, ConvertContext context) {
    DomElement parent = context.getInvocationElement().getParent();
    assert parent != null;
    return CodeInsightLocalize.errorCannotResolve01(TypePresentationService.getInstance().getTypeName(parent), s).get();
  }

  @Nonnull
  public Collection<? extends PsiMember> getVariants(ConvertContext context) {
    PsiClass psiClass = getTargetClass(context);
    if (psiClass == null) return Collections.emptyList();

    ArrayList<PsiMember> list = new ArrayList<>();
    for (PsiField psiField : isLookDeep()? psiClass.getAllFields() : psiClass.getFields()) {
      if (fieldSuits(psiField)) {
        list.add(psiField);
      }
    }
    for (PsiMethod psiMethod : isLookDeep()? psiClass.getAllMethods() : psiClass.getMethods()) {
      if (methodSuits(psiMethod)) {
        list.add(psiMethod);
      }
    }
    return list;
  }

  protected boolean methodSuits(PsiMethod psiMethod) {
    return !psiMethod.isConstructor() && !psiMethod.hasModifierProperty(PsiModifier.STATIC) && PropertyUtil.getPropertyName(psiMethod) != null;
  }

  protected boolean fieldSuits(PsiField psiField) {
    return !psiField.hasModifierProperty(PsiModifier.STATIC);
  }

  public LocalQuickFix[] getQuickFixes(ConvertContext context) {
    String targetName = ((GenericValue)context.getInvocationElement()).getStringValue();
    if (!PsiNameHelper.getInstance(context.getProject()).isIdentifier(targetName)) return super.getQuickFixes(context);
    PsiClass targetClass = getTargetClass(context);
    if (targetClass == null) return super.getQuickFixes(context);
    PropertyMemberType memberType = getMemberTypes(context)[0];

    PsiType psiType = getPsiType(context);
    IntentionAction fix = QuickFixFactory.getInstance().createCreateFieldOrPropertyFix(targetClass, targetName, psiType, memberType);
    return fix instanceof LocalQuickFix localQuickFix ? new LocalQuickFix[] {localQuickFix} : super.getQuickFixes(context);
  }

  public void handleElementRename(GenericDomValue<PsiMember> genericValue, ConvertContext context, String newElementName) {
    super.handleElementRename(genericValue, context, getPropertyName(newElementName, context));
  }

  public void bindReference(GenericDomValue<PsiMember> genericValue, ConvertContext context, PsiElement newTarget) {
    if (newTarget instanceof PsiMember member) {
      String elementName = member.getName();
      genericValue.setStringValue(getPropertyName(elementName, context));
    }
  }
}