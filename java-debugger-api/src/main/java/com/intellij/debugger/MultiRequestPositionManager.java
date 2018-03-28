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
package com.intellij.debugger;

import java.util.List;

import javax.annotation.Nonnull;
import com.intellij.debugger.requests.ClassPrepareRequestor;
import consulo.internal.com.sun.jdi.request.ClassPrepareRequest;

public interface MultiRequestPositionManager extends PositionManager
{

	/**
	 * @see PositionManager#createPrepareRequest
	 */
	@Nonnull
	List<ClassPrepareRequest> createPrepareRequests(@Nonnull ClassPrepareRequestor requestor, @Nonnull SourcePosition position) throws NoDataException;
}
