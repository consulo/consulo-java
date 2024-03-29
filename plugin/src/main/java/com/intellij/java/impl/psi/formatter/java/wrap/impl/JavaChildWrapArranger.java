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
package com.intellij.java.impl.psi.formatter.java.wrap.impl;

import com.intellij.java.impl.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.java.impl.psi.formatter.java.AbstractJavaBlock;
import com.intellij.java.impl.psi.formatter.java.JavaFormatterUtil;
import com.intellij.java.impl.psi.formatter.java.wrap.JavaWrapManager;
import com.intellij.java.impl.psi.formatter.java.wrap.ReservedWrapsProvider;
import com.intellij.java.language.impl.psi.impl.source.tree.ChildRole;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.psi.*;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.ast.TokenType;
import consulo.language.codeStyle.CommonCodeStyleSettings;
import consulo.language.codeStyle.FormatterUtil;
import consulo.language.codeStyle.Wrap;
import consulo.language.codeStyle.WrapType;
import consulo.language.impl.ast.CompositeElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiErrorElement;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.DumbService;
import consulo.util.collection.ArrayUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import static com.intellij.java.impl.psi.formatter.java.JavaFormatterUtil.getWrapType;
import static com.intellij.java.language.impl.psi.impl.PsiImplUtil.isTypeAnnotation;

/**
 * Encapsulates the implementation of
 * {@link JavaWrapManager#arrangeChildWrap(ASTNode, ASTNode, CommonCodeStyleSettings, Wrap, ReservedWrapsProvider)}.
 * <p>
 * Thread-safe.
 *
 * @author Denis Zhdanov
 * @since Apr 21, 2010
 */
