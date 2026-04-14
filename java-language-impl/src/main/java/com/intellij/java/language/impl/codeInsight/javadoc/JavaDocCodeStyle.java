package com.intellij.java.language.impl.codeInsight.javadoc;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.project.Project;

@ServiceAPI(ComponentScope.PROJECT)
public abstract class JavaDocCodeStyle {
  public static JavaDocCodeStyle getInstance(Project project) {
    return project.getInstance(JavaDocCodeStyle.class);
  }

  public abstract boolean spaceBeforeComma();

  public abstract boolean spaceAfterComma();
}
