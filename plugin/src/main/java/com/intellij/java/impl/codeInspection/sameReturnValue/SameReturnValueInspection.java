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
package com.intellij.java.impl.codeInspection.sameReturnValue;

import com.intellij.java.analysis.codeInspection.GlobalJavaInspectionContext;
import com.intellij.java.analysis.codeInspection.GlobalJavaInspectionTool;
import com.intellij.java.analysis.codeInspection.GroupNames;
import com.intellij.java.analysis.codeInspection.reference.RefJavaVisitor;
import com.intellij.java.analysis.codeInspection.reference.RefMethod;
import com.intellij.java.language.psi.PsiMethod;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.*;
import consulo.language.editor.inspection.reference.RefElement;
import consulo.language.editor.inspection.reference.RefEntity;
import consulo.language.editor.inspection.reference.RefManager;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.editor.scope.AnalysisScope;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author max
 */
@ExtensionImpl
public class SameReturnValueInspection extends GlobalJavaInspectionTool
{
	@Override
	@Nullable
	public CommonProblemDescriptor[] checkElement(RefEntity refEntity,
												  AnalysisScope scope,
												  InspectionManager manager,
												  GlobalInspectionContext globalContext,
												  ProblemDescriptionsProcessor processor,
												  Object state)
	{
		if(refEntity instanceof RefMethod)
		{
			final RefMethod refMethod = (RefMethod) refEntity;

			if(refMethod.isConstructor())
			{
				return null;
			}
			if(refMethod.hasSuperMethods())
			{
				return null;
			}

			String returnValue = refMethod.getReturnValueIfSame();
			if(returnValue != null)
			{
				final String message;
				if(refMethod.getDerivedMethods().isEmpty())
				{
					message = InspectionsBundle.message("inspection.same.return.value.problem.descriptor", "<code>" + returnValue + "</code>");
				}
				else if(refMethod.hasBody())
				{
					message = InspectionsBundle.message("inspection.same.return.value.problem.descriptor1", "<code>" + returnValue + "</code>");
				}
				else
				{
					message = InspectionsBundle.message("inspection.same.return.value.problem.descriptor2", "<code>" + returnValue + "</code>");
				}

				return new ProblemDescriptor[]{
						manager.createProblemDescriptor(refMethod.getElement().getNavigationElement(),
								message,
								false,
								null,
								ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
				};
			}
		}

		return null;
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
				if(refEntity instanceof RefElement && processor.getDescriptions(refEntity) != null)
				{
					refEntity.accept(new RefJavaVisitor()
					{
						@Override
						public void visitMethod(@Nonnull final RefMethod refMethod)
						{
							globalContext.enqueueDerivedMethodsProcessor(refMethod, new GlobalJavaInspectionContext.DerivedMethodsProcessor()
							{
								@Override
								public boolean process(PsiMethod derivedMethod)
								{
									processor.ignoreElement(refMethod);
									return false;
								}
							});
						}
					});
				}
			}
		});

		return false;
	}

	@Override
	@jakarta.annotation.Nonnull
	public String getDisplayName()
	{
		return InspectionsBundle.message("inspection.same.return.value.display.name");
	}

	@Override
	@Nonnull
	public String getGroupDisplayName()
	{
		return GroupNames.DECLARATION_REDUNDANCY;
	}

	@Override
	@Nonnull
	public String getShortName()
	{
		return "SameReturnValue";
	}
}
