package com.intellij.codeInsight.psi;

import com.intellij.java.language.impl.psi.controlFlow.*;
import consulo.ide.impl.idea.openapi.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.java.language.psi.PsiCodeBlock;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiJavaFile;
import consulo.language.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightCodeInsightTestCase;
import consulo.util.collection.primitive.ints.IntList;
import consulo.util.collection.primitive.ints.IntLists;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.FilenameFilter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author cdr
 * Date: Nov 25, 2002
 */
public abstract class ControlFlowTest extends LightCodeInsightTestCase {
  @NonNls private static final String BASE_PATH = "/psi/controlFlow";

  private static void doTestFor(File file) throws Exception {
    String contents = StringUtil.convertLineSeparators(FileUtil.loadFile(file));
    configureFromFileText(file.getName(), contents);
    // extract factory policy class name
    Pattern pattern = Pattern.compile("^// (\\S*).*", Pattern.DOTALL);
    Matcher matcher = pattern.matcher(contents);
    assertTrue(matcher.matches());
    String policyClassName = matcher.group(1);
    ControlFlowPolicy policy;
    if ("LocalsOrMyInstanceFieldsControlFlowPolicy".equals(policyClassName)) {
      policy = LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance();
    }
    else {
      policy = null;
    }

    int offset = getEditor().getCaretModel().getOffset();
    PsiElement element = getFile().findElementAt(offset);
    element = PsiTreeUtil.getParentOfType(element, PsiCodeBlock.class, false);
    assertTrue("Selected element: "+element, element instanceof PsiCodeBlock);

    ControlFlow controlFlow = ControlFlowFactory.getInstance(getProject()).getControlFlow(element, policy);
    String result = controlFlow.toString().trim();

    String expectedFullPath = StringUtil.trimEnd(file.getPath(),".java") + ".txt";
    VirtualFile expectedFile = LocalFileSystem.getInstance().findFileByPath(expectedFullPath);
    String expected = new String(expectedFile.contentsToByteArray()).trim();
    expected = expected.replaceAll("\r","");
    assertEquals("Text mismatch (in file "+expectedFullPath+"):\n",expected, result);
  }

  private static void doAllTests() throws Exception {
    String testDirPath = BASE_PATH;
    File testDir = new File(testDirPath);
    File[] files = testDir.listFiles(new FilenameFilter() {
      @Override
      public boolean accept(File dir, String name) {
        return name.endsWith(".java");
      }
    });
    for (int i = 0; i < files.length; i++) {
      File file = files[i];
      doTestFor(file);

      System.out.print((i+1)+" ");
    }
  }

  public void test() throws Exception { doAllTests(); }

  public void testMethodWithOnlyDoWhileStatementHasExitPoints() throws Exception {
    configureFromFileText("a.java", "public class Foo {\n" +
                                    "  public void foo() {\n" +
                                    "    boolean f;\n" +
                                    "    do {\n" +
                                    "      f = something();\n" +
                                    "    } while (f);\n" +
                                    "  }\n" +
                                    "}");
    PsiCodeBlock body = ((PsiJavaFile)getFile()).getClasses()[0].getMethods()[0].getBody();
    ControlFlow flow = ControlFlowFactory.getInstance(getProject()).getControlFlow(body, new LocalsControlFlowPolicy(body), false);
    IntList exitPoints = IntLists.newArrayList();
    ControlFlowUtil.findExitPointsAndStatements(flow, 0, flow.getSize() -1 , exitPoints, ControlFlowUtil.DEFAULT_EXIT_STATEMENTS_CLASSES);
    assertEquals(1, exitPoints.size());
  }
}
