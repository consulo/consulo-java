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
import consulo.ide.impl.idea.refactoring.inline.InlineRefactoringActionHandler;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import jakarta.inject.Singleton;

@Singleton
@ServiceImpl
public class JavaRefactoringActionHandlerFactoryImpl extends JavaRefactoringActionHandlerFactory {
  public RefactoringActionHandler createAnonymousToInnerHandler() {
    return new AnonymousToInnerHandler();
  }

  public RefactoringActionHandler createPullUpHandler() {
    return new JavaPullUpHandler();
  }

  public RefactoringActionHandler createPushDownHandler() {
    return new JavaPushDownHandler();
  }

  public RefactoringActionHandler createTurnRefsToSuperHandler() {
    return new TurnRefsToSuperHandler();
  }

  public RefactoringActionHandler createTempWithQueryHandler() {
    return new TempWithQueryHandler();
  }

  public RefactoringActionHandler createIntroduceParameterHandler() {
    return new IntroduceParameterHandler();
  }

  public RefactoringActionHandler createMakeMethodStaticHandler() {
    return new MakeStaticHandler();
  }

  public RefactoringActionHandler createConvertToInstanceMethodHandler() {
    return new ConvertToInstanceMethodHandler();
  }

  public RefactoringActionHandler createReplaceConstructorWithFactoryHandler() {
    return new ReplaceConstructorWithFactoryHandler();
  }

  public RefactoringActionHandler createEncapsulateFieldsHandler() {
    return new EncapsulateFieldsHandler();
  }

  public RefactoringActionHandler createMethodDuplicatesHandler() {
    return new MethodDuplicatesHandler();
  }

  public RefactoringActionHandler createChangeSignatureHandler() {
    return new JavaChangeSignatureHandler();
  }

  public RefactoringActionHandler createExtractSuperclassHandler() {
    return new ExtractSuperclassHandler();
  }

  public RefactoringActionHandler createTypeCookHandler() {
    return new TypeCookHandler();
  }

  public RefactoringActionHandler createInlineHandler() {
    return new InlineRefactoringActionHandler();
  }

  public RefactoringActionHandler createExtractMethodHandler() {
    return new ExtractMethodHandler();
  }

  public RefactoringActionHandler createInheritanceToDelegationHandler() {
    return new InheritanceToDelegationHandler();
  }

  public RefactoringActionHandler createExtractInterfaceHandler() {
    return new ExtractInterfaceHandler();
  }

  public RefactoringActionHandler createIntroduceFieldHandler() {
    return new IntroduceFieldHandler();
  }

  public RefactoringActionHandler createIntroduceVariableHandler() {
    return new IntroduceVariableHandler();
  }

  public IntroduceConstantHandler createIntroduceConstantHandler() {
    return new IntroduceConstantHandlerImpl();
  }

  public RefactoringActionHandler createInvertBooleanHandler() {
    return new InvertBooleanHandler();
  }
}
