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
package com.intellij.java.language.impl.psi.impl.source.tree.java;

import consulo.language.ast.ASTNode;
import com.intellij.java.language.psi.PsiImportList;
import com.intellij.java.language.psi.PsiImportStatementBase;
import com.intellij.java.language.impl.psi.impl.JavaPsiImplementationHelper;
import consulo.language.impl.psi.SourceTreeToPsiMap;
import consulo.language.impl.ast.CompositeElement;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import consulo.language.impl.ast.TreeElement;

public class ImportListElement extends CompositeElement
{
	public ImportListElement()
	{
		super(JavaElementType.IMPORT_LIST);
	}

	@Override
	public TreeElement addInternal(TreeElement first, ASTNode last, ASTNode anchor, Boolean before)
	{
		if(before == null)
		{
			if(first == last && (first.getElementType() == JavaElementType.IMPORT_STATEMENT || first.getElementType() == JavaElementType.IMPORT_STATIC_STATEMENT))
			{
				final PsiImportList list = (PsiImportList) SourceTreeToPsiMap.treeElementToPsi(this);
				final PsiImportStatementBase statement = (PsiImportStatementBase) SourceTreeToPsiMap.treeElementToPsi(first);
				final JavaPsiImplementationHelper instance = JavaPsiImplementationHelper.getInstance(list.getProject());
				if(instance != null)
				{
					anchor = instance.getDefaultImportAnchor(list, statement);
				}
				before = Boolean.TRUE;
			}
		}
		return super.addInternal(first, last, anchor, before);
	}
}
