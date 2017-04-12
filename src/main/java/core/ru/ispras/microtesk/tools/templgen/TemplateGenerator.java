/*
 * Copyright 2017 ISP RAS (http://www.ispras.ru)
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

package ru.ispras.microtesk.tools.templgen;

import ru.ispras.fortress.util.InvariantChecks;
import ru.ispras.microtesk.Logger;
import ru.ispras.microtesk.SysUtils;
import ru.ispras.microtesk.model.api.Model;
import ru.ispras.microtesk.model.api.metadata.MetaModel;
import ru.ispras.microtesk.options.Options;

public final class TemplateGenerator {
  public static boolean generate(final Options options, final String modelName) {
    InvariantChecks.checkNotNull(options);
    InvariantChecks.checkNotNull(modelName);

    final Model model = loadModel(modelName);
    if (null == model) {
      return false;
    }

    final MetaModel metaModel = model.getMetaData();
    // TODO

    Logger.error("Templates generation is not currently supported.");
    return false;
  }

  private static Model loadModel(final String modelName) {
    try {
      return SysUtils.loadModel(modelName);
    } catch (final Exception e) {
      Logger.error("Failed to load the %s model. Reason: %s.", modelName, e.getMessage());
      return null;
    }
  }
}
