package com.intellij.java.analysis.impl.codeInspection.dataFlow.inference;

import com.intellij.java.language.impl.psi.impl.source.JavaLightTreeUtil;
import com.intellij.java.language.impl.psi.impl.source.tree.ElementType;
import com.intellij.java.language.psi.CommonClassNames;
import com.intellij.java.language.psi.JavaTokenType;
import consulo.language.ast.*;
import consulo.util.collection.ContainerUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

import static com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType.*;

/**
 * from kotlin
 */
class ParameterNullityInferenceKt {
  static BitSet inferNotNullParameters(LighterAST tree, LighterASTNode method, List<LighterASTNode> statements) {
    List<String> parameterNames = getParameterNames(tree, method);
    return inferNotNullParameters(tree, parameterNames, statements);
  }

  private static BitSet inferNotNullParameters(LighterAST tree, List<String> parameterNames, List<LighterASTNode> statements) {
    Set<String> canBeNulls = new HashSet<>(ContainerUtil.mapNotNull(parameterNames, it -> it));
    if (canBeNulls.isEmpty()) {
      return new BitSet();
    }
    Set<String> notNulls = new HashSet<>();
    Deque<LighterASTNode> queue = new ArrayDeque<>(statements);

    while (!queue.isEmpty() && !canBeNulls.isEmpty()) {
      LighterASTNode element = queue.removeFirst();
      IElementType type = element.getTokenType();

      if (type == CONDITIONAL_EXPRESSION || type == EXPRESSION_STATEMENT) {
        LighterASTNode let = JavaLightTreeUtil.findExpressionChild(tree, element);
        if (let != null) {
          queue.addFirst(let);
        }
      } else if (type == RETURN_STATEMENT) {
        queue.clear();
        LighterASTNode let = JavaLightTreeUtil.findExpressionChild(tree, element);
        if (let != null) {
          queue.addFirst(let);
        }
      } else if (type == FOR_STATEMENT) {
        LighterASTNode condition = JavaLightTreeUtil.findExpressionChild(tree, element);
        queue.clear();
        if (condition != null) {
          queue.addFirst(condition);
          LighterASTNode let = LightTreeUtil.firstChildOfType(tree, element, ElementType.JAVA_STATEMENT_BIT_SET);
          if (let != null) {
            queue.addFirst(let);
          }
        } else {
          // no condition == endless loop: we may analyze body (at least until break/return/if/etc.)
          ContainerUtil.reverse(tree.getChildren(element)).forEach(queue::addFirst);
        }
      } else if (type == WHILE_STATEMENT) {
        queue.clear();
        LighterASTNode expression = JavaLightTreeUtil.findExpressionChild(tree, element);
        if (expression != null && expression.getTokenType() == LITERAL_EXPRESSION && LightTreeUtil.firstChildOfType(tree, expression, JavaTokenType.TRUE_KEYWORD) != null) {
          // while(true) == endless loop: we may analyze body (at least until break/return/if/etc.)
          ContainerUtil.reverse(tree.getChildren(element)).forEach(queue::addFirst);
        } else {
          dereference(tree, expression, canBeNulls, notNulls, queue);
        }
      } else if (type == FOREACH_STATEMENT || type == SWITCH_STATEMENT || type == IF_STATEMENT || type == THROW_STATEMENT) {
        queue.clear();
        LighterASTNode expression = JavaLightTreeUtil.findExpressionChild(tree, element);
        dereference(tree, expression, canBeNulls, notNulls, queue);
      } else if (type == BINARY_EXPRESSION || type == POLYADIC_EXPRESSION) {
        if (LightTreeUtil.firstChildOfType(tree, element, TokenSet.create(JavaTokenType.ANDAND, JavaTokenType.OROR)) != null) {
          LighterASTNode let = JavaLightTreeUtil.findExpressionChild(tree, element);
          if (let != null) {
            queue.addFirst(let);
          }
        } else {
          ContainerUtil.reverse(tree.getChildren(element)).forEach(queue::addFirst);
        }
      } else if (type == EMPTY_STATEMENT || type == ASSERT_STATEMENT || type == DO_WHILE_STATEMENT || type == DECLARATION_STATEMENT || type == BLOCK_STATEMENT) {
        ContainerUtil.reverse(tree.getChildren(element)).forEach(queue::addFirst);
      } else if (type == SYNCHRONIZED_STATEMENT) {
        LighterASTNode sync = JavaLightTreeUtil.findExpressionChild(tree, element);
        dereference(tree, sync, canBeNulls, notNulls, queue);

        LighterASTNode let = LightTreeUtil.firstChildOfType(tree, element, CODE_BLOCK);
        if (let != null) {
          queue.addFirst(let);
        }
      } else if (type == FIELD || type == PARAMETER || type == LOCAL_VARIABLE) {
        canBeNulls.remove(JavaLightTreeUtil.getNameIdentifierText(tree, element));
        LighterASTNode let = JavaLightTreeUtil.findExpressionChild(tree, element);
        if (let != null) {
          queue.addFirst(let);
        }
      } else if (type == EXPRESSION_LIST) {
        List<LighterASTNode> children = JavaLightTreeUtil.getExpressionChildren(tree, element);
        // When parameter is passed to another method, that method may have "null -> fail" contract,
        // so without knowing this we cannot continue inference for the parameter
        children.forEach(it -> ignore(tree, it, canBeNulls));
        ContainerUtil.reverse(children).forEach(queue::addFirst);
      } else if (type == ASSIGNMENT_EXPRESSION) {
        LighterASTNode lvalue = JavaLightTreeUtil.findExpressionChild(tree, element);
        ignore(tree, lvalue, canBeNulls);
        ContainerUtil.reverse(tree.getChildren(element)).forEach(queue::addFirst);
      } else if (type == ARRAY_ACCESS_EXPRESSION) {
        JavaLightTreeUtil.getExpressionChildren(tree, element).forEach(it -> dereference(tree, it, canBeNulls, notNulls, queue));
      } else if (type == METHOD_REF_EXPRESSION || type == REFERENCE_EXPRESSION) {
        LighterASTNode qualifier = JavaLightTreeUtil.findExpressionChild(tree, element);
        dereference(tree, qualifier, canBeNulls, notNulls, queue);
      } else if (type == CLASS || type == METHOD || type == LAMBDA_EXPRESSION) {
        // Ignore classes, methods and lambda expression bodies as it's not known whether they will be instantiated/executed.
        // For anonymous classes argument list, field initializers and instance initialization sections are checked.
      } else if (type == TRY_STATEMENT) {
        queue.clear();

        List<LighterASTNode> params = ContainerUtil.mapNotNull(LightTreeUtil.getChildrenOfType(tree, element, CATCH_SECTION), it -> LightTreeUtil.firstChildOfType(tree, it, PARAMETER));

        List<LighterASTNode> paramTypes = ContainerUtil.map(params, parameter -> LightTreeUtil.firstChildOfType(tree, parameter, TYPE));

        boolean canCatchNpe = ContainerUtil.or(paramTypes, it -> canCatchNpe(tree, it));
        if (!canCatchNpe) {
          LightTreeUtil.getChildrenOfType(tree, element, RESOURCE_LIST).forEach(queue::addFirst);

          LighterASTNode let = LightTreeUtil.firstChildOfType(tree, element, CODE_BLOCK);
          if (let != null) {
            queue.addFirst(let);
          }

          // stop analysis after first try as we are not sure how execution goes further:
          // whether or not it visit catch blocks, etc.
        }
      } else if (ElementType.JAVA_STATEMENT_BIT_SET.contains(type)) {
        // Unknown/unprocessed statement: just stop processing the rest of the method
        queue.clear();
      } else {
        ContainerUtil.reverse(tree.getChildren(element)).forEach(queue::addFirst);
      }
    }

    BitSet notNullParameters = new BitSet();

    for (int index = 0; index < parameterNames.size(); index++) {
      String parameterName = parameterNames.get(index);

      if (notNulls.contains(parameterName)) {
        notNullParameters.set(index);
      }
    }

    return notNullParameters;
  }

