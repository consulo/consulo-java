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
package com.intellij.java.language.impl.psi.impl.java.stubs;

import com.intellij.java.language.impl.psi.impl.compiled.*;
import com.intellij.java.language.psi.*;

/**
 * @author max
 */
public class ClsStubPsiFactory extends StubPsiFactory {
  public static final ClsStubPsiFactory INSTANCE = new ClsStubPsiFactory();

  @Override
  public PsiClass createClass(PsiClassStub stub) {
    return new ClsClassImpl(stub);
  }

  @Override
  public PsiAnnotation createAnnotation(PsiAnnotationStub stub) {
    return new ClsAnnotationImpl(stub);
  }

  @Override
  public PsiClassInitializer createClassInitializer(PsiClassInitializerStub stub) {
    throw new UnsupportedOperationException();
  }

  @Override
  public PsiReferenceList createClassReferenceList(PsiClassReferenceListStub stub) {
    return new ClsReferenceListImpl(stub);
  }

  @Override
  public PsiField createField(PsiFieldStub stub) {
    return stub.isEnumConstant() ? new ClsEnumConstantImpl(stub) : new ClsFieldImpl(stub);
  }

  @Override
  public PsiImportList createImportList(PsiImportListStub stub) {
    throw new UnsupportedOperationException();
  }

  @Override
  public PsiImportStatementBase createImportStatement(PsiImportStatementStub stub) {
    throw new UnsupportedOperationException();
  }

  @Override
  public PsiMethod createMethod(PsiMethodStub stub) {
    return new ClsMethodImpl(stub);
  }

  @Override
  public PsiModifierList createModifierList(PsiModifierListStub stub) {
    return new ClsModifierListImpl(stub);
  }

  @Override
  public PsiParameter createParameter(PsiParameterStub stub) {
    return new ClsParameterImpl(stub);
  }

  @Override
  public PsiParameterList createParameterList(PsiParameterListStub stub) {
    return new ClsParameterListImpl(stub);
  }

  @Override
  public PsiTypeParameter createTypeParameter(PsiTypeParameterStub stub) {
    return new ClsTypeParameterImpl(stub);
  }

  @Override
  public PsiTypeParameterList createTypeParameterList(PsiTypeParameterListStub stub) {
    return new ClsTypeParametersListImpl(stub);
  }

  @Override
  public PsiAnnotationParameterList createAnnotationParameterList(PsiAnnotationParameterListStub stub) {
    return null; // todo
  }

  @Override
  public PsiNameValuePair createNameValuePair(PsiNameValuePairStub stub) {
    return null; // todo
  }

  @Override
  public PsiJavaModule createModule(PsiJavaModuleStub stub) {
    return new ClsJavaModuleImpl(stub);
  }

  @Override
  public PsiRequiresStatement createRequiresStatement(PsiRequiresStatementStub stub) {
    return new ClsRequiresStatementImpl(stub);
  }

  @Override
  public PsiPackageAccessibilityStatement createPackageAccessibilityStatement(PsiPackageAccessibilityStatementStub stub) {
    return new ClsPackageAccessibilityStatementImpl(stub);
  }

  @Override
  public PsiUsesStatement createUsesStatement(PsiUsesStatementStub stub) {
    return new ClsUsesStatementImpl(stub);
  }

  @Override
  public PsiProvidesStatement createProvidesStatement(PsiProvidesStatementStub stub) {
    return new ClsProvidesStatementImpl(stub);
  }

  @Override
  public PsiRecordComponent createRecordComponent(PsiRecordComponentStub stub) {
    return new ClsRecordComponentImpl(stub);
  }

  @Override
  public PsiRecordHeader createRecordHeader(PsiRecordHeaderStub stub) {
    return new ClsRecordHeaderImpl(stub);
  }
}