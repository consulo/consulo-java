package com.intellij.java.analysis.impl.codeInspection.dataFlow.inference;

import com.intellij.java.language.impl.psi.impl.source.JavaLightStubBuilder;
import com.intellij.java.language.impl.psi.impl.source.JavaLightTreeUtil;
import com.intellij.java.language.psi.JavaTokenType;
import consulo.language.ast.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Consumer;

import static com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType.*;

/**
 * from kotlin
 */
class InferenceVisitor extends RecursiveLighterASTNodeWalkingVisitor {
  @Nonnull
  private final LighterAST tree;

  private Map<Integer, MethodData> result = new HashMap<>();
  private int methodIndex = 0;
  private Map<LighterASTNode, ClassData> classData = new HashMap<>();

  InferenceVisitor(@Nonnull LighterAST tree) {
    super(tree);
    this.tree = tree;
  }

  @Override
  public void visitNode(@Nonnull LighterASTNode element) {
    IElementType tokenType = element.getTokenType();
    if (tokenType == CLASS || tokenType == ANONYMOUS_CLASS) {
      classData.put(element, calcClassData(element));
    } else if (tokenType == METHOD) {
      MethodData data = calcData(element);
      if (data != null) {
        result.put(methodIndex, data);
      }
      methodIndex++;
    }

    if (JavaLightStubBuilder.isCodeBlockWithoutStubs(element)) {
      return;
    }
    super.visitNode(element);
  }

  @Nullable
  private MethodData calcData(LighterASTNode method) {
    LighterASTNode body = LightTreeUtil.firstChildOfType(tree, method, CODE_BLOCK);
    if (body == null) {
      return null;
    }
    ClassData clsData = this.classData.get(tree.getParent(method));
    Map<String, LighterASTNode> fieldMap = clsData == null ? Collections.emptyMap() : clsData.getFieldModifiers();
    // Constructor which has super classes may implicitly call impure super constructor, so don't infer purity for subclasses
    boolean ctor = LightTreeUtil.firstChildOfType(tree, method, TYPE) == null;
    boolean maybeImpureCtor = ctor && (clsData == null || clsData.isHasSuper() || !clsData.isHasPureInitializer());
    List<LighterASTNode> statements = ContractInferenceInterpreter.getStatements(body, tree);

    ContractInferenceInterpreter contractInference = new ContractInferenceInterpreter(tree, method, body);
    List<PreContract> contracts = contractInference.inferContracts(statements);

    MethodReturnInferenceVisitor nullityVisitor = new MethodReturnInferenceVisitor(tree, contractInference.getParameters(), body);
    PurityInferenceVisitor purityVisitor = new PurityInferenceVisitor(tree, body, fieldMap, ctor);
    for (LighterASTNode statement : statements) {
      walkMethodBody(statement, it -> {
        nullityVisitor.visitNode(it);
        if (!maybeImpureCtor) {
          purityVisitor.visitNode(it);
        }
      });
    }

    BitSet notNullParams = ParameterNullityInferenceKt.inferNotNullParameters(tree, method, statements);
    return createData(body, contracts, nullityVisitor.getResult(), maybeImpureCtor ? null : purityVisitor.getResult(), notNullParams);
  }

  @Nullable
  private MethodData createData(LighterASTNode body, List<PreContract> contracts, MethodReturnInferenceResult methodReturn, PurityInferenceResult purity, BitSet notNullParams) {
    if (methodReturn == null && purity == null && contracts.isEmpty() && notNullParams.isEmpty()) {
      return null;
    }
    return new MethodData(methodReturn, purity, contracts, notNullParams, body.getStartOffset(), body.getEndOffset());
  }

  private ClassData calcClassData(LighterASTNode aClass) {
    boolean hasSuper = aClass.getTokenType() == ANONYMOUS_CLASS;
    Map<String, LighterASTNode> fieldModifiers = new HashMap<>();
    List<LighterASTNode> initializers = new ArrayList<>();

    for (LighterASTNode child : tree.getChildren(aClass)) {
      IElementType childTokenType = child.getTokenType();

      if (childTokenType == EXTENDS_LIST) {
        if (LightTreeUtil.firstChildOfType(tree, child, JAVA_CODE_REFERENCE) != null) {
          hasSuper = true;
        }
      } else if (childTokenType == FIELD) {
        String fieldName = JavaLightTreeUtil.getNameIdentifierText(tree, child);
        if (fieldName != null) {
          LighterASTNode modifiers = LightTreeUtil.firstChildOfType(tree, child, MODIFIER_LIST);
          fieldModifiers.put(fieldName, modifiers);
          boolean isStatic = LightTreeUtil.firstChildOfType(tree, modifiers, JavaTokenType.STATIC_KEYWORD) != null;
          if (!isStatic) {
            LighterASTNode initializer = JavaLightTreeUtil.findExpressionChild(tree, child);
            if (initializer != null) {
              initializers.add(initializer);
            }
          }
        }
      } else if (childTokenType == CLASS_INITIALIZER) {
        LighterASTNode modifiers = LightTreeUtil.firstChildOfType(tree, child, MODIFIER_LIST);
        boolean isStatic = LightTreeUtil.firstChildOfType(tree, modifiers, JavaTokenType.STATIC_KEYWORD) != null;
        if (!isStatic) {
          LighterASTNode body = LightTreeUtil.firstChildOfType(tree, child, CODE_BLOCK);
          if (body != null) {
            initializers.add(body);
          }
        }
      }
    }

    boolean pureInitializer = true;
    if (!initializers.isEmpty()) {
      PurityInferenceVisitor visitor = new PurityInferenceVisitor(tree, aClass, fieldModifiers, true);
      for (LighterASTNode initializer : initializers) {
        walkMethodBody(initializer, visitor::visitNode);

        PurityInferenceResult result = visitor.getResult();

        pureInitializer = result != null && result.getSingleCall() == null && result.getMutableRefs().isEmpty();

        if (!pureInitializer) {
          break;
        }
      }
    }
    return new ClassData(hasSuper, pureInitializer, fieldModifiers);
  }

  private void walkMethodBody(LighterASTNode root, Consumer<LighterASTNode> processor) {
    new RecursiveLighterASTNodeWalkingVisitor(tree) {
      @Override
      public void visitNode(@Nonnull LighterASTNode element) {
        IElementType type = element.getTokenType();
        if (type == CLASS || type == FIELD || type == METHOD || type == ANNOTATION_METHOD || type == LAMBDA_EXPRESSION) {
          return;
        }

        processor.accept(element);

        super.visitNode(element);
      }
    }.visitNode(root);
  }

  @Nonnull
  public Map<Integer, MethodData> getResult() {
    return result;
  }
}
