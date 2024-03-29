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
package com.intellij.java.debugger.impl.memory.utils;

import java.util.Locale;

import consulo.internal.com.sun.jdi.VirtualMachine;
import jakarta.annotation.Nonnull;

public class AndroidUtil
{
	public static final int ANDROID_COUNT_BY_CLASSES_BATCH_SIZE = 500;
	public static final int ANDROID_INSTANCES_LIMIT = 30000;

	public static boolean isAndroidVM(@Nonnull VirtualMachine vm)
	{
		return vm.name().toLowerCase(Locale.ENGLISH).contains("dalvik");
	}
}
