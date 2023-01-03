package com.intellij.java.impl.codeInsight.completion;

import consulo.language.editor.completion.ClassConditionKey;

/**
 * @author peter
 */
public interface StaticallyImportable {
  ClassConditionKey<StaticallyImportable> CLASS_CONDITION_KEY = ClassConditionKey.create(StaticallyImportable.class);

  void setShouldBeImported(boolean shouldImportStatic);

  boolean canBeImported();

  boolean willBeImported();
}
