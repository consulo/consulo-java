// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.inference;

import consulo.language.ast.LighterAST;
import consulo.language.ast.LighterASTNode;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.impl.psi.impl.source.FileLocalResolver;
import com.intellij.java.language.impl.psi.impl.source.JavaLightTreeUtil;
import consulo.language.ast.LightTreeUtil;
import consulo.language.ast.IElementType;
import consulo.util.collection.ContainerUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType.*;

class PurityInferenceVisitor
{
	private final LighterAST tree;
	private final LighterASTNode body;
	private final Map<String, LighterASTNode> myFieldModifiers;
	private final List<LighterASTNode> mutatedRefs = new ArrayList<>();
	private final boolean constructor;
	private boolean hasVolatileReads;
	private final List<LighterASTNode> calls = new ArrayList<>();

	PurityInferenceVisitor(LighterAST tree, LighterASTNode body, Map<String, LighterASTNode> fieldModifiers, boolean isConstructor)
	{
		this.tree = tree;
		this.body = body;
		this.constructor = isConstructor;
		myFieldModifiers = fieldModifiers;
	}

	void visitNode(LighterASTNode element)
	{
		IElementType type = element.getTokenType();
		if(type == ASSIGNMENT_EXPRESSION)
		{
			addMutation(tree.getChildren(element).get(0));
		}
		else if((type == PREFIX_EXPRESSION || type == POSTFIX_EXPRESSION) && isMutatingOperation(element))
		{
			addMutation(JavaLightTreeUtil.findExpressionChild(tree, element));
		}
		else if(isCall(element, type))
		{
			calls.add(element);
		}
		else if(type == REFERENCE_EXPRESSION && !myFieldModifiers.isEmpty())
		{
			if(isEffectivelyUnqualified(element))
			{
				LighterASTNode modifiers = myFieldModifiers.get(JavaLightTreeUtil.getNameIdentifierText(tree, element));
				boolean isVolatile = LightTreeUtil.firstChildOfType(tree, modifiers, JavaTokenType.VOLATILE_KEYWORD) != null;
				if(isVolatile)
				{
					LighterASTNode target = new FileLocalResolver(tree).resolveLocally(element).getTarget();
					if(target != null && target.getTokenType() == FIELD &&
							JavaLightTreeUtil.hasExplicitModifier(tree, target, JavaTokenType.VOLATILE_KEYWORD))
					{
						hasVolatileReads = true;
					}
				}
			}
		}
	}

	private boolean isEffectivelyUnqualified(LighterASTNode element)
	{
		LighterASTNode qualifier = JavaLightTreeUtil.findExpressionChild(tree, element);
		return qualifier == null || qualifier.getTokenType() == THIS_EXPRESSION &&
				JavaLightTreeUtil.findExpressionChild(tree, qualifier) == null;
	}

	private void addMutation(LighterASTNode mutated)
	{
		if(mutated == null)
		{
			return;
		}
		if(constructor && !myFieldModifiers.isEmpty())
		{
			IElementType type = mutated.getTokenType();
			// writes to own fields in constructor do not count as mutations
			if(type == REFERENCE_EXPRESSION && isEffectivelyUnqualified(mutated))
			{
				LighterASTNode modifiers = myFieldModifiers.get(JavaLightTreeUtil.getNameIdentifierText(tree, mutated));
				if(modifiers != null)
				{
					boolean isStatic = LightTreeUtil.firstChildOfType(tree, modifiers, JavaTokenType.STATIC_KEYWORD) != null;
					if(!isStatic)
					{
						return;
					}
				}
			}
		}
		mutatedRefs.add(mutated);
	}

	private boolean isCall(@Nonnull LighterASTNode element, IElementType type)
	{
		return type == NEW_EXPRESSION &&
				(LightTreeUtil.firstChildOfType(tree, element, EXPRESSION_LIST) != null ||
						LightTreeUtil.firstChildOfType(tree, element, ANONYMOUS_CLASS) != null) ||
				type == METHOD_CALL_EXPRESSION;
	}

	private boolean isMutatingOperation(@Nonnull LighterASTNode element)
	{
		return LightTreeUtil.firstChildOfType(tree, element, JavaTokenType.PLUSPLUS) != null ||
				LightTreeUtil.firstChildOfType(tree, element, JavaTokenType.MINUSMINUS) != null;
	}

	@Nullable
	PurityInferenceResult getResult()
	{
		if(calls.size() > 1 || (!constructor && hasVolatileReads))
		{
			return null;
		}

		int bodyStart = body.getStartOffset();
		return new PurityInferenceResult(ContainerUtil.map(mutatedRefs, node -> ExpressionRange.create(node, bodyStart)),
				calls.isEmpty() ? null : ExpressionRange.create(calls.get(0), bodyStart));
	}
}
