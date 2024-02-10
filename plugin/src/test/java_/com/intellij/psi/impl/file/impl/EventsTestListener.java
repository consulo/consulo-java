package com.intellij.psi.impl.file.impl;

import jakarta.annotation.Nonnull;

import consulo.language.psi.event.PsiTreeChangeEvent;
import consulo.language.psi.event.PsiTreeChangeListener;

/**
 *  @author dsl
 */
class EventsTestListener implements PsiTreeChangeListener {
  StringBuffer myBuffer = new StringBuffer();

  public String getEventsString() {
    return myBuffer.toString();
  }

  @Override
  public void beforeChildAddition(@Nonnull PsiTreeChangeEvent event) {
    myBuffer.append("beforeChildAddition\n");
  }

  @Override
  public void beforeChildRemoval(@Nonnull PsiTreeChangeEvent event) {
    myBuffer.append("beforeChildRemoval\n");
  }

  @Override
  public void beforeChildReplacement(@Nonnull PsiTreeChangeEvent event) {
    myBuffer.append("beforeChildReplacement\n");
  }

  @Override
  public void beforeChildMovement(@Nonnull PsiTreeChangeEvent event) {
    myBuffer.append("beforeChildMovement\n");
  }

  @Override
  public void beforeChildrenChange(@Nonnull PsiTreeChangeEvent event) {
    myBuffer.append("beforeChildrenChange\n");
  }

  @Override
  public void beforePropertyChange(@Nonnull PsiTreeChangeEvent event) {
    myBuffer.append("beforePropertyChange\n");
  }

  @Override
  public void childAdded(@Nonnull PsiTreeChangeEvent event) {
    myBuffer.append("childAdded\n");
  }

  @Override
  public void childRemoved(@Nonnull PsiTreeChangeEvent event) {
    myBuffer.append("childRemoved\n");
  }

  @Override
  public void childReplaced(@Nonnull PsiTreeChangeEvent event) {
    myBuffer.append("childReplaced\n");
  }

  @Override
  public void childrenChanged(@Nonnull PsiTreeChangeEvent event) {
    myBuffer.append("childrenChanged\n");
  }

  @Override
  public void childMoved(@Nonnull PsiTreeChangeEvent event) {
    myBuffer.append("childMoved\n");
  }

  @Override
  public void propertyChanged(@Nonnull PsiTreeChangeEvent event) {
    myBuffer.append("propertyChanged\n");
  }
}
