/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.encapsulateFields;

import com.intellij.java.impl.codeInsight.generation.GenerateMembersUtil;
import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PropertyUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.util.VisibilityUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.util.lang.StringUtil;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Max Medvedev
 */
@ExtensionImpl
public class JavaEncapsulateFieldHelper extends EncapsulateFieldHelper {
  private static final Logger LOG = Logger.getInstance(JavaEncapsulateFieldHelper.class);

  @Nullable
  public EncapsulateFieldUsageInfo createUsage(@Nonnull EncapsulateFieldsDescriptor descriptor,
                                               @Nonnull FieldDescriptor fieldDescriptor,
                                               @Nonnull PsiReference reference) {
    if (!(reference instanceof PsiReferenceExpression)) return null;

    boolean findSet = descriptor.isToEncapsulateSet();
    boolean findGet = descriptor.isToEncapsulateGet();
    PsiReferenceExpression ref = (PsiReferenceExpression)reference;
    // [Jeka] to avoid recursion in the field's accessors
    if (findGet && isUsedInExistingAccessor(descriptor.getTargetClass(), fieldDescriptor.getGetterPrototype(), ref)) return null;
    if (findSet && isUsedInExistingAccessor(descriptor.getTargetClass(), fieldDescriptor.getSetterPrototype(), ref)) return null;
    if (!findGet) {
      if (!PsiUtil.isAccessedForWriting(ref)) return null;
    }
    if (!findSet || fieldDescriptor.getField().hasModifierProperty(PsiModifier.FINAL)) {
      if (!PsiUtil.isAccessedForReading(ref)) return null;
    }
    if (!descriptor.isToUseAccessorsWhenAccessible()) {
      PsiModifierList newModifierList = createNewModifierList(descriptor);

      PsiClass accessObjectClass = null;
      PsiExpression qualifier = ref.getQualifierExpression();
      if (qualifier != null) {
        accessObjectClass = (PsiClass)PsiUtil.getAccessObjectClass(qualifier).getElement();
      }
      final PsiResolveHelper helper = JavaPsiFacade.getInstance(((PsiReferenceExpression)reference).getProject()).getResolveHelper();
      if (helper.isAccessible(fieldDescriptor.getField(), newModifierList, ref, accessObjectClass, null)) {
        return null;
      }
    }
    return new EncapsulateFieldUsageInfo(ref, fieldDescriptor);
  }

