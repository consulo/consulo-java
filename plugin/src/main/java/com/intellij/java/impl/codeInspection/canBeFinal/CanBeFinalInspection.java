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
 * User: max
 * Date: Dec 24, 2001
 * Time: 2:46:32 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.java.impl.codeInspection.canBeFinal;

import com.intellij.java.analysis.codeInspection.GlobalJavaInspectionContext;
import com.intellij.java.analysis.codeInspection.GlobalJavaInspectionTool;
import com.intellij.java.analysis.codeInspection.reference.*;
import com.intellij.java.analysis.impl.codeInspection.canBeFinal.CanBeFinalHandler;
import com.intellij.java.analysis.impl.codeInspection.reference.RefClassImpl;
import com.intellij.java.impl.codeInspection.reference.RefFieldImpl;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.java.deadCodeNotWorking.OldStyleInspection;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.impl.inspection.reference.RefElementImpl;
import consulo.language.editor.inspection.*;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.editor.inspection.reference.RefElement;
import consulo.language.editor.inspection.reference.RefEntity;
import consulo.language.editor.inspection.reference.RefGraphAnnotator;
import consulo.language.editor.inspection.reference.RefManager;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.editor.scope.AnalysisScope;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;

@ExtensionImpl
public class CanBeFinalInspection extends GlobalJavaInspectionTool implements OldStyleInspection
{
	private static final Logger LOG = Logger.getInstance(CanBeFinalInspection.class);

	public boolean REPORT_CLASSES = false;
	public boolean REPORT_METHODS = false;
	public boolean REPORT_FIELDS = true;
	@NonNls
	public static final String SHORT_NAME = "CanBeFinal";

	private class OptionsPanel extends JPanel
	{
		private final JCheckBox myReportClassesCheckbox;
		private final JCheckBox myReportMethodsCheckbox;
		private final JCheckBox myReportFieldsCheckbox;

		private OptionsPanel()
		{
			super(new GridBagLayout());

			GridBagConstraints gc = new GridBagConstraints();
			gc.weighty = 0;
			gc.weightx = 1;
			gc.fill = GridBagConstraints.HORIZONTAL;
			gc.anchor = GridBagConstraints.NORTHWEST;


			myReportClassesCheckbox = new JCheckBox(InspectionLocalize.inspectionCanBeFinalOption().get());
			myReportClassesCheckbox.setSelected(REPORT_CLASSES);
			myReportClassesCheckbox.getModel().addChangeListener(e -> REPORT_CLASSES = myReportClassesCheckbox.isSelected());
			gc.gridy = 0;
			add(myReportClassesCheckbox, gc);

			myReportMethodsCheckbox = new JCheckBox(InspectionLocalize.inspectionCanBeFinalOption1().get());
			myReportMethodsCheckbox.setSelected(REPORT_METHODS);
			myReportMethodsCheckbox.getModel().addChangeListener(e -> REPORT_METHODS = myReportMethodsCheckbox.isSelected());
			gc.gridy++;
			add(myReportMethodsCheckbox, gc);

			myReportFieldsCheckbox = new JCheckBox(InspectionLocalize.inspectionCanBeFinalOption2().get());
			myReportFieldsCheckbox.setSelected(REPORT_FIELDS);
			myReportFieldsCheckbox.getModel().addChangeListener(e -> REPORT_FIELDS = myReportFieldsCheckbox.isSelected());

			gc.weighty = 1;
			gc.gridy++;
			add(myReportFieldsCheckbox, gc);
		}
	}

	public boolean isReportClasses()
	{
		return REPORT_CLASSES;
	}

	public boolean isReportMethods()
	{
		return REPORT_METHODS;
	}

	public boolean isReportFields()
	{
		return REPORT_FIELDS;
	}

	@Override
	public JComponent createOptionsPanel()
	{
		return new OptionsPanel();
	}

	@Override
	@Nullable
	public RefGraphAnnotator getAnnotator(final RefManager refManager)
	{
		return new CanBeFinalAnnotator(refManager);
	}

	@Override
	@Nullable
	public CommonProblemDescriptor[] checkElement(
		@Nonnull final RefEntity refEntity,
		@Nonnull final AnalysisScope scope,
		@Nonnull final InspectionManager manager,
		@Nonnull final GlobalInspectionContext globalContext,
		@Nonnull final ProblemDescriptionsProcessor processor,
		@Nonnull Object state
	)
	{
		if (refEntity instanceof RefJavaElement refElement)
		{
			if (refElement instanceof RefParameter || !refElement.isReferenced() || refElement.isSyntheticJSP() || refElement.isFinal()
				|| !((RefElementImpl)refElement).checkFlag(CanBeFinalAnnotator.CAN_BE_FINAL_MASK)) {
				return null;
			}

			final PsiMember psiMember = (PsiMember) refElement.getElement();
			if (psiMember == null || !CanBeFinalHandler.allowToBeFinal(psiMember))
			{
				return null;
			}

			PsiIdentifier psiIdentifier = null;
			if (refElement instanceof RefClass refClass)
			{
				if (refClass.isInterface() || refClass.isAnonymous() || refClass.isAbstract() || !isReportClasses()) {
					return null;
				}
				psiIdentifier = ((PsiClass) psiMember).getNameIdentifier();
			}
			else if (refElement instanceof RefMethod)
			{
				RefMethod refMethod = (RefMethod) refElement;
				if (refMethod.getOwnerClass().isFinal())
				{
					return null;
				}
				if (!isReportMethods())
				{
					return null;
				}
				psiIdentifier = ((PsiMethod) psiMember).getNameIdentifier();
			}
			else if (refElement instanceof RefField)
			{
				if (!isReportFields())
				{
					return null;
				}
				psiIdentifier = ((PsiField) psiMember).getNameIdentifier();
			}

			if (psiIdentifier != null)
			{
				return new ProblemDescriptor[]{
					manager.createProblemDescriptor(
						psiIdentifier,
						InspectionLocalize.inspectionExportResultsCanBeFinalDescription().get(),
						new AcceptSuggested(globalContext.getRefManager()),
						ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
						false
					)
				};
			}
		}
		return null;
	}