  private static final Set<String> NPC_CATCHES = Set.of("Throwable", "Exception", "RuntimeException", "NullPointerException",
      CommonClassNames.JAVA_LANG_THROWABLE, CommonClassNames.JAVA_LANG_EXCEPTION,
      CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION, CommonClassNames.JAVA_LANG_NULL_POINTER_EXCEPTION);

  private static boolean canCatchNpe(LighterAST tree, @Nullable LighterASTNode type) {
    if (type == null) {
      return false;
    }
    LighterASTNode codeRef = LightTreeUtil.firstChildOfType(tree, type, JAVA_CODE_REFERENCE);
    String name = JavaLightTreeUtil.getNameIdentifierText(tree, codeRef);
    if (name == null) {
      // Multicatch
      return ContainerUtil.or(LightTreeUtil.getChildrenOfType(tree, type, TYPE), it -> canCatchNpe(tree, it));
    }
    return NPC_CATCHES.contains(name);
  }

  private static void ignore(LighterAST tree, @Nullable LighterASTNode expression, Set<String> canBeNulls) {
    if (expression != null && expression.getTokenType() == REFERENCE_EXPRESSION && JavaLightTreeUtil.findExpressionChild(tree, expression) == null) {
      canBeNulls.remove(JavaLightTreeUtil.getNameIdentifierText(tree, expression));
    }
  }

  private static void dereference(LighterAST tree, LighterASTNode expression, Set<String> canBeNulls, Set<String> notNulls, Deque<LighterASTNode> queue) {
    if (expression == null) {
      return;
    }

    if (expression.getTokenType() == REFERENCE_EXPRESSION && JavaLightTreeUtil.findExpressionChild(tree, expression) == null) {
      String name = JavaLightTreeUtil.getNameIdentifierText(tree, expression);
      if (canBeNulls.remove(name)) {
        notNulls.add(name);
      }
    } else {
      queue.addFirst(expression);
    }
  }

  @Nonnull
  private static List<String> getParameterNames(LighterAST tree, LighterASTNode method) {
    LighterASTNode parameterList = LightTreeUtil.firstChildOfType(tree, method, PARAMETER_LIST);
    if (parameterList == null) {
      return Collections.emptyList();
    }
    List<LighterASTNode> parameters = LightTreeUtil.getChildrenOfType(tree, parameterList, PARAMETER);

    return ContainerUtil.map(parameters, it -> {
      if (LightTreeUtil.firstChildOfType(tree, it, ElementType.PRIMITIVE_TYPE_BIT_SET) != null) {
        return null;
      }
      return JavaLightTreeUtil.getNameIdentifierText(tree, it);
    });
  }
}
