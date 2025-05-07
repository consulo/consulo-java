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

/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 06.08.2002
 * Time: 17:17:27
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.java.impl.refactoring.inheritanceToDelegation;

import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.impl.refactoring.util.RefactoringHierarchyUtil;
import com.intellij.java.impl.refactoring.util.classMembers.MemberInfo;
import com.intellij.java.impl.refactoring.util.classMembers.MemberInfoStorage;
import com.intellij.java.language.psi.*;
import consulo.codeEditor.Editor;
import consulo.codeEditor.ScrollType;
import consulo.dataContext.DataContext;
import consulo.java.language.module.util.JavaClassNames;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

public class InheritanceToDelegationHandler implements RefactoringActionHandler {
  private static final Logger LOG = Logger.getInstance(InheritanceToDelegationHandler.class);
  public static final String REFACTORING_NAME = RefactoringLocalize.replaceInheritanceWithDelegationTitle().get();

  private static final MemberInfo.Filter<PsiMember> MEMBER_INFO_FILTER = element -> {
    if (element instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)element;
      return !method.hasModifierProperty(PsiModifier.STATIC) && !method.hasModifierProperty(PsiModifier.PRIVATE);
    }
    else if (element instanceof PsiClass && ((PsiClass)element).isInterface()) {
      return true;
    }
    return false;
  };


  public void invoke(@Nonnull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    while (true) {
      if (element == null || element instanceof PsiFile) {
        String message = RefactoringBundle.getCannotRefactorMessage(RefactoringLocalize.errorWrongCaretPositionClass().get());
        CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.INHERITANCE_TO_DELEGATION);
        return;
      }

      if (element instanceof PsiClass && !(element instanceof PsiAnonymousClass)) {
        invoke(project, new PsiElement[]{element}, dataContext);
        return;
      }
      element = element.getParent();
    }
  }

  public void invoke(@Nonnull Project project, @Nonnull PsiElement[] elements, DataContext dataContext) {
    if (elements.length != 1) return;

    final PsiClass aClass = (PsiClass)elements[0];

    Editor editor = dataContext.getData(Editor.KEY);
    if (aClass.isInterface()) {
      LocalizeValue message =
          RefactoringLocalize.cannotPerformRefactoringWithReason(RefactoringLocalize.classIsInterface(aClass.getQualifiedName()));
      CommonRefactoringUtil.showErrorHint(project, editor, message.get(), REFACTORING_NAME, HelpID.INHERITANCE_TO_DELEGATION);
      return;
    }

 /*   if (aClass instanceof JspClass) {
      RefactoringMessageUtil.showNotSupportedForJspClassesError(project, editor, REFACTORING_NAME, HelpID.INHERITANCE_TO_DELEGATION);
      return;
    }    */

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, aClass)) return;

    final PsiClass[] bases = aClass.getSupers();
    @NonNls final String javaLangObject = JavaClassNames.JAVA_LANG_OBJECT;
    if (bases.length == 0 || bases.length == 1 && javaLangObject.equals(bases[0].getQualifiedName())) {
      LocalizeValue message = RefactoringLocalize.cannotPerformRefactoringWithReason(
        RefactoringLocalize.classDoesNotHaveBaseClassesOrInterfaces(aClass.getQualifiedName())
      );
      CommonRefactoringUtil.showErrorHint(project, editor, message.get(), REFACTORING_NAME, HelpID.INHERITANCE_TO_DELEGATION);
      return;
    }

    final HashMap<PsiClass, Collection<MemberInfo>> basesToMemberInfos = new HashMap<>();

    for (PsiClass base : bases) {
      basesToMemberInfos.put(base, createBaseClassMemberInfos(base));
    }

    new InheritanceToDelegationDialog(project, aClass, bases, basesToMemberInfos).show();
  }

  private static List<MemberInfo> createBaseClassMemberInfos(PsiClass baseClass) {
    final PsiClass deepestBase = RefactoringHierarchyUtil.getDeepestNonObjectBase(baseClass);
    LOG.assertTrue(deepestBase != null);

    final MemberInfoStorage memberInfoStorage = new MemberInfoStorage(baseClass, MEMBER_INFO_FILTER);

    ArrayList<MemberInfo> memberInfoList = new ArrayList<>(memberInfoStorage.getClassMemberInfos(deepestBase));
    List<MemberInfo> memberInfos = memberInfoStorage.getIntermediateMemberInfosList(deepestBase);
    for (final MemberInfo memberInfo : memberInfos) {
      memberInfoList.add(memberInfo);
    }

    return memberInfoList;
  }
}
