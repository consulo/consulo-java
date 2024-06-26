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
  protected abstract PsiClass getTargetClass(final ConvertContext context);

  @Nonnull
  protected abstract PropertyMemberType[] getMemberTypes(final ConvertContext context);

  @Nonnull
  protected PsiType getPsiType(final ConvertContext context) {
    return PsiType.getJavaLangObject(context.getPsiManager(), (GlobalSearchScope) ProjectScopes.getAllScope(context.getPsiManager().getProject()));
  }

  protected boolean isLookDeep() {
    return true;
  }

  protected String getPropertyName(final String s, final ConvertContext context) {
    return s;
  }

  public PsiMember fromString(final String s, final ConvertContext context) {
    if (s == null) return null;
    final PsiClass psiClass = getTargetClass(context);
    if (psiClass == null) return null;
    for (PropertyMemberType type : getMemberTypes(context)) {
      switch (type) {
        case FIELD:
          final PsiField field = psiClass.findFieldByName(s, isLookDeep());
          if (field != null) return field;
          break;
        case GETTER:
          final PsiMethod getter = PropertyUtil.findPropertyGetter(psiClass, getPropertyName(s, context), false, isLookDeep());
          if (getter != null) return getter;
          break;
        case SETTER:
          final PsiMethod setter = PropertyUtil.findPropertySetter(psiClass, getPropertyName(s, context), false, isLookDeep());
          if (setter != null) return setter;
          break;
      }
    }
    return null;
  }


  public String toString(final PsiMember t, final ConvertContext context) {
    return t == null? null : getPropertyName(t.getName(), context);
  }

  public String getErrorMessage(final String s, final ConvertContext context) {
    final DomElement parent = context.getInvocationElement().getParent();
    assert parent != null;
    return CodeInsightLocalize.errorCannotResolve01(TypePresentationService.getInstance().getTypeName(parent), s).get();
  }

  @Nonnull
  public Collection<? extends PsiMember> getVariants(final ConvertContext context) {
    final PsiClass psiClass = getTargetClass(context);
    if (psiClass == null) return Collections.emptyList();

    final ArrayList<PsiMember> list = new ArrayList<>();
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

  protected boolean methodSuits(final PsiMethod psiMethod) {
    return !psiMethod.isConstructor() && !psiMethod.hasModifierProperty(PsiModifier.STATIC) && PropertyUtil.getPropertyName(psiMethod) != null;
  }

  protected boolean fieldSuits(final PsiField psiField) {
    return !psiField.hasModifierProperty(PsiModifier.STATIC);
  }

  public LocalQuickFix[] getQuickFixes(final ConvertContext context) {
    final String targetName = ((GenericValue)context.getInvocationElement()).getStringValue();
    if (!PsiNameHelper.getInstance(context.getProject()).isIdentifier(targetName)) return super.getQuickFixes(context);
    final PsiClass targetClass = getTargetClass(context);
    if (targetClass == null) return super.getQuickFixes(context);
    final PropertyMemberType memberType = getMemberTypes(context)[0];

    final PsiType psiType = getPsiType(context);
    final IntentionAction fix = QuickFixFactory.getInstance().createCreateFieldOrPropertyFix(targetClass, targetName, psiType, memberType);
    return fix instanceof LocalQuickFix localQuickFix ? new LocalQuickFix[] {localQuickFix} : super.getQuickFixes(context);
  }

  public void handleElementRename(final GenericDomValue<PsiMember> genericValue, final ConvertContext context, final String newElementName) {
    super.handleElementRename(genericValue, context, getPropertyName(newElementName, context));
  }

  public void bindReference(final GenericDomValue<PsiMember> genericValue, final ConvertContext context, final PsiElement newTarget) {
    if (newTarget instanceof PsiMember member) {
      final String elementName = member.getName();
      genericValue.setStringValue(getPropertyName(elementName, context));
    }
  }
}