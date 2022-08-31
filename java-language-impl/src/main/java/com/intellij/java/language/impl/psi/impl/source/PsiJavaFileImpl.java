/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.java.language.impl.psi.impl.source;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.intellij.java.language.impl.JavaFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import com.intellij.java.language.psi.PsiJavaModule;
import com.intellij.java.language.impl.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.java.language.impl.psi.impl.java.stubs.PsiJavaFileStub;
import com.intellij.psi.util.PsiTreeUtil;
import consulo.annotation.access.RequiredReadAction;

public class PsiJavaFileImpl extends PsiJavaFileBaseImpl
{
	public PsiJavaFileImpl(FileViewProvider file)
	{
		super(JavaStubElementTypes.JAVA_FILE, JavaStubElementTypes.JAVA_FILE, file);
	}

	public String toString()
	{
		return "PsiJavaFile:" + getName();
	}

	@RequiredReadAction
	@Nullable
	@Override
	public PsiJavaModule getModuleDeclaration()
	{
		PsiJavaFileStub stub = (PsiJavaFileStub) getGreenStub();
		return stub != null ? stub.getModule() : PsiTreeUtil.getChildOfType(this, PsiJavaModule.class);
	}

	@Override
	@Nonnull
	public FileType getFileType()
	{
		return JavaFileType.INSTANCE;
	}
}
