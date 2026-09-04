/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.language.impl.psi.impl.compiled;

import com.intellij.java.language.impl.psi.impl.cache.ModifierFlags;
import com.intellij.java.language.impl.psi.impl.java.stubs.JavaPackageAccessibilityStatementElementType;
import com.intellij.java.language.impl.psi.impl.java.stubs.PsiJavaFileStub;
import com.intellij.java.language.impl.psi.impl.java.stubs.PsiJavaModuleStub;
import com.intellij.java.language.impl.psi.impl.java.stubs.impl.*;
import consulo.internal.org.objectweb.asm.AnnotationVisitor;
import consulo.internal.org.objectweb.asm.Attribute;
import consulo.internal.org.objectweb.asm.ClassVisitor;
import consulo.internal.org.objectweb.asm.ModuleVisitor;
import consulo.internal.org.objectweb.asm.Opcodes;
import consulo.internal.org.objectweb.asm.commons.ModuleResolutionAttribute;
import consulo.util.collection.ArrayUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static com.intellij.java.language.impl.psi.impl.java.stubs.JavaStubElementTypes.*;
import static consulo.util.collection.ContainerUtil.map2Array;
import static consulo.util.lang.BitUtil.isSet;

public class ModuleStubBuildingVisitor extends ClassVisitor {
  private static final Function<String, String> NAME_MAPPER = name1 -> name1.replace('/', '.');

  public static final Attribute[] ATTRIBUTES = {new ModuleResolutionAttribute()};

  private final ModuleStubBuilder myBuilder;

  public ModuleStubBuildingVisitor(PsiJavaFileStub parent) {
    super(Opcodes.API_VERSION);
    myBuilder = new ModuleStubBuilder(parent);
  }

  public PsiJavaModuleStub getResult() {
    return myBuilder.build();
  }

  public Attribute[] attributes() {
    return ATTRIBUTES;
  }

  @Override
  public ModuleVisitor visitModule(String name, int access, String version) {
    myBuilder.name(name);
    myBuilder.flags(moduleFlags(access));

    return new ModuleVisitor(Opcodes.API_VERSION) {
      @Override
      public void visitRequire(String module, int access, String version) {
        if (!isGenerated(access)) {
          myBuilder.addRequires(module, requiresFlags(access));
        }
      }

      @Override
      public void visitExport(String packageName, int access, String... modules) {
        if (!isGenerated(access)) {
          myBuilder.addPackageAccessibility(EXPORTS_STATEMENT, packageName, modules);
        }
      }

      @Override
      public void visitOpen(String packageName, int access, String... modules) {
        if (!isGenerated(access)) {
          myBuilder.addPackageAccessibility(OPENS_STATEMENT, packageName, modules);
        }
      }

      @Override
      public void visitUse(String service) {
        myBuilder.addUses(service);
      }

      @Override
      public void visitProvide(String service, String... providers) {
        myBuilder.addProvide(service, providers);
      }
    };
  }

  @Override
  public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
    return StubBuildingVisitor.getAnnotationTextCollector(desc, text -> myBuilder.addAnnotation(text));
  }

  @Override
  public void visitAttribute(Attribute attribute) {
    if (attribute instanceof ModuleResolutionAttribute resolutionAttribute) {
      myBuilder.resolution(resolutionAttribute.resolution);
    }
    super.visitAttribute(attribute);
  }

  private static boolean isGenerated(int access) {
    return isSet(access, Opcodes.ACC_SYNTHETIC) || isSet(access, Opcodes.ACC_MANDATED);
  }

  private static int moduleFlags(int access) {
    return isSet(access, Opcodes.ACC_OPEN) ? ModifierFlags.OPEN_MASK : 0;
  }

  private static int requiresFlags(int access) {
    int flags = 0;
    if (isSet(access, Opcodes.ACC_TRANSITIVE)) {
      flags |= ModifierFlags.TRANSITIVE_MASK;
    }
    if (isSet(access, Opcodes.ACC_STATIC_PHASE)) {
      flags |= ModifierFlags.STATIC_MASK;
    }
    return flags;
  }

  private static class ModuleStubBuilder {
    private final PsiJavaFileStub myParent;

    private volatile PsiJavaModuleStub myResult;

    private volatile String myName;
    private volatile int myFlags;
    private volatile int myResolution;

    private final List<Requires> myRequires = new ArrayList<>();
    private final List<PackageAccessibility> myPackageAccessibilities = new ArrayList<>();
    private final List<Provide> myProvides = new ArrayList<>();
    private final List<String> myServices = new ArrayList<>();
    private final List<String> myAnnotations = new ArrayList<>();

    ModuleStubBuilder(PsiJavaFileStub parent) {
      myParent = parent;
    }

    void name(String name) {
      myName = name;
    }

    void flags(int flags) {
      myFlags = flags;
    }

    void resolution(int resolution) {
      myResolution = resolution;
    }

    void addRequires(String module, int flags) {
      myRequires.add(new Requires(module, flags));
    }

    void addPackageAccessibility(JavaPackageAccessibilityStatementElementType type, String packageName, String[] modules) {
      myPackageAccessibilities.add(new PackageAccessibility(type, packageName, modules));
    }

    void addUses(String service) {
      myServices.add(service);
    }

    void addProvide(String service, String[] providers) {
      myProvides.add(new Provide(service, providers));
    }

    void addAnnotation(String text) {
      myAnnotations.add(text);
    }

    PsiJavaModuleStub build() {
      if (myResult == null) {
        synchronized (this) {
          if (myResult == null) {
            if (myName == null) {
              return null;
            }
            PsiJavaModuleStub result = new PsiJavaModuleStubImpl(myParent, myName, myResolution);
            PsiModifierListStubImpl modifiers = new PsiModifierListStubImpl(result, myFlags);
            for (String annotation : myAnnotations) {
              new PsiAnnotationStubImpl(modifiers, annotation);
            }
            for (Requires require : myRequires) {
              PsiRequiresStatementStubImpl statementStub = new PsiRequiresStatementStubImpl(result, require.myName);
              new PsiModifierListStubImpl(statementStub, require.myFlags);
            }
            for (PackageAccessibility accessibility : myPackageAccessibilities) {
              new PsiPackageAccessibilityStatementStubImpl(
                result,
                accessibility.myType,
                NAME_MAPPER.apply(accessibility.myPackageName),
                accessibility.myModules == null ? null : Arrays.asList(accessibility.myModules)
              );
            }
            for (String service : myServices) {
              new PsiUsesStatementStubImpl(result, NAME_MAPPER.apply(service));
            }
            for (Provide provide : myProvides) {
              PsiProvidesStatementStubImpl statementStub = new PsiProvidesStatementStubImpl(result, NAME_MAPPER.apply(provide.myService));
              String[] names = map2Array(provide.myProviders, String.class, NAME_MAPPER);
              new PsiClassReferenceListStubImpl(PROVIDES_WITH_LIST, statementStub, names.length == 0 ? ArrayUtil.EMPTY_STRING_ARRAY : names);
            }
            myResult = result;
          }
        }
      }
      return myResult;
    }
  }

  private record Requires(String myName, int myFlags) {
  }

  private record PackageAccessibility(JavaPackageAccessibilityStatementElementType myType, String myPackageName, String[] myModules) {
  }

  private record Provide(String myService, String[] myProviders) {
  }
}
