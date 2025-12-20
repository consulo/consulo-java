// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.intellij.java.impl.refactoring.typeMigration;

import com.intellij.java.impl.refactoring.typeMigration.ui.TypeMigrationDialog;
import com.intellij.java.language.psi.*;
import consulo.codeEditor.Editor;
import consulo.codeEditor.ScrollType;
import consulo.dataContext.DataContext;
import consulo.language.editor.TargetElementUtil;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.logging.Logger;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ChangeTypeSignatureHandler implements RefactoringActionHandler
{
	private static final Logger LOG = Logger.getInstance(ChangeTypeSignatureHandler.class);

	public static final String REFACTORING_NAME = "Type Migration";

	@Override
	public void invoke(@Nonnull Project project, Editor editor, PsiFile file, DataContext dataContext)
	{
		editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
		int offset = TargetElementUtil.adjustOffset(file, editor.getDocument(), editor.getCaretModel().getOffset());
		PsiElement element = file.findElementAt(offset);
		PsiTypeElement typeElement = PsiTreeUtil.getParentOfType(element, PsiTypeElement.class);
		while(typeElement != null)
		{
			PsiElement parent = typeElement.getParent();
			PsiElement[] toMigrate = null;
			if(parent instanceof PsiVariable)
			{
				toMigrate = extractReferencedVariables(typeElement);
			}
			else if((parent instanceof PsiMember && !(parent instanceof PsiClass)) || isClassArgument(parent))
			{
				toMigrate = new PsiElement[]{parent};
			}
			if(toMigrate != null && toMigrate.length > 0)
			{
				invoke(project, toMigrate, editor);
				return;
			}
			typeElement = PsiTreeUtil.getParentOfType(parent, PsiTypeElement.class, false);
		}
		CommonRefactoringUtil.showErrorHint(project, editor, "The caret should be positioned on type of field, variable, method or method parameter to be refactored", REFACTORING_NAME, "refactoring" +
				".migrateType");
	}

	@Override
	public void invoke(@Nonnull Project project, @Nonnull PsiElement[] elements, DataContext dataContext)
	{
		LOG.assertTrue(elements.length == 1);
		PsiElement element = elements[0];
		invokeOnElement(project, element);
	}

	private static void invokeOnElement(Project project, PsiElement element)
	{
		if(element instanceof PsiVariable || (element instanceof PsiMember && !(element instanceof PsiClass)) || element instanceof PsiFile || isClassArgument(element))
		{
			invoke(project, new PsiElement[]{element}, (Editor) null);
		}
	}

	private static boolean isClassArgument(PsiElement element)
	{
		if(element instanceof PsiReferenceParameterList)
		{
			PsiMember member = PsiTreeUtil.getParentOfType(element, PsiMember.class);
			if(member instanceof PsiAnonymousClass)
			{
				return ((PsiAnonymousClass) member).getBaseClassReference().getParameterList() == element;
			}
			if(member instanceof PsiClass)
			{
				PsiReferenceList implementsList = ((PsiClass) member).getImplementsList();
				PsiReferenceList extendsList = ((PsiClass) member).getExtendsList();
				return PsiTreeUtil.isAncestor(implementsList, element, false) || PsiTreeUtil.isAncestor(extendsList, element, false);
			}
		}
		return false;
	}

	private static void invoke(@Nonnull Project project, @Nonnull PsiElement[] roots, @Nullable Editor editor)
	{
		if(Util.canBeMigrated(roots))
		{
			TypeMigrationDialog dialog = new TypeMigrationDialog.SingleElement(project, roots);
			dialog.show();
			return;
		}

		CommonRefactoringUtil.showErrorHint(
			project,
			editor,
			RefactoringLocalize.onlyFieldsVariablesOfMethodsOfValidTypeCanBeConsidered().get(),
			RefactoringLocalize.unableToStartTypeMigration().get(),
			null
		);
	}

	@Nonnull
	private static PsiElement[] extractReferencedVariables(@Nonnull PsiTypeElement typeElement)
	{
		PsiElement parent = typeElement.getParent();
		if(parent instanceof PsiVariable)
		{
			if(parent instanceof PsiField)
			{
				PsiField aField = (PsiField) parent;
				List<PsiField> fields = new ArrayList<>();
				while(true)
				{
					fields.add(aField);
					aField = PsiTreeUtil.getNextSiblingOfType(aField, PsiField.class);
					if(aField == null || aField.getTypeElement() != typeElement)
					{
						return fields.toArray(PsiElement.EMPTY_ARRAY);
					}
				}
			}
			else if(parent instanceof PsiLocalVariable)
			{
				PsiDeclarationStatement declaration = PsiTreeUtil.getParentOfType(parent, PsiDeclarationStatement.class);
				if(declaration != null)
				{
					return Arrays.stream(declaration.getDeclaredElements()).filter(PsiVariable.class::isInstance).toArray(PsiVariable[]::new);
				}
			}
			return new PsiElement[]{parent};
		}
		else
		{
			return PsiElement.EMPTY_ARRAY;
		}
	}
}