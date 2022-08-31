/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.java.language.impl.core;

import com.intellij.core.CoreApplicationEnvironment;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.impl.JarArchiveFileType;
import com.intellij.java.language.impl.JavaClassFileType;
import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.language.impl.JavaParserDefinition;
import com.intellij.java.language.impl.psi.ClassFileViewProviderFactory;
import com.intellij.java.language.impl.psi.impl.LanguageConstantExpressionEvaluator;
import com.intellij.java.language.impl.psi.impl.PsiExpressionEvaluator;
import com.intellij.java.language.impl.psi.impl.compiled.ClassFileStubBuilder;
import com.intellij.java.language.impl.psi.impl.file.PsiPackageImplementationHelper;
import com.intellij.java.language.impl.psi.presentation.java.*;
import com.intellij.java.language.projectRoots.JavaVersionService;
import com.intellij.java.language.psi.*;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.navigation.ItemPresentationProviders;
import com.intellij.psi.FileTypeFileViewProviders;
import com.intellij.psi.stubs.BinaryFileStubBuilders;
import consulo.disposer.Disposable;

/**
 * @author yole
 */
public class JavaCoreApplicationEnvironment extends CoreApplicationEnvironment {
  public JavaCoreApplicationEnvironment(Disposable parentDisposable) {
    super(parentDisposable);

    registerFileType(JavaClassFileType.INSTANCE, "class");
    registerFileType(JavaFileType.INSTANCE, "java");
    registerFileType(JarArchiveFileType.INSTANCE, "jar");

    addExplicitExtension(FileTypeFileViewProviders.INSTANCE, JavaClassFileType.INSTANCE, new ClassFileViewProviderFactory());
    addExplicitExtension(BinaryFileStubBuilders.INSTANCE, JavaClassFileType.INSTANCE, new ClassFileStubBuilder());

    addExplicitExtension(LanguageParserDefinitions.INSTANCE, JavaLanguage.INSTANCE, new JavaParserDefinition());
    addExplicitExtension(LanguageConstantExpressionEvaluator.INSTANCE, JavaLanguage.INSTANCE, new PsiExpressionEvaluator());

    //registerExtensionPoint(Extensions.getRootArea(), PsiAugmentProvider.EP_NAME, PsiAugmentProvider.class);
    //registerExtensionPoint(Extensions.getRootArea(), JavaMainMethodProvider.EP_NAME, JavaMainMethodProvider.class);

    myApplication.registerService(PsiPackageImplementationHelper.class, new CorePsiPackageImplementationHelper());

    //myApplication.registerService(EmptySubstitutor.class, new EmptySubstitutorImpl());
    myApplication.registerService(JavaDirectoryService.class, createJavaDirectoryService());
    myApplication.registerService(JavaVersionService.class, new JavaVersionService());

    addExplicitExtension(ItemPresentationProviders.INSTANCE, PsiJavaPackage.class, new PackagePresentationProvider());
    addExplicitExtension(ItemPresentationProviders.INSTANCE, PsiClass.class, new ClassPresentationProvider());
    addExplicitExtension(ItemPresentationProviders.INSTANCE, PsiMethod.class, new MethodPresentationProvider());
    addExplicitExtension(ItemPresentationProviders.INSTANCE, PsiField.class, new FieldPresentationProvider());
    addExplicitExtension(ItemPresentationProviders.INSTANCE, PsiLocalVariable.class, new VariablePresentationProvider());
    addExplicitExtension(ItemPresentationProviders.INSTANCE, PsiParameter.class, new VariablePresentationProvider());
  }

  protected CoreJavaDirectoryService createJavaDirectoryService() {
    return new CoreJavaDirectoryService();
  }
}