public class JavaChildWrapArranger
{
	/**
	 * Provides implementation of {@link JavaWrapManager#arrangeChildWrap} method.
	 *
	 * @param child                 child node which {@link Wrap wrap} is to be defined
	 * @param parent                direct or indirect parent of the given {@code 'child'} node. Defines usage context
	 *                              of {@code 'child'} node processing
	 * @param settings              code style settings to use during wrap definition
	 * @param suggestedWrap         wrap suggested to use by clients of current class. I.e. those clients offer wrap to
	 *                              use based on their information about current processing state. However, it's possible
	 *                              that they don't know details of fine-grained wrap definition algorithm encapsulated
	 *                              at the current class. Hence, this method takes suggested wrap into consideration but
	 *                              is not required to use it all the time node based on the given parameters
	 * @param reservedWrapsProvider reserved {@code 'element type -> wrap instance'} mappings provider. <b>Note:</b> this
	 *                              argument is considered to be a part of legacy heritage and is intended to be removed as
	 *                              soon as formatting code refactoring is done
	 * @return wrap to use for the given {@code 'child'} node if it's possible to define the one;
	 * {@code null} otherwise
	 */
	@SuppressWarnings({"MethodMayBeStatic"})
	@Nullable
	public Wrap arrange(ASTNode child, ASTNode parent, CommonCodeStyleSettings settings, JavaCodeStyleSettings javaSettings, Wrap suggestedWrap, AbstractJavaBlock reservedWrapsProvider)
	{
		ASTNode directParent = child.getTreeParent();
		int role = ((CompositeElement) directParent).getChildRole(child);

		if(parent instanceof PsiPolyadicExpression)
		{
			if(role == ChildRole.OPERATION_SIGN && !settings.BINARY_OPERATION_SIGN_ON_NEXT_LINE)
			{
				return null;
			}
			boolean rOperand = ArrayUtil.indexOf(((PsiPolyadicExpression) parent).getOperands(), child.getPsi()) > 0;
			if(settings.BINARY_OPERATION_SIGN_ON_NEXT_LINE && rOperand)
			{
				return null;
			}
			return suggestedWrap;
		}

		IElementType nodeType = parent.getElementType();
		IElementType childType = child.getElementType();

		if(childType == JavaElementType.EXTENDS_LIST || childType == JavaElementType.IMPLEMENTS_LIST)
		{
			return Wrap.createWrap(settings.EXTENDS_KEYWORD_WRAP, true);
		}

		else if(childType == JavaElementType.THROWS_LIST)
		{
			return Wrap.createWrap(settings.THROWS_KEYWORD_WRAP, true);
		}

		else if(nodeType == JavaElementType.EXTENDS_LIST || nodeType == JavaElementType.IMPLEMENTS_LIST)
		{
			if(role == ChildRole.REFERENCE_IN_LIST)
			{
				return suggestedWrap;
			}
			else
			{
				return null;
			}
		}

		else if(nodeType == JavaElementType.THROWS_LIST)
		{
			if(role == ChildRole.REFERENCE_IN_LIST)
			{
				return suggestedWrap;
			}
			else
			{
				return null;
			}
		}

		else if(nodeType == JavaElementType.CONDITIONAL_EXPRESSION)
		{
			if(role == ChildRole.COLON && !settings.TERNARY_OPERATION_SIGNS_ON_NEXT_LINE)
			{
				return null;
			}
			if(role == ChildRole.QUEST && !settings.TERNARY_OPERATION_SIGNS_ON_NEXT_LINE)
			{
				return null;
			}
			if(role == ChildRole.THEN_EXPRESSION && settings.TERNARY_OPERATION_SIGNS_ON_NEXT_LINE)
			{
				return null;
			}
			if(role == ChildRole.ELSE_EXPRESSION && settings.TERNARY_OPERATION_SIGNS_ON_NEXT_LINE)
			{
				return null;
			}
			return suggestedWrap;
		}

		else if(JavaFormatterUtil.isAssignment(parent) && role != ChildRole.TYPE)
		{
			if(role == ChildRole.INITIALIZER_EQ)
			{
				return settings.PLACE_ASSIGNMENT_SIGN_ON_NEXT_LINE ? suggestedWrap : null;
			}
			if(role == ChildRole.OPERATION_SIGN)
			{
				return settings.PLACE_ASSIGNMENT_SIGN_ON_NEXT_LINE ? suggestedWrap : null;
			}
			if(role == ChildRole.INITIALIZER)
			{
				return settings.PLACE_ASSIGNMENT_SIGN_ON_NEXT_LINE ? null : suggestedWrap;
			}
			if(role == ChildRole.ROPERAND)
			{
				return settings.PLACE_ASSIGNMENT_SIGN_ON_NEXT_LINE ? null : suggestedWrap;
			}
			if(role == ChildRole.CLOSING_SEMICOLON)
			{
				return null;
			}
			return suggestedWrap;
		}

		else if(nodeType == JavaElementType.REFERENCE_EXPRESSION)
		{
			if(role == ChildRole.DOT)
			{
				return reservedWrapsProvider.getReservedWrap(JavaElementType.REFERENCE_EXPRESSION);
			}
			else
			{
				return suggestedWrap;
			}
		}

		else if(nodeType == JavaElementType.FOR_STATEMENT)
		{
			if(role == ChildRole.FOR_INITIALIZATION || role == ChildRole.CONDITION || role == ChildRole.FOR_UPDATE)
			{
				return suggestedWrap;
			}
			if(role == ChildRole.LOOP_BODY)
			{
				final boolean dontWrap = (childType == JavaElementType.CODE_BLOCK || childType == JavaElementType.BLOCK_STATEMENT) && settings.BRACE_STYLE == CommonCodeStyleSettings.END_OF_LINE;
				return Wrap.createWrap(dontWrap ? WrapType.NONE : WrapType.NORMAL, true);
			}
			else
			{
				return null;
			}
		}

		else if(parent.getPsi() instanceof PsiModifierListOwner)
		{
			ASTNode prev = FormatterUtil.getPreviousNonWhitespaceSibling(child);
			if(prev != null && prev.getElementType() == JavaElementType.MODIFIER_LIST)
			{
				ASTNode last = prev.getLastChildNode();
				if(last != null && last.getElementType() == JavaElementType.ANNOTATION)
				{
					if(isTypeAnnotationOrFalseIfDumb(last) || javaSettings.DO_NOT_WRAP_AFTER_SINGLE_ANNOTATION && isFieldModifierListWithSingleAnnotation(prev))
					{
						return Wrap.createWrap(WrapType.NONE, false);
					}
					else
					{
						return Wrap.createWrap(getWrapType(getAnnotationWrapType(parent, child, settings)), true);
					}
				}
			}

			return null;
		}

		else if(nodeType == JavaElementType.MODIFIER_LIST)
		{
			if(childType == JavaElementType.ANNOTATION)
			{
				if(isTypeAnnotationOrFalseIfDumb(child))
				{
					ASTNode prev = FormatterUtil.getPreviousNonWhitespaceSibling(child);
					if(prev == null || prev.getElementType() != JavaElementType.ANNOTATION || isTypeAnnotationOrFalseIfDumb(prev))
					{
						return Wrap.createWrap(WrapType.NONE, false);
					}
				}

				return Wrap.createWrap(getWrapType(getAnnotationWrapType(parent.getTreeParent(), child, settings)), true);
			}
			else if(childType == JavaTokenType.END_OF_LINE_COMMENT)
			{
				return Wrap.createWrap(WrapType.NORMAL, true);
			}

			ASTNode prev = FormatterUtil.getPreviousNonWhitespaceSibling(child);
			if(prev != null && prev.getElementType() == JavaElementType.ANNOTATION)
			{
				if(javaSettings.DO_NOT_WRAP_AFTER_SINGLE_ANNOTATION && isFieldModifierListWithSingleAnnotation(parent))
				{
					return Wrap.createWrap(WrapType.NONE, false);
				}
				Wrap wrap = Wrap.createWrap(getWrapType(getAnnotationWrapType(parent.getTreeParent(), child, settings)), true);
				putPreferredWrapInParentBlock(reservedWrapsProvider, wrap);
				return wrap;
			}

			return null;
		}

		else if(nodeType == JavaElementType.ASSERT_STATEMENT)
		{
			if(role == ChildRole.CONDITION)
			{
				return suggestedWrap;
			}
			if(role == ChildRole.ASSERT_DESCRIPTION && !settings.ASSERT_STATEMENT_COLON_ON_NEXT_LINE)
			{
				return suggestedWrap;
			}
			if(role == ChildRole.COLON && settings.ASSERT_STATEMENT_COLON_ON_NEXT_LINE)
			{
				return suggestedWrap;
			}
			return null;
		}

		else if(nodeType == JavaElementType.CODE_BLOCK)
		{
			if(child.getPsi() instanceof PsiStatement)
			{
				return suggestedWrap;
			}
			else
			{
				return null;
			}
		}

		else if(nodeType == JavaElementType.IF_STATEMENT)
		{
			if(childType == JavaElementType.IF_STATEMENT && role == ChildRole.ELSE_BRANCH && settings.SPECIAL_ELSE_IF_TREATMENT)
			{
				return Wrap.createWrap(WrapType.NONE, false);
			}
			if(role == ChildRole.THEN_BRANCH || role == ChildRole.ELSE_BRANCH)
			{
				if(childType == JavaElementType.BLOCK_STATEMENT)
				{
					return null;
				}
				else
				{
					return Wrap.createWrap(WrapType.NORMAL, true);
				}
			}
		}

		else if(nodeType == JavaElementType.FOREACH_STATEMENT || nodeType == JavaElementType.WHILE_STATEMENT)
		{
			if(role == ChildRole.LOOP_BODY)
			{
				if(childType == JavaElementType.BLOCK_STATEMENT)
				{
					return null;
				}
				else
				{
					return Wrap.createWrap(WrapType.NORMAL, true);
				}
			}
		}

		else if(nodeType == JavaElementType.DO_WHILE_STATEMENT)
		{
			if(role == ChildRole.LOOP_BODY)
			{
				return Wrap.createWrap(WrapType.NORMAL, true);
			}
			else if(role == ChildRole.WHILE_KEYWORD)
			{
				return Wrap.createWrap(WrapType.NORMAL, true);
			}
		}

		else if(nodeType == JavaElementType.ANNOTATION_ARRAY_INITIALIZER)
		{
			if(suggestedWrap != null)
			{
				return suggestedWrap;
			}
			if(role == ChildRole.ANNOTATION_VALUE)
			{
				return Wrap.createWrap(WrapType.NORMAL, true);
			}
		}

		return suggestedWrap;
	}

