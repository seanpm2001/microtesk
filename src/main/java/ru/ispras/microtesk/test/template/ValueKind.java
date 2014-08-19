/*
 * Copyright (c) 2013 ISPRAS
 * 
 * Institute for System Programming of Russian Academy of Sciences
 * 
 * 25 Alexander Solzhenitsyn st. Moscow 109004 Russia
 * 
 * All rights reserved.
 * 
 * ValueKind.java, May 14, 2013 2:55:41 PM Andrei Tatarnikov
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package ru.ispras.microtesk.test.template;

/**
 * The ValueKind enumeration describes various uses of data values.
 * 
 * <p>N.B. At the moment, it is not used. However, it is supposed
 * to be used in test data generation logic. For the present, it is
 * left here to serve as a requirement.
 * 
 * @author Andrei Tatarnikov
 */

public enum ValueKind
{
    /** 
     * Concrete value that does not participate in test data
     * generation (default).
     */

    DEFAULT,

    /**
     * Unknown value to be generated by the test data generation engine.
     */

    FREE,

    /**
     * Used as input value for test data generation.
     */

    CLOSED
}
