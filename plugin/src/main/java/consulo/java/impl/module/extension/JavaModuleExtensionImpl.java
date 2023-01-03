/*
 * Copyright 2013 Consulo.org
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
package consulo.java.impl.module.extension;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.projectRoots.JavaSdk;
import com.intellij.java.language.projectRoots.JavaSdkVersion;
import consulo.annotation.access.RequiredReadAction;
import consulo.compiler.CompileContext;
import consulo.compiler.ModuleChunk;
import consulo.content.bundle.Sdk;
import consulo.content.bundle.SdkType;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.java.language.module.extension.SpecialDirLocation;
import consulo.module.content.layer.ModuleRootLayer;
import consulo.module.content.layer.extension.ModuleExtensionWithSdkBase;
import consulo.module.extension.ModuleInheritableNamedPointer;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import org.jdom.Element;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author VISTALL
 * @since 10:02/19.05.13
 */
public class JavaModuleExtensionImpl extends ModuleExtensionWithSdkBase<JavaModuleExtensionImpl> implements JavaModuleExtension<JavaModuleExtensionImpl> {
  private static final String SPECIAL_DIR_LOCATION = "special-dir-location";
  private static final String BYTECODE_VERSION = "bytecode-version";
  private static final String COMPILER_ARGUMENTS = "compiler-arguments";
  private static final String COMPILER_ARGUMENT = "compiler-argument";

  protected LanguageLevelModuleInheritableNamedPointerImpl myLanguageLevel;
  protected SpecialDirLocation mySpecialDirLocation = SpecialDirLocation.SOURCE_DIR;
  protected String myBytecodeVersion;
  protected List<String> myCompilerArguments = new ArrayList<>();

  private LazyValueBySdk<LanguageLevel> myLanguageLevelValue;

  public JavaModuleExtensionImpl(@Nonnull String id, @Nonnull ModuleRootLayer moduleRootLayer) {
    super(id, moduleRootLayer);
    myLanguageLevel = new LanguageLevelModuleInheritableNamedPointerImpl(moduleRootLayer, id);
    myLanguageLevelValue = new LazyValueBySdk<>(this, LanguageLevel.HIGHEST, sdk -> {
      JavaSdkVersion sdkVersion = JavaSdk.getInstance().getVersion(sdk);
      return sdkVersion == null ? LanguageLevel.HIGHEST : sdkVersion.getMaxLanguageLevel();
    });
  }

  @RequiredReadAction
  @Override
  public void commit(@Nonnull JavaModuleExtensionImpl mutableModuleExtension) {
    super.commit(mutableModuleExtension);

    myLanguageLevel.set(mutableModuleExtension.getInheritableLanguageLevel());
    mySpecialDirLocation = mutableModuleExtension.getSpecialDirLocation();
    myBytecodeVersion = mutableModuleExtension.getBytecodeVersion();
    myCompilerArguments.clear();
    myCompilerArguments.addAll(mutableModuleExtension.getCompilerArguments());
  }

  @Override
  @Nonnull
  public LanguageLevel getLanguageLevel() {
    return myLanguageLevel.isNull() ? myLanguageLevelValue.getValue() : myLanguageLevel.get();
  }

  @Nullable
  @Override
  public LanguageLevel getLanguageLevelNoDefault() {
    return myLanguageLevel.get();
  }

  @Override
  @Nonnull
  public SpecialDirLocation getSpecialDirLocation() {
    return mySpecialDirLocation;
  }

  @Nullable
  @Override
  public Sdk getSdkForCompilation() {
    return getSdk();
  }

  @Nullable
  @Override
  public String getBytecodeVersion() {
    return myBytecodeVersion;
  }

  @Nonnull
  @Override
  public Set<VirtualFile> getCompilationClasspath(@Nonnull CompileContext compileContext, @Nonnull ModuleChunk moduleChunk) {
    return moduleChunk.getCompilationClasspathFiles(JavaSdk.getInstance());
  }

  @Nonnull
  @Override
  public Set<VirtualFile> getCompilationBootClasspath(@Nonnull CompileContext compileContext, @Nonnull ModuleChunk moduleChunk) {
    return moduleChunk.getCompilationBootClasspathFiles(JavaSdk.getInstance());
  }

  @Nonnull
  public ModuleInheritableNamedPointer<LanguageLevel> getInheritableLanguageLevel() {
    return myLanguageLevel;
  }

  @Nonnull
  @Override
  public Class<? extends SdkType> getSdkTypeClass() {
    return JavaSdk.class;
  }

  @Nonnull
  @Override
  public List<String> getCompilerArguments() {
    return myCompilerArguments;
  }

  @Override
  protected void getStateImpl(@Nonnull Element element) {
    super.getStateImpl(element);

    myLanguageLevel.toXml(element);

    if (mySpecialDirLocation != SpecialDirLocation.SOURCE_DIR) {
      element.setAttribute(SPECIAL_DIR_LOCATION, mySpecialDirLocation.name());
    }

    if (!StringUtil.isEmpty(myBytecodeVersion)) {
      element.setAttribute(BYTECODE_VERSION, myBytecodeVersion);
    }

    if (!myCompilerArguments.isEmpty()) {
      Element compilerArgs = new Element(COMPILER_ARGUMENTS);
      element.addContent(compilerArgs);

      for (String compilerArgument : myCompilerArguments) {
        compilerArgs.addContent(new Element(COMPILER_ARGUMENT).setText(compilerArgument));
      }
    }
  }

  @RequiredReadAction
  @Override
  protected void loadStateImpl(@Nonnull Element element) {
    super.loadStateImpl(element);

    myLanguageLevel.fromXml(element);
    mySpecialDirLocation = SpecialDirLocation.valueOf(element.getAttributeValue(SPECIAL_DIR_LOCATION, SpecialDirLocation.SOURCE_DIR.name()));
    myBytecodeVersion = element.getAttributeValue(BYTECODE_VERSION, (String) null);

    Element compilerArgs = element.getChild(COMPILER_ARGUMENTS);
    if (compilerArgs != null) {
      for (Element compilerArg : compilerArgs.getChildren(COMPILER_ARGUMENT)) {
        myCompilerArguments.add(compilerArg.getTextTrim());
      }
    }
  }
}