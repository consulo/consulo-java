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

/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 18.06.2002
 * Time: 12:45:30
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.java.impl.refactoring.memberPullUp;

import java.util.ArrayList;
import java.util.List;

import consulo.application.Application;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

import com.intellij.java.language.psi.*;
import consulo.dataContext.DataContext;
import consulo.codeEditor.Editor;
import consulo.codeEditor.ScrollType;
import consulo.application.progress.ProgressManager;
import consulo.project.Project;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.language.psi.*;
import com.intellij.java.impl.refactoring.HelpID;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.classMember.MemberInfoBase;
import consulo.language.editor.refactoring.ElementsHandler;
import consulo.language.editor.refactoring.ui.ConflictsDialog;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import com.intellij.java.impl.refactoring.util.RefactoringHierarchyUtil;
import com.intellij.java.impl.refactoring.util.classMembers.MemberInfo;
import com.intellij.java.impl.refactoring.util.classMembers.MemberInfoStorage;
import consulo.util.collection.MultiMap;
import consulo.logging.Logger;

public class JavaPullUpHandler implements RefactoringActionHandler, PullUpDialog.Callback, ElementsHandler
{
	private static final Logger LOG = Logger.getInstance(JavaPullUpHandler.class);
	public static final String REFACTORING_NAME = RefactoringBundle.message("pull.members.up.title");
	private PsiClass mySubclass;
	private Project myProject;

	@Override
	public void invoke(@Nonnull Project project, Editor editor, PsiFile file, DataContext dataContext)
	{
		int offset = editor.getCaretModel().getOffset();
		editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
		PsiElement element = file.findElementAt(offset);

		while (true)
		{
			if (element == null || element instanceof PsiFile)
			{
				LocalizeValue message = RefactoringLocalize.cannotPerformRefactoringWithReason(
					RefactoringLocalize.theCaretShouldBePositionedInsideAClassToPullMembersFrom()
				);
				CommonRefactoringUtil.showErrorHint(project, editor, message.get(), REFACTORING_NAME, HelpID.MEMBERS_PULL_UP);
				return;
			}

			if (!CommonRefactoringUtil.checkReadOnlyStatus(project, element))
			{
				return;
			}

			if (element instanceof PsiClass || element instanceof PsiField || element instanceof PsiMethod)
			{
				invoke(project, new PsiElement[]{element}, dataContext);
				return;
			}
			element = element.getParent();
		}
	}

	@Override
	public void invoke(@Nonnull final Project project, @Nonnull PsiElement[] elements, DataContext dataContext)
	{
		if (elements.length != 1)
		{
			return;
		}
		myProject = project;

		PsiElement element = elements[0];
		PsiClass aClass;
		PsiElement aMember = null;

		if (element instanceof PsiClass psiClass)
		{
			aClass = psiClass;
		}
		else if (element instanceof PsiMethod method)
		{
			aClass = method.getContainingClass();
			aMember = element;
		}
		else if (element instanceof PsiField field)
		{
			aClass = field.getContainingClass();
			aMember = element;
		}
		else
		{
			return;
		}

		invoke(project, dataContext, aClass, aMember);
	}

	private void invoke(Project project, DataContext dataContext, PsiClass aClass, PsiElement aMember)
	{
		final Editor editor = dataContext != null ? dataContext.getData(Editor.KEY) : null;
		if (aClass == null)
		{
			LocalizeValue message = RefactoringLocalize.cannotPerformRefactoringWithReason(
				RefactoringLocalize.isNotSupportedInTheCurrentContext(REFACTORING_NAME)
			);
			CommonRefactoringUtil.showErrorHint(project, editor, message.get(), REFACTORING_NAME, HelpID.MEMBERS_PULL_UP);
			return;
		}

		ArrayList<PsiClass> bases = RefactoringHierarchyUtil.createBasesList(aClass, false, true);

		if (bases.isEmpty())
		{
			final PsiClass containingClass = aClass.getContainingClass();
			if (containingClass != null)
			{
				invoke(project, dataContext, containingClass, aClass);
				return;
			}
			LocalizeValue message = RefactoringLocalize.cannotPerformRefactoringWithReason(
				RefactoringLocalize.classDoesNotHaveBaseClassesInterfacesInCurrentProject(aClass.getQualifiedName())
			);
			CommonRefactoringUtil.showErrorHint(project, editor, message.get(), REFACTORING_NAME, HelpID.MEMBERS_PULL_UP);
			return;
		}


		mySubclass = aClass;
		MemberInfoStorage memberInfoStorage = new MemberInfoStorage(mySubclass, element -> true);
		List<MemberInfo> members = memberInfoStorage.getClassMemberInfos(mySubclass);
		PsiManager manager = mySubclass.getManager();

		for (MemberInfoBase<PsiMember> member : members)
		{
			if (manager.areElementsEquivalent(member.getMember(), aMember))
			{
				member.setChecked(true);
				break;
			}
		}

		final PullUpDialog dialog = new PullUpDialog(project, aClass, bases, memberInfoStorage, this);

		dialog.show();
	}

	@Override
	public boolean checkConflicts(final PullUpDialog dialog)
	{
		final List<MemberInfo> infos = dialog.getSelectedMemberInfos();
		final MemberInfo[] memberInfos = infos.toArray(new MemberInfo[infos.size()]);
		final PsiClass superClass = dialog.getSuperClass();
		if (!checkWritable(superClass, memberInfos))
		{
			return false;
		}
		final MultiMap<PsiElement, String> conflicts = new MultiMap<>();
		if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(
			() -> Application.get().runReadAction(() -> {
				final PsiDirectory targetDirectory = superClass.getContainingFile().getContainingDirectory();
				final PsiJavaPackage targetPackage =
					targetDirectory != null ? JavaDirectoryService.getInstance().getPackage(targetDirectory) : null;
				conflicts.putAllValues(PullUpConflictsUtil.checkConflicts(
					memberInfos,
					mySubclass,
					superClass,
					targetPackage,
					targetDirectory,
					dialog.getContainmentVerifier()
				));
			}),
			RefactoringLocalize.detectingPossibleConflicts().get(),
			true,
			myProject
		))
		{
			return false;
		}
		if (!conflicts.isEmpty())
		{
			ConflictsDialog conflictsDialog = new ConflictsDialog(myProject, conflicts);
			conflictsDialog.show();
			final boolean ok = conflictsDialog.isOK();
			if (!ok && conflictsDialog.isShowConflicts())
			{
				dialog.close(DialogWrapper.CANCEL_EXIT_CODE);
			}
			return ok;
		}
		return true;
	}

	private boolean checkWritable(PsiClass superClass, MemberInfo[] infos)
	{
		if (!CommonRefactoringUtil.checkReadOnlyStatus(myProject, superClass))
		{
			return false;
		}
		for (MemberInfo info : infos)
		{
			if (info.getMember() instanceof PsiClass && info.getOverrides() != null)
			{
				continue;
			}
			if (!CommonRefactoringUtil.checkReadOnlyStatus(myProject, info.getMember()))
			{
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean isEnabledOnElements(PsiElement[] elements)
	{
    /*
    if (elements.length == 1) {
      return elements[0] instanceof PsiClass || elements[0] instanceof PsiField || elements[0] instanceof PsiMethod;
    }
    else if (elements.length > 1){
      for (int  idx = 0;  idx < elements.length;  idx++) {
        PsiElement element = elements[idx];
        if (!(element instanceof PsiField || element instanceof PsiMethod)) return false;
      }
      return true;
    }
    return false;
    */
		// todo: multiple selection etc
		return elements.length == 1 && elements[0] instanceof PsiClass;
	}
}
