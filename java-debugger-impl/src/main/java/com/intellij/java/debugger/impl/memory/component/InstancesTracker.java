/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.java.debugger.impl.memory.component;

import com.intellij.java.debugger.impl.memory.event.InstancesTrackerListener;
import com.intellij.java.debugger.impl.memory.tracking.TrackingType;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.persist.StoragePathMacros;
import consulo.disposer.Disposable;
import consulo.ide.ServiceManager;
import consulo.project.Project;
import consulo.proxy.EventDispatcher;
import consulo.util.xml.serializer.annotation.AbstractCollection;
import jakarta.inject.Singleton;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
@State(name = "InstancesTracker", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public class InstancesTracker implements PersistentStateComponent<InstancesTracker.MyState> {
  private final EventDispatcher<InstancesTrackerListener> myDispatcher = EventDispatcher.create(InstancesTrackerListener.class);
  private MyState myState = new MyState();

  public static InstancesTracker getInstance(@jakarta.annotation.Nonnull Project project) {
    return ServiceManager.getService(project, InstancesTracker.class);
  }

  public boolean isTracked(@jakarta.annotation.Nonnull String className) {
    return myState.classes.containsKey(className);
  }

  public boolean isBackgroundTrackingEnabled() {
    return myState.isBackgroundTrackingEnabled;
  }

  @Nullable
  public TrackingType getTrackingType(@jakarta.annotation.Nonnull String className) {
    return myState.classes.getOrDefault(className, null);
  }

  @Nonnull
  public Map<String, TrackingType> getTrackedClasses() {
    return new HashMap<>(myState.classes);
  }

  public void add(@jakarta.annotation.Nonnull String name, @Nonnull TrackingType type) {
    if (type.equals(myState.classes.getOrDefault(name, null))) {
      return;
    }

    myState.classes.put(name, type);
    myDispatcher.getMulticaster().classChanged(name, type);
  }

  public void remove(@jakarta.annotation.Nonnull String name) {
    TrackingType removed = myState.classes.remove(name);
    if (removed != null) {
      myDispatcher.getMulticaster().classRemoved(name);
    }
  }

  public void addTrackerListener(@jakarta.annotation.Nonnull InstancesTrackerListener listener) {
    myDispatcher.addListener(listener);
  }

  public void addTrackerListener(@jakarta.annotation.Nonnull InstancesTrackerListener listener, @jakarta.annotation.Nonnull Disposable parentDisposable) {
    myDispatcher.addListener(listener, parentDisposable);
  }

  public void removeTrackerListener(@jakarta.annotation.Nonnull InstancesTrackerListener listener) {
    myDispatcher.removeListener(listener);
  }

  public void setBackgroundTackingEnabled(boolean state) {
    boolean oldState = myState.isBackgroundTrackingEnabled;
    if (state != oldState) {
      myState.isBackgroundTrackingEnabled = state;
      myDispatcher.getMulticaster().backgroundTrackingValueChanged(state);
    }
  }

  @jakarta.annotation.Nullable
  @Override
  public MyState getState() {
    return new MyState(myState);
  }

  @Override
  public void loadState(MyState state) {
    myState = new MyState(state);
  }

  static class MyState {
    boolean isBackgroundTrackingEnabled = false;

    @AbstractCollection(surroundWithTag = false, elementTypes = {Map.Entry.class})
    final Map<String, TrackingType> classes = new ConcurrentHashMap<>();

    MyState() {
    }

    MyState(@jakarta.annotation.Nonnull MyState state) {
      isBackgroundTrackingEnabled = state.isBackgroundTrackingEnabled;
      for (Map.Entry<String, TrackingType> classState : state.classes.entrySet()) {
        classes.put(classState.getKey(), classState.getValue());
      }
    }
  }
}
