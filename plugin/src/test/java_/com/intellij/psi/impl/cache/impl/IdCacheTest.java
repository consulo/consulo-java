/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.impl.cache.impl;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightTestCase;
import consulo.ide.impl.idea.ide.todo.TodoConfiguration;
import consulo.ide.impl.idea.ide.todo.TodoIndexPatternProvider;
import consulo.document.FileDocumentManager;
import consulo.ide.impl.idea.openapi.util.io.FileUtil;
import consulo.ide.impl.idea.openapi.vfs.VfsUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiFile;
import consulo.language.cacheBuilder.CacheManager;
import consulo.language.psi.stub.todo.TodoCacheManager;
import consulo.ide.impl.psi.impl.cache.impl.id.IdIndex;
import consulo.ide.impl.psi.impl.cache.impl.todo.TodoIndex;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.ide.impl.psi.search.TodoAttributesUtil;
import consulo.language.psi.search.TodoPattern;
import consulo.language.psi.search.UsageSearchContext;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import consulo.util.collection.ArrayUtil;
import consulo.language.psi.stub.FileBasedIndex;

public abstract class IdCacheTest extends CodeInsightTestCase{

  private VirtualFile myRootDir;
  private File myCacheFile;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    FileBasedIndex.getInstance().requestRebuild(IdIndex.NAME);
    FileBasedIndex.getInstance().requestRebuild(TodoIndex.NAME);

    String root = JavaTestUtil.getJavaTestDataPath()+ "/psi/impl/cache/";

    PsiTestUtil.removeAllRoots(myModule, IdeaTestUtil.getMockJdk17());
    myRootDir = PsiTestUtil.createTestProjectStructure(myProject, myModule, root, myFilesToDelete);