  public static PsiModifierList createNewModifierList(EncapsulateFieldsDescriptor descriptor) {
    PsiModifierList newModifierList = null;
    PsiElementFactory factory = JavaPsiFacade.getInstance(descriptor.getTargetClass().getProject()).getElementFactory();
    try {
      PsiField field = factory.createField("a", PsiType.INT);
      EncapsulateFieldsProcessor.setNewFieldVisibility(field, descriptor);
      newModifierList = field.getModifierList();
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return newModifierList;
  }

  public static boolean isUsedInExistingAccessor(PsiClass aClass, PsiMethod prototype, PsiElement element) {
    PsiMethod existingAccessor = aClass.findMethodBySignature(prototype, false);
    if (existingAccessor != null) {
      PsiElement parent = element;
      while (parent != null) {
        if (existingAccessor.equals(parent)) return true;
        parent = parent.getParent();
      }
    }
    return false;
  }

  public boolean processUsage(@Nonnull EncapsulateFieldUsageInfo usage,
                              @Nonnull EncapsulateFieldsDescriptor descriptor,
                              PsiMethod setter,
                              PsiMethod getter) {
    final PsiElement element = usage.getElement();
    if (!(element instanceof PsiReferenceExpression)) return false;

    final FieldDescriptor fieldDescriptor = usage.getFieldDescriptor();
    PsiField field = fieldDescriptor.getField();
    boolean processGet = descriptor.isToEncapsulateGet();
    boolean processSet = descriptor.isToEncapsulateSet() && !field.hasModifierProperty(PsiModifier.FINAL);
    if (!processGet && !processSet) return true;
    PsiElementFactory factory = JavaPsiFacade.getInstance(descriptor.getTargetClass().getProject()).getElementFactory();

    try{
      final PsiReferenceExpression expr = (PsiReferenceExpression)element;
      final PsiElement parent = expr.getParent();
      if (parent instanceof PsiAssignmentExpression && expr.equals(((PsiAssignmentExpression)parent).getLExpression())){
        PsiAssignmentExpression assignment = (PsiAssignmentExpression)parent;
        if (assignment.getRExpression() == null) return true;
        PsiJavaToken opSign = assignment.getOperationSign();
        IElementType opType = opSign.getTokenType();
        if (opType == JavaTokenType.EQ) {
          {
            if (!processSet) return true;
            final PsiExpression setterArgument = assignment.getRExpression();

            PsiMethodCallExpression methodCall = createSetterCall(fieldDescriptor, setterArgument, expr, descriptor.getTargetClass(), setter);

            if (methodCall != null) {
              assignment.replace(methodCall);
            }
            //TODO: check if value is used!!!
          }
        }
        else if (opType == JavaTokenType.ASTERISKEQ || opType == JavaTokenType.DIVEQ || opType == JavaTokenType.PERCEQ ||
                 opType == JavaTokenType.PLUSEQ ||
                 opType == JavaTokenType.MINUSEQ ||
                 opType == JavaTokenType.LTLTEQ ||
                 opType == JavaTokenType.GTGTEQ ||
                 opType == JavaTokenType.GTGTGTEQ ||
                 opType == JavaTokenType.ANDEQ ||
                 opType == JavaTokenType.OREQ ||
                 opType == JavaTokenType.XOREQ) {
          {
            // Q: side effects of qualifier??!

            String opName = opSign.getText();
            LOG.assertTrue(StringUtil.endsWithChar(opName, '='));
            opName = opName.substring(0, opName.length() - 1);

            PsiExpression getExpr = expr;
            if (processGet) {
              final PsiMethodCallExpression getterCall = createGetterCall(fieldDescriptor, expr, descriptor.getTargetClass(), getter);
              if (getterCall != null) {
                getExpr = getterCall;
              }
            }

            @NonNls String text = "a" + opName + "b";
            PsiBinaryExpression binExpr = (PsiBinaryExpression)factory.createExpressionFromText(text, expr);
            binExpr.getLOperand().replace(getExpr);
            binExpr.getROperand().replace(assignment.getRExpression());

            PsiExpression setExpr;
            if (processSet) {
              setExpr = createSetterCall(fieldDescriptor, binExpr, expr, descriptor.getTargetClass(), setter);
            }
            else {
              text = "a = b";
              PsiAssignmentExpression assignment1 = (PsiAssignmentExpression)factory.createExpressionFromText(text, null);
              assignment1.getLExpression().replace(expr);
              assignment1.getRExpression().replace(binExpr);
              setExpr = assignment1;
            }

            assignment.replace(setExpr);
            //TODO: check if value is used!!!
          }
        }
      }
      else if (RefactoringUtil.isPlusPlusOrMinusMinus(parent)){
        IElementType sign;
        if (parent instanceof PsiPrefixExpression){
          sign = ((PsiPrefixExpression)parent).getOperationTokenType();
        }
        else{
          sign = ((PsiPostfixExpression)parent).getOperationTokenType();
        }

        PsiExpression getExpr = expr;
        if (processGet){
          final PsiMethodCallExpression getterCall = createGetterCall(fieldDescriptor, expr, descriptor.getTargetClass(), getter);
          if (getterCall != null) {
            getExpr = getterCall;
          }
        }

        @NonNls String text;
        if (sign == JavaTokenType.PLUSPLUS){
          text = "a+1";
        }
        else{
          text = "a-1";
        }
        PsiBinaryExpression binExpr = (PsiBinaryExpression)factory.createExpressionFromText(text, null);
        binExpr.getLOperand().replace(getExpr);

        PsiExpression setExpr;
        if (processSet){
          setExpr = createSetterCall(fieldDescriptor, binExpr, expr, descriptor.getTargetClass(), setter);
        }
        else {
          text = "a = b";
          PsiAssignmentExpression assignment = (PsiAssignmentExpression)factory.createExpressionFromText(text, null);
          assignment.getLExpression().replace(expr);
          assignment.getRExpression().replace(binExpr);
          setExpr = assignment;
        }
        parent.replace(setExpr);
      }
      else{
        if (!processGet) return true;
        PsiMethodCallExpression methodCall = createGetterCall(fieldDescriptor, expr, descriptor.getTargetClass(), getter);

        if (methodCall != null) {
          expr.replace(methodCall);
        }
      }
    }
    catch(IncorrectOperationException e){
      LOG.error(e);
    }
    return true;
  }

  private static PsiMethodCallExpression createSetterCall(FieldDescriptor fieldDescriptor,
                                                          PsiExpression setterArgument,
                                                          PsiReferenceExpression expr,
                                                          PsiClass aClass,
                                                          PsiMethod setter) throws IncorrectOperationException {
    PsiElementFactory factory = JavaPsiFacade.getInstance(expr.getProject()).getElementFactory();
    final String setterName = fieldDescriptor.getSetterName();
    @NonNls String text = setterName + "(a)";
    PsiExpression qualifier = expr.getQualifierExpression();
    if (qualifier != null){
      text = "q." + text;
    }
    PsiMethodCallExpression methodCall = (PsiMethodCallExpression)factory.createExpressionFromText(text, expr);

    methodCall.getArgumentList().getExpressions()[0].replace(setterArgument);
    if (qualifier != null){
      methodCall.getMethodExpression().getQualifierExpression().replace(qualifier);
    }
    methodCall = checkMethodResolvable(methodCall, setter, expr, aClass);
    if (methodCall == null) {
      VisibilityUtil.escalateVisibility(fieldDescriptor.getField(), expr);
    }
    return methodCall;
  }

  @Nullable
  private static PsiMethodCallExpression createGetterCall(FieldDescriptor fieldDescriptor,
                                                          PsiReferenceExpression expr,
                                                          PsiClass aClass,
                                                          PsiMethod getter) throws IncorrectOperationException {
    PsiElementFactory factory = JavaPsiFacade.getInstance(expr.getProject()).getElementFactory();
    final String getterName = fieldDescriptor.getGetterName();
    @NonNls String text = getterName + "()";
    PsiExpression qualifier = expr.getQualifierExpression();
    if (qualifier != null) {
      text = "q." + text;
    }
    PsiMethodCallExpression methodCall = (PsiMethodCallExpression)factory.createExpressionFromText(text, expr);

    if (qualifier != null) {
      methodCall.getMethodExpression().getQualifierExpression().replace(qualifier);
    }

    methodCall = checkMethodResolvable(methodCall, getter, expr, aClass);
    if (methodCall == null) {
      VisibilityUtil.escalateVisibility(fieldDescriptor.getField(), expr);
    }
    return methodCall;
  }

  @Nullable
  private static PsiMethodCallExpression checkMethodResolvable(PsiMethodCallExpression methodCall,
                                                               PsiMethod targetMethod,
                                                               PsiReferenceExpression context,
                                                               PsiClass aClass) throws IncorrectOperationException {
    PsiElementFactory factory = JavaPsiFacade.getInstance(targetMethod.getProject()).getElementFactory();
    final PsiElement resolved = methodCall.getMethodExpression().resolve();
    if (resolved != targetMethod) {
      PsiClass containingClass;
      if (resolved instanceof PsiMethod) {
        containingClass = ((PsiMethod)resolved).getContainingClass();
      }
      else if (resolved instanceof PsiClass) {
        containingClass = (PsiClass)resolved;
      }
      else {
        return null;
      }
      if (containingClass != null && containingClass.isInheritor(aClass, false)) {
        final PsiExpression newMethodExpression =
          factory.createExpressionFromText("super." + targetMethod.getName(), context);
        methodCall.getMethodExpression().replace(newMethodExpression);
      }
      else {
        methodCall = null;
      }
    }
    return methodCall;
  }

  @Nonnull
  @Override
  public PsiField[] getApplicableFields(@Nonnull PsiClass aClass) {
    return aClass.getFields();
  }

  @Override
  @Nonnull
  public String suggestSetterName(@Nonnull PsiField field) {
    return PropertyUtil.suggestSetterName(field);
  }

  @Override
  @Nonnull
  public String suggestGetterName(@Nonnull PsiField field) {
    return PropertyUtil.suggestGetterName(field);
  }

  @Override
  @Nullable
  public PsiMethod generateMethodPrototype(@Nonnull PsiField field, @Nonnull String methodName, boolean isGetter) {
    PsiMethod prototype = isGetter
                          ? GenerateMembersUtil.generateGetterPrototype(field)
                          : GenerateMembersUtil.generateSetterPrototype(field);
    try {
      prototype.setName(methodName);
      return prototype;
    }
    catch (IncorrectOperationException e) {
      return null;
    }
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }
}
