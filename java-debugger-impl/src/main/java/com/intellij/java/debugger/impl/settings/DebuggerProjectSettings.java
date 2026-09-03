// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.settings;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import consulo.util.xml.serializer.XmlSerializerUtil;
import consulo.util.xml.serializer.annotation.AbstractCollection;
import consulo.util.xml.serializer.annotation.Tag;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

@Singleton
@State(name = "DebuggerSettings", storages = @Storage("debugger.xml"))
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public class DebuggerProjectSettings implements PersistentStateComponent<DebuggerProjectSettings> {
    @Tag("async-schedule-annotations")
    @AbstractCollection(surroundWithTag = false, elementTag = "annotation", elementValueAttribute = "name")
    public String[] myAsyncScheduleAnnotations = ArrayUtil.EMPTY_STRING_ARRAY;

    @Tag("async-execute-annotations")
    @AbstractCollection(surroundWithTag = false, elementTag = "annotation", elementValueAttribute = "name")
    public String[] myAsyncExecuteAnnotations = ArrayUtil.EMPTY_STRING_ARRAY;

    public static DebuggerProjectSettings getInstance(Project project) {
        return project.getInstance(DebuggerProjectSettings.class);
    }

    @Override
    public @Nullable DebuggerProjectSettings getState() {
        return this;
    }

    @Override
    public void loadState(DebuggerProjectSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}
