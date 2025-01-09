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
package com.intellij.java.impl.refactoring.openapi.impl;

import com.intellij.java.analysis.refactoring.IntroduceConstantHandler;
import com.intellij.java.analysis.refactoring.JavaRefactoringActionHandlerFactory;
import com.intellij.java.impl.refactoring.anonymousToInner.AnonymousToInnerHandler;
import com.intellij.java.impl.refactoring.changeSignature.JavaChangeSignatureHandler;
import com.intellij.java.impl.refactoring.convertToInstanceMethod.ConvertToInstanceMethodHandler;
import com.intellij.java.impl.refactoring.encapsulateFields.EncapsulateFieldsHandler;
import com.intellij.java.impl.refactoring.extractInterface.ExtractInterfaceHandler;
import com.intellij.java.impl.refactoring.extractMethod.ExtractMethodHandler;
import com.intellij.java.impl.refactoring.extractSuperclass.ExtractSuperclassHandler;
import com.intellij.java.impl.refactoring.inheritanceToDelegation.InheritanceToDelegationHandler;
import com.intellij.java.impl.refactoring.introduceField.IntroduceConstantHandlerImpl;
import com.intellij.java.impl.refactoring.introduceField.IntroduceFieldHandler;
import com.intellij.java.impl.refactoring.introduceParameter.IntroduceParameterHandler;
import com.intellij.java.impl.refactoring.introduceVariable.IntroduceVariableHandler;
import com.intellij.java.impl.refactoring.invertBoolean.InvertBooleanHandler;
import com.intellij.java.impl.refactoring.makeStatic.MakeStaticHandler;
import com.intellij.java.impl.refactoring.memberPullUp.JavaPullUpHandler;
import com.intellij.java.impl.refactoring.memberPushDown.JavaPushDownHandler;
import com.intellij.java.impl.refactoring.replaceConstructorWithFactory.ReplaceConstructorWithFactoryHandler;
import com.intellij.java.impl.refactoring.tempWithQuery.TempWithQueryHandler;
import com.intellij.java.impl.refactoring.turnRefsToSuper.TurnRefsToSuperHandler;
import com.intellij.java.impl.refactoring.typeCook.TypeCookHandler;
import com.intellij.java.impl.refactoring.util.duplicates.MethodDuplicatesHandler;
import consulo.annotation.component.ServiceImpl;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.editor.refactoring.action.RefactoringActionHandlerFactory;
import jakarta.inject.Singleton;

@Singleton
@ServiceImpl
public class JavaRefactoringActionHandlerFactoryImpl extends JavaRefactoringActionHandlerFactory {
  @Override
  public RefactoringActionHandler createAnonymousToInnerHandler() {
    return new AnonymousToInnerHandler();
  }

  @Override
  public RefactoringActionHandler createPullUpHandler() {
    return new JavaPullUpHandler();
  }

  @Override
  public RefactoringActionHandler createPushDownHandler() {
    return new JavaPushDownHandler();
  }

  @Override
  public RefactoringActionHandler createTurnRefsToSuperHandler() {
    return new TurnRefsToSuperHandler();
  }

  @Override
  public RefactoringActionHandler createTempWithQueryHandler() {
    return new TempWithQueryHandler();
  }

  @Override
  public RefactoringActionHandler createIntroduceParameterHandler() {
    return new IntroduceParameterHandler();
  }

  @Override
  public RefactoringActionHandler createMakeMethodStaticHandler() {
    return new MakeStaticHandler();
  }

  @Override
  public RefactoringActionHandler createConvertToInstanceMethodHandler() {
    return new ConvertToInstanceMethodHandler();
  }

  @Override
  public RefactoringActionHandler createReplaceConstructorWithFactoryHandler() {
    return new ReplaceConstructorWithFactoryHandler();
  }

  @Override
  public RefactoringActionHandler createEncapsulateFieldsHandler() {
    return new EncapsulateFieldsHandler();
  }

  @Override
  public RefactoringActionHandler createMethodDuplicatesHandler() {
    return new MethodDuplicatesHandler();
  }

  @Override
  public RefactoringActionHandler createChangeSignatureHandler() {
    return new JavaChangeSignatureHandler();
  }

  @Override
  public RefactoringActionHandler createExtractSuperclassHandler() {
    return new ExtractSuperclassHandler();
  }

  @Override
  public RefactoringActionHandler createTypeCookHandler() {
    return new TypeCookHandler();
  }

  @Override
  public RefactoringActionHandler createInlineHandler() {
    return RefactoringActionHandlerFactory.getInstance().createInlineHandler();
  }

  @Override
  public RefactoringActionHandler createExtractMethodHandler() {
    return new ExtractMethodHandler();
  }

  @Override
  public RefactoringActionHandler createInheritanceToDelegationHandler() {
    return new InheritanceToDelegationHandler();
  }

  @Override
  public RefactoringActionHandler createExtractInterfaceHandler() {
    return new ExtractInterfaceHandler();
  }

  @Override
  public RefactoringActionHandler createIntroduceFieldHandler() {
    return new IntroduceFieldHandler();
  }

  @Override
  public RefactoringActionHandler createIntroduceVariableHandler() {
    return new IntroduceVariableHandler();
  }

  @Override
  public IntroduceConstantHandler createIntroduceConstantHandler() {
    return new IntroduceConstantHandlerImpl();
  }

  @Override
  public RefactoringActionHandler createInvertBooleanHandler() {
    return new InvertBooleanHandler();
  }
}
