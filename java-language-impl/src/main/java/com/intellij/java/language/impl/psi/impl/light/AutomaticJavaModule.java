// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.impl.light;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.util.CachedValueProvider;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.logging.Logger;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import consulo.virtualFileSystem.util.VirtualFileVisitor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AutomaticJavaModule extends LightJavaModuleBase {
  private final VirtualFile myRoot;

  private AutomaticJavaModule(@Nonnull PsiManager manager, @Nonnull VirtualFile root, @Nonnull String name) {
    super(manager, name);
    myRoot = root;
  }

  @Nonnull
  @Override
  public VirtualFile getRootVirtualFile() {
    return myRoot;
  }

  @Override
  protected List<PsiPackageAccessibilityStatement> findExports() {
    List<PsiPackageAccessibilityStatement> exports = new ArrayList<>();

    VirtualFileUtil.visitChildrenRecursively(myRoot, new VirtualFileVisitor<Void>() {
      private final JavaDirectoryService service = JavaDirectoryService.getInstance();

      @Override
      public boolean visitFile(@Nonnull VirtualFile file) {
        if (file.isDirectory() && !myRoot.equals(file)) {
          PsiDirectory directory = getManager().findDirectory(file);
          if (directory != null) {
            PsiJavaPackage pkg = service.getPackage(directory);
            if (pkg != null) {
              String packageName = pkg.getQualifiedName();
              if (!packageName.isEmpty() && !PsiUtil.isPackageEmpty(new PsiDirectory[]{directory}, packageName)) {
                exports.add(new LightPackageAccessibilityStatement(getManager(), packageName));
              }
            }
          }
        }
        return true;
      }
    });

    return exports;
  }

  @Override
  @Nonnull
  public PsiElement getNavigationElement() {
    return ObjectUtil.notNull(myManager.findDirectory(myRoot), super.getNavigationElement());
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof AutomaticJavaModule && myRoot.equals(((AutomaticJavaModule)obj).myRoot) && getManager() == ((AutomaticJavaModule)obj).getManager();
  }

  /**
   * @deprecated method scope was extended, use {@link #findModule} instead
   */
  @Deprecated
  @Nonnull
  public static AutomaticJavaModule getModule(@Nonnull PsiManager manager, @Nonnull VirtualFile root) {
    AutomaticJavaModule module = findModule(manager, root);
    assert module != null : root;
    return module;
  }

  /**
   * The method is expected to be called on roots obtained from JavaAutoModuleNameIndex/JavaSourceModuleNameIndex
   */
  @Nullable
  @RequiredReadAction
  public static AutomaticJavaModule findModule(@Nonnull PsiManager manager, @Nonnull VirtualFile root) {
    PsiElement directory = manager.findDirectory(root);
    if (directory == null) {
      return null;
    }
    if (root.isInLocalFileSystem()) {
      return LanguageCachedValueUtil.getCachedValue(directory, () -> {
        VirtualFile manifest = root.findFileByRelativePath(JarFile.MANIFEST_NAME);
        if (manifest != null) {
          PsiElement file = manager.findFile(manifest);
          if (file != null) {
            String name = claimedModuleName(manifest);
            AutomaticJavaModule module = name != null ? new AutomaticJavaModule(manager, root, name) : null;
            return CachedValueProvider.Result.create(module, file);
          }
        }
        return CachedValueProvider.Result.create(null, PsiModificationTracker.MODIFICATION_COUNT);
      });
    }
    else {
      return LanguageCachedValueUtil.getCachedValue(directory, () -> {
        AutomaticJavaModule module = new AutomaticJavaModule(manager, root, moduleName(root));
        return CachedValueProvider.Result.create(module, directory);
      });
    }
  }

  @Nonnull
  public static String moduleName(@Nonnull VirtualFile jarRoot) {
    VirtualFile manifest = jarRoot.findFileByRelativePath(JarFile.MANIFEST_NAME);
    if (manifest != null) {
      String claimed = claimedModuleName(manifest);
      if (claimed != null) {
        return claimed;
      }
    }

    return moduleName(jarRoot.getNameWithoutExtension());
  }

  @Nullable
  public static String claimedModuleName(@Nonnull VirtualFile manifest) {
    try (InputStream stream = manifest.getInputStream()) {
      return new Manifest(stream).getMainAttributes().getValue(PsiJavaModule.AUTO_MODULE_NAME);
    }
    catch (IOException e) {
      Logger.getInstance(AutomaticJavaModule.class).warn(manifest.getPath(), e);
      return null;
    }
  }

  /**
   * <p>Implements a name deriving for automatic modules as described in ModuleFinder.of(Path...) method documentation.</p>
   *
   * <p>Please note that the result may not be a valid module name when the source contains a sequence that starts with a digit
   * (e.g. "org.7gnomes..."). One may validate the result with {@link PsiNameHelper#isValidModuleName}.</p>
   *
   * @param name a .jar file name without extension
   * @see <a href="http://docs.oracle.com/javase/9/docs/api/java/lang/module/ModuleFinder.html#of-java.nio.file.Path...-">ModuleFinder.of(Path...)</a>
   */
  @Nonnull
  public static String moduleName(@Nonnull String name) {
    // If the name matches the regular expression "-(\\d+(\\.|$))" then the module name will be derived from the sub-sequence
    // preceding the hyphen of the first occurrence.
    Matcher m = Patterns.VERSION.matcher(name);
    if (m.find()) {
      name = name.substring(0, m.start());
    }

    // All non-alphanumeric characters ([^A-Za-z0-9]) are replaced with a dot (".") ...
    name = Patterns.NON_NAME.matcher(name).replaceAll(".");
    // ... all repeating dots are replaced with one dot ...
    name = Patterns.DOT_SEQUENCE.matcher(name).replaceAll(".");
    // ... and all leading and trailing dots are removed.
    name = StringUtil.trimLeading(StringUtil.trimTrailing(name, '.'), '.');

    return name;
  }

  private static class Patterns {
    private static final Pattern VERSION = Pattern.compile("-(\\d+(\\.|$))");
    private static final Pattern NON_NAME = Pattern.compile("[^A-Za-z0-9]");
    private static final Pattern DOT_SEQUENCE = Pattern.compile("\\.{2,}");
  }
}
