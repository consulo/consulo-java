/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.java.language.projectRoots;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.application.util.JavaVersion;
import consulo.application.Application;
import consulo.platform.CpuArchitecture;
import org.jspecify.annotations.Nullable;

/**
 * @author nik
 */
@ServiceAPI(ComponentScope.APPLICATION)
public abstract class OwnJdkVersionDetector {
    public record JdkVersionInfo(JavaVersion version, CpuArchitecture arch) {
    }

    public static OwnJdkVersionDetector getInstance() {
        return Application.get().getInstance(OwnJdkVersionDetector.class);
    }

    @Nullable
    public abstract JdkVersionInfo detectJdkVersionInfo(String homePath);

    public static String formatVersionString(JavaVersion version) {
        return "java version \"" + version + '"';
    }
}