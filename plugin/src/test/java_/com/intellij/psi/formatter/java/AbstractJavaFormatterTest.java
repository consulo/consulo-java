/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.formatter.java;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

import jakarta.annotation.Nonnull;

import org.jetbrains.annotations.NonNls;
import com.intellij.JavaTestUtil;
import consulo.ide.impl.idea.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.language.JavaLanguage;
import consulo.application.ApplicationManager;
import consulo.undoRedo.CommandProcessor;
import consulo.document.Document;
import consulo.document.impl.DocumentImpl;
import consulo.document.util.TextRange;
import consulo.ide.impl.idea.openapi.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiFile;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.codeStyle.CodeStyleSettings;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.language.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.LightIdeaTestCase;
import consulo.language.util.IncorrectOperationException;
import consulo.ide.impl.idea.util.text.LineReader;

/**
 * Base class for java formatter tests that holds utility methods.
 *
 * @author Denis Zhdanov
 * @since Apr 27, 2010 6:26:29 PM
 */
public abstract class AbstractJavaFormatterTest extends LightIdeaTestCase {

  @Nonnull
  public static String shiftIndentInside(@Nonnull String initial, final int i, boolean shiftEmptyLines) throws IOException {
    StringBuilder result = new StringBuilder(initial.length());
    LineReader reader = new LineReader(new ByteArrayInputStream(initial.getBytes()));
    boolean first = true;
    for (byte[] line : reader.readLines()) {
      try {
        if (!first) result.append('\n');
        if (line.length > 0 || shiftEmptyLines) {
          StringUtil.repeatSymbol(result, ' ', i);
        }
        result.append(new String(line));
      }
      finally {
        first = false;
      }
    }

    return result.toString();
  }

  protected enum Action {REFORMAT, INDENT}

  private interface TestFormatAction {
    void run(PsiFile psiFile, int startOffset, int endOffset);
  }

  private static final Map<Action, TestFormatAction> ACTIONS = new EnumMap<Action, TestFormatAction>(Action.class);
  static {
    ACTIONS.put(Action.REFORMAT, new TestFormatAction() {
      @Override
      public void run(PsiFile psiFile, int startOffset, int endOffset) {
        CodeStyleManager.getInstance(getProject()).reformatText(psiFile, startOffset, endOffset);
      }
    });
    ACTIONS.put(Action.INDENT, new TestFormatAction() {
      @Override
      public void run(PsiFile psiFile, int startOffset, int endOffset) {
        CodeStyleManager.getInstance(getProject()).adjustLineIndent(psiFile, startOffset);
      }
    });
  }

  private static final String BASE_PATH = JavaTestUtil.getJavaTestDataPath() + "/psi/formatter/java";

  public TextRange myTextRange;
  public TextRange myLineRange;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
   // LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.HIGHEST);
  }

  public static CommonCodeStyleSettings getSettings() {
    CodeStyleSettings rootSettings = CodeStyleSettingsManager.getSettings(getProject());
    return rootSettings.getCommonSettings(JavaLanguage.INSTANCE);
  }

  public static CommonCodeStyleSettings.IndentOptions getIndentOptions() {
    return getSettings().getRootSettings().getIndentOptions(JavaFileType.INSTANCE);
  }

  public void doTest() throws Exception {
    doTest(getTestName(false) + ".java", getTestName(false) + "_after.java");
  }

  public void doTest(@NonNls String fileNameBefore, @NonNls String fileNameAfter) throws Exception {
    doTextTest(Action.REFORMAT, loadFile(fileNameBefore), loadFile(fileNameAfter));
  }

  public void doTextTest(@NonNls final String text, @NonNls String textAfter) throws IncorrectOperationException {
    doTextTest(Action.REFORMAT, text, textAfter);
  }

  public void doTextTest(final Action action, final String text, String textAfter) throws IncorrectOperationException {
    final PsiFile file = createFile("A.java", text);

    if (myLineRange != null) {
      final DocumentImpl document = new DocumentImpl(text);
      myTextRange =
        new TextRange(document.getLineStartOffset(myLineRange.getStartOffset()), document.getLineEndOffset(myLineRange.getEndOffset()));
    }

    /*
    CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            performFormatting(file);
          }
        });
      }
    }, null, null);

    assertEquals(prepareText(textAfter), prepareText(file.getText()));


    */

    final PsiDocumentManager manager = PsiDocumentManager.getInstance(getProject());
    final Document document = manager.getDocument(file);


    CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            document.replaceString(0, document.getTextLength(), text);
            manager.commitDocument(document);
            try {
              TextRange rangeToUse = myTextRange;
              if (rangeToUse == null) {
                rangeToUse = file.getTextRange();
              }
              ACTIONS.get(action).run(file, rangeToUse.getStartOffset(), rangeToUse.getEndOffset());
            }
            catch (IncorrectOperationException e) {
              assertTrue(e.getLocalizedMessage(), false);
            }
          }
        });
      }
    }, action == Action.REFORMAT ? ReformatCodeProcessor.COMMAND_NAME : "", "");


    if (document == null) {
      fail("Don't expect the document to be null");
      return;
    }
    assertEquals(textAfter, document.getText());
    manager.commitDocument(document);
    assertEquals(textAfter, file.getText());

  }

  public void doMethodTest(@NonNls final String before, @NonNls final String after) throws Exception {
    doTextTest(
      Action.REFORMAT,
      "class Foo{\n" + "    void foo() {\n" + before + '\n' + "    }\n" + "}",
      "class Foo {\n" + "    void foo() {\n" + shiftIndentInside(after, 8, false) + '\n' + "    }\n" + "}"
    );
  }

  public void doClassTest(@NonNls final String before, @NonNls final String after) throws Exception {
    doTextTest(
      Action.REFORMAT,
      "class Foo{\n" + before + '\n' + "}",
      "class Foo {\n" + shiftIndentInside(after, 4, false) + '\n' + "}"
    );
  }

  private static String loadFile(String name) throws Exception {
    String fullName = BASE_PATH + File.separatorChar + name;
    String text = FileUtil.loadFile(new File(fullName));
    text = StringUtil.convertLineSeparators(text);
    return text;
  }
}
