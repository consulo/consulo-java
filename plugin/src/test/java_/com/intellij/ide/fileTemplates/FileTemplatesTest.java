package com.intellij.ide.fileTemplates;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import consulo.fileTemplate.impl.internal.CustomFileTemplate;
import com.intellij.java.language.impl.codeInsight.template.JavaTemplateUtil;
import consulo.module.content.ModuleRootManager;
import consulo.ide.impl.idea.openapi.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.java.language.psi.JavaDirectoryService;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiManager;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.PsiTestUtil;
import consulo.ide.impl.idea.util.properties.EncodingAwareProperties;

@SuppressWarnings({"HardCodedStringLiteral"})
public abstract class FileTemplatesTest extends IdeaTestCase {
  public void testAllTemplates() throws Exception {
    final File testsDir = new File("/ide/fileTemplates");

    final String includeTemplateName = "include1.inc";
    final String includeTemplateExtension = "txt";
    final String customIncludeFileName = includeTemplateName + "." + includeTemplateExtension;
    final File customInclude = new File(testsDir, customIncludeFileName);
    final String includeText = FileUtil.loadFile(customInclude, FileTemplate.ourEncoding);

    final FileTemplateManager templateManager = FileTemplateManager.getDefaultInstance();
    final ArrayList<FileTemplate> originalIncludes = new ArrayList<FileTemplate>(Arrays.asList(templateManager.getAllPatterns()));
    try {
      // configure custom include
      final List<FileTemplate> allIncludes = new ArrayList<FileTemplate>(originalIncludes);
      final CustomFileTemplate custom = new CustomFileTemplate(includeTemplateName, includeTemplateExtension);
      custom.setText(includeText);
      allIncludes.add(custom);
      templateManager.setTemplates(FileTemplateManager.INCLUDES_TEMPLATES_CATEGORY, allIncludes);

      final String txt = ".txt";
      File[] children = testsDir.listFiles(new FilenameFilter() {
        @Override
        public boolean accept(File dir, String name) {
          return name.endsWith(".out"+txt);
        }
      });

      assertTrue(children.length > 0);
      for (File resultFile : children) {
        String name = resultFile.getName();
        String base = name.substring(0, name.length() - txt.length() - ".out".length());
        File propFile = new File(resultFile.getParent(), base + ".prop" + txt);
        File inFile = new File(resultFile.getParent(), base + txt);
  
        String inputText = FileUtil.loadFile(inFile, FileTemplate.ourEncoding);
        String outputText = FileUtil.loadFile(resultFile, FileTemplate.ourEncoding);
  
        EncodingAwareProperties properties = new EncodingAwareProperties();
  
        properties.load(propFile, FileTemplate.ourEncoding);
  
        System.out.println(resultFile.getName());
        doTestTemplate(inputText, properties, outputText, resultFile.getParent());
      }
    }
    finally {
      templateManager.setTemplates(FileTemplateManager.INCLUDES_TEMPLATES_CATEGORY, originalIncludes);
    }
  }

  private static void doTestTemplate(String inputString, Properties properties, String expected, String dir) throws Exception {
    inputString = StringUtil.convertLineSeparators(inputString);
    expected = StringUtil.convertLineSeparators(expected);
    
    final String result = FileTemplateUtil.mergeTemplate(properties, inputString, false);
    assertEquals(expected, result);

    List attrs = Arrays.asList(FileTemplateUtil.calculateAttributes(inputString, new HashMap<>(), false, null));
    assertTrue(properties.size() <= attrs.size());
    Enumeration e = properties.propertyNames();
    while (e.hasMoreElements()) {
      String s = (String)e.nextElement();
      assertTrue("Attribute '" + s + "' not found in properties", attrs.contains(s));
    }
  }

  public void testFindFileByUrl() throws Exception {
    FileTemplate catchBodyTemplate = FileTemplateManager.getDefaultInstance().getCodeTemplate(JavaTemplateUtil.TEMPLATE_CATCH_BODY);
    assertNotNull(catchBodyTemplate);
  }

  public void testDefaultPackage() throws Exception {
    String name = "myclass";
    FileTemplate template = FileTemplateManager.getDefaultInstance().addTemplate(name/*+"ForTest"*/, "java");
    try {
      template.setText("package ${PACKAGE_NAME}; public class ${NAME} {}");

      File temp = FileUtil.createTempDirectory(getTestName(true), "");

      myFilesToDelete.add(temp);
      final VirtualFile tempDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(temp);

      PsiTestUtil.addSourceRoot(getModule(), tempDir);

      VirtualFile sourceRoot = ModuleRootManager.getInstance(getModule()).getSourceRoots()[0];
      PsiDirectory psiDirectory = PsiManager.getInstance(getProject()).findDirectory(sourceRoot);

      PsiClass psiClass = JavaDirectoryService.getInstance().createClass(psiDirectory, "XXX", name);
      assertNotNull(psiClass);
      assertEquals("public class XXX {\n}", psiClass.getContainingFile().getText());
    }
    finally {
      FileTemplateManager.getDefaultInstance().removeTemplate(template);
    }
  }
}
