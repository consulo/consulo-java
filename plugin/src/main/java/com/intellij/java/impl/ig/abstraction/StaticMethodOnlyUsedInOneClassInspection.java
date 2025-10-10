/*
 * Copyright 2006-2017 Bas Leijdekkers
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
package com.intellij.java.impl.ig.abstraction;

import com.intellij.java.analysis.codeInspection.GlobalJavaInspectionContext;
import com.intellij.java.analysis.codeInspection.reference.RefClass;
import com.intellij.java.analysis.codeInspection.reference.RefJavaUtil;
import com.intellij.java.analysis.codeInspection.reference.RefJavaVisitor;
import com.intellij.java.analysis.codeInspection.reference.RefMethod;
import com.intellij.java.impl.ig.BaseGlobalInspection;
import com.intellij.java.impl.ig.fixes.RefactoringInspectionGadgetsFix;
import com.intellij.java.indexing.search.searches.MethodReferencesSearch;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.BaseSharedLocalInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.DeclarationSearchUtils;
import com.siyeh.ig.psiutils.TestUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.progress.ProgressManager;
import consulo.application.util.function.Processor;
import consulo.application.util.query.Query;
import consulo.dataContext.DataContext;
import consulo.deadCodeNotWorking.impl.MultipleCheckboxOptionsPanel;
import consulo.language.editor.LangDataKeys;
import consulo.language.editor.inspection.*;
import consulo.language.editor.inspection.reference.RefElement;
import consulo.language.editor.inspection.reference.RefEntity;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.editor.refactoring.action.RefactoringActionHandlerFactory;
import consulo.language.editor.scope.AnalysisScope;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.util.dataholder.Key;
import consulo.util.lang.ref.Ref;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicReference;

@ExtensionImpl
public class StaticMethodOnlyUsedInOneClassInspection extends BaseGlobalInspection
{

	@SuppressWarnings("PublicField")
	public boolean ignoreTestClasses = false;

	@SuppressWarnings("PublicField")
	public boolean ignoreAnonymousClasses = true;

	@SuppressWarnings("PublicField")
	public boolean ignoreOnConflicts = true;

	static final Key<SmartPsiElementPointer<PsiClass>> MARKER = Key.create("STATIC_METHOD_USED_IN_ONE_CLASS");

	@Override
	@Nonnull
	public LocalizeValue getDisplayName()
	{
		return InspectionGadgetsLocalize.staticMethodOnlyUsedInOneClassDisplayName();
	}

	@Nonnull
	@Override
	public HighlightDisplayLevel getDefaultLevel()
	{
		return HighlightDisplayLevel.WARNING;
	}

	@Nullable
	@Override
	public JComponent createOptionsPanel()
	{
		final MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
		panel.addCheckbox(
			InspectionGadgetsLocalize.staticMethodOnlyUsedInOneClassIgnoreTestOption().get(),
			"ignoreTestClasses"
		);
		panel.addCheckbox(
			InspectionGadgetsLocalize.staticMethodOnlyUsedInOneClassIgnoreAnonymousOption().get(),
			"ignoreAnonymousClasses"
		);
		panel.addCheckbox(
			InspectionGadgetsLocalize.staticMethodOnlyUsedInOneClassIgnoreOnConflicts().get(),
			"ignoreOnConflicts"
		);
		return panel;
	}

	@Nullable
	@Override
	public CommonProblemDescriptor[] checkElement(@Nonnull RefEntity refEntity,
												  @Nonnull AnalysisScope scope,
												  @Nonnull InspectionManager manager,
												  @Nonnull GlobalInspectionContext globalContext,
												  Object state)
	{
		if(!(refEntity instanceof RefMethod))
		{
			return null;
		}
		final RefMethod method = (RefMethod) refEntity;
		if(!method.isStatic() || method.getAccessModifier() == PsiModifier.PRIVATE)
		{
			return null;
		}
		RefClass usageClass = null;
		for(RefElement reference : method.getInReferences())
		{
			final RefClass ownerClass = RefJavaUtil.getInstance().getOwnerClass(reference);
			if(usageClass == null)
			{
				usageClass = ownerClass;
			}
			else if(usageClass != ownerClass)
			{
				return null;
			}
		}
		final RefClass containingClass = method.getOwnerClass();
		if(usageClass == containingClass)
		{
			return null;
		}
		if(usageClass == null)
		{
			final PsiClass aClass = containingClass.getElement();
			if(aClass != null)
			{
				final SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(manager.getProject());
				method.putUserData(MARKER, smartPointerManager.createSmartPsiElementPointer(aClass));
			}
			return null;
		}
		if(ignoreAnonymousClasses && (usageClass.isAnonymous() || usageClass.isLocalClass() || usageClass.getOwner() instanceof RefClass && !usageClass.isStatic()))
		{
			return null;
		}
		if(ignoreTestClasses && usageClass.isTestCase())
		{
			return null;
		}
		final PsiClass psiClass = usageClass.getElement();
		if(psiClass == null)
		{
			return null;
		}
		final PsiMethod psiMethod = (PsiMethod) method.getElement();
		if(psiMethod == null)
		{
			return null;
		}
		if(ignoreOnConflicts)
		{
			if(psiClass.findMethodsBySignature(psiMethod, true).length > 0 || !areReferenceTargetsAccessible(psiMethod, psiClass))
			{
				return null;
			}
		}
		final SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(manager.getProject());
		method.putUserData(MARKER, smartPointerManager.createSmartPsiElementPointer(psiClass));
		return new ProblemDescriptor[]{createProblemDescriptor(manager, psiMethod.getNameIdentifier(), psiClass)};
	}

	@Nonnull
	static ProblemDescriptor createProblemDescriptor(@Nonnull InspectionManager manager, PsiElement problemElement, PsiClass usageClass)
	{
		final String message = (usageClass instanceof PsiAnonymousClass)
			? InspectionGadgetsBundle.message(
				"static.method.only.used.in.one.anonymous.class.problem.descriptor",
				((PsiAnonymousClass)usageClass).getBaseClassReference().getText()
			)
			: InspectionGadgetsLocalize.staticMethodOnlyUsedInOneClassProblemDescriptor(usageClass.getName()).get();
		return manager.createProblemDescriptor(problemElement, message, false, null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
	}

	@Override
	public boolean queryExternalUsagesRequests(@Nonnull final InspectionManager manager,
											   @Nonnull final GlobalInspectionContext globalContext,
											   @Nonnull final ProblemDescriptionsProcessor problemDescriptionsProcessor,
											   Object state)
	{
		globalContext.getRefManager().iterate(new RefJavaVisitor()
		{
			@Override
			public void visitElement(@Nonnull RefEntity refEntity)
			{
				if(refEntity instanceof RefMethod)
				{
					final SmartPsiElementPointer<PsiClass> classPointer = refEntity.getUserData(MARKER);
					if(classPointer != null)
					{
						final Ref<PsiClass> ref = Ref.create(classPointer.getElement());
						final GlobalJavaInspectionContext globalJavaContext = globalContext.getExtension(GlobalJavaInspectionContext.CONTEXT);
						globalJavaContext.enqueueMethodUsagesProcessor((RefMethod) refEntity, new GlobalJavaInspectionContext.UsagesProcessor()
						{
							@Override
							public boolean process(PsiReference reference)
							{
								final PsiClass containingClass = ClassUtils.getContainingClass(reference.getElement());
								if(problemDescriptionsProcessor.getDescriptions(refEntity) != null)
								{
									if(containingClass != ref.get())
									{
										problemDescriptionsProcessor.ignoreElement(refEntity);
										return false;
									}
									return true;
								}
								else
								{
									final PsiIdentifier identifier = ((PsiMethod) ((RefMethod) refEntity).getElement()).getNameIdentifier();
									final ProblemDescriptor problemDescriptor = createProblemDescriptor(manager, identifier, containingClass);
									problemDescriptionsProcessor.addProblemElement(refEntity, problemDescriptor);
									ref.set(containingClass);
									return true;
								}
							}
						});
					}
				}
			}
		});

		return false;
	}

	static boolean areReferenceTargetsAccessible(final PsiElement elementToCheck, final PsiElement place)
	{
		final AccessibleVisitor visitor = new AccessibleVisitor(elementToCheck, place);
		elementToCheck.accept(visitor);
		return visitor.isAccessible();
	}

	private static class AccessibleVisitor extends JavaRecursiveElementWalkingVisitor
	{
		private final PsiElement myElementToCheck;
		private final PsiElement myPlace;
		private boolean myAccessible = true;

		public AccessibleVisitor(PsiElement elementToCheck, PsiElement place)
		{
			myElementToCheck = elementToCheck;
			myPlace = place;
		}

		@Override
		public void visitCallExpression(PsiCallExpression callExpression)
		{
			if(!myAccessible)
			{
				return;
			}
			super.visitCallExpression(callExpression);
			final PsiMethod method = callExpression.resolveMethod();
			if(callExpression instanceof PsiNewExpression && method == null)
			{
				final PsiNewExpression newExpression = (PsiNewExpression) callExpression;
				final PsiJavaCodeReferenceElement reference = newExpression.getClassReference();
				if(reference != null)
				{
					checkElement(reference.resolve());
				}
			}
			else
			{
				checkElement(method);
			}
		}

		@Override
		public void visitReferenceExpression(PsiReferenceExpression expression)
		{
			if(!myAccessible)
			{
				return;
			}
			super.visitReferenceExpression(expression);
			checkElement(expression.resolve());
		}

		private void checkElement(PsiElement element)
		{
			if(!(element instanceof PsiMember))
			{
				return;
			}
			if(PsiTreeUtil.isAncestor(myElementToCheck, element, false))
			{
				return; // internal reference
			}
			myAccessible = PsiUtil.isAccessible((PsiMember) element, myPlace, null);
		}

		public boolean isAccessible()
		{
			return myAccessible;
		}
	}

	private static class UsageProcessor implements Processor<PsiReference>
	{

		private final AtomicReference<PsiClass> foundClass = new AtomicReference<>();

		@Override
		public boolean process(PsiReference reference)
		{
			ProgressManager.checkCanceled();
			final PsiElement element = reference.getElement();
			final PsiClass usageClass = ClassUtils.getContainingClass(element);
			if(usageClass == null)
			{
				return true;
			}
			if(foundClass.compareAndSet(null, usageClass))
			{
				return true;
			}
			final PsiClass aClass = foundClass.get();
			final PsiManager manager = usageClass.getManager();
			return manager.areElementsEquivalent(aClass, usageClass);
		}

		/**
		 * @return the class the specified method is used from, or null if it is
		 * used from 0 or more than 1 other classes.
		 */
		@Nullable
		public PsiClass findUsageClass(final PsiMethod method)
		{
			ProgressManager.getInstance().runProcess(() ->
			{
				final Query<PsiReference> query = MethodReferencesSearch.search(method);
				if(!query.forEach(this))
				{
					foundClass.set(null);
				}
			}, null);
			return foundClass.get();
		}
	}

	@Nullable
	@Override
	public LocalInspectionTool getSharedLocalInspectionTool()
	{
		return new StaticMethodOnlyUsedInOneClassLocalInspection(this);
	}

	private static class StaticMethodOnlyUsedInOneClassLocalInspection extends BaseSharedLocalInspection<StaticMethodOnlyUsedInOneClassInspection>
	{

		public StaticMethodOnlyUsedInOneClassLocalInspection(StaticMethodOnlyUsedInOneClassInspection settingsDelegate)
		{
			super(settingsDelegate);
		}

		@Override
		@Nonnull
		protected String buildErrorString(Object... infos)
		{
			final PsiClass usageClass = (PsiClass) infos[0];
			return (usageClass instanceof PsiAnonymousClass) ? InspectionGadgetsBundle.message("static.method.only.used.in.one.anonymous.class.problem.descriptor", ((PsiAnonymousClass) usageClass)
					.getBaseClassReference().getText()) : InspectionGadgetsLocalize.staticMethodOnlyUsedInOneClassProblemDescriptor(
				usageClass.getName()).get();
		}

		@Override
		@Nullable
		protected InspectionGadgetsFix buildFix(Object... infos)
		{
			final PsiClass usageClass = (PsiClass) infos[0];
			return new StaticMethodOnlyUsedInOneClassFix(usageClass);
		}

		private static class StaticMethodOnlyUsedInOneClassFix extends RefactoringInspectionGadgetsFix
		{

			private final SmartPsiElementPointer<PsiClass> usageClass;

			public StaticMethodOnlyUsedInOneClassFix(PsiClass usageClass)
			{
				final SmartPointerManager pointerManager = SmartPointerManager.getInstance(usageClass.getProject());
				this.usageClass = pointerManager.createSmartPsiElementPointer(usageClass);
			}

			@Override
            @Nonnull
			public LocalizeValue getName()
			{
				return InspectionGadgetsLocalize.staticMethodOnlyUsedInOneClassQuickfix();
			}

			@Nonnull
			@Override
			public RefactoringActionHandler getHandler()
			{
				return RefactoringActionHandlerFactory.getInstance().createMoveHandler();
			}

			@Nonnull
			@Override
			public DataContext enhanceDataContext(DataContext context)
			{
				return DataContext.builder().parent(context).add(LangDataKeys.TARGET_PSI_ELEMENT, usageClass.getElement()).build();
			}
		}

		@Override
		public BaseInspectionVisitor buildVisitor()
		{
			return new StaticMethodOnlyUsedInOneClassVisitor();
		}

		private class StaticMethodOnlyUsedInOneClassVisitor extends BaseInspectionVisitor
		{

			@Override
			public void visitMethod(final PsiMethod method)
			{
				super.visitMethod(method);
				if(!method.hasModifierProperty(PsiModifier.STATIC) || method.hasModifierProperty(PsiModifier.PRIVATE) || method.getNameIdentifier() == null)
				{
					return;
				}
				if(DeclarationSearchUtils.isTooExpensiveToSearch(method, true))
				{
					return;
				}
				final UsageProcessor usageProcessor = new UsageProcessor();
				final PsiClass usageClass = usageProcessor.findUsageClass(method);
				if(usageClass == null)
				{
					return;
				}
				final PsiClass containingClass = method.getContainingClass();
				if(usageClass.equals(containingClass))
				{
					return;
				}
				if(mySettingsDelegate.ignoreTestClasses && TestUtils.isInTestCode(usageClass))
				{
					return;
				}
				if(usageClass.getContainingClass() != null && !usageClass.hasModifierProperty(PsiModifier.STATIC) || PsiUtil.isLocalOrAnonymousClass(usageClass))
				{
					if(mySettingsDelegate.ignoreAnonymousClasses)
					{
						return;
					}
					if(PsiTreeUtil.isAncestor(containingClass, usageClass, true))
					{
						return;
					}
				}
				if(mySettingsDelegate.ignoreOnConflicts)
				{
					if(usageClass.findMethodsBySignature(method, true).length > 0 || !areReferenceTargetsAccessible(method, usageClass))
					{
						return;
					}
				}
				registerMethodError(method, usageClass);
			}
		}
	}
}
