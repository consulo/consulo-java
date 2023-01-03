/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

class MethodAnnotations
{
	// @NotNull keys
	final Set<EKey> notNulls = new HashSet<>(1);
	// @Nullable keys
	final Set<EKey> nullables = new HashSet<>(1);
	// @Contract(pure=true) part of contract
	final Set<EKey> pures = new HashSet<>(1);
	// @Contracts
	final Map<EKey, String> contractsValues = new HashMap<>();
	DataValue returnValue = DataValue.UnknownDataValue1;
}
