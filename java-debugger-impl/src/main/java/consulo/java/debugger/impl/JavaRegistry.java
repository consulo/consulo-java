/*
 * Copyright 2013-2017 consulo.io
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

package consulo.java.debugger.impl;

/**
 * @author VISTALL
 * @since 13-May-17
 */
public interface JavaRegistry {
  // Reduce watch return values overhead by applying extra filters
  boolean DEBUGGER_WATCH_RETURN_SPEEDUP = true;

  boolean DEBUGGER_CAPTURE_POINTS_ANNOTATIONS = false;

  boolean DEBUGGER_CAPTURE_POINTS = false;
}
