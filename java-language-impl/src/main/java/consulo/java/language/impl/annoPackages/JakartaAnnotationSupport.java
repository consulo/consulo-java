// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package consulo.java.language.impl.annoPackages;

import com.intellij.java.language.annoPackages.AnnotationPackageSupport;
import com.intellij.java.language.codeInsight.Nullability;
import consulo.annotation.component.ExtensionImpl;
import jakarta.annotation.Nonnull;

import java.util.Collections;
import java.util.List;

// packages: jakarta.annotation:jakarta.annotation-api
@ExtensionImpl
public final class JakartaAnnotationSupport implements AnnotationPackageSupport {
  @Nonnull
  @Override
  public List<String> getNullabilityAnnotations(@Nonnull Nullability nullability) {
    return switch (nullability) {
      case NOT_NULL -> List.of("jakarta.annotation.Nonnull");
      case NULLABLE -> List.of("jakarta.annotation.Nullable");
      default -> Collections.emptyList();
    };
  }
}
