package com.siyeh.ig.threading;

import com.siyeh.ig.IGInspectionTestCase;
import consulo.java.language.module.util.JavaClassNames;

public class AccessToStaticFieldLockedOnInstanceInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    final AccessToStaticFieldLockedOnInstanceInspection tool = new AccessToStaticFieldLockedOnInstanceInspection();
    tool.ignoredClasses.add(JavaClassNames.JAVA_UTIL_LIST);
    doTest("com/siyeh/igtest/threading/access_to_static_field_locked_on_instance_data", tool);
  }
}