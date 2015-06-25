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

package ru.ispras.microtesk.test.template;

import java.util.HashMap;
import java.util.Map;

import ru.ispras.fortress.util.InvariantChecks;

public final class StreamStore {
  private StreamPreparator preparator;
  private Map<String, Stream> streams;

  public StreamStore() {
    this.preparator = null;
    this.streams = new HashMap<>();
  }

  public StreamPreparator getPreparator() {
    return preparator;
  }

  public void setPreparator(final StreamPreparator preparator) {
    InvariantChecks.checkNotNull(preparator);
    this.preparator = preparator;
  }

  public Stream getStream(final String startLabelName) {
    return streams.get(startLabelName);
  }

  public void addStream(final Stream stream) {
    InvariantChecks.checkNotNull(stream);
    streams.put(stream.getStartLabelName(), stream);
  }

  public Stream addStream(
      final String startLabelName,
      final Primitive dataSource,
      final Primitive indexSource,
      final int length) {

    if (null == preparator) {
      throw new IllegalStateException("No stream preparator is defined.");
    }

    final Stream stream = preparator.newStream(
        startLabelName, dataSource, indexSource, length);

    addStream(stream);
    return stream;
  }
}
