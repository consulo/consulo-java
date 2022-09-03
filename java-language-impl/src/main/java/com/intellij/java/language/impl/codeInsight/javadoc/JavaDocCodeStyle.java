package com.intellij.java.language.impl.codeInsight.javadoc;

import consulo.ide.ServiceManager;
import consulo.project.Project;

public abstract class JavaDocCodeStyle {
  public static JavaDocCodeStyle getInstance(Project project) {
    return ServiceManager.getService(project, JavaDocCodeStyle.class);
  }

  public abstract boolean spaceBeforeComma();
  public abstract boolean spaceAfterComma();
}
