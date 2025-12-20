/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: Anna.Kozlova
 * Date: 06-Sep-2006
 * Time: 13:56:11
 */
package com.intellij.codeInsight.daemon.quickFix;

import jakarta.annotation.Nonnull;

import consulo.language.editor.inspection.LocalInspectionTool;
import com.intellij.java.impl.codeInspection.javaDoc.JavaDocLocalInspection;

public abstract class JavadocInspectionQuickFixTest extends LightQuickFix15TestCase {

  @Nonnull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    JavaDocLocalInspection javaDocLocalInspection = new JavaDocLocalInspection();
    javaDocLocalInspection.TOP_LEVEL_CLASS_OPTIONS.REQUIRED_TAGS = "param";
    javaDocLocalInspection.TOP_LEVEL_CLASS_OPTIONS.ACCESS_JAVADOC_REQUIRED_FOR = "package";
    javaDocLocalInspection.METHOD_OPTIONS.ACCESS_JAVADOC_REQUIRED_FOR = "package";
    return new LocalInspectionTool[]{javaDocLocalInspection};
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  public void test() throws Exception { doAllTests(); }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/javadocTags";
  }

}