	private static boolean isTypeAnnotationOrFalseIfDumb(@Nonnull ASTNode child)
	{
		PsiElement node = child.getPsi();
		PsiElement next = PsiTreeUtil.skipSiblingsForward(node, PsiWhiteSpace.class, PsiAnnotation.class);
		if(next instanceof PsiKeyword)
		{
			return false;
		}
		return !DumbService.isDumb(node.getProject()) && isTypeAnnotation(node);
	}

	private static void putPreferredWrapInParentBlock(@Nonnull AbstractJavaBlock block, @Nonnull Wrap preferredWrap)
	{
		AbstractJavaBlock parentBlock = block.getParentBlock();
		if(parentBlock != null)
		{
			parentBlock.setReservedWrap(preferredWrap, JavaElementType.MODIFIER_LIST);
		}
	}

	private static boolean isFieldModifierListWithSingleAnnotation(@Nonnull ASTNode elem)
	{
		ASTNode parent = elem.getTreeParent();
		if(parent != null && parent.getElementType() == JavaElementType.FIELD)
		{
			return isModifierListWithSingleAnnotation(elem);
		}
		return false;
	}

	private static boolean isModifierListWithSingleAnnotation(@Nonnull ASTNode elem)
	{
		if(elem.getPsi() instanceof PsiModifierList)
		{
			if(((PsiModifierList) elem.getPsi()).getAnnotations().length == 1)
			{
				return true;
			}
		}
		return false;
	}

