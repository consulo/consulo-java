package com.intellij.refactoring.migration;

import com.intellij.JavaTestUtil;
import com.intellij.java.impl.refactoring.migration.MigrationMap;
import com.intellij.java.impl.refactoring.migration.MigrationMapEntry;
import com.intellij.java.impl.refactoring.migration.MigrationProcessor;
import com.intellij.java.language.psi.CommonClassNames;
import com.intellij.refactoring.MultiFileTestCase;
import consulo.document.FileDocumentManager;
import consulo.virtualFileSystem.VirtualFile;

/**
 * @author dsl
 */
public abstract class MigrationTest extends MultiFileTestCase {
  public void testUnexistingClassInUnexistingPackage() throws Exception {
    doTest(createAction(new MigrationMap(new MigrationMapEntry[]{
      new MigrationMapEntry("qqq.aaa.Yahoo", CommonClassNames.JAVA_LANG_STRING, MigrationMapEntry.CLASS, false)
    })));
  }

  public void testToNonExistentClass() throws Exception {
    doTest(createAction(new MigrationMap(new MigrationMapEntry[]{
      new MigrationMapEntry("qqq.aaa.Yahoo", "zzz.bbb.QQQ", MigrationMapEntry.CLASS, false)
    })));
  }

  public void testPackage() throws Exception {
    doTest(createAction(new MigrationMap(new MigrationMapEntry[]{
      new MigrationMapEntry("qqq", "java.lang", MigrationMapEntry.PACKAGE, true)
    })));
  }

  public void testPackageToNonExistentPackage() throws Exception {
    doTest(createAction(new MigrationMap(new MigrationMapEntry[]{
      new MigrationMapEntry("qqq", "zzz.bbb", MigrationMapEntry.PACKAGE, true)
    })));
  }

  public void testTwoClasses() throws Exception {
    doTest(createAction(new MigrationMap(new MigrationMapEntry[]{
      new MigrationMapEntry("A", "A1", MigrationMapEntry.CLASS, true),
      new MigrationMapEntry("B", "B1", MigrationMapEntry.CLASS, true)
    })));
  }

  private MultiFileTestCase.PerformAction createAction(final MigrationMap migrationMap) {
    return new MultiFileTestCase.PerformAction() {
      @Override
      public void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception {
        new MigrationProcessor(myProject, migrationMap).run();
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    };
  }

  @Override
  protected String getTestRoot() {
    return "/refactoring/migration/";
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }
}
