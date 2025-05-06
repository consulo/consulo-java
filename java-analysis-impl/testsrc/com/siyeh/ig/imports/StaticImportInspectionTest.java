package com.siyeh.ig.imports;

import com.siyeh.ig.IGInspectionTestCase;
import consulo.java.language.module.util.JavaClassNames;

public class StaticImportInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    final StaticImportInspection tool = new StaticImportInspection();
    tool.allowedClasses.add(JavaClassNames.JAVA_UTIL_MAP);
    doTest("com/siyeh/igtest/imports/static_import", tool);
  }

  public void testMethodAllowed() {
    final StaticImportInspection tool = new StaticImportInspection();
    tool.ignoreSingeMethodImports = true;
    doTest("com/siyeh/igtest/imports/static_import_method_allowed", tool);
  }
}