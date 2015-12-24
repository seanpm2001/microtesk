/*
 * Copyright 2012-2014 ISP RAS (http://www.ispras.ru)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package ru.ispras.microtesk.model.api.data;

import java.math.BigInteger;

import ru.ispras.fortress.data.types.Radix;

public interface IValueConverter {
  Data fromLong(Type type, long value);
  long toLong(Data data);

  Data fromInt(Type type, int value);
  int toInt(Data data);

  Data fromBigInteger(Type type, BigInteger value);

  Data fromString(Type type, String value, Radix radix);
}
