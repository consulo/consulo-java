/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.extractSuperclass;

import com.intellij.java.impl.refactoring.turnRefsToSuper.TurnRefsToSuperProcessorBase;
import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.impl.refactoring.util.classMembers.MemberInfo;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.MethodSignatureUtil;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.ui.RefactoringUIUtil;
import consulo.language.editor.ui.util.DocCommentPolicy;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.usage.UsageInfo;
import consulo.usage.UsageViewDescriptor;
import consulo.usage.UsageViewUtil;
import consulo.util.lang.Comparing;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author dsl
 */
public abstract class ExtractSuperBaseProcessor extends TurnRefsToSuperProcessorBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.extractSuperclass.ExtractSuperClassProcessor");
  protected PsiDirectory myTargetDirectory;
  protected final String myNewClassName;
  protected final MemberInfo[] myMemberInfos;
  protected final DocCommentPolicy myJavaDocPolicy;


  public ExtractSuperBaseProcessor(Project project,
                                   boolean replaceInstanceOf,
                                   PsiDirectory targetDirectory,
                                   String newClassName,
                                   PsiClass aClass, MemberInfo[] memberInfos, DocCommentPolicy javaDocPolicy) {
    super(project, replaceInstanceOf, newClassName);
    myTargetDirectory = targetDirectory;
    myNewClassName = newClassName;
    myClass = aClass;
    myMemberInfos = memberInfos;
    myJavaDocPolicy = javaDocPolicy;
  }

  @Nonnull
  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    return new ExtractSuperClassViewDescriptor(myTargetDirectory, myClass, myMemberInfos);
  }

  protected boolean doesAnyExtractedInterfaceExtends(PsiClass aClass) {
    for (MemberInfo memberInfo : myMemberInfos) {
      PsiElement member = memberInfo.getMember();
      if (member instanceof PsiClass && memberInfo.getOverrides() != null) {
        if (InheritanceUtil.isInheritorOrSelf((PsiClass)member, aClass, true)) {
          return true;
        }
      }
    }
    return false;
  }

  protected boolean doMemberInfosContain(PsiMethod method) {
    for (MemberInfo info : myMemberInfos) {
      if (info.getMember() instanceof PsiMethod) {
        if (MethodSignatureUtil.areSignaturesEqual(method, (PsiMethod)info.getMember())) return true;
      }
      else if (info.getMember() instanceof PsiClass && info.getOverrides() != null) {
        PsiMethod methodBySignature = ((PsiClass)info.getMember()).findMethodBySignature(method, true);
        if (methodBySignature != null) {
          return true;
        }
      }
    }
    return false;
  }

  protected boolean doMemberInfosContain(PsiField field) {
    for (MemberInfo info : myMemberInfos) {
      if (myManager.areElementsEquivalent(field, info.getMember())) return true;
    }
    return false;
  }

  @Nonnull
  protected UsageInfo[] findUsages() {
    PsiReference[] refs = ReferencesSearch.search(myClass, GlobalSearchScope.projectScope(myProject), false).toArray(new PsiReference[0]);
    ArrayList<UsageInfo> result = new ArrayList<UsageInfo>();
    detectTurnToSuperRefs(refs, result);
    PsiJavaPackage originalPackage = JavaDirectoryService.getInstance().getPackage(myClass.getContainingFile().getContainingDirectory());
    if (Comparing.equal(JavaDirectoryService.getInstance().getPackage(myTargetDirectory), originalPackage)) {
      result.clear();
    }
    for (PsiReference ref : refs) {
      PsiElement element = ref.getElement();
      if (!canTurnToSuper(element) && !RefactoringUtil.inImportStatement(ref, element)) {
        result.add(new BindToOldUsageInfo(element, ref, myClass));
      }
    }
    UsageInfo[] usageInfos = result.toArray(new UsageInfo[result.size()]);
    return UsageViewUtil.removeDuplicatedUsages(usageInfos);
  }

  protected void performRefactoring(UsageInfo[] usages) {
    try {
      String superClassName = myClass.getName();
      String oldQualifiedName = myClass.getQualifiedName();
      myClass.setName(myNewClassName);
      PsiClass superClass = extractSuper(superClassName);
      PsiDirectory initialDirectory = myClass.getContainingFile().getContainingDirectory();
      try {
        if (myTargetDirectory != initialDirectory) {
          myTargetDirectory.add(myClass.getContainingFile().copy());
          myClass.getContainingFile().delete();
        }
      }
      catch (IncorrectOperationException e) {
        RefactoringUIUtil.processIncorrectOperation(myProject, e);
      }
      for (UsageInfo usage : usages) {
        if (usage instanceof BindToOldUsageInfo) {
          PsiReference reference = usage.getReference();
          if (reference != null && reference.getElement().isValid()) {
            reference.bindToElement(myClass);
          }
        }
      }
      if (!Comparing.equal(oldQualifiedName, superClass.getQualifiedName())) {
        processTurnToSuperRefs(usages, superClass);
      }
      PsiFile containingFile = myClass.getContainingFile();
      if (containingFile instanceof PsiJavaFile) {
        JavaCodeStyleManager.getInstance(myProject).removeRedundantImports((PsiJavaFile) containingFile);
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }

    performVariablesRenaming();
  }

  protected abstract PsiClass extractSuper(String superClassName) throws IncorrectOperationException;

  protected void refreshElements(PsiElement[] elements) {
    myClass = (PsiClass)elements[0];
    myTargetDirectory = (PsiDirectory)elements[1];
    for (int i = 0; i < myMemberInfos.length; i++) {
      MemberInfo info = myMemberInfos[i];
      info.updateMember((PsiMember)elements[i + 2]);
    }
  }

  protected String getCommandName() {
    return RefactoringLocalize.extractSubclassCommand().get();
  }

  @Nonnull
  @Override
  protected Collection<? extends PsiElement> getElementsToWrite(@Nonnull UsageViewDescriptor descriptor) {
    return ((ExtractSuperClassViewDescriptor) descriptor).getMembersToMakeWritable();
  }
}
