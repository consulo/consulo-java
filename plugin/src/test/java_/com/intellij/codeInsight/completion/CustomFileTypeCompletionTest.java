package com.intellij.codeInsight.completion;

import java.io.IOException;

import jakarta.annotation.Nonnull;

import com.intellij.JavaTestUtil;
import consulo.ide.impl.idea.openapi.fileTypes.MockLanguageFileType;
import consulo.ide.impl.idea.openapi.fileTypes.ex.FileTypeManagerEx;

/**
 * @author Maxim.Mossienko
 */
public abstract class CustomFileTypeCompletionTest extends LightCompletionTestCase {
  private static final String BASE_PATH = "/codeInsight/completion/customFileType/";

  @Nonnull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testKeyWordCompletion() throws Exception {
    configureByFile(BASE_PATH + "1.cs");
    checkResultByFile(BASE_PATH + "1_after.cs");

    configureByFile(BASE_PATH + "2.cs");
    checkResultByFile(BASE_PATH + "2_after.cs");
  }

  public void testWordCompletion() throws Throwable {
    configureByFile(BASE_PATH + "WordCompletion.cs");
    testByCount(2, "while", "whiwhiwhi");
  }

  public void testErlang() throws Throwable {
    configureByFile(BASE_PATH + "Erlang.erl");
    testByCount(2, "case", "catch");
  }

  public void testPlainTextSubstitution() throws IOException {
    FileTypeManagerEx.getInstanceEx().registerFileType(consulo.ide.impl.idea.openapi.fileTypes.MockLanguageFileType.INSTANCE, "xxx");
    try {
      configureFromFileText("a.xxx", "aaa a<caret>");
      complete();
      checkResultByText("aaa aaa<caret>");
    }
    finally {
      FileTypeManagerEx.getInstanceEx().unregisterFileType(consulo.ide.impl.idea.openapi.fileTypes.MockLanguageFileType.INSTANCE);

    }
  }

}