    myCacheFile = FileUtil.createTempFile("cache", "");
    myCacheFile.delete();
    myFilesToDelete.add(myCacheFile);
  }

  public void testBuildCache() throws Exception {
    checkCache(CacheManager.getInstance(myProject), TodoCacheManager.getInstance(myProject));
  }

  public void testLoadCacheNoTodo() throws Exception {

    CacheManager cache = CacheManager.getInstance(myProject);

    checkResult(new String[]{"1.java", "2.java"}, convert(cache.getFilesWithWord("b", UsageSearchContext.ANY, GlobalSearchScope.projectScope(myProject), false)));
  }

  public void testUpdateCache1() throws Exception {
    myRootDir.createChildData(null, "4.java");
    Thread.sleep(1000);
    checkCache(CacheManager.getInstance(myProject), TodoCacheManager.getInstance(myProject));
  }

  public void testUpdateCache2() throws Exception {
    VirtualFile child = myRootDir.findChild("1.java");
    VfsUtil.saveText(child, "xxx");

    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    FileDocumentManager.getInstance().saveAllDocuments();

    CacheManager cache = CacheManager.getInstance(myProject);
    TodoCacheManager todocache = TodoCacheManager.getInstance(myProject);
    GlobalSearchScope scope = GlobalSearchScope.projectScope(myProject);
    checkResult(new String[] {"1.java"}, convert(cache.getFilesWithWord("xxx", UsageSearchContext.ANY, scope, false)));
    checkResult(new String[]{}, convert(cache.getFilesWithWord("a", UsageSearchContext.ANY, scope, false)));
    checkResult(new String[]{"2.java"}, convert(cache.getFilesWithWord("b", UsageSearchContext.ANY,scope, false)));
    checkResult(new String[]{"2.java", "3.java"}, convert(cache.getFilesWithWord("c", UsageSearchContext.ANY,scope, false)));
    checkResult(new String[]{"2.java", "3.java"}, convert(cache.getFilesWithWord("d", UsageSearchContext.ANY,scope, false)));
    checkResult(new String[]{"3.java"}, convert(cache.getFilesWithWord("e", UsageSearchContext.ANY,scope, false)));

    checkResult(new String[]{"3.java"}, convert(todocache.getFilesWithTodoItems()));
    assertEquals(0, todocache.getTodoCount(myRootDir.findChild("1.java"), TodoIndexPatternProvider.getInstance()));
    assertEquals(0, todocache.getTodoCount(myRootDir.findChild("2.java"), TodoIndexPatternProvider.getInstance()));
    assertEquals(2, todocache.getTodoCount(myRootDir.findChild("3.java"), TodoIndexPatternProvider.getInstance()));
  }

  public void testUpdateCache3() throws Exception {
    VirtualFile child = myRootDir.findChild("1.java");
    child.delete(null);

    CacheManager cache2 = CacheManager.getInstance(myProject);
    TodoCacheManager todocache2 = TodoCacheManager.getInstance(myProject);
    GlobalSearchScope scope = GlobalSearchScope.projectScope(myProject);
    checkResult(ArrayUtil.EMPTY_STRING_ARRAY, convert(cache2.getFilesWithWord("xxx", UsageSearchContext.ANY, scope, false)));
    checkResult(ArrayUtil.EMPTY_STRING_ARRAY, convert(cache2.getFilesWithWord("a", UsageSearchContext.ANY, scope, false)));
    checkResult(new String[]{"2.java"}, convert(cache2.getFilesWithWord("b", UsageSearchContext.ANY, scope, false)));
    checkResult(new String[]{"2.java", "3.java"}, convert(cache2.getFilesWithWord("c", UsageSearchContext.ANY, scope, false)));
    checkResult(new String[]{"2.java", "3.java"}, convert(cache2.getFilesWithWord("d", UsageSearchContext.ANY, scope, false)));
    checkResult(new String[]{"3.java"}, convert(cache2.getFilesWithWord("e", UsageSearchContext.ANY, scope, false)));

    checkResult(new String[]{"3.java"}, convert(todocache2.getFilesWithTodoItems()));
    assertEquals(0, todocache2.getTodoCount(myRootDir.findChild("2.java"), TodoIndexPatternProvider.getInstance()));
    assertEquals(2, todocache2.getTodoCount(myRootDir.findChild("3.java"), TodoIndexPatternProvider.getInstance()));
  }

  public void testUpdateCacheNoTodo() throws Exception {
    myRootDir.createChildData(null, "4.java");
    GlobalSearchScope scope = GlobalSearchScope.projectScope(myProject);
    CacheManager cache = CacheManager.getInstance(myProject);
    checkResult(new String[]{"1.java", "2.java"}, convert(cache.getFilesWithWord("b", UsageSearchContext.ANY, scope, false)));
  }

  public void testUpdateOnTodoChange() throws Exception {
    TodoPattern pattern = new TodoPattern("newtodo", TodoAttributesUtil.createDefault(), true);
    TodoPattern[] oldPatterns = TodoConfiguration.getInstance().getTodoPatterns();
    TodoConfiguration.getInstance().setTodoPatterns(new TodoPattern[]{pattern});

    try{
      TodoCacheManager todocache = TodoCacheManager.getInstance(myProject);
      checkResult(new String[]{"2.java"}, convert(todocache.getFilesWithTodoItems()));
      assertEquals(0, todocache.getTodoCount(myRootDir.findChild("1.java"), TodoIndexPatternProvider.getInstance()));
      assertEquals(1, todocache.getTodoCount(myRootDir.findChild("2.java"), TodoIndexPatternProvider.getInstance()));
      assertEquals(0, todocache.getTodoCount(myRootDir.findChild("3.java"), TodoIndexPatternProvider.getInstance()));
    }
    finally{
      TodoConfiguration.getInstance().setTodoPatterns(oldPatterns);
    }
  }

  public void testFileModification() throws Exception {
    CacheManager cache = CacheManager.getInstance(myProject);
    TodoCacheManager todocache = TodoCacheManager.getInstance(myProject);
    checkCache(cache, todocache);

    VirtualFile child = myRootDir.findChild("1.java");

    checkCache(cache, todocache);

    VfsUtil.saveText(child, "xxx");
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    GlobalSearchScope scope = GlobalSearchScope.projectScope(myProject);
    checkResult(new String[] {"1.java"}, convert(cache.getFilesWithWord("xxx", UsageSearchContext.ANY, scope, false)));
    checkResult(new String[]{}, convert(cache.getFilesWithWord("a", UsageSearchContext.ANY, scope, false)));
    checkResult(new String[]{"2.java"}, convert(cache.getFilesWithWord("b", UsageSearchContext.ANY, scope, false)));
    checkResult(new String[]{"2.java", "3.java"}, convert(cache.getFilesWithWord("c", UsageSearchContext.ANY, scope, false)));
    checkResult(new String[]{"2.java", "3.java"}, convert(cache.getFilesWithWord("d", UsageSearchContext.ANY, scope, false)));
    checkResult(new String[]{"3.java"}, convert(cache.getFilesWithWord("e", UsageSearchContext.ANY, scope, false)));

    checkResult(new String[]{"3.java"}, convert(todocache.getFilesWithTodoItems()));
    assertEquals(0, todocache.getTodoCount(myRootDir.findChild("1.java"), TodoIndexPatternProvider.getInstance()));
    assertEquals(0, todocache.getTodoCount(myRootDir.findChild("2.java"), TodoIndexPatternProvider.getInstance()));
    assertEquals(2, todocache.getTodoCount(myRootDir.findChild("3.java"), TodoIndexPatternProvider.getInstance()));
  }

  public void testFileDeletion() throws Exception {
    CacheManager cache = CacheManager.getInstance(myProject);
    TodoCacheManager todocache = TodoCacheManager.getInstance(myProject);
    checkCache(cache, todocache);

    VirtualFile child = myRootDir.findChild("1.java");
    child.delete(null);

    GlobalSearchScope scope = GlobalSearchScope.projectScope(myProject);
    checkResult(new String[]{}, convert(cache.getFilesWithWord("xxx", UsageSearchContext.ANY, scope, false)));
    checkResult(new String[]{}, convert(cache.getFilesWithWord("a", UsageSearchContext.ANY, scope, false)));
    checkResult(new String[]{"2.java"}, convert(cache.getFilesWithWord("b", UsageSearchContext.ANY, scope, false)));
    checkResult(new String[]{"2.java", "3.java"}, convert(cache.getFilesWithWord("c", UsageSearchContext.ANY, scope, false)));
    checkResult(new String[]{"2.java", "3.java"}, convert(cache.getFilesWithWord("d", UsageSearchContext.ANY, scope, false)));
    checkResult(new String[]{"3.java"}, convert(cache.getFilesWithWord("e", UsageSearchContext.ANY, scope, false)));

    checkResult(new String[]{"3.java"}, convert(todocache.getFilesWithTodoItems()));
    assertEquals(0, todocache.getTodoCount(myRootDir.findChild("2.java"), TodoIndexPatternProvider.getInstance()));
    assertEquals(2, todocache.getTodoCount(myRootDir.findChild("3.java"), TodoIndexPatternProvider.getInstance()));
  }

  public void testFileCreation() throws Exception {
    CacheManager cache = CacheManager.getInstance(myProject);
    TodoCacheManager todocache = TodoCacheManager.getInstance(myProject);
    checkCache(cache, todocache);

    VirtualFile child = myRootDir.createChildData(null, "4.java");
    VfsUtil.saveText(child, "xxx //todo");
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    GlobalSearchScope scope = GlobalSearchScope.projectScope(myProject);
    checkResult(new String[]{"4.java"}, convert(cache.getFilesWithWord("xxx", UsageSearchContext.ANY, scope, false)));
    checkResult(new String[]{"1.java"}, convert(cache.getFilesWithWord("a", UsageSearchContext.ANY, scope, false)));
    checkResult(new String[]{"1.java", "2.java"}, convert(cache.getFilesWithWord("b", UsageSearchContext.ANY, scope, false)));
    checkResult(new String[]{"1.java", "2.java", "3.java"}, convert(cache.getFilesWithWord("c", UsageSearchContext.ANY, scope, false)));
    checkResult(new String[]{"2.java", "3.java"}, convert(cache.getFilesWithWord("d", UsageSearchContext.ANY, scope, false)));
    checkResult(new String[]{"3.java"}, convert(cache.getFilesWithWord("e", UsageSearchContext.ANY, scope, false)));

    checkResult(new String[]{"1.java", "3.java", "4.java"}, convert(todocache.getFilesWithTodoItems()));
    assertEquals(1, todocache.getTodoCount(myRootDir.findChild("1.java"), TodoIndexPatternProvider.getInstance()));
    assertEquals(0, todocache.getTodoCount(myRootDir.findChild("2.java"), TodoIndexPatternProvider.getInstance()));
    assertEquals(2, todocache.getTodoCount(myRootDir.findChild("3.java"), TodoIndexPatternProvider.getInstance()));
    assertEquals(1, todocache.getTodoCount(myRootDir.findChild("4.java"), TodoIndexPatternProvider.getInstance()));
  }

  public void testCrash() throws Exception {
    CacheManager cache = CacheManager.getInstance(myProject);
    cache.getFilesWithWord("xxx", UsageSearchContext.ANY, GlobalSearchScope.projectScope(myProject), false);
    System.gc();
  }

  private void checkCache(CacheManager cache, TodoCacheManager todocache) {
    GlobalSearchScope scope = GlobalSearchScope.projectScope(myProject);
    checkResult(ArrayUtil.EMPTY_STRING_ARRAY, convert(cache.getFilesWithWord("xxx", UsageSearchContext.ANY, scope, false)));
    checkResult(new String[]{"1.java"}, convert(cache.getFilesWithWord("a", UsageSearchContext.ANY, scope, false)));
    checkResult(new String[]{"1.java", "2.java"}, convert(cache.getFilesWithWord("b", UsageSearchContext.ANY, scope, false)));
    checkResult(new String[]{"1.java", "2.java", "3.java"}, convert(cache.getFilesWithWord("c", UsageSearchContext.ANY, scope, false)));
    checkResult(new String[]{"2.java", "3.java"}, convert(cache.getFilesWithWord("d", UsageSearchContext.ANY, scope, false)));
    checkResult(new String[]{"3.java"}, convert(cache.getFilesWithWord("e", UsageSearchContext.ANY, scope, false)));

    checkResult(new String[]{"1.java", "3.java"}, convert(todocache.getFilesWithTodoItems()));
    assertEquals(1, todocache.getTodoCount(myRootDir.findChild("1.java"), TodoIndexPatternProvider.getInstance()));
    assertEquals(0, todocache.getTodoCount(myRootDir.findChild("2.java"), TodoIndexPatternProvider.getInstance()));
    assertEquals(2, todocache.getTodoCount(myRootDir.findChild("3.java"), TodoIndexPatternProvider.getInstance()));
  }

  private static VirtualFile[] convert(PsiFile[] psiFiles) {
    VirtualFile[] files = new VirtualFile[psiFiles.length];
    for (int idx = 0; idx < psiFiles.length; idx++) {
      files[idx] = psiFiles[idx].getVirtualFile();
    }
    return files;
  }
  
  private static void checkResult(String[] expected, VirtualFile[] result){
    assertEquals(expected.length, result.length);
    
    Arrays.sort(expected);
    Arrays.sort(result, new Comparator() {
      @Override
      public int compare(Object o1, Object o2) {
        VirtualFile file1 = (VirtualFile)o1;
        VirtualFile file2 = (VirtualFile)o2;
        return file1.getName().compareTo(file2.getName());
      }
    });

    for(int i = 0; i < expected.length; i++){
      String name = expected[i];
      assertEquals(name, result[i].getName());
    }
  }
}
