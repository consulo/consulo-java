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
package com.intellij.java.impl.codeInspection.unneededThrows;

import com.intellij.java.analysis.codeInspection.GlobalJavaInspectionContext;
import com.intellij.java.analysis.codeInspection.GlobalJavaInspectionTool;
import com.intellij.java.analysis.codeInspection.reference.RefJavaVisitor;
import com.intellij.java.analysis.codeInspection.reference.RefMethod;
import com.intellij.java.indexing.search.searches.AllOverridingMethodsSearch;
import com.intellij.java.language.impl.codeInsight.ExceptionUtil;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.query.Query;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.inspection.*;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.editor.inspection.reference.RefElement;
import consulo.language.editor.inspection.reference.RefEntity;
import consulo.language.editor.inspection.reference.RefManager;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.editor.scope.AnalysisScope;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.collection.BidirectionalMap;
import consulo.util.lang.Comparing;
import consulo.util.lang.Pair;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;

/**
 * @author max
 */
@ExtensionImpl
public class RedundantThrows extends GlobalJavaInspectionTool
{
	private static final Logger LOG = Logger.getInstance(RedundantThrows.class);
	private final BidirectionalMap<String, QuickFix> myQuickFixes = new BidirectionalMap<>();
	@NonNls
	private static final String SHORT_NAME = "RedundantThrows";

	@Override
	@Nullable
	public CommonProblemDescriptor[] checkElement(
		@Nonnull RefEntity refEntity,
		@Nonnull AnalysisScope scope,
		@Nonnull InspectionManager manager,
		@Nonnull GlobalInspectionContext globalContext,
		@Nonnull ProblemDescriptionsProcessor processor,
		@Nonnull Object state
	)
	{
		if (refEntity instanceof RefMethod refMethod)
		{
			if (refMethod.isSyntheticJSP() || refMethod.hasSuperMethods() || refMethod.isEntry()) {
				return null;
			}

			PsiClass[] unThrown = refMethod.getUnThrownExceptions();
			if (unThrown == null)
			{
				return null;
			}

			PsiMethod psiMethod = (PsiMethod) refMethod.getElement();
			PsiClassType[] throwsList = psiMethod.getThrowsList().getReferencedTypes();
			PsiJavaCodeReferenceElement[] throwsRefs = psiMethod.getThrowsList().getReferenceElements();
			ArrayList<ProblemDescriptor> problems = null;

			final PsiManager psiManager = psiMethod.getManager();
			for (int i = 0; i < throwsList.length; i++)
			{
				final PsiClassType throwsType = throwsList[i];
				final String throwsClassName = throwsType.getClassName();
				final PsiJavaCodeReferenceElement throwsRef = throwsRefs[i];
				if (ExceptionUtil.isUncheckedException(throwsType) || declaredInRemotableMethod(psiMethod, throwsType)) {
					continue;
				}

				for (PsiClass s : unThrown)
				{
					final PsiClass throwsResolvedType = throwsType.resolve();
					if (psiManager.areElementsEquivalent(s, throwsResolvedType))
					{
						if (problems == null)
						{
							problems = new ArrayList<>(1);
						}

						if (refMethod.isAbstract() || refMethod.getOwnerClass().isInterface())
						{
							problems.add(manager.createProblemDescriptor(
								throwsRef,
								InspectionLocalize.inspectionRedundantThrowsProblemDescriptor("<code>#ref</code>").get(),
								getFix(processor, throwsClassName),
								ProblemHighlightType.LIKE_UNUSED_SYMBOL,
								false
							));
						}
						else if (!refMethod.getDerivedMethods().isEmpty())
						{
							problems.add(manager.createProblemDescriptor(
								throwsRef,
								InspectionLocalize.inspectionRedundantThrowsProblemDescriptor1("<code>#ref</code>").get(),
								getFix(processor, throwsClassName),
								ProblemHighlightType.LIKE_UNUSED_SYMBOL,
								false
							));
						}
						else
						{
							problems.add(manager.createProblemDescriptor(
								throwsRef,
								InspectionLocalize.inspectionRedundantThrowsProblemDescriptor2("<code>#ref</code>").get(),
								getFix(processor, throwsClassName),
								ProblemHighlightType.LIKE_UNUSED_SYMBOL,
								false
							));
						}
					}
				}
			}

			if (problems != null)
			{
				return problems.toArray(new ProblemDescriptorBase[problems.size()]);
			}
		}

		return null;
	}

	private static boolean declaredInRemotableMethod(final PsiMethod psiMethod, final PsiClassType throwsType)
	{
		if (!throwsType.equalsToText("java.rmi.RemoteException"))
		{
			return false;
		}
		PsiClass aClass = psiMethod.getContainingClass();
		if (aClass == null)
		{
			return false;
		}
		PsiClass remote =
				JavaPsiFacade.getInstance(aClass.getProject()).findClass("java.rmi.Remote", GlobalSearchScope.allScope(aClass.getProject()));
		return remote != null && aClass.isInheritor(remote, true);
	}


	@Override
	protected boolean queryExternalUsagesRequests(
			final RefManager manager, final GlobalJavaInspectionContext globalContext,
			final ProblemDescriptionsProcessor processor, Object state)
	{
		manager.iterate(new RefJavaVisitor()
		{
			@Override
			public void visitElement(@Nonnull RefEntity refEntity)
			{
				if (processor.getDescriptions(refEntity) != null)
				{
					refEntity.accept(new RefJavaVisitor()
					{
						@Override
						public void visitMethod(@Nonnull final RefMethod refMethod)
						{
							globalContext.enqueueDerivedMethodsProcessor(refMethod, derivedMethod -> {
								processor.ignoreElement(refMethod);
								return true;
							});
						}
					});
				}
			}
		});

		return false;
	}

