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
package com.intellij.java.impl.codeInspection.emptyMethod;

import com.intellij.java.analysis.codeInspection.GlobalJavaInspectionContext;
import com.intellij.java.analysis.codeInspection.GlobalJavaInspectionTool;
import com.intellij.java.analysis.codeInspection.reference.RefJavaUtil;
import com.intellij.java.analysis.codeInspection.reference.RefJavaVisitor;
import com.intellij.java.analysis.codeInspection.reference.RefMethod;
import com.intellij.java.analysis.impl.codeInspection.util.SpecialAnnotationsUtilBase;
import com.intellij.java.impl.codeInspection.util.SpecialAnnotationsUtil;
import com.intellij.java.indexing.search.searches.AllOverridingMethodsSearch;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.application.util.query.Query;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import consulo.java.deadCodeNotWorking.OldStyleInspection;
import consulo.language.editor.inspection.*;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.editor.inspection.reference.RefElement;
import consulo.language.editor.inspection.reference.RefEntity;
import consulo.language.editor.inspection.reference.RefManager;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.editor.intention.BatchQuickFix;
import consulo.language.editor.refactoring.safeDelete.SafeDeleteHandler;
import consulo.language.editor.scope.AnalysisScope;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.collection.BidirectionalMap;
import consulo.util.lang.Comparing;
import consulo.util.lang.Pair;
import consulo.util.xml.serializer.JDOMExternalizableStringList;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author max
 */
@ExtensionImpl
public class EmptyMethodInspection extends GlobalJavaInspectionTool implements OldStyleInspection
{
	@NonNls
	private static final String SHORT_NAME = "EmptyMethod";

	private final BidirectionalMap<Boolean, QuickFix> myQuickFixes = new BidirectionalMap<Boolean, QuickFix>();

	public final JDOMExternalizableStringList EXCLUDE_ANNOS = new JDOMExternalizableStringList();
	private static final Logger LOG = Logger.getInstance(EmptyMethodInspection.class);

	@Override
	@Nullable
	public CommonProblemDescriptor[] checkElement(
		@Nonnull RefEntity refEntity,
		@Nonnull AnalysisScope scope,
		@Nonnull InspectionManager manager,
		@Nonnull GlobalInspectionContext globalContext,
		@Nonnull ProblemDescriptionsProcessor processor,
		Object state
	)
	{
		if (!(refEntity instanceof RefMethod))
		{
			return null;
		}
		final RefMethod refMethod = (RefMethod) refEntity;

		if (!isBodyEmpty(refMethod))
		{
			return null;
		}
		if (refMethod.isConstructor())
		{
			return null;
		}
		if (refMethod.isSyntheticJSP())
		{
			return null;
		}

		for (RefMethod refSuper : refMethod.getSuperMethods())
		{
			if (checkElement(refSuper, scope, manager, globalContext, processor) != null)
			{
				return null;
			}
		}

		String message = null;
		boolean needToDeleteHierarchy = false;
		if (refMethod.isOnlyCallsSuper() && !refMethod.isFinal())
		{
			RefMethod refSuper = findSuperWithBody(refMethod);
			final RefJavaUtil refUtil = RefJavaUtil.getInstance();
			if (refSuper != null && Comparing.strEqual(refMethod.getAccessModifier(), refSuper.getAccessModifier()))
			{
				if (Comparing.strEqual(refSuper.getAccessModifier(), PsiModifier.PROTECTED) //protected modificator gives access to method in another package
						&& !Comparing.strEqual(refUtil.getPackageName(refSuper), refUtil.getPackageName(refMethod)))
				{
					return null;
				}
				final PsiModifierListOwner modifierListOwner = refMethod.getElement();
				if (modifierListOwner != null)
				{
					final PsiModifierList list = modifierListOwner.getModifierList();
					if (list != null)
					{
						final PsiModifierListOwner supMethod = refSuper.getElement();
						if (supMethod != null)
						{
							final PsiModifierList superModifiedList = supMethod.getModifierList();
							LOG.assertTrue(superModifiedList != null);
							if (list.hasModifierProperty(PsiModifier.SYNCHRONIZED) && !superModifiedList.hasModifierProperty(PsiModifier.SYNCHRONIZED))
							{
								return null;
							}
						}
					}
				}
			}
			if (refSuper == null || refUtil.compareAccess(refMethod.getAccessModifier(), refSuper.getAccessModifier()) <= 0)
			{
				message = InspectionLocalize.inspectionEmptyMethodProblemDescriptor().get();
			}
		}
		else if (refMethod.hasBody() && hasEmptySuperImplementation(refMethod))
		{

			message = InspectionLocalize.inspectionEmptyMethodProblemDescriptor1().get();
		}
		else if (areAllImplementationsEmpty(refMethod))
		{
			if (refMethod.hasBody())
			{
				if (refMethod.getDerivedMethods().isEmpty())
				{
					if (refMethod.getSuperMethods().isEmpty())
					{
						message = InspectionLocalize.inspectionEmptyMethodProblemDescriptor2().get();
					}
				}
				else
				{
					needToDeleteHierarchy = true;
					message = InspectionLocalize.inspectionEmptyMethodProblemDescriptor3().get();
				}
			}
			else if (!refMethod.getDerivedMethods().isEmpty()) {
				needToDeleteHierarchy = true;
				message = InspectionLocalize.inspectionEmptyMethodProblemDescriptor4().get();
			}
		}

		if (message != null)
		{
			final ArrayList<LocalQuickFix> fixes = new ArrayList<LocalQuickFix>();
			fixes.add(getFix(processor, needToDeleteHierarchy));
			SpecialAnnotationsUtilBase.createAddToSpecialAnnotationFixes(refMethod.getElement(), qualifiedName -> {
				fixes.add(SpecialAnnotationsUtilBase.createAddToSpecialAnnotationsListQuickFix(
						JavaQuickFixBundle.message("fix.add.special.annotation.text", qualifiedName),
						JavaQuickFixBundle.message("fix.add.special.annotation.family"),
						EXCLUDE_ANNOS, qualifiedName, refMethod.getElement()));
				return true;
			});

			final ProblemDescriptor descriptor = manager.createProblemDescriptor(
				refMethod.getElement().getNavigationElement(),
				message,
				false,
				fixes.toArray(new LocalQuickFix[fixes.size()]),
				ProblemHighlightType.GENERIC_ERROR_OR_WARNING
			);
			return new ProblemDescriptor[]{descriptor};
		}

		return null;
	}

