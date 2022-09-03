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
package com.intellij.java.language.impl.psi.impl.source.tree.java;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import consulo.language.ast.ASTNode;
import consulo.logging.Logger;
import com.intellij.java.language.psi.PsiJavaFile;
import consulo.language.ast.TokenType;
import com.intellij.java.language.impl.psi.impl.java.stubs.JavaStubElementTypes;
import consulo.language.impl.psi.SourceTreeToPsiMap;
import com.intellij.java.language.impl.psi.impl.source.tree.ChildRole;
import consulo.language.impl.ast.FileElement;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import consulo.language.impl.ast.TreeElement;
import consulo.language.ast.ChildRoleBase;
import consulo.language.ast.IElementType;

public class JavaFileElement extends FileElement
{
	private static final Logger LOG = Logger.getInstance(JavaFileElement.class);

	public JavaFileElement(CharSequence text)
	{
		super(JavaStubElementTypes.JAVA_FILE, text);
	}

	@Override
	public void deleteChildInternal(@Nonnull ASTNode child)
	{
		if(child.getElementType() == JavaElementType.CLASS)
		{
			PsiJavaFile file = SourceTreeToPsiMap.treeToPsiNotNull(this);
			if(file.getClasses().length == 1)
			{
				file.delete();
				return;
			}
		}
		super.deleteChildInternal(child);
	}

	@Override
	@Nullable
	public ASTNode findChildByRole(int role)
	{
		LOG.assertTrue(ChildRole.isUnique(role));
		switch(role)
		{
			default:
				return null;

			case ChildRole.PACKAGE_STATEMENT:
				return findChildByType(JavaElementType.PACKAGE_STATEMENT);

			case ChildRole.IMPORT_LIST:
				return findChildByType(JavaElementType.IMPORT_LIST);
		}
	}

	@Override
	public int getChildRole(@Nonnull ASTNode child)
	{
		LOG.assertTrue(child.getTreeParent() == this);
		IElementType i = child.getElementType();
		if(i == JavaElementType.PACKAGE_STATEMENT)
		{
			return ChildRole.PACKAGE_STATEMENT;
		}
		else if(i == JavaElementType.IMPORT_LIST)
		{
			return ChildRole.IMPORT_LIST;
		}
		else if(i == JavaElementType.CLASS)
		{
			return ChildRole.CLASS;
		}
		else
		{
			return ChildRoleBase.NONE;
		}
	}

	@Override
	public void replaceChildInternal(@Nonnull ASTNode child, @Nonnull TreeElement newElement)
	{
		if(newElement.getElementType() == JavaElementType.IMPORT_LIST)
		{
			LOG.assertTrue(child.getElementType() == JavaElementType.IMPORT_LIST);
			if(newElement.getFirstChildNode() == null)
			{ //empty import list
				ASTNode next = child.getTreeNext();
				if(next != null && next.getElementType() == TokenType.WHITE_SPACE)
				{
					removeChild(next);
				}
			}
		}
		super.replaceChildInternal(child, newElement);
	}
}
