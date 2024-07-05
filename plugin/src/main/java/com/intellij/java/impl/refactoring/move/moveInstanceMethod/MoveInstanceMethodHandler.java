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
package com.intellij.java.impl.refactoring.move.moveInstanceMethod;

import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.impl.refactoring.makeStatic.MakeStaticHandler;
import com.intellij.java.impl.refactoring.move.MoveInstanceMembersUtil;
import com.intellij.java.indexing.search.searches.OverridingMethodsSearch;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.codeStyle.VariableKind;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.codeEditor.Editor;
import consulo.codeEditor.ScrollType;
import consulo.dataContext.DataContext;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.rename.SuggestedNameInfo;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.util.collection.ContainerUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;

/**
 * @author ven
 */
public class MoveInstanceMethodHandler implements RefactoringActionHandler
{
	private static final Logger LOG = Logger.getInstance(MoveInstanceMethodHandler.class);
	static final String REFACTORING_NAME = RefactoringBundle.message("move.instance.method.title");

	@RequiredUIAccess
	public void invoke(@Nonnull Project project, Editor editor, PsiFile file, DataContext dataContext)
	{
		PsiElement element = dataContext.getData(PsiElement.KEY);
		editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
		if (element == null)
		{
			element = file.findElementAt(editor.getCaretModel().getOffset());
		}

		if (element == null)
		{
			return;
		}
		if (element instanceof PsiIdentifier)
		{
			element = element.getParent();
		}

		if (!(element instanceof PsiMethod))
		{
			String message = RefactoringBundle.getCannotRefactorMessage(RefactoringLocalize.errorWrongCaretPositionMethod().get());
			CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.MOVE_INSTANCE_METHOD);
			return;
		}
		if (LOG.isDebugEnabled())
		{
			LOG.debug("Move Instance Method invoked");
		}
		invoke(project, new PsiElement[]{element}, dataContext);
	}

	@RequiredUIAccess
	public void invoke(@Nonnull final Project project, @Nonnull final PsiElement[] elements, final DataContext dataContext)
	{
		if (elements.length != 1 || !(elements[0] instanceof PsiMethod))
		{
			return;
		}
		final PsiMethod method = (PsiMethod) elements[0];
		String message = null;
		if (!method.getManager().isInProject(method))
		{
			message = "Move method is not supported for non-project methods";
		}
		else if (method.isConstructor())
		{
			message = RefactoringLocalize.moveMethodIsNotSupportedForConstructors().get();
		}
		else if (method.getLanguage() != JavaLanguage.INSTANCE)
		{
			message = RefactoringLocalize.moveMethodIsNotSupportedFor0(method.getLanguage().getDisplayName()).get();
		}
		else
		{
			final PsiClass containingClass = method.getContainingClass();
			if (containingClass != null && PsiUtil.typeParametersIterator(containingClass).hasNext()
				&& TypeParametersSearcher.hasTypeParameters(method))
			{
				message = RefactoringLocalize.moveMethodIsNotSupportedForGenericClasses().get();
			}
			else if (method.findSuperMethods().length > 0
				|| OverridingMethodsSearch.search(method, true).toArray(PsiMethod.EMPTY_ARRAY).length > 0)
			{
				message = RefactoringLocalize.moveMethodIsNotSupportedWhenMethodIsPartOfInheritanceHierarchy().get();
			}
			else
			{
				final Set<PsiClass> classes = MoveInstanceMembersUtil.getThisClassesToMembers(method).keySet();
				for (PsiClass aClass : classes)
				{
		 /* if (aClass instanceof JspClass) {
            message = RefactoringBundle.message("synthetic.jsp.class.is.referenced.in.the.method");
            Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
            CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.MOVE_INSTANCE_METHOD);
            break;
          }  */
				}
			}
		}
		if (message != null)
		{
			showErrorHint(project, dataContext, message);
			return;
		}

		final List<PsiVariable> suitableVariables = new ArrayList<>();
		message = collectSuitableVariables(method, suitableVariables);
		if (message != null)
		{
			final String unableToMakeStaticMessage = MakeStaticHandler.validateTarget(method);
			if (unableToMakeStaticMessage != null)
			{
				showErrorHint(project, dataContext, message);
			}
			else
			{
				final String suggestToMakeStaticMessage = "Would you like to make method \'" + method.getName() + "\' static and then move?";
				if (Messages.showYesNoCancelDialog(
					project,
					message + ". " + suggestToMakeStaticMessage,
					REFACTORING_NAME,
					UIUtil.getErrorIcon()
				) == DialogWrapper.OK_EXIT_CODE)
				{
					MakeStaticHandler.invoke(method);
				}
			}
			return;
		}

		new MoveInstanceMethodDialog(method, suitableVariables.toArray(new PsiVariable[suitableVariables.size()])).show();
	}

	private static void showErrorHint(Project project, DataContext dataContext, String message)
	{
		Editor editor = dataContext == null ? null : dataContext.getData(Editor.KEY);
		CommonRefactoringUtil.showErrorHint(
			project,
			editor,
			RefactoringBundle.getCannotRefactorMessage(message),
			REFACTORING_NAME,
			HelpID.MOVE_INSTANCE_METHOD
		);
	}

	@Nullable
	private static String collectSuitableVariables(final PsiMethod method, final List<PsiVariable> suitableVariables)
	{
		final List<PsiVariable> allVariables = new ArrayList<>();
		ContainerUtil.addAll(allVariables, method.getParameterList().getParameters());
		ContainerUtil.addAll(allVariables, method.getContainingClass().getFields());
		boolean classTypesFound = false;
		boolean resolvableClassesFound = false;
		boolean classesInProjectFound = false;
		for (PsiVariable variable : allVariables)
		{
			final PsiType type = variable.getType();
			if (type instanceof PsiClassType classType && !classType.hasParameters())
			{
				classTypesFound = true;
				final PsiClass psiClass = classType.resolve();
				if (psiClass != null && !(psiClass instanceof PsiTypeParameter))
				{
					resolvableClassesFound = true;
					final boolean inProject = method.getManager().isInProject(psiClass);
					if (inProject)
					{
						classesInProjectFound = true;
						suitableVariables.add(variable);
					}
				}
			}
		}

		if (suitableVariables.isEmpty())
		{
			if (!classTypesFound)
			{
				return RefactoringLocalize.thereAreNoVariablesThatHaveReferenceType().get();
			}
			else if (!resolvableClassesFound)
			{
				return RefactoringLocalize.allCandidateVariablesHaveUnknownTypes().get();
			}
			else if (!classesInProjectFound)
			{
				return RefactoringLocalize.allCandidateVariablesHaveTypesNotInProject().get();
			}
		}
		return null;
	}

	public static String suggestParameterNameForThisClass(final PsiClass thisClass)
	{
		PsiManager manager = thisClass.getManager();
		PsiType type = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createType(thisClass);
		final SuggestedNameInfo suggestedNameInfo =
			JavaCodeStyleManager.getInstance(manager.getProject()).suggestVariableName(VariableKind.PARAMETER, null, null, type);
		return suggestedNameInfo.names.length > 0 ? suggestedNameInfo.names[0] : "";
	}

	public static Map<PsiClass, String> suggestParameterNames(final PsiMethod method, final PsiVariable targetVariable)
	{
		final Map<PsiClass, Set<PsiMember>> classesToMembers = MoveInstanceMembersUtil.getThisClassesToMembers(method);
		Map<PsiClass, String> result = new LinkedHashMap<>();
		for (Map.Entry<PsiClass, Set<PsiMember>> entry : classesToMembers.entrySet())
		{
			PsiClass aClass = entry.getKey();
			final Set<PsiMember> members = entry.getValue();
			if (members.size() == 1 && members.contains(targetVariable))
			{
				continue;
			}
			result.put(aClass, suggestParameterNameForThisClass(aClass));
		}
		return result;
	}

	private static class TypeParametersSearcher extends PsiTypeVisitor<Boolean>
	{
		public static boolean hasTypeParameters(PsiElement element)
		{
			final TypeParametersSearcher searcher = new TypeParametersSearcher();
			final boolean[] hasParameters = new boolean[]{false};
			element.accept(new JavaRecursiveElementWalkingVisitor()
			{
				@Override
				public void visitTypeElement(@Nonnull PsiTypeElement type)
				{
					super.visitTypeElement(type);
					hasParameters[0] |= type.getType().accept(searcher);
				}
			});
			return hasParameters[0];
		}

		@Override
		public Boolean visitClassType(PsiClassType classType)
		{
			final PsiClass psiClass = PsiUtil.resolveClassInType(classType);
			return psiClass instanceof PsiTypeParameter ? Boolean.TRUE : super.visitClassType(classType);
		}

		@Override
		public Boolean visitWildcardType(PsiWildcardType wildcardType)
		{
			final PsiType bound = wildcardType.getBound();
			return PsiUtil.resolveClassInType(bound) instanceof PsiTypeParameter ? Boolean.TRUE : super.visitWildcardType(wildcardType);
		}

		@Override
		public Boolean visitType(PsiType type)
		{
			return Boolean.FALSE;
		}
	}
}
