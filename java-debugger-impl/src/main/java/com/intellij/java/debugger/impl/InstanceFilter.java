/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.java.debugger.impl;

import consulo.util.xml.serializer.DefaultJDOMExternalizer;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.JDOMExternalizable;
import consulo.util.xml.serializer.WriteExternalException;
import com.intellij.java.debugger.ui.classFilter.ClassFilter;
import org.jdom.Element;

/**
 * User: lex
 * Date: Aug 29, 2003
 * Time: 2:49:27 PM
 */
public class InstanceFilter implements JDOMExternalizable{
  public static final InstanceFilter[] EMPTY_ARRAY = new InstanceFilter[0];
  
  public long    ID      = 0;
  public boolean ENABLED = true;

  protected InstanceFilter(long ID, boolean ENABLED) {
    this.ID = ID;
    this.ENABLED = ENABLED;
  }

  public long getId() {
    return ID;
  }

  public boolean isEnabled() {
    return ENABLED;
  }

  public void setId(long id) {
    ID = id;
  }

  public void setEnabled(boolean enabled) {
    ENABLED = enabled;
  }

  public static InstanceFilter create(String pattern) {
    return new InstanceFilter(Long.parseLong(pattern), true);
  }

  public static InstanceFilter create(final ClassFilter filter) {
    return new InstanceFilter(Long.parseLong(filter.getPattern()), filter.isEnabled());
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public static ClassFilter[] createClassFilters(InstanceFilter[] filters) {
    ClassFilter [] cFilters = new ClassFilter[filters.length];
    for (int i = 0; i < cFilters.length; i++) {
      InstanceFilter instanceFilter = filters[i];

      ClassFilter classFilter = new ClassFilter();
      classFilter.setEnabled(instanceFilter.isEnabled());
      classFilter.setPattern(Long.toString(instanceFilter.getId()));

      cFilters[i] = classFilter;
    }
    return cFilters;
  }
}
