package com.intellij.psi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;

import com.intellij.JavaTestUtil;
import consulo.content.OrderRootType;
import consulo.ide.impl.idea.openapi.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.java.language.impl.psi.impl.compiled.ClsFileImpl;
import consulo.language.psi.stub.PsiFileStub;
import consulo.language.psi.stub.StubBase;
import com.intellij.testFramework.LightIdeaTestCase;
import com.intellij.java.language.util.cls.ClsFormatException;

/**
 * @author max
 */
public abstract class ClsBuilderTest extends LightIdeaTestCase {
  public void testUtilList() throws Exception {
    doTest("java/util/List.class");
  }

  public void testNullable() throws Exception {
    doTest("org/jetbrains/annotations/Nullable.class");
  }

  public void testUtilCollections() throws Exception {
    doTest("java/util/Collections.class");
  }

  public void testUtilHashMap() throws Exception {
    doTest("java/util/HashMap.class");
  }

  public void testUtilMap() throws Exception {
    doTest("java/util/Map.class");
  }

  public void testTimeUnit() throws Exception {
    doTest("java/util/concurrent/TimeUnit.class");
  }

  public void testTestSuite() throws Exception {
    doTestFromTestData();
  }

  public void testDoubleTest() throws Exception {  // IDEA-53195
    doTestFromTestData();
  }

  public void testAnnotatedNonStaticInnerClassConstructor() throws Exception {
    doTestFromTestData();
  }

  public void testAnnotatedEnumConstructor() throws Exception {
    doTestFromTestData();
  }

  public void testModifiers() throws Exception {
    String clsFilePath = JavaTestUtil.getJavaTestDataPath() + "/psi/repositoryUse/cls/pack/" + getTestName(false) + ".class";
    VirtualFile clsFile = LocalFileSystem.getInstance().findFileByPath(clsFilePath);
    assert clsFile != null : clsFilePath;
    doTest(clsFile, getTestName(false) + ".txt");
  }

  private void doTestFromTestData() throws ClsFormatException, IOException {
    String clsFilePath = JavaTestUtil.getJavaTestDataPath() + "/psi/cls/stubBuilder/" + getTestName(false) + ".class";
    VirtualFile clsFile = LocalFileSystem.getInstance().findFileByPath(clsFilePath);
    assert clsFile != null : clsFilePath;
    doTest(clsFile, getTestName(false) + ".txt");
  }

  private void doTest(String className) throws IOException, ClsFormatException {
    VirtualFile clsFile = findFile(className);
    doTest(clsFile, getTestName(false) + ".txt");
  }

  private static void doTest(VirtualFile vFile, String goldFile) throws ClsFormatException, IOException {
    PsiFileStub stub = ClsFileImpl.buildFileStub(vFile, vFile.contentsToByteArray());
    assert stub != null : vFile;
    String butWas = ((StubBase)stub).printTree();

    String goldFilePath = JavaTestUtil.getJavaTestDataPath() + "/psi/cls/stubBuilder/" + goldFile;
    String expected = "";
    try {
      expected = FileUtil.loadFile(new File(goldFilePath));
      expected = StringUtil.convertLineSeparators(expected);
    }
    catch (FileNotFoundException e) {
      System.out.println("No expected data found at: " + goldFilePath + ", creating one.");
      FileWriter fileWriter = new FileWriter(goldFilePath);
      try {
        fileWriter.write(butWas);
        fileWriter.close();
      }
      finally {
        fileWriter.close();
        fail("No test data found. Created one");
      }
    }

    assertEquals(expected, butWas);
  }

  private VirtualFile findFile(String className) {
    VirtualFile[] roots = getProjectJDK().getRootProvider().getFiles(OrderRootType.CLASSES);
    for (VirtualFile root : roots) {
      VirtualFile vFile = root.findFileByRelativePath(className);
      if (vFile != null) return vFile;
    }

    fail("Cannot file class file for: " + className);
    return null;
  }
}
