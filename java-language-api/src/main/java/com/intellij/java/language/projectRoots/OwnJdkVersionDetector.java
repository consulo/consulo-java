/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.java.language.projectRoots;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.application.Application;
import consulo.platform.CpuArchitecture;
import consulo.platform.JavaVersion;
import consulo.platform.Platform;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

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

    /**
     * @param homePath a JDK home of the local machine
     */
    @Nullable
    public JdkVersionInfo detectJdkVersionInfo(String homePath) {
        return detectJdkVersionInfo(Platform.current(), Path.of(homePath));
    }

    /**
     * @param home a JDK home of the platform - read, and run if need be, there
     */
    @Nullable
    public abstract JdkVersionInfo detectJdkVersionInfo(Platform platform, Path home);

    public static String formatVersionString(JavaVersion version) {
        return "java version \"" + version + '"';
    }
}