	@Override
	@Nonnull
	public String getDisplayName()
	{
		return InspectionLocalize.inspectionRedundantThrowsDisplayName().get();
	}

	@Override
	@Nonnull
	public String getGroupDisplayName()
	{
		return InspectionLocalize.groupNamesDeclarationRedundancy().get();
	}

	@Override
	@Nonnull
	public String getShortName()
	{
		return SHORT_NAME;
	}

	private LocalQuickFix getFix(final ProblemDescriptionsProcessor processor, final String hint)
	{
		QuickFix fix = myQuickFixes.get(hint);
		if (fix == null)
		{
			fix = new MyQuickFix(processor, hint);
			if (hint != null)
			{
				myQuickFixes.put(hint, fix);
			}
		}
		return (LocalQuickFix) fix;
	}


	@Override
	@Nullable
	public QuickFix getQuickFix(String hint)
	{
		return getFix(null, hint);
	}

	@Override
	@Nullable
	public String getHint(@Nonnull final QuickFix fix)
	{
		final List<String> hints = myQuickFixes.getKeysByValue(fix);
		LOG.assertTrue(hints != null && hints.size() == 1);
		return hints.get(0);
	}

	private static class MyQuickFix implements LocalQuickFix
	{
		private final ProblemDescriptionsProcessor myProcessor;
		private final String myHint;

		public MyQuickFix(final ProblemDescriptionsProcessor processor, final String hint)
		{
			myProcessor = processor;
			myHint = hint;
		}

		@Override
		@Nonnull
		public String getName()
		{
			return InspectionLocalize.inspectionRedundantThrowsRemoveQuickfix().get();
		}

		@Override
		@RequiredReadAction
		public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor)
		{
			if (myProcessor != null)
			{
				RefElement refElement = (RefElement) myProcessor.getElement(descriptor);
				if (refElement instanceof RefMethod refMethod && refElement.isValid())
				{
					final CommonProblemDescriptor[] problems = myProcessor.getDescriptions(refMethod);
					if (problems != null)
					{
						removeExcessiveThrows(refMethod, null, problems);
					}
				}
			}
			else
			{
				final PsiMethod psiMethod = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiMethod.class);
				if (psiMethod != null)
				{
					removeExcessiveThrows(null, psiMethod, new CommonProblemDescriptor[]{descriptor});
				}
			}
		}

		@Override
		@Nonnull
		public String getFamilyName()
		{
			return getName();
		}

		@RequiredReadAction
		private void removeExcessiveThrows(
			@Nullable RefMethod refMethod,
			@Nullable final PsiModifierListOwner element,
			final CommonProblemDescriptor[] problems
		)
		{
			try
			{
				@Nullable final PsiMethod psiMethod;
				if (element == null)
				{
					LOG.assertTrue(refMethod != null);
					psiMethod = (PsiMethod) refMethod.getElement();
				}
				else
				{
					psiMethod = (PsiMethod) element;
				}
				if (psiMethod == null)
				{
					return; //invalid refMethod
				}
				final Project project = psiMethod.getProject();
				final PsiManager psiManager = PsiManager.getInstance(project);
				final List<PsiJavaCodeReferenceElement> refsToDelete = new ArrayList<>();
				for (CommonProblemDescriptor problem : problems)
				{
					final PsiElement psiElement = ((ProblemDescriptor) problem).getPsiElement();
					if (psiElement instanceof PsiJavaCodeReferenceElement classRef)
					{
						final PsiType psiType = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory().createType(classRef);
						removeException(refMethod, psiType, refsToDelete, psiMethod);
					}
					else
					{
						final PsiReferenceList throwsList = psiMethod.getThrowsList();
						final PsiClassType[] classTypes = throwsList.getReferencedTypes();
						for (PsiClassType classType : classTypes)
						{
							final String text = classType.getClassName();
							if (Comparing.strEqual(myHint, text))
							{
								removeException(refMethod, classType, refsToDelete, psiMethod);
								break;
							}
						}
					}
				}

				//check read-only status for derived methods
				if (!FileModificationService.getInstance().preparePsiElementsForWrite(refsToDelete))
				{
					return;
				}

				for (final PsiJavaCodeReferenceElement aRefsToDelete : refsToDelete)
				{
					aRefsToDelete.delete();
				}
			}
			catch (IncorrectOperationException e)
			{
				LOG.error(e);
			}
		}

		private static void removeException(final RefMethod refMethod,
											final PsiType exceptionType,
											final List<PsiJavaCodeReferenceElement> refsToDelete,
											final PsiMethod psiMethod)
		{
			PsiManager psiManager = psiMethod.getManager();

			PsiJavaCodeReferenceElement[] refs = psiMethod.getThrowsList().getReferenceElements();
			for (PsiJavaCodeReferenceElement ref : refs)
			{
				PsiType refType = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory().createType(ref);
				if (exceptionType.isAssignableFrom(refType))
				{
					refsToDelete.add(ref);
				}
			}

			if (refMethod != null)
			{
				for (RefMethod refDerived : refMethod.getDerivedMethods())
				{
					removeException(refDerived, exceptionType, refsToDelete, (PsiMethod) refDerived.getElement());
				}
			}
			else
			{
				final Query<Pair<PsiMethod, PsiMethod>> query = AllOverridingMethodsSearch.search(psiMethod.getContainingClass());
				query.forEach(pair -> {
					if (pair.first == psiMethod)
					{
						removeException(null, exceptionType, refsToDelete, pair.second);
					}
					return true;
				});
			}
		}
	}
}
