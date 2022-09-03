package com.intellij.roots;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.io.IOException;

import consulo.application.ApplicationManager;
import consulo.module.content.layer.ContentEntry;
import consulo.module.content.layer.ContentFolder;
import consulo.module.content.layer.ModifiableRootModel;
import consulo.module.content.ModuleRootManager;
import consulo.util.lang.Comparing;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.PsiTestUtil;
import consulo.roots.impl.ExcludedContentFolderTypeProvider;
import consulo.roots.impl.ProductionContentFolderTypeProvider;

public abstract class ManagingContentRootFoldersTest extends IdeaTestCase {
  private VirtualFile root;
  private ContentEntry entry;
  private ModifiableRootModel myModel;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        initContentRoot();
        initModifiableModel();
      }
    });
  }

  @Override
  protected void tearDown() throws Exception {
    if (myModel != null && myModel.isWritable()) {
      myModel.dispose();
    }
    myModel = null;
    super.tearDown();
  }

  private void initContentRoot() {
    try {
      File dir = createTempDirectory();
      root = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir);
      PsiTestUtil.addContentRoot(myModule, root);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void initModifiableModel() {
    myModel = ModuleRootManager.getInstance(myModule).getModifiableModel();
    for (ContentEntry e : myModel.getContentEntries()) {
      if (Comparing.equal(e.getFile(), root)) entry = e;
    }
  }

  public void testCreationOfSourceFolderWithFile() throws IOException {
    VirtualFile dir = root.createChildDirectory(null, "src");
    String url = dir.getUrl();

    ContentFolder f = entry.addFolder(dir, ProductionContentFolderTypeProvider.getInstance());
    assertEquals(dir, f.getFile());
    assertEquals(url, f.getUrl());

    dir.delete(null);
    assertNull(f.getFile());
    assertEquals(url, f.getUrl());

    dir = root.createChildDirectory(null, "src");
    assertEquals(dir, f.getFile());
    assertEquals(url, f.getUrl());
  }


  public void testCreationOfSourceFolderWithUrl() throws IOException {
    VirtualFile dir = root.createChildDirectory(null, "src");
    String url = dir.getUrl();
    dir.delete(null);

    ContentFolder f = entry.addFolder(url, ProductionContentFolderTypeProvider.getInstance());
    assertNull(f.getFile());
    assertEquals(url, f.getUrl());

    dir = root.createChildDirectory(null, "src");
    assertEquals(dir, f.getFile());
    assertEquals(url, f.getUrl());
  }

  public void testCreationOfSourceFolderWithUrlWhenFileExists() throws IOException {
    VirtualFile dir = root.createChildDirectory(null, "src");
    String url = dir.getUrl();

    ContentFolder f = entry.addFolder(url, ProductionContentFolderTypeProvider.getInstance());
    assertEquals(dir, f.getFile());
    assertEquals(url, f.getUrl());
  }

  public void testCreationOfExcludedFolderWithFile() throws IOException {
    VirtualFile dir = root.createChildDirectory(null, "src");
    String url = dir.getUrl();

    ContentFolder f = entry.addFolder(dir, ExcludedContentFolderTypeProvider.getInstance());
    assertEquals(dir, f.getFile());
    assertEquals(url, f.getUrl());

    dir.delete(null);
    assertNull(f.getFile());
    assertEquals(url, f.getUrl());

    dir = root.createChildDirectory(null, "src");
    assertEquals(dir, f.getFile());
    assertEquals(url, f.getUrl());
  }

  public void testCreationOfExcludedFolderWithUrl() throws IOException {
    VirtualFile dir = root.createChildDirectory(null, "src");
    String url = dir.getUrl();
    dir.delete(null);

    ContentFolder f = entry.addFolder(url, ExcludedContentFolderTypeProvider.getInstance());
    assertNull(f.getFile());
    assertEquals(url, f.getUrl());

    dir = root.createChildDirectory(null, "src");
    assertEquals(dir, f.getFile());
    assertEquals(url, f.getUrl());
  }

  public void testCreationOfExcludedFolderWithUrlWhenFileExists() throws IOException {
    VirtualFile dir = root.createChildDirectory(null, "src");
    String url = dir.getUrl();

    ContentFolder f = entry.addFolder(url, ExcludedContentFolderTypeProvider.getInstance());
    assertEquals(dir, f.getFile());
    assertEquals(url, f.getUrl());
  }
}
