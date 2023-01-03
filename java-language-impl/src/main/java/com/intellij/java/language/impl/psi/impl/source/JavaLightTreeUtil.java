/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import consulo.language.ast.LighterAST;
import consulo.language.ast.LighterASTNode;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.impl.psi.impl.cache.RecordUtil;
import com.intellij.java.language.impl.psi.impl.source.tree.ElementType;
import consulo.language.ast.LightTreeUtil;
import consulo.language.ast.IElementType;
import org.jetbrains.annotations.Contract;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.List;

import static com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType.*;

/**
 * @author peter
 */
public class JavaLightTreeUtil
{
	@Nullable
	@Contract("_,null->null")
	public static List<LighterASTNode> getArgList(@Nonnull LighterAST tree, @Nullable LighterASTNode call)
	{
		LighterASTNode anonClass = LightTreeUtil.firstChildOfType(tree, call, ANONYMOUS_CLASS);
		LighterASTNode exprList = LightTreeUtil.firstChildOfType(tree, anonClass != null ? anonClass : call, EXPRESSION_LIST);
		return exprList == null ? null : getExpressionChildren(tree, exprList);
	}

	@Nullable
	@Contract("_,null->null")
	public static String getNameIdentifierText(@Nonnull LighterAST tree, @Nullable LighterASTNode idOwner)
	{
		LighterASTNode id = LightTreeUtil.firstChildOfType(tree, idOwner, JavaTokenType.IDENTIFIER);
		return id != null ? RecordUtil.intern(tree.getCharTable(), id) : null;
	}

	@Nonnull
	public static List<LighterASTNode> getExpressionChildren(@Nonnull LighterAST tree, @Nonnull LighterASTNode node)
	{
		return LightTreeUtil.getChildrenOfType(tree, node, ElementType.EXPRESSION_BIT_SET);
	}

	@Nullable
	public static LighterASTNode findExpressionChild(@Nonnull LighterAST tree, @Nullable LighterASTNode node)
	{
		return LightTreeUtil.firstChildOfType(tree, node, ElementType.EXPRESSION_BIT_SET);
	}

	@Nullable
	public static LighterASTNode skipParenthesesCastsDown(@Nonnull LighterAST tree, @Nullable LighterASTNode node)
	{
		while(node != null && (node.getTokenType() == PARENTH_EXPRESSION || node.getTokenType() == TYPE_CAST_EXPRESSION))
		{
			node = findExpressionChild(tree, node);
		}
		return node;
	}

	@Nullable
	public static LighterASTNode skipParenthesesDown(@Nonnull LighterAST tree, @Nullable LighterASTNode expression)
	{
		while(expression != null && expression.getTokenType() == PARENTH_EXPRESSION)
		{
			expression = findExpressionChild(tree, expression);
		}
		return expression;
	}

	/**
	 * Returns true if given element (which is modifier list owner) has given explicit modifier
	 *
	 * @param tree              an AST tree
	 * @param modifierListOwner element to check modifier of
	 * @param modifierKeyword   modifier to look for (e.g. {@link JavaTokenType#VOLATILE_KEYWORD}
	 * @return true if given element has given explicit modifier
	 */
	public static boolean hasExplicitModifier(@Nonnull LighterAST tree,
											  @Nullable LighterASTNode modifierListOwner,
											  @Nonnull IElementType modifierKeyword)
	{
		LighterASTNode modifierList = LightTreeUtil.firstChildOfType(tree, modifierListOwner, MODIFIER_LIST);
		return LightTreeUtil.firstChildOfType(tree, modifierList, modifierKeyword) != null;
	}
}
