// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.java.language.impl.annoPackages;

import com.intellij.java.language.annoPackages.AnnotationPackageSupport;
import com.intellij.java.language.codeInsight.Nullability;
import consulo.annotation.component.ExtensionImpl;
import jakarta.annotation.Nonnull;

import java.util.Collections;
import java.util.List;

@ExtensionImpl
public final class JetBrainsAnnotationSupport implements AnnotationPackageSupport {
  @Nonnull
  @Override
  public List<String> getNullabilityAnnotations(@Nonnull Nullability nullability) {
    return switch (nullability) {
      case NOT_NULL -> Collections.singletonList("org.jetbrains.annotations.NotNull");
      case NULLABLE -> Collections.singletonList("org.jetbrains.annotations.Nullable");
      case UNKNOWN -> Collections.singletonList("org.jetbrains.annotations.UnknownNullability");
    };
  }

  @Override
  public boolean isTypeUseAnnotationLocationRestricted() {
    return true;
  }
}
