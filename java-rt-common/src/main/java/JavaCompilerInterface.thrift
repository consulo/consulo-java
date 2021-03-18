/*
 * Copyright 2013-2020 consulo.io
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

namespace * consulo.java.rt.common.compiler

service JavaCompilerInterface
{
  void logInfo(1:string message, 2:string fileUri, 3:i64 lineNumber, 4:i64 columnNumber);
  
  void logError(1:string message, 2:string fileUri, 3:i64 lineNumber, 4:i64 columnNumber);

  void logWarning(1:string message, 2:string fileUri, 3:i64 lineNumber, 4:i64 columnNumber);

  void fileWrote(1:string filePath);
}