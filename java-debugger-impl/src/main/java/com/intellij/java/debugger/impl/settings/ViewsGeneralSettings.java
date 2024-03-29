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
package com.intellij.java.debugger.impl.settings;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.persist.StoragePathMacros;
import consulo.ide.ServiceManager;
import consulo.util.xml.serializer.DefaultJDOMExternalizer;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.WriteExternalException;
import jakarta.inject.Singleton;
import org.jdom.Element;

@Singleton
@State(
  name = "ViewsSettings",
  storages = {
    @Storage(
      file = StoragePathMacros.APP_CONFIG + "/debugger.frameview.xml"
    )}
)
@ServiceAPI(ComponentScope.APPLICATION)
@ServiceImpl
public class ViewsGeneralSettings implements PersistentStateComponent<Element> {
  public boolean SHOW_OBJECTID = true;
  public boolean HIDE_NULL_ARRAY_ELEMENTS = true;
  public boolean AUTOSCROLL_TO_NEW_LOCALS = true;

  public static ViewsGeneralSettings getInstance() {
    return ServiceManager.getService(ViewsGeneralSettings.class);
  }

  public void loadState(Element element) {
    try {
      DefaultJDOMExternalizer.readExternal(this, element);
    }
    catch (InvalidDataException e) {
      // ignore
    }
  }

  public Element getState() {
    Element element = new Element("ViewsGeneralSettings");
    try {
      DefaultJDOMExternalizer.writeExternal(this, element);
    }
    catch (WriteExternalException e) {
      // ignore
    }
    return element;
  }

  public boolean equals(Object object) {
    if (!(object instanceof ViewsGeneralSettings)) return false;
    ViewsGeneralSettings generalSettings = ((ViewsGeneralSettings)object);
    return SHOW_OBJECTID == generalSettings.SHOW_OBJECTID &&
      HIDE_NULL_ARRAY_ELEMENTS == generalSettings.HIDE_NULL_ARRAY_ELEMENTS &&
      AUTOSCROLL_TO_NEW_LOCALS == generalSettings.AUTOSCROLL_TO_NEW_LOCALS;
  }

}