	@RequiredReadAction
	private boolean isBodyEmpty(final RefMethod refMethod)
	{
		if (!refMethod.isBodyEmpty())
		{
			return false;
		}
		final PsiModifierListOwner owner = refMethod.getElement();
		if (owner == null || AnnotationUtil.isAnnotated(owner, EXCLUDE_ANNOS)) {
			return false;
		}
		for (ImplicitMethodBodyProvider provider : Application.get().getExtensionPoint(ImplicitMethodBodyProvider.class))
		{
			if (provider.hasImplicitMethodBody(refMethod))
			{
				return false;
			}
		}

		return true;
	}

	@Nullable
	private static RefMethod findSuperWithBody(RefMethod refMethod)
	{
		for (RefMethod refSuper : refMethod.getSuperMethods())
		{
			if (refSuper.hasBody())
			{
				return refSuper;
			}
		}
		return null;
	}

	private boolean areAllImplementationsEmpty(RefMethod refMethod)
	{
		if (refMethod.hasBody() && !isBodyEmpty(refMethod))
		{
			return false;
		}

		for (RefMethod refDerived : refMethod.getDerivedMethods())
		{
			if (!areAllImplementationsEmpty(refDerived))
			{
				return false;
			}
		}

		return true;
	}

	private boolean hasEmptySuperImplementation(RefMethod refMethod)
	{
		for (RefMethod refSuper : refMethod.getSuperMethods())
		{
			if (refSuper.hasBody() && isBodyEmpty(refSuper))
			{
				return true;
			}
		}

		return false;
	}

