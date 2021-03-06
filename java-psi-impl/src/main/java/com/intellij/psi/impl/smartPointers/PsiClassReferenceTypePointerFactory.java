/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.psi.impl.smartPointers;

import javax.annotation.Nonnull;

import consulo.logging.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.ClassTypePointerFactory;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.SmartTypePointer;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.util.IncorrectOperationException;

/**
 * Created by Max Medvedev on 10/25/13
 */
public class PsiClassReferenceTypePointerFactory implements ClassTypePointerFactory
{
	private static final Logger LOG = Logger.getInstance(PsiClassReferenceTypePointerFactory.class);

	@javax.annotation.Nullable
	@Override
	public SmartTypePointer createClassTypePointer(@Nonnull PsiClassType classType, @Nonnull Project project)
	{
		if(classType instanceof PsiClassReferenceType)
		{
			return new ClassReferenceTypePointer((PsiClassReferenceType) classType, project);
		}

		return null;
	}

	private static class ClassReferenceTypePointer extends TypePointerBase<PsiClassReferenceType>
	{
		private final SmartPsiElementPointer mySmartPsiElementPointer;
		private final String myReferenceText;
		private final Project myProject;

		ClassReferenceTypePointer(@Nonnull PsiClassReferenceType type, Project project)
		{
			super(type);
			myProject = project;

			final PsiJavaCodeReferenceElement reference = type.getReference();
			mySmartPsiElementPointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer
					(reference);
			myReferenceText = reference.getText();
		}

		@Override
		protected PsiClassReferenceType calcType()
		{
			PsiClassReferenceType myType = null;
			final PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)
					mySmartPsiElementPointer.getElement();
			final PsiElementFactory factory = JavaPsiFacade.getInstance(myProject).getElementFactory();
			if(referenceElement != null)
			{
				myType = (PsiClassReferenceType) factory.createType(referenceElement);
			}
			else
			{
				try
				{
					myType = (PsiClassReferenceType) factory.createTypeFromText(myReferenceText, null);
				}
				catch(IncorrectOperationException e)
				{
					LOG.error(e);
				}
			}
			return myType;
		}
	}

}
