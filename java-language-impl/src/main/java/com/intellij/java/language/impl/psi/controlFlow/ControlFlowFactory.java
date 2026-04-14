// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.controlFlow;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.disposer.Disposable;
import consulo.language.psi.AnyPsiChangeListener;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import consulo.util.collection.ConcurrentList;
import consulo.util.collection.Lists;
import consulo.util.collection.Maps;
import consulo.util.dataholder.NotNullLazyKey;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;

@Singleton
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public final class ControlFlowFactory implements Disposable {
  // psiElements hold weakly, controlFlows softly
  private final Map<PsiElement, ConcurrentList<ControlFlowContext>> cachedFlows = Maps.newConcurrentWeakKeySoftValueHashMap();

  private static final NotNullLazyKey<ControlFlowFactory, Project> INSTANCE_KEY = NotNullLazyKey.create(ControlFlowFactory.class.getName(), p -> p.getInstance(ControlFlowFactory.class));

  public static ControlFlowFactory getInstance(Project project) {
    return INSTANCE_KEY.getValue(project);
  }

  @Inject
  public ControlFlowFactory(Project project) {
    project.getMessageBus().connect(this).subscribe(AnyPsiChangeListener.class, new AnyPsiChangeListener() {
      @Override
      public void beforePsiChanged(boolean isPhysical) {
        if (isPhysical) {
          clearCache();
        }
      }
    });
  }

  private void clearCache() {
    cachedFlows.clear();
  }

  void registerSubRange(final PsiElement codeFragment,
                        final ControlFlowSubRange flow,
                        final ControlFlowOptions options,
                        final ControlFlowPolicy policy) {
    registerControlFlow(codeFragment, flow, options, policy);
  }

  private static final class ControlFlowContext {
    private final ControlFlowPolicy policy;
    private final
    ControlFlowOptions options;
    private final long modificationCount;
    private final
    ControlFlow controlFlow;

    private ControlFlowContext(ControlFlowOptions options,
                               ControlFlowPolicy policy,
                               long modificationCount,
                               ControlFlow controlFlow) {
      this.options = options;
      this.policy = policy;
      this.modificationCount = modificationCount;
      this.controlFlow = controlFlow;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      final ControlFlowContext that = (ControlFlowContext)o;

      return isFor(that);
    }

    @Override
    public int hashCode() {
      int result = policy.hashCode();
      result = 31 * result + (options.hashCode());
      result = 31 * result + (int)(modificationCount ^ (modificationCount >>> 32));
      return result;
    }

    private boolean isFor(ControlFlowPolicy policy,
                          ControlFlowOptions options,
                          long modificationCount) {
      if (modificationCount != this.modificationCount) {
        return false;
      }
      if (!policy.equals(this.policy)) {
        return false;
      }
      if (!options.equals(this.options)) {
        // optimization: when no constant condition were computed, both control flows are the same
        return !controlFlow.isConstantConditionOccurred() && this.options.dontEvaluateConstantIfCondition().equals(options);
      }
      return true;
    }

    private boolean isFor(ControlFlowContext that) {
      return isFor(that.policy, that.options, that.modificationCount);
    }
  }

  public ControlFlow getControlFlow(PsiElement element, ControlFlowPolicy policy) throws AnalysisCanceledException {
    return doGetControlFlow(element, policy, ControlFlowOptions.create(true, true, true));
  }

  public ControlFlow getControlFlow(PsiElement element,
                                    ControlFlowPolicy policy,
                                    boolean evaluateConstantIfCondition) throws AnalysisCanceledException {
    return doGetControlFlow(element, policy, ControlFlowOptions.create(true, evaluateConstantIfCondition, true));
  }

  public static ControlFlow getControlFlow(PsiElement element,
                                           ControlFlowPolicy policy,
                                           ControlFlowOptions options) throws AnalysisCanceledException {
    return getInstance(element.getProject()).doGetControlFlow(element, policy, options);
  }

  private ControlFlow doGetControlFlow(PsiElement element,
                                       ControlFlowPolicy policy,
                                       ControlFlowOptions options) throws AnalysisCanceledException {
    if (!element.isPhysical()) {
      return new ControlFlowAnalyzer(element, policy, options).buildControlFlow();
    }
    final long modificationCount = element.getManager().getModificationTracker().getModificationCount();
    ConcurrentList<ControlFlowContext> cached = getOrCreateCachedFlowsForElement(element);
    for (ControlFlowContext context : cached) {
      if (context.isFor(policy, options, modificationCount)) {
        return context.controlFlow;
      }
    }
    ControlFlow controlFlow = new ControlFlowAnalyzer(element, policy, options).buildControlFlow();
    ControlFlowContext context = createContext(options, policy, controlFlow, modificationCount);
    cached.addIfAbsent(context);
    return controlFlow;
  }

  private static ControlFlowContext createContext(ControlFlowOptions options,
                                                  ControlFlowPolicy policy,
                                                  ControlFlow controlFlow,
                                                  final long modificationCount) {
    return new ControlFlowContext(options, policy, modificationCount, controlFlow);
  }

  private void registerControlFlow(PsiElement element,
                                   ControlFlow flow,
                                   ControlFlowOptions options,
                                   ControlFlowPolicy policy) {
    final long modificationCount = element.getManager().getModificationTracker().getModificationCount();
    ControlFlowContext controlFlowContext = createContext(options, policy, flow, modificationCount);

    ConcurrentList<ControlFlowContext> cached = getOrCreateCachedFlowsForElement(element);
    cached.addIfAbsent(controlFlowContext);
  }

  private ConcurrentList<ControlFlowContext> getOrCreateCachedFlowsForElement(PsiElement element) {
    return cachedFlows.computeIfAbsent(element, __ -> Lists.newLockFreeCopyOnWriteList());
  }

  @Override
  public void dispose() {

  }
}

