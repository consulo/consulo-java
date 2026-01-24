// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.psi.impl;

import com.intellij.debugger.streams.psi.ChainDetector;
import com.intellij.debugger.streams.psi.ChainTransformer;
import com.intellij.debugger.streams.psi.PsiUtil;
import com.intellij.java.language.psi.*;
import consulo.execution.debug.stream.wrapper.StreamChain;
import consulo.execution.debug.stream.wrapper.StreamChainBuilder;
import consulo.language.psi.PsiElement;
import consulo.util.collection.ContainerUtil;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.Contract;
import jakarta.annotation.Nullable;

import java.util.*;

/**
 * @author Vitaliy.Bibaev
 */
public class JavaStreamChainBuilder implements StreamChainBuilder {
  private final ChainTransformer.Java myChainTransformer;
  private final ChainDetector myDetector;

  public JavaStreamChainBuilder(@Nonnull ChainTransformer.Java transformer, @Nonnull ChainDetector detector) {
    myChainTransformer = transformer;
    myDetector = detector;
  }

  @Override
  public boolean isChainExists(@Nonnull PsiElement startElement) {
    PsiElement current = getLatestElementInCurrentScope(PsiUtil.ignoreWhiteSpaces(startElement));
    MyStreamChainExistenceChecker existenceChecker = new MyStreamChainExistenceChecker();
    while (current != null) {
      current.accept(existenceChecker);
      if (existenceChecker.found()) {
        return true;
      }
      current = toUpperLevel(current);
    }

    return false;
  }

  @Override
  public @Nonnull List<StreamChain> build(@Nonnull PsiElement startElement) {
    final MyChainCollectorVisitor visitor = new MyChainCollectorVisitor();

    PsiElement current = getLatestElementInCurrentScope(PsiUtil.ignoreWhiteSpaces(startElement));
    while (current != null) {
      current.accept(visitor);
      current = toUpperLevel(current);
    }

    final List<List<PsiMethodCallExpression>> chains = visitor.getPsiChains();
    return buildChains(chains, startElement);
  }

  private static @Nullable PsiElement toUpperLevel(@Nonnull PsiElement element) {
    element = element.getParent();
    while (element != null && !(element instanceof PsiLambdaExpression) && !(element instanceof PsiAnonymousClass)) {
      element = element.getParent();
    }

    return getLatestElementInCurrentScope(element);
  }

  @Contract("null -> null")
  private static @Nullable PsiElement getLatestElementInCurrentScope(@Nullable PsiElement element) {
    PsiElement current = element;
    while (current != null) {
      final PsiElement parent = current.getParent();

      if (parent instanceof PsiCodeBlock || parent instanceof PsiLambdaExpression || parent instanceof PsiStatement) {
        break;
      }

      current = parent;
    }

    return current;
  }

  private @Nonnull List<StreamChain> buildChains(@Nonnull List<List<PsiMethodCallExpression>> chains, @Nonnull PsiElement context) {
    return ContainerUtil.map(chains, x -> myChainTransformer.transform(x, context));
  }

  private class MyStreamChainExistenceChecker extends MyVisitorBase {
    private boolean myFound = false;

    @Override
    public void visitMethodCallExpression(@Nonnull PsiMethodCallExpression expression) {
      if (myFound) return;
      super.visitMethodCallExpression(expression);
      if (!myFound && myDetector.isTerminationCall(expression)) {
        myFound = true;
      }
    }

    boolean found() {
      return myFound;
    }
  }

  private class MyChainCollectorVisitor extends MyVisitorBase {
    private final Set<PsiMethodCallExpression> myTerminationCalls = new HashSet<>();
    private final Map<PsiMethodCallExpression, PsiMethodCallExpression> myPreviousCalls = new HashMap<>();

    @Override
    public void visitMethodCallExpression(@Nonnull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!myPreviousCalls.containsKey(expression) && myDetector.isStreamCall(expression)) {
        updateCallTree(expression);
      }
    }

    private void updateCallTree(@Nonnull PsiMethodCallExpression expression) {
      if (myDetector.isTerminationCall(expression)) {
        myTerminationCalls.add(expression);
      }

      final PsiElement parent = expression.getParent();
      if (!(parent instanceof PsiReferenceExpression)) return;
      final PsiElement parentCall = parent.getParent();
      if (parentCall instanceof PsiMethodCallExpression parentCallExpression && myDetector.isStreamCall(parentCallExpression)) {
        myPreviousCalls.put(parentCallExpression, expression);
        updateCallTree(parentCallExpression);
      }
    }

    @Nonnull
    List<List<PsiMethodCallExpression>> getPsiChains() {
      final List<List<PsiMethodCallExpression>> chains = new ArrayList<>();
      for (final PsiMethodCallExpression terminationCall : myTerminationCalls) {
        List<PsiMethodCallExpression> chain = new ArrayList<>();
        PsiMethodCallExpression current = terminationCall;
        while (current != null) {
          if (!myDetector.isIntermediateCall(current) && !myDetector.isTerminationCall(current)) break;
          chain.add(current);
          current = myPreviousCalls.get(current);
        }

        Collections.reverse(chain);
        chains.add(chain);
      }

      return chains;
    }
  }

  private static class MyVisitorBase extends JavaRecursiveElementVisitor {
    @Override
    public void visitCodeBlock(@Nonnull PsiCodeBlock block) {
    }

    @Override
    public void visitLambdaExpression(@Nonnull PsiLambdaExpression expression) {
    }
  }
}
