/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.java.impl.codeInsight.ExpectedTypeInfo;
import com.intellij.java.impl.codeInsight.ExpectedTypeInfoImpl;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.language.editor.completion.lookup.TailType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.SmartPointerManager;
import consulo.language.psi.SmartPsiElementPointer;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Pair;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public class CreateMethodFromMethodReferenceFix extends CreateFromUsageBaseFix {
  private static final Logger LOG = Logger.getInstance(CreateMethodFromMethodReferenceFix.class);

  private final SmartPsiElementPointer myMethodReferenceExpression;

  public CreateMethodFromMethodReferenceFix(@Nonnull PsiMethodReferenceExpression methodRef) {
    myMethodReferenceExpression = SmartPointerManager.getInstance(methodRef.getProject())
        .createSmartPsiElementPointer(methodRef);

    setText(JavaQuickFixLocalize.createMethodFromUsageFamily());
  }

  @Override
  protected boolean isAvailableImpl(int offset) {
    PsiMethodReferenceExpression call = getMethodReference();
    if (call == null || !call.isValid()) {
      return false;
    }
    PsiType functionalInterfaceType = call.getFunctionalInterfaceType();
    if (functionalInterfaceType == null || LambdaUtil.getFunctionalInterfaceMethod(functionalInterfaceType) == null) {
      return false;
    }

    String name = call.getReferenceName();

    if (name == null) {
      return false;
    }
    if (call.isConstructor() && name.equals("new") || PsiNameHelper.getInstance(call.getProject()).isIdentifier
        (name)) {
      setText(call.isConstructor() ? JavaQuickFixLocalize.createConstructorFromNewText() : JavaQuickFixLocalize.createMethodFromUsageText(name));
      return true;
    }
    return false;
  }

  @Override
  protected PsiElement getElement() {
    PsiMethodReferenceExpression call = getMethodReference();
    if (call == null || !call.getManager().isInProject(call)) {
      return null;
    }
    return call;
  }

  @Override
  @Nonnull
  protected List<PsiClass> getTargetClasses(PsiElement element) {
    List<PsiClass> targets = super.getTargetClasses(element);
    PsiMethodReferenceExpression call = getMethodReference();
    if (call == null) {
      return Collections.emptyList();
    }
    return targets;
  }

  @Override
  protected void invokeImpl(PsiClass targetClass) {
    if (targetClass == null) {
      return;
    }
    PsiMethodReferenceExpression expression = getMethodReference();
    if (expression == null) {
      return;
    }

    if (isValidElement(expression)) {
      return;
    }

    PsiClass parentClass = PsiTreeUtil.getParentOfType(expression, PsiClass.class);
    PsiMember enclosingContext = PsiTreeUtil.getParentOfType(expression, PsiMethod.class, PsiField.class,
        PsiClassInitializer.class);

    String methodName = expression.getReferenceName();
    LOG.assertTrue(methodName != null);


    Project project = targetClass.getProject();
    JVMElementFactory elementFactory = JVMElementFactories.getFactory(targetClass.getLanguage(), project);
    if (elementFactory == null) {
      elementFactory = JavaPsiFacade.getElementFactory(project);
    }

    PsiMethod method = expression.isConstructor() ? (PsiMethod) targetClass.add(elementFactory.createConstructor()
    ) : CreateMethodFromUsageFix.createMethod(targetClass, parentClass, enclosingContext, methodName);
    if (method == null) {
      return;
    }

    if (!expression.isConstructor()) {
      setupVisibility(parentClass, targetClass, method.getModifierList());
    }

    expression = getMethodReference();
    LOG.assertTrue(expression.isValid());

    if (!expression.isConstructor() && shouldCreateStaticMember(expression, targetClass)) {
      PsiUtil.setModifierProperty(method, PsiModifier.STATIC, true);
    }

    PsiElement context = PsiTreeUtil.getParentOfType(expression, PsiClass.class, PsiMethod.class);

    PsiType functionalInterfaceType = expression.getFunctionalInterfaceType();
    PsiClassType.ClassResolveResult classResolveResult = PsiUtil.resolveGenericsClassInType
        (functionalInterfaceType);
    PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(classResolveResult);
    LOG.assertTrue(interfaceMethod != null);

    PsiType interfaceReturnType = LambdaUtil.getFunctionalInterfaceReturnType(functionalInterfaceType);
    LOG.assertTrue(interfaceReturnType != null);

    final PsiSubstitutor substitutor = LambdaUtil.getSubstitutor(interfaceMethod, classResolveResult);
    ExpectedTypeInfo[] expectedTypes = {new ExpectedTypeInfoImpl(interfaceReturnType,
        ExpectedTypeInfo.TYPE_OR_SUBTYPE, interfaceReturnType, TailType.NONE, null,
        ExpectedTypeInfoImpl.NULL)};
    CreateMethodFromUsageFix.doCreate(targetClass, method, false, ContainerUtil.map2List(interfaceMethod
        .getParameterList().getParameters(), new Function<PsiParameter, Pair<PsiExpression, PsiType>>() {
      @Override
      public Pair<PsiExpression, PsiType> apply(PsiParameter parameter) {
        return Pair.create(null, substitutor.substitute(parameter.getType()));
      }
    }), PsiSubstitutor.EMPTY, expectedTypes, context);
  }


  @Override
  protected boolean isValidElement(PsiElement element) {
    return false;
  }

  @Nullable
  protected PsiMethodReferenceExpression getMethodReference() {
    return (PsiMethodReferenceExpression) myMethodReferenceExpression.getElement();
  }
}
