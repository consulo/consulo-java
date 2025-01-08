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

import com.intellij.java.impl.refactoring.*;
import com.intellij.java.impl.refactoring.move.moveClassesOrPackages.AutocreatingSingleSourceRootMoveDestination;
import com.intellij.java.impl.refactoring.move.moveClassesOrPackages.MultipleRootsMoveDestination;
import com.intellij.java.impl.refactoring.move.moveInner.MoveInnerImpl;
import com.intellij.java.language.psi.*;
import consulo.annotation.component.ServiceImpl;
import consulo.language.editor.refactoring.RenameRefactoring;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * @author dsl
 */
@Singleton
@ServiceImpl
public class JavaRefactoringFactoryImpl extends JavaRefactoringFactory {
  private static final Logger LOG = Logger.getInstance(JavaRefactoringFactoryImpl.class);
  private final Project myProject;

  @Inject
  public JavaRefactoringFactoryImpl(Project project) {
    myProject = project;
  }

  public JavaRenameRefactoring createRename(PsiElement element, String newName) {
    return new JavaRenameRefactoringImpl(myProject, element, newName, true, true);
  }

  @Override
  public RenameRefactoring createRename(PsiElement element, String newName, boolean searchInComments, boolean searchInNonJavaFiles) {
    return new JavaRenameRefactoringImpl(myProject, element, newName, searchInComments, searchInNonJavaFiles);
  }

  public MoveInnerRefactoring createMoveInner(PsiClass innerClass, String newName, boolean passOuterClass, String parameterName) {
    final PsiElement targetContainer = MoveInnerImpl.getTargetContainer(innerClass, false);
    if (targetContainer == null) return null;
    return new MoveInnerRefactoringImpl(myProject, innerClass, newName, passOuterClass, parameterName, targetContainer);
  }

  public MoveDestination createSourceFolderPreservingMoveDestination(@Nonnull String targetPackage) {
    return new MultipleRootsMoveDestination(createPackageWrapper(targetPackage));
  }

  private PackageWrapper createPackageWrapper(@Nonnull String targetPackage) {
    return new PackageWrapper(PsiManager.getInstance(myProject), targetPackage);
  }

  public MoveDestination createSourceRootMoveDestination(@Nonnull String targetPackageQualifiedName, @Nonnull VirtualFile sourceRoot) {
    final PsiDirectory directory = PsiManager.getInstance(myProject).findDirectory(sourceRoot);
    LOG.assertTrue(directory != null && JavaDirectoryService.getInstance().isSourceRoot(directory), "Should pass source root");
    return new AutocreatingSingleSourceRootMoveDestination(createPackageWrapper(targetPackageQualifiedName),
        sourceRoot);
  }


  public MoveClassesOrPackagesRefactoring createMoveClassesOrPackages(PsiElement[] elements, MoveDestination moveDestination) {
    return new MoveClassesOrPackagesRefactoringImpl(myProject, elements, moveDestination);
  }

  public MoveMembersRefactoring createMoveMembers(final PsiMember[] elements,
                                                  final String targetClassQualifiedName,
                                                  final String newVisibility) {
    return createMoveMembers(elements, targetClassQualifiedName, newVisibility, false);
  }

  public MoveMembersRefactoring createMoveMembers(final PsiMember[] elements,
                                                  final String targetClassQualifiedName,
                                                  final String newVisibility,
                                                  final boolean makeEnumConstants) {
    return new MoveMembersRefactoringImpl(myProject, elements, targetClassQualifiedName, newVisibility, makeEnumConstants);
  }

  public MakeStaticRefactoring<PsiMethod> createMakeMethodStatic(PsiMethod method,
                                                                 boolean replaceUsages,
                                                                 String classParameterName,
                                                                 PsiField[] fields,
                                                                 String[] names) {
    return new MakeMethodStaticRefactoringImpl(myProject, method, replaceUsages, classParameterName, fields, names);
  }

  public MakeStaticRefactoring<PsiClass> createMakeClassStatic(PsiClass aClass,
                                                               boolean replaceUsages,
                                                               String classParameterName,
                                                               PsiField[] fields,
                                                               String[] names) {
    return new MakeClassStaticRefactoringImpl(myProject, aClass, replaceUsages, classParameterName, fields, names);
  }

  public ConvertToInstanceMethodRefactoring createConvertToInstanceMethod(PsiMethod method,
                                                                          PsiParameter targetParameter) {
    return new ConvertToInstanceMethodRefactoringImpl(myProject, method, targetParameter);
  }

  public TurnRefsToSuperRefactoring createTurnRefsToSuper(PsiClass aClass,
                                                          PsiClass aSuper,
                                                          boolean replaceInstanceOf) {
    return new TurnRefsToSuperRefactoringImpl(myProject, aClass, aSuper, replaceInstanceOf);
  }

  public ReplaceConstructorWithFactoryRefactoring createReplaceConstructorWithFactory(PsiMethod method,
                                                                                      PsiClass targetClass,
                                                                                      String factoryName) {
    return new ReplaceConstructorWithFactoryRefactoringImpl(myProject, method, targetClass, factoryName);
  }

  public ReplaceConstructorWithFactoryRefactoring createReplaceConstructorWithFactory(PsiClass originalClass,
                                                                                      PsiClass targetClass,
                                                                                      String factoryName) {
    return new ReplaceConstructorWithFactoryRefactoringImpl(myProject, originalClass, targetClass, factoryName);
  }

  public TypeCookRefactoring createTypeCook(PsiElement[] elements,
                                            boolean dropObsoleteCasts,
                                            boolean leaveObjectsRaw,
                                            boolean preserveRawArrays,
                                            boolean exhaustive,
                                            boolean cookObjects,
                                            boolean cookToWildcards) {
    return new TypeCookRefactoringImpl(myProject, elements, dropObsoleteCasts, leaveObjectsRaw, preserveRawArrays, exhaustive, cookObjects, cookToWildcards);
  }

  public IntroduceParameterRefactoring createIntroduceParameterRefactoring(PsiMethod methodToReplaceIn,
                                                                           PsiMethod methodToSearchFor,
                                                                           String parameterName, PsiExpression parameterInitializer,
                                                                           PsiLocalVariable localVariable,
                                                                           boolean removeLocalVariable, boolean declareFinal) {
    return new IntroduceParameterRefactoringImpl(myProject, methodToReplaceIn, methodToSearchFor, parameterName, parameterInitializer,
        localVariable, removeLocalVariable, declareFinal);
  }

  public IntroduceParameterRefactoring createIntroduceParameterRefactoring(PsiMethod methodToReplaceIn,
                                                                           PsiMethod methodToSearchFor,
                                                                           String parameterName, PsiExpression parameterInitializer,
                                                                           PsiExpression expressionToSearchFor,
                                                                           boolean declareFinal, final boolean replaceAllOccurences) {
    return new IntroduceParameterRefactoringImpl(myProject, methodToReplaceIn, methodToSearchFor, parameterName, parameterInitializer,
        expressionToSearchFor, declareFinal, replaceAllOccurences);
  }
}
