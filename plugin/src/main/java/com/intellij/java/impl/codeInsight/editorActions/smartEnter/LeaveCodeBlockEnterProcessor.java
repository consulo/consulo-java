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
package com.intellij.java.impl.codeInsight.editorActions.smartEnter;

import consulo.codeEditor.action.EditorActionManager;
import consulo.dataContext.DataManager;
import consulo.language.ast.ASTNode;
import consulo.dataContext.DataContext;
import consulo.ui.ex.action.IdeActions;
import consulo.codeEditor.Editor;
import consulo.codeEditor.action.EditorActionHandler;
import com.intellij.java.language.psi.PsiCodeBlock;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiReturnStatement;
import com.intellij.java.language.psi.PsiThrowStatement;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import consulo.language.ast.TokenSet;
import consulo.util.lang.CharArrayUtil;
import jakarta.annotation.Nullable;

public class LeaveCodeBlockEnterProcessor implements EnterProcessor
{

	private static final TokenSet CONTROL_FLOW_ELEMENT_TYPES = TokenSet.create(JavaElementType.IF_STATEMENT, JavaElementType.WHILE_STATEMENT, JavaElementType.DO_WHILE_STATEMENT, JavaElementType
			.FOR_STATEMENT, JavaElementType.FOREACH_STATEMENT);

	@Override
	public boolean doEnter(Editor editor, PsiElement psiElement, boolean isModified)
	{
		PsiElement parent = psiElement.getParent();
		if(!(parent instanceof PsiCodeBlock))
		{
			return false;
		}

		final ASTNode node = psiElement.getNode();
		if(node != null && CONTROL_FLOW_ELEMENT_TYPES.contains(node.getElementType()))
		{
			return false;
		}

		boolean leaveCodeBlock = isControlFlowBreak(psiElement);
		if(!leaveCodeBlock)
		{
			return false;
		}

		final int offset = parent.getTextRange().getEndOffset();

		// Check if there is empty line after the code block. Just move caret there in the case of the positive answer.
		final CharSequence text = editor.getDocument().getCharsSequence();
		if(offset < text.length() - 1)
		{
			final int i = CharArrayUtil.shiftForward(text, offset + 1, " \t");
			if(i < text.length() && text.charAt(i) == '\n')
			{
				editor.getCaretModel().moveToOffset(offset + 1);
				EditorActionManager actionManager = EditorActionManager.getInstance();
				EditorActionHandler actionHandler = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_MOVE_LINE_END);
				final DataContext dataContext = DataManager.getInstance().getDataContext(editor.getComponent());
				if(dataContext != null)
				{
					actionHandler.execute(editor, dataContext);
					return true;
				}
			}
		}

		editor.getCaretModel().moveToOffset(offset);
		return false;
	}

	/**
	 * Handles situations like the one below:
	 * <pre>
	 *   void foo(int i) {
	 *     if (i < 0) {
	 *       return;[caret]
	 *     }
	 *   }
	 * </pre>
	 * <p>
	 * <b>Output:</b>
	 * <pre>
	 *   void foo(int i) {
	 *     if (i < 0) {
	 *       return;
	 *     }
	 *     [caret]
	 *   }
	 * </pre>
	 *
	 * @param element
	 * @return
	 */
	private static boolean isControlFlowBreak(@Nullable PsiElement element)
	{
		return element instanceof PsiReturnStatement || element instanceof PsiThrowStatement;
	}
}