	private static int getAnnotationWrapType(ASTNode parent, ASTNode child, CommonCodeStyleSettings settings)
	{
		IElementType nodeType = parent.getElementType();

		if(nodeType == JavaElementType.METHOD)
		{
			return settings.METHOD_ANNOTATION_WRAP;
		}

		if(nodeType == JavaElementType.CLASS)
		{
			// There is a possible case that current document state is invalid from language syntax point of view, e.g. the user starts
			// typing field definition and re-formatting is triggered by 'auto insert javadoc' processing. Example:
			//     class Test {
			//         @NotNull Object
			//     }
			// Here '@NotNull' has a 'class' node as a parent but we want to use field annotation setting value.
			// Hence we check if subsequent parsed info is valid.
			for(ASTNode node = child.getTreeNext(); node != null; node = node.getTreeNext())
			{
				if(node.getElementType() == TokenType.WHITE_SPACE || node instanceof PsiTypeElement)
				{
					continue;
				}
				if(node instanceof PsiErrorElement)
				{
					return settings.FIELD_ANNOTATION_WRAP;
				}
			}
			return settings.CLASS_ANNOTATION_WRAP;
		}

		if(nodeType == JavaElementType.FIELD)
		{
			return settings.FIELD_ANNOTATION_WRAP;
		}

		if(nodeType == JavaElementType.PARAMETER ||
				nodeType == JavaElementType.RECEIVER_PARAMETER ||
				nodeType == JavaElementType.RESOURCE_VARIABLE)
		{
			return settings.PARAMETER_ANNOTATION_WRAP;
		}

		if(nodeType == JavaElementType.LOCAL_VARIABLE)
		{
			return settings.VARIABLE_ANNOTATION_WRAP;
		}

		return CommonCodeStyleSettings.DO_NOT_WRAP;
	}
}
