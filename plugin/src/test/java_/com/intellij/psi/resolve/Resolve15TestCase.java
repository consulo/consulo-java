/*
 * Copyright (c) 2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.psi.resolve;

import com.intellij.java.language.projectRoots.JavaSdkVersion;
import com.intellij.java.language.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.ResolveTestCase;

/**
 * @author ven
 */
public abstract class Resolve15TestCase extends ResolveTestCase {
  private LanguageLevel myOldLanguageLevel;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myOldLanguageLevel = LanguageLevel.JDK_1_5;//LanguageLevelProjectExtension.getInstance(myJavaFacade.getProject()).getLanguageLevel();
    //LanguageLevelProjectExtension.getInstance(myJavaFacade.getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_5, getModule(), getTestRootDisposable());
  }

  @Override
  protected void tearDown() throws Exception {
   // LanguageLevelProjectExtension.getInstance(myJavaFacade.getProject()).setLanguageLevel(myOldLanguageLevel);
    super.tearDown();
  }
}
