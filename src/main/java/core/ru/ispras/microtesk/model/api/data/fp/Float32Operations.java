/*
 * Copyright 2015 ISP RAS (http://www.ispras.ru)
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

package ru.ispras.microtesk.model.api.data.fp;

import ru.ispras.fortress.data.types.bitvector.BitVector;
import ru.ispras.softfloat.JSoftFloat;

final class Float32Operations implements Operations {
  private static Operations instance = null;

  public static Operations get() {
    if (null == instance) {
      instance = new Float32Operations();
    }
    return instance;
  }

  private Float32Operations() {}

  @Override
  public FloatX add(final FloatX lhs, final FloatX rhs) {
    final float result = JSoftFloat.float32_add(lhs.floatValue(), rhs.floatValue());
    return newFloatX(result);
  }

  @Override
  public FloatX sub(final FloatX lhs, final FloatX rhs) {
    final float result = JSoftFloat.float32_sub(lhs.floatValue(), rhs.floatValue());
    return newFloatX(result);
  }

  @Override
  public FloatX mul(final FloatX lhs, final FloatX rhs) {
    final float result = JSoftFloat.float32_mul(lhs.floatValue(), rhs.floatValue());
    return newFloatX(result);
  }

  @Override
  public FloatX div(final FloatX lhs, final FloatX rhs) {
    final float result = JSoftFloat.float32_div(lhs.floatValue(), rhs.floatValue());
    return newFloatX(result);
  }

  @Override
  public FloatX rem(final FloatX lhs, final FloatX rhs) {
    final float result = JSoftFloat.float32_rem(lhs.floatValue(), rhs.floatValue());
    return newFloatX(result);
  }

  @Override
  public FloatX sqrt(final FloatX arg) {
    final float result = JSoftFloat.float32_sqrt(arg.floatValue());
    return newFloatX(result);
  }

  @Override
  public int compare(final FloatX first, final FloatX second) {
    final float value1 = first.floatValue();
    final float value2 = second.floatValue();

    if (JSoftFloat.float32_eq(value1, value2)){
      return 0;
    }

    return JSoftFloat.float32_lt(value1, value2) ? -1 : 1;
  }

  @Override
  public String toString(final FloatX arg) {
    return Float.toString(arg.floatValue());
  }

  @Override
  public String toHexString(final FloatX arg) {
    return Float.toHexString(arg.floatValue());
  }

  private static FloatX newFloatX(final float value) {
    return new FloatX(
        BitVector.valueOf(Float.floatToRawIntBits(value), Float.SIZE),
        Precision.FLOAT32
    );
  }
}
