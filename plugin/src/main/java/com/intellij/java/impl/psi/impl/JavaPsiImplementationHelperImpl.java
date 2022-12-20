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
package com.intellij.java.impl.psi.impl;

import com.intellij.java.language.impl.codeInsight.template.JavaTemplateUtil;
import com.intellij.java.impl.psi.impl.source.codeStyle.ImportHelper;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.impl.JavaClassFileType;
import com.intellij.java.language.impl.psi.impl.JavaPsiImplementationHelper;
import com.intellij.java.language.impl.psi.impl.compiled.ClsClassImpl;
import com.intellij.java.language.impl.psi.impl.compiled.ClsElementImpl;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.*;
import consulo.annotation.component.ServiceImpl;
import consulo.component.ProcessCanceledException;
import consulo.content.base.BinariesOrderRootType;
import consulo.content.base.SourcesOrderRootType;
import consulo.fileTemplate.FileTemplate;
import consulo.fileTemplate.FileTemplateManager;
import consulo.ide.ServiceManager;
import consulo.ide.impl.idea.openapi.module.ModuleUtil;
import consulo.ide.impl.idea.openapi.roots.impl.LibraryScopeCache;
import consulo.ide.impl.idea.openapi.vfs.VfsUtilCore;
import consulo.ide.impl.language.codeStyle.arrangement.MemberOrderService;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.language.ast.ASTNode;
import consulo.language.codeStyle.CodeStyleSettings;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.language.psi.*;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.module.content.DirectoryIndex;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.module.content.layer.orderEntry.OrderEntry;
import consulo.module.content.layer.orderEntry.OrderEntryWithTracking;
import consulo.project.DumbService;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * @author yole
 */
@Singleton
@ServiceImpl
public class JavaPsiImplementationHelperImpl extends JavaPsiImplementationHelper {
  private static final Logger LOG = Logger.getInstance(JavaPsiImplementationHelperImpl.class);

  private final Project myProject;

  @Inject
  public JavaPsiImplementationHelperImpl(Project project) {
    myProject = project;
  }

  @Override
  public PsiClass getOriginalClass(PsiClass psiClass) {
    PsiCompiledElement cls = psiClass.getUserData(ClsElementImpl.COMPILED_ELEMENT);
    if (cls != null && cls.isValid()) {
      return (PsiClass)cls;
    }

    if (DumbService.isDumb(myProject)) {
      return psiClass;
    }

    VirtualFile vFile = psiClass.getContainingFile().getVirtualFile();
    final ProjectFileIndex idx = ProjectRootManager.getInstance(myProject).getFileIndex();
    if (vFile == null || !idx.isInLibrarySource(vFile)) {
      return psiClass;
    }

    String fqn = psiClass.getQualifiedName();
    if (fqn == null) {
      return psiClass;
    }

    final Set<OrderEntry> orderEntries = Set.copyOf(idx.getOrderEntriesForFile(vFile));
    GlobalSearchScope librariesScope = LibraryScopeCache.getInstance(myProject).getLibrariesOnlyScope();
    for (PsiClass original : JavaPsiFacade.getInstance(myProject).findClasses(fqn, librariesScope)) {
      PsiFile psiFile = original.getContainingFile();
      if (psiFile != null) {
        VirtualFile candidateFile = psiFile.getVirtualFile();
        if (candidateFile != null) {
          // order for file and vFile has non empty intersection.
          List<OrderEntry> entries = idx.getOrderEntriesForFile(candidateFile);
          //noinspection ForLoopReplaceableByForEach
          for (int i = 0; i < entries.size(); i++) {
            if (orderEntries.contains(entries.get(i))) {
              return original;
            }
          }
        }
      }
    }

    return psiClass;
  }

  @Override
  public PsiElement getClsFileNavigationElement(PsiJavaFile clsFile) {
    PsiClass[] classes = clsFile.getClasses();
    if (classes.length == 0) {
      return clsFile;
    }

    String sourceFileName = ((ClsClassImpl)classes[0]).getSourceFileName();
    String packageName = clsFile.getPackageName();
    String relativePath = packageName.isEmpty() ? sourceFileName : packageName.replace('.', '/') + '/' + sourceFileName;

    ProjectFileIndex index = ProjectFileIndex.SERVICE.getInstance(clsFile.getProject());
    for (OrderEntry orderEntry : index.getOrderEntriesForFile(clsFile.getContainingFile().getVirtualFile())) {
      if (!(orderEntry instanceof OrderEntryWithTracking)) {
        continue;
      }
      for (VirtualFile root : orderEntry.getFiles(SourcesOrderRootType.getInstance())) {
        VirtualFile source = root.findFileByRelativePath(relativePath);
        if (source != null && source.isValid()) {
          PsiFile psiSource = clsFile.getManager().findFile(source);
          if (psiSource instanceof PsiClassOwner) {
            return psiSource;
          }
        }
      }
    }

    return clsFile;
  }

