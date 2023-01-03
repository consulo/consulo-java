package com.intellij.java.execution.unscramble;

import consulo.execution.unscramble.ThreadState;

/**
 * @author VISTALL
 * @since 01-Sep-22
 */
public class JavaThreadState extends ThreadState {

  public JavaThreadState(String name, String state) {
    super(name, state);
  }

  @Override
  public boolean isBuiltinThread() {
    return super.isBuiltinThread() || ThreadDumpParser.isKnownJdkThread(this);
  }
}
