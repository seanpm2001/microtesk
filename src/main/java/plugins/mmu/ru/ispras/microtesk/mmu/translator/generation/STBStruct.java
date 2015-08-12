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

package ru.ispras.microtesk.mmu.translator.generation;

import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.STGroup;

import ru.ispras.fortress.util.InvariantChecks;

import ru.ispras.microtesk.mmu.translator.ir.Address;
import ru.ispras.microtesk.mmu.translator.ir.Type;
import ru.ispras.microtesk.translator.generation.STBuilder;

import java.util.Map;

final class STBStruct implements STBuilder {
  public static final Class<?> BIT_VECTOR_CLASS =
      ru.ispras.fortress.data.types.bitvector.BitVector.class;

  public static final Class<?> ADDRESS_CLASS =
      ru.ispras.microtesk.mmu.model.api.Address.class;

  private final String packageName; 
  private final boolean isAddress;
  private final Type type;
  private final String valueFieldName;
  private boolean isBitVectorImported;

  public STBStruct(final String packageName, final Address address) {
    InvariantChecks.checkNotNull(packageName);
    InvariantChecks.checkNotNull(address);

    this.packageName = packageName;
    this.isAddress = true;
    this.type = address.getContentType();

    final StringBuilder sb = new StringBuilder();
    for(final String name : address.getAccessChain()) {
      if (sb.length() > 0) sb.append('.');
      sb.append(name);
    }

    this.valueFieldName = sb.toString();
  }

  public STBStruct(final String packageName, final Type type) {
    InvariantChecks.checkNotNull(packageName);
    InvariantChecks.checkNotNull(type);

    this.packageName = packageName;
    this.isAddress = false;
    this.type = type;
    this.valueFieldName = null;
  }

  @Override
  public ST build(final STGroup group) {
    final ST st = group.getInstanceOf("source_file");
    isBitVectorImported = false;

    buildHeader(st);
    buildFields(st, group);
    buildGetValue(st, group);

    return st;
  }

  private void buildHeader(final ST st) {
    st.add("name", type.getId());
    st.add("pack", packageName);

    if (isAddress) {
      st.add("ext", ADDRESS_CLASS.getSimpleName());
      st.add("imps", ADDRESS_CLASS.getName());
    }
  }

  private void buildFields(final ST st, final STGroup group) {
    final ST stConstructor = group.getInstanceOf("struct_constructor");
    stConstructor.add("name", type.getId());

    for (final Map.Entry<String, Type>  field : type.getFields().entrySet()) {
      final String fieldName = field.getKey();
      final Type fieldType = field.getValue();

      final String fieldTypeName;
      final String fieldValue;

      if (fieldType.getId() != null) {
        fieldTypeName = fieldType.getId();
        fieldValue = String.format("new %s()", fieldTypeName);
      } else {
        importBitVector(st);
        fieldTypeName = BIT_VECTOR_CLASS.getSimpleName();
        fieldValue = fieldType.getDefaultValue() != null ?
            ExprPrinter.bitVectorToString(fieldType.getDefaultValue()) :
            String.format("%s.newEmpty(%d)", fieldTypeName, fieldType.getBitSize());
      }

      final ST stField = group.getInstanceOf("struct_field");
      stField.add("name", fieldName);
      stField.add("type", fieldTypeName);
      st.add("members", stField);

      final ST stFieldInit = group.getInstanceOf("struct_field_init");
      stFieldInit.add("name", fieldName);
      stFieldInit.add("value", fieldValue);
      stConstructor.add("fields", stFieldInit);
    }

    st.add("members", "");
    st.add("members", stConstructor);
  }

  private void buildGetValue(final ST st, final STGroup group) {
    if (!isAddress) {
      return;
    }

    importBitVector(st);

    final ST stAddress = group.getInstanceOf("struct_get_value");
    stAddress.add("field_name", valueFieldName);

    st.add("members", "");
    st.add("members", stAddress);
  }

  private void importBitVector(final ST st) {
    if (!isBitVectorImported) {
      st.add("imps", BIT_VECTOR_CLASS.getName());
      isBitVectorImported = true;
    }
  }
}