	@Override
	protected boolean queryExternalUsagesRequests(
    @Nonnull final RefManager manager,
    @Nonnull final GlobalJavaInspectionContext context,
    @Nonnull final ProblemDescriptionsProcessor descriptionsProcessor,
		Object state
	)
	{
		manager.iterate(new RefJavaVisitor()
		{
			@Override
			public void visitElement(@Nonnull RefEntity refEntity)
			{
				if (refEntity instanceof RefElement && descriptionsProcessor.getDescriptions(refEntity) != null)
				{
					refEntity.accept(new RefJavaVisitor()
					{
						@Override
						public void visitMethod(@Nonnull final RefMethod refMethod)
						{
							context.enqueueDerivedMethodsProcessor(refMethod, derivedMethod -> {
								PsiCodeBlock body = derivedMethod.getBody();
								if (body == null || body.getStatements().length == 0
									|| RefJavaUtil.getInstance().isMethodOnlyCallsSuper(derivedMethod)) {
									return true;
								}
								descriptionsProcessor.ignoreElement(refMethod);
								return false;
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
		return InspectionLocalize.inspectionEmptyMethodDisplayName().get();
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

	private LocalQuickFix getFix(final ProblemDescriptionsProcessor processor, final boolean needToDeleteHierarchy)
	{
		QuickFix fix = myQuickFixes.get(needToDeleteHierarchy);
		if (fix == null)
		{
			fix = new DeleteMethodQuickFix(processor, needToDeleteHierarchy);
			myQuickFixes.put(needToDeleteHierarchy, fix);
			return (LocalQuickFix) fix;
		}
		return (LocalQuickFix) fix;
	}

	@Override
	public String getHint(@Nonnull final QuickFix fix)
	{
		final List<Boolean> list = myQuickFixes.getKeysByValue(fix);
		if (list != null)
		{
			LOG.assertTrue(list.size() == 1);
			return String.valueOf(list.get(0));
		}
		return null;
	}

	@Override
	@Nullable
	public LocalQuickFix getQuickFix(final String hint)
	{
		return new DeleteMethodIntention(hint);
	}

	@Override
	@Nullable
	public JComponent createOptionsPanel()
	{
		final JPanel listPanel = SpecialAnnotationsUtil.createSpecialAnnotationsListControl(
			EXCLUDE_ANNOS,
			InspectionLocalize.specialAnnotationsAnnotationsList().get()
		);

		final JPanel panel = new JPanel(new BorderLayout(2, 2));
		panel.add(listPanel, BorderLayout.CENTER);
		return panel;
	}

	private class DeleteMethodIntention implements LocalQuickFix
	{
		private final String myHint;

		public DeleteMethodIntention(final String hint)
		{
			myHint = hint;
		}

		@Override
		@Nonnull
		public String getName()
		{
			return InspectionLocalize.inspectionEmptyMethodDeleteQuickfix().get();
		}

		@Override
		@Nonnull
		public String getFamilyName()
		{
			return InspectionLocalize.inspectionEmptyMethodDeleteQuickfix().get();
		}

		@Override
		@RequiredReadAction
		public void applyFix(@Nonnull final Project project, @Nonnull final ProblemDescriptor descriptor)
		{
			final PsiMethod psiMethod = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiMethod.class, false);
			if (psiMethod != null)
			{
				final List<PsiElement> psiElements = new ArrayList<>();
				psiElements.add(psiMethod);
				if (Boolean.valueOf(myHint))
				{
					final Query<Pair<PsiMethod, PsiMethod>> query = AllOverridingMethodsSearch.search(psiMethod.getContainingClass());
					query.forEach(pair -> {
						if (pair.first == psiMethod)
						{
							psiElements.add(pair.second);
						}
						return true;
					});
				}

				project.getApplication().invokeLater(
					() -> SafeDeleteHandler.invoke(project, PsiUtilCore.toPsiElementArray(psiElements), false),
					project.getDisposed()
				);
			}
		}
	}

	private class DeleteMethodQuickFix implements LocalQuickFix, BatchQuickFix<CommonProblemDescriptor>
	{
		private final ProblemDescriptionsProcessor myProcessor;
		private final boolean myNeedToDeleteHierarchy;

		public DeleteMethodQuickFix(final ProblemDescriptionsProcessor processor, final boolean needToDeleteHierarchy)
		{
			myProcessor = processor;
			myNeedToDeleteHierarchy = needToDeleteHierarchy;
		}

		@Override
		@Nonnull
		public String getName()
		{
			return InspectionLocalize.inspectionEmptyMethodDeleteQuickfix().get();
		}

		@Override
		public void applyFix(@Nonnull final Project project, @Nonnull ProblemDescriptor descriptor)
		{
			applyFix(project, new ProblemDescriptor[]{descriptor}, new ArrayList<>(), null);
		}

		@Override
		@Nonnull
		public String getFamilyName()
		{
			return getName();
		}

		private void deleteHierarchy(RefMethod refMethod, List<PsiElement> result)
		{
			Collection<RefMethod> derivedMethods = refMethod.getDerivedMethods();
			RefMethod[] refMethods = derivedMethods.toArray(new RefMethod[derivedMethods.size()]);
			for (RefMethod refDerived : refMethods)
			{
				deleteMethod(refDerived, result);
			}
			deleteMethod(refMethod, result);
		}

		private void deleteMethod(RefMethod refMethod, List<PsiElement> result)
		{
			PsiElement psiElement = refMethod.getElement();
			if (psiElement == null)
			{
				return;
			}
			if (!result.contains(psiElement))
			{
				result.add(psiElement);
			}
		}

		@Override
		public void applyFix(
			@Nonnull final Project project,
			@Nonnull final CommonProblemDescriptor[] descriptors,
			final List<PsiElement> psiElementsToIgnore,
			final Runnable refreshViews
		)
		{
			for (CommonProblemDescriptor descriptor : descriptors)
			{
				RefElement refElement = (RefElement) myProcessor.getElement(descriptor);
				if (refElement.isValid() && refElement instanceof RefMethod refMethod)
				{
					if (myNeedToDeleteHierarchy)
					{
						deleteHierarchy(refMethod, psiElementsToIgnore);
					}
					else
					{
						deleteMethod(refMethod, psiElementsToIgnore);
					}
				}
			}
			project.getApplication().invokeLater(
				() -> SafeDeleteHandler.invoke(project, PsiUtilCore.toPsiElementArray(psiElementsToIgnore), false, refreshViews),
				project.getDisposed()
			);
		}
	}
}
