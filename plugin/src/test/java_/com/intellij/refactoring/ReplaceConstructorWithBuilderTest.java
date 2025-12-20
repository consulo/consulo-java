/*
 * User: anna
 * Date: 06-May-2008
 */
package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import consulo.document.FileDocumentManager;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import consulo.language.psi.scope.GlobalSearchScope;
import com.intellij.java.impl.refactoring.replaceConstructorWithBuilder.ParameterData;
import com.intellij.java.impl.refactoring.replaceConstructorWithBuilder.ReplaceConstructorWithBuilderProcessor;

import java.util.HashMap;

import java.util.LinkedHashMap;
import java.util.Map;

public abstract class ReplaceConstructorWithBuilderTest extends MultiFileTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testVarargs() throws Exception {
    doTest(true);
  }

  public void testExistingEmptyBuilder() throws Exception {
    doTest(false);
  }

  public void testMultipleParams() throws Exception {
    doTest(true);
  }

  public void testExistingHalfEmptyBuilder() throws Exception {
    doTest(false);
  }

  public void testExistingVoidSettersBuilder() throws Exception {
    doTest(false);
  }

  public void testConstructorChain() throws Exception {
    HashMap<String, String> defaults = new HashMap<String, String>();
    defaults.put("i", "2");
    doTest(true, defaults);
  }

  public void testConstructorChainWithoutDefaults() throws Exception {
    HashMap<String, String> defaults = new HashMap<String, String>();
    defaults.put("i", "2");
    defaults.put("j", null);
    doTest(true, defaults);
  }

  public void testConstructorTree() throws Exception {
    doTest(true, null, "Found constructors are not reducible to simple chain");
  }

  public void testGenerics() throws Exception {
    doTest(true);
  }

  public void testImports() throws Exception {
    doTest(true, null, null, "foo");
  }

  private void doTest(boolean createNewBuilderClass) throws Exception {
    doTest(createNewBuilderClass, null);
  }

  private void doTest(boolean createNewBuilderClass, Map<String, String> expectedDefaults) throws Exception {
    doTest(createNewBuilderClass, expectedDefaults, null);
  }

  private void doTest(boolean createNewBuilderClass, Map<String, String> expectedDefaults, String conflicts) throws Exception {
    doTest(createNewBuilderClass, expectedDefaults, conflicts, "");
  }

  private void doTest(final boolean createNewBuilderClass,
                      final Map<String, String> expectedDefaults,
                      final String conflicts,
                      final String packageName) throws Exception {
    doTest(new PerformAction() {
      @Override
      public void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception {
        PsiClass aClass = myJavaFacade.findClass("Test", GlobalSearchScope.projectScope(getProject()));
        assertNotNull("Class Test not found", aClass);

        LinkedHashMap<String, ParameterData> map = new LinkedHashMap<String, ParameterData>();
        PsiMethod[] constructors = aClass.getConstructors();
        for (PsiMethod constructor : constructors) {
          ParameterData.createFromConstructor(constructor, map);
        }
        if (expectedDefaults != null) {
          for (Map.Entry<String, String> entry : expectedDefaults.entrySet()) {
            ParameterData parameterData = map.get(entry.getKey());
            assertNotNull(parameterData);
            assertEquals(entry.getValue(), parameterData.getDefaultValue());
          }
        }
        try {
          new ReplaceConstructorWithBuilderProcessor(getProject(), constructors, map, "Builder", packageName, null, createNewBuilderClass).run();
          if (conflicts != null) {
            fail("Conflicts were not detected:" + conflicts);
          }
        }
        catch (BaseRefactoringProcessor.ConflictsInTestsException e) {

          if (conflicts == null) {
            fail("Conflict detected:" + e.getMessage());
          }
        }
        LocalFileSystem.getInstance().refresh(false);
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    });
  }


  @Override
  protected String getTestRoot() {
    return "/refactoring/replaceConstructorWithBuilder/";
  }
}
