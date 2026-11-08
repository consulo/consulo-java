// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.TypeConstraints;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.transfer.EnterFinallyTrap;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.transfer.ExceptionTransfer;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.transfer.TryCatchAllTrap;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.transfer.TryCatchTrap;
import consulo.language.dataFlow.value.DfaControlTransferValue;
import consulo.language.dataFlow.value.DfaControlTransferValue.TransferTarget;
import consulo.language.dataFlow.value.DfaControlTransferValue.Trap;
import consulo.language.dataFlow.value.DfaValueFactory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.FList;
import consulo.util.collection.FactoryMap;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * A helper class to build CFG
 */
public class TrapTracker {
  private FList<Trap> myTrapStack = FList.emptyList();
  private final DfaValueFactory myFactory;
  private final Map<String, ExceptionTransfer> myExceptionCache;

  public TrapTracker(DfaValueFactory factory, TypeConstraints.TypeConstraintFactory typeConstraintFactory) {
    myFactory = factory;
    myExceptionCache = FactoryMap.create(fqn -> new ExceptionTransfer(typeConstraintFactory.create(fqn).instanceOf()));
  }

  /**
   * @param exceptionName exception name
   * @return a transfer value (possibly a cached one)
   */
  public ExceptionTransfer transfer(String exceptionName) {
    return myExceptionCache.get(exceptionName);
  }

  /**
   * @return current trap stack
   */
  public FList<Trap> trapStack() {
    return myTrapStack;
  }

  public DfaControlTransferValue transferValue(String exceptionName) {
    return myFactory.controlTransfer(transfer(exceptionName), myTrapStack);
  }

  /**
   * @param exceptionName exception to create transfer value using the current trap stack
   * @return transfer value or null if exception is not intercepted currently
   */
  public @Nullable DfaControlTransferValue maybeTransferValue(String exceptionName) {
    return shouldHandleException() ? myFactory.controlTransfer(transfer(exceptionName), myTrapStack) : null;
  }

  public DfaControlTransferValue transferValue(TransferTarget target) {
    return myFactory.controlTransfer(target, myTrapStack);
  }

  /**
   * @param element psi element (usually element to exit e.g. via break/continue)
   * @return traps whose anchors lie inside the element (e.g., catch or finally blocks that should be processed).
   */
  public FList<Trap> getTrapsInsideElement(@Nullable PsiElement element) {
    if (element == null) return FList.emptyList();
    return FList.createFromReversed(ContainerUtil.reverse(
      ContainerUtil.findAll(myTrapStack, cd -> PsiTreeUtil.isAncestor(element, cd.getAnchor(), true))));
  }

  /**
   * @return true if exception that happens at current CFG point might not lead to execution finish. I.e., some catch or finally block
   * may need to be executed. Used for CFG optimization to avoid generating exception jumps when unnecessary.
   */
  public boolean shouldHandleException() {
    for (Trap trap : myTrapStack) {
      if (trap instanceof TryCatchTrap || trap instanceof EnterFinallyTrap || trap instanceof TryCatchAllTrap) {
        return true;
      }
    }
    return false;
  }

  /**
   * Add a new trap to the stack
   * @param trap trap value
   */
  public void pushTrap(Trap trap) {
    myTrapStack = myTrapStack.prepend(trap);
  }

  /**
   * Pops a trap from the stack
   * @param aClass expected trap class. The method will fail if the popped trap class differs.
   */
  public void popTrap(Class<? extends Trap> aClass) {
    if (!aClass.isInstance(myTrapStack.getHead())) {
      throw new IllegalStateException("Unexpected trap-stack head (wanted: "+aClass.getSimpleName()+"); stack: "+myTrapStack);
    }
    myTrapStack = myTrapStack.getTail();
  }
}
