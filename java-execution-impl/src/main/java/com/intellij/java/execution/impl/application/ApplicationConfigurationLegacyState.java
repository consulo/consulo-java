/*
 * Copyright 2013-2026 consulo.io
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
package com.intellij.java.execution.impl.application;

import org.jspecify.annotations.Nullable;

/**
 * The {@code <option name value>} fields an application configuration wrote before the fragment model.
 *
 * @author VISTALL
 * @since 2026-10-24
 */
public class ApplicationConfigurationLegacyState {
    public @Nullable String MAIN_CLASS_NAME;
    public @Nullable String VM_PARAMETERS;
    public @Nullable String PROGRAM_PARAMETERS;
    public @Nullable String WORKING_DIRECTORY;
    public boolean ALTERNATIVE_JRE_PATH_ENABLED;
    public @Nullable String ALTERNATIVE_JRE_PATH;
    public boolean INCLUDE_PROVIDED_SCOPE;
    public boolean PASS_PARENT_ENVS = true;
}
