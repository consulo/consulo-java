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
package com.intellij.psi.impl.source;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiJavaFileStub;
import com.intellij.psi.util.PsiTreeUtil;
import consulo.annotations.RequiredReadAction;

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
		if(stub != null)
		{
			return stub.getModule();
		}

		PsiElement element = getFirstChild();
		if(element instanceof PsiWhiteSpace || element instanceof PsiComment)
		{
			element = PsiTreeUtil.skipSiblingsForward(element, PsiWhiteSpace.class, PsiComment.class);
		}
		return element instanceof PsiJavaModule ? (PsiJavaModule) element : null;
	}

	@Override
	@Nonnull
	public FileType getFileType()
	{
		return JavaFileType.INSTANCE;
	}
}