	@Override
	protected boolean queryExternalUsagesRequests(
		final RefManager manager,
		final GlobalJavaInspectionContext globalContext,
		final ProblemDescriptionsProcessor problemsProcessor,
		Object state
	)
	{
		for (RefElement entryPoint : globalContext.getEntryPointsManager(manager).getEntryPoints())
		{
			problemsProcessor.ignoreElement(entryPoint);
		}

		manager.iterate(new RefJavaVisitor()
		{
			@Override
			public void visitElement(@Nonnull RefEntity refEntity)
			{
				if (problemsProcessor.getDescriptions(refEntity) == null)
				{
					return;
				}
				refEntity.accept(new RefJavaVisitor()
				{
					@Override
					public void visitMethod(@Nonnull final RefMethod refMethod)
					{
						if (!refMethod.isStatic() && !PsiModifier.PRIVATE.equals(refMethod.getAccessModifier())
							&& !(refMethod instanceof RefImplicitConstructor))
						{
							globalContext.enqueueDerivedMethodsProcessor(refMethod, derivedMethod -> {
								((RefElementImpl) refMethod).setFlag(false, CanBeFinalAnnotator.CAN_BE_FINAL_MASK);
								problemsProcessor.ignoreElement(refMethod);
								return false;
							});
						}
					}

					@Override
					public void visitClass(@Nonnull final RefClass refClass)
					{
						if (!refClass.isAnonymous())
						{
							globalContext.enqueueDerivedClassesProcessor(
								refClass,
								inheritor -> {
									((RefClassImpl) refClass).setFlag(false, CanBeFinalAnnotator.CAN_BE_FINAL_MASK);
									problemsProcessor.ignoreElement(refClass);
									return false;
								}
							);
						}
					}

					@Override
					public void visitField(@Nonnull final RefField refField)
					{
						globalContext.enqueueFieldUsagesProcessor(
							refField,
							psiReference -> {
								PsiElement expression = psiReference.getElement();
								if (expression instanceof PsiReferenceExpression referenceExpression && PsiUtil.isAccessedForWriting(referenceExpression))
								{
									((RefFieldImpl) refField).setFlag(false, CanBeFinalAnnotator.CAN_BE_FINAL_MASK);
									problemsProcessor.ignoreElement(refField);
									return false;
								}
								return true;
							}
						);
					}
				});
			}
		});

		return false;
	}


	@Override
	@Nullable
	public QuickFix getQuickFix(final String hint)
	{
		return new AcceptSuggested(null);
	}

	@Override
	@Nonnull
	public String getDisplayName()
	{
		return InspectionLocalize.inspectionCanBeFinalDisplayName().get();
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

	private static class AcceptSuggested implements LocalQuickFix
	{
		private final RefManager myManager;

		public AcceptSuggested(final RefManager manager)
		{
			myManager = manager;
		}

		@Override
		@Nonnull
		public String getName()
		{
			return InspectionLocalize.inspectionCanBeFinalAcceptQuickfix().get();
		}

		@Override
		@Nonnull
		public String getFamilyName()
		{
			return getName();
		}

		@Override
		@RequiredReadAction
		public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor)
		{
			if (!FileModificationService.getInstance().preparePsiElementForWrite(descriptor.getPsiElement()))
			{
				return;
			}
			final PsiElement element = descriptor.getPsiElement();
			final PsiModifierListOwner psiElement = PsiTreeUtil.getParentOfType(element, PsiModifierListOwner.class);
			if (psiElement != null)
			{
				RefJavaElement refElement = (RefJavaElement) (myManager != null ? myManager.getReference(psiElement) : null);
				try
				{
					if (psiElement instanceof PsiVariable variable)
					{
						variable.normalizeDeclaration();
					}
					final PsiModifierList modifierList = psiElement.getModifierList();
					LOG.assertTrue(modifierList != null);
					modifierList.setModifierProperty(PsiModifier.FINAL, true);
				}
				catch (IncorrectOperationException e)
				{
					LOG.error(e);
				}

				if (refElement != null)
				{
					RefJavaUtil.getInstance().setIsFinal(refElement, true);
				}
			}
		}
	}

}
