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
package com.intellij.java.language.psi.compiled;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import com.intellij.java.language.util.cls.ClsFormatException;
import consulo.language.psi.stub.FileContent;
import consulo.language.psi.stub.PsiFileStub;

public abstract class ClsStubBuilder
{
	/**
	 * Non-zero positive number expected.
	 */
	public abstract int getStubVersion();

	/**
	 * May return {@code null} for inner or synthetic classes - i.e. those indexed as a part of their parent .class file.
	 */
	@Nullable
	public abstract PsiFileStub<?> buildFileStub(@Nonnull FileContent fileContent) throws ClsFormatException;
}
