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
package com.intellij.java.language.impl.psi.impl.java.stubs;

import com.intellij.java.language.psi.PsiAnnotation;
import consulo.language.psi.stub.StubElement;
import consulo.util.collection.ArrayFactory;
import jakarta.annotation.Nonnull;

/**
 * @author max
 */
public interface PsiAnnotationStub extends StubElement<PsiAnnotation>
{
	public static final PsiAnnotationStub[] EMPTY_ARRAY = new PsiAnnotationStub[0];

	public static ArrayFactory<PsiAnnotationStub> ARRAY_FACTORY = new ArrayFactory<PsiAnnotationStub>()
	{
		@Nonnull
		@Override
		public PsiAnnotationStub[] create(int count)
		{
			return count == 0 ? EMPTY_ARRAY : new PsiAnnotationStub[count];
		}
	};

	String getText();

	PsiAnnotation getPsiElement();
}