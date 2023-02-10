/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
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

package software.amazon.jdbc.exceptions;

import software.amazon.jdbc.dialect.DatabaseDialect;

public class ExceptionManager {

  protected static ExceptionHandler customHandler;

  public static void setCustomHandler(final ExceptionHandler exceptionHandler) {
    customHandler = exceptionHandler;
  }

  public static void resetCustomHandler() {
    customHandler = null;
  }

  public boolean isLoginException(final DatabaseDialect databaseDialect, final Throwable throwable) {
    final ExceptionHandler handler = getHandler(databaseDialect);
    return handler.isLoginException(throwable);
  }

  public boolean isLoginException(final DatabaseDialect databaseDialect, final String sqlState) {
    final ExceptionHandler handler = getHandler(databaseDialect);
    return handler.isLoginException(sqlState);
  }

  public boolean isNetworkException(final DatabaseDialect databaseDialect, final Throwable throwable) {
    final ExceptionHandler handler = getHandler(databaseDialect);
    return handler.isNetworkException(throwable);
  }

  public boolean isNetworkException(final DatabaseDialect databaseDialect, final String sqlState) {
    final ExceptionHandler handler = getHandler(databaseDialect);
    return handler.isNetworkException(sqlState);
  }

  private ExceptionHandler getHandler(DatabaseDialect databaseDialect) {
    if (customHandler != null) {
      return customHandler;
    }
    return databaseDialect.getExceptionHandler();
  }

}