  @Nullable
  @Override
  public LanguageLevel getClassesLanguageLevel(VirtualFile virtualFile) {
    final ProjectFileIndex index = ProjectRootManager.getInstance(myProject).getFileIndex();
    final VirtualFile sourceRoot = index.getSourceRootForFile(virtualFile);
    final VirtualFile folder = virtualFile.getParent();
    if (sourceRoot != null && folder != null) {
      String relativePath = VfsUtilCore.getRelativePath(folder, sourceRoot, '/');
      if (relativePath == null) {
        throw new AssertionError("Null relative path: folder=" + folder + "; root=" + sourceRoot);
      }
      List<OrderEntry> orderEntries = index.getOrderEntriesForFile(virtualFile);
      if (orderEntries.isEmpty()) {
        LOG.error("Inconsistent: " + DirectoryIndex.getInstance(myProject).getInfoForDirectory(folder).toString());
      }
      final VirtualFile[] files = orderEntries.get(0).getFiles(BinariesOrderRootType.getInstance());
      for (VirtualFile rootFile : files) {
        final VirtualFile classFile = rootFile.findFileByRelativePath(relativePath);
        if (classFile != null) {
          final PsiJavaFile javaFile = getPsiFileInRoot(classFile);
          if (javaFile != null) {
            return javaFile.getLanguageLevel();
          }
        }
      }
      final Module moduleForFile = ModuleUtil.findModuleForFile(virtualFile, myProject);
      if (moduleForFile == null) {
        return null;
      }
      final JavaModuleExtension extension = ModuleUtil.getExtension(moduleForFile, JavaModuleExtension.class);
      return extension == null ? null : extension.getLanguageLevel();
    }
    return null;
  }

  @Nullable
  private PsiJavaFile getPsiFileInRoot(final VirtualFile dirFile) {
    final VirtualFile[] children = dirFile.getChildren();
    for (VirtualFile child : children) {
      if (JavaClassFileType.INSTANCE.equals(child.getFileType())) {
        final PsiFile psiFile = PsiManager.getInstance(myProject).findFile(child);
        if (psiFile instanceof PsiJavaFile) {
          return (PsiJavaFile)psiFile;
        }
      }
    }
    return null;
  }

  @Override
  public ASTNode getDefaultImportAnchor(PsiImportList list, PsiImportStatementBase statement) {
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(list.getProject());
    ImportHelper importHelper = new ImportHelper(settings);
    return importHelper.getDefaultAnchor(list, statement);
  }

  @Nullable
  @Override
  public PsiElement getDefaultMemberAnchor(@Nonnull PsiClass aClass, @Nonnull PsiMember member) {
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(aClass.getProject());
    MemberOrderService service = ServiceManager.getService(MemberOrderService.class);
    PsiElement anchor = service.getAnchor(member, settings.getCommonSettings(JavaLanguage.INSTANCE), aClass);

    PsiElement newAnchor = skipWhitespaces(aClass, anchor);
    if (newAnchor != null) {
      return newAnchor;
    }

    if (anchor != null && anchor != aClass) {
      anchor = anchor.getNextSibling();
      while (anchor instanceof PsiJavaToken && (anchor.getText().equals(",") || anchor.getText().equals(";"))) {
        final boolean afterComma = anchor.getText().equals(",");
        anchor = anchor.getNextSibling();
        if (afterComma) {
          newAnchor = skipWhitespaces(aClass, anchor);
          if (newAnchor != null) {
            return newAnchor;
          }
        }
      }
      if (anchor != null) {
        return anchor;
      }
    }

    // The main idea is to avoid to anchor to 'white space' element because that causes reformatting algorithm
    // to perform incorrectly. The algorithm is encapsulated at the PostprocessReformattingAspect.doPostponedFormattingInner().
    final PsiElement lBrace = aClass.getLBrace();
    if (lBrace != null) {
      PsiElement result = lBrace.getNextSibling();
      while (result instanceof PsiWhiteSpace) {
        result = result.getNextSibling();
      }
      return result;
    }

    return aClass.getRBrace();
  }

  private static PsiElement skipWhitespaces(PsiClass aClass, PsiElement anchor) {
    if (anchor != null && PsiTreeUtil.skipSiblingsForward(anchor, PsiWhiteSpace.class) == aClass.getRBrace()) {
      // Given member should be inserted as the last child.
      return aClass.getRBrace();
    }
    return null;
  }

  @Override
  public void setupCatchBlock(String exceptionName, PsiElement context, PsiCatchSection catchSection) {
    final FileTemplate catchBodyTemplate =
      FileTemplateManager.getInstance(catchSection.getProject()).getCodeTemplate(JavaTemplateUtil.TEMPLATE_CATCH_BODY);
    LOG.assertTrue(catchBodyTemplate != null);

    final Properties props = new Properties();
    props.setProperty(FileTemplate.ATTRIBUTE_EXCEPTION, exceptionName);
    if (context != null && context.isPhysical()) {
      final PsiDirectory directory = context.getContainingFile().getContainingDirectory();
      if (directory != null) {
        JavaTemplateUtil.setPackageNameAttribute(props, directory);
      }
    }

    final PsiCodeBlock codeBlockFromText;
    try {
      codeBlockFromText =
        PsiElementFactory.getInstance(myProject).createCodeBlockFromText("{\n" + catchBodyTemplate.getText(props) + "\n}", null);
    }
    catch (ProcessCanceledException ce) {
      throw ce;
    }
    catch (Throwable e) {
      throw new IncorrectOperationException("Incorrect file template", e);
    }
    catchSection.getCatchBlock().replace(codeBlockFromText);
  }
}
