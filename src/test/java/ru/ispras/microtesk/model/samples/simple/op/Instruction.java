/*
 * Copyright (c) 2012 ISPRAS
 * 
 * Institute for System Programming of Russian Academy of Sciences
 * 
 * 25 Alexander Solzhenitsyn st. Moscow 109004 Russia
 * 
 * All rights reserved.
 * 
 * Instruction.java, Nov 20, 2012 1:35:45 PM Andrei Tatarnikov
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

package ru.ispras.microtesk.model.samples.simple.op;

import java.util.Map;

import ru.ispras.microtesk.model.api.instruction.IOperation;
import ru.ispras.microtesk.model.api.instruction.Operation;

/*
    op Instruction(x: arith_mem_inst)
    syntax  = x.syntax
    image   = x.image
    actions = x.action
*/

public class Instruction extends Operation
{
    public static final ParamDecls DECLS = new ParamDecls()
        .declareParam("x", Arith_Mem_Inst.INFO);

    public static final IFactory FACTORY = new IFactory()
    {
        @Override
        public IOperation create(Map<String, Object> args)
        {
            final IOperation x = (IOperation) getArgument("x", DECLS, args);
            return new Instruction(x);
        }
    };

    public static final IInfo INFO = new Info(
        Instruction.class, Instruction.class.getSimpleName(), FACTORY, DECLS);

    private final IOperation x;

    public Instruction(IOperation x)
    {
        assert Arith_Mem_Inst.INFO.isSupported(x);
        this.x = x;
    }

    @Override
    public String syntax()
    { 
        return x.syntax();
    }

    @Override
    public String image()
    { 
        return x.image();
    }

    @Override
    public void action() 
    { 
        x.action();
    }
}
