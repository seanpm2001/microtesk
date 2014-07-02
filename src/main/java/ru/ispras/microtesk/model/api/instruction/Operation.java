/*
 * Copyright (c) 2012 ISPRAS
 * 
 * Institute for System Programming of Russian Academy of Sciences
 * 
 * 25 Alexander Solzhenitsyn st. Moscow 109004 Russia
 * 
 * All rights reserved.
 * 
 * Operation.java, Nov 27, 2012 4:19:03 PM Andrei Tatarnikov
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

package ru.ispras.microtesk.model.api.instruction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ru.ispras.microtesk.model.api.type.Type;
import ru.ispras.microtesk.model.api.metadata.MetaAddressingMode;
import ru.ispras.microtesk.model.api.metadata.MetaArgument;
import ru.ispras.microtesk.model.api.metadata.MetaOperation;

/**
 * The Operation abstract class is the base class for all classes that
 * simulate behavior specified by "op" Sim-nML statements. The class
 * provides definitions of classes to be used by its descendants
 * (generated classes that are to implement the IOperation interface). 
 * 
 * @author Andrei Tatarnikov
 */

public abstract class Operation implements IOperation
{
    /**
     * The ParamDecl class is aimed to specify declarations
     * operation parameters. 
     * 
     * @author Andrei Tatarnikov
     */

    public final static class ParamDecls
    {
        private final Collection<MetaArgument> metaData;

        public ParamDecls()
        {
            this.metaData = new ArrayList<MetaArgument>();
        }

        public ParamDecls declareParam(String name, Type type)
        {
            metaData.add(new MetaArgument(name, Collections.singletonList(AddressingModeImm.NAME)));
            return this;
        }

        public ParamDecls declareParam(String name, IAddressingMode.IInfo info)
        {
            final List<String> modeNames =
                new ArrayList<String>(info.getMetaData().size());

            for (MetaAddressingMode mode : info.getMetaData())
                modeNames.add(mode.getName());

            metaData.add(new MetaArgument(name, modeNames));
            return this;
        }

        public ParamDecls declareParam(String name, IOperation.IInfo info)
        {
            final List<String> opNames =
                new ArrayList<String>(info.getMetaData().size());

            for (MetaOperation op : info.getMetaData())
                opNames.add(op.getName());

            metaData.add(new MetaArgument(name, opNames));
            return this;
        }

        public Collection<MetaArgument> getMetaData()
        {
            return metaData;
        }
    }

    protected static Object getArgument(String name, ParamDecls decls, Map<String, Object> args)
    {
        final Object arg = args.get(name);
        // TODO Check argument
        return arg;
    }

    /**
     * The Info class is an implementation of the IInfo interface.
     * It is designed to store information about a single operation. 
     * The class is to be used by generated classes that implement
     * behavior of particular operations.
     * 
     * @author Andrei Tatarnikov
     */

    public static final class Info implements IInfo
    {
        private final Class<?>  opClass;
        private final String       name;
        private final IFactory  factory;
        private final ParamDecls  decls;
        private final Collection<MetaOperation> metaData;

        public Info(Class<?> opClass, String name, IFactory factory, ParamDecls decls)
        {
            this.opClass  = opClass;
            this.name     = name;
            this.factory  = factory;
            this.decls    = decls;
            this.metaData = Collections.singletonList(new MetaOperation(name, decls.getMetaData()));
        }

        @Override
        public String getName()
        {
            return name;
        }

        @Override
        public boolean isSupported(IOperation op)
        {
            return opClass.equals(op.getClass());
        }

        @Override
        public Collection<MetaOperation> getMetaData()
        {
            return metaData;
        }

        @Override
        public Map<String, IOperationBuilder> createBuilders()
        {
            final IOperationBuilder builder =
                new OperationBuilder(name, factory, decls);

            return Collections.singletonMap(name, builder);
        }        
    }

    /**
     * The InfoOrRule class is an implementation of the IInfo interface
     * that provides logic for storing information about a group of operations
     * united by an OR-rule. The class is to be used by generated classes
     * that specify a set of operations united by an OR rule.
     * 
     * @author Andrei Tatarnikov
     */

    public static final class InfoOrRule implements IInfo
    {
        private final String  name;
        private final IInfo[] childs;
        private final Collection<MetaOperation> metaData;

        public InfoOrRule(String name, IInfo ... childs)
        {
            this.name   = name;
            this.childs = childs;
            this.metaData = createMetaData(name, childs);
        }

        private static Collection<MetaOperation> createMetaData(String name, IInfo[] childs)
        {
            final List<MetaOperation> result = new ArrayList<MetaOperation>();  

            for (IInfo i : childs)
                result.addAll(i.getMetaData());

            return Collections.unmodifiableCollection(result);
        }

        @Override
        public String getName() 
        { 
            return name;
        }

        @Override
        public boolean isSupported(IOperation op)
        {
            for (IInfo i : childs)
                if (i.isSupported(op))
                    return true;

            return false;
        }

        @Override
        public Collection<MetaOperation> getMetaData()
        {
            return metaData;
        }

        @Override
        public Map<String, IOperationBuilder> createBuilders()
        {
            final Map<String, IOperationBuilder> result =
                new HashMap<String, IOperationBuilder>();

            for (IInfo i : childs)
                result.putAll(i.createBuilders());

            return Collections.unmodifiableMap(result);
        } 
    }

    /**
     * Default implementation of the syntax attribute. Provided to allow using 
     * addressing modes that have no explicitly specified syntax attribute. This
     * method does not do any useful work and should never be called. It is needed
     * only to let inherited classes compile.
     */

    @Override
    public String syntax()
    {
        // This code should never be called!
        assert false : "Default implementation. Should never be called!";
        return null;
    }

    /**
     * Default implementation of the image attribute. Provided to allow using 
     * addressing modes that have no explicitly specified image attribute. This
     * method does not do any useful work and should never be called. It is needed
     * only to let inherited classes compile.
     */

    @Override
    public String image()
    {
        // This code should never be called!
        assert false : "Default implementation. Should never be called!";
        return null;
    }

    /**
     * Default implementation of the action attribute. Provided to allow using 
     * addressing modes that have no explicitly specified action attribute. This
     * method does not do any useful work and should never be called. It is needed
     * only to let inherited classes compile.
     */

    public void action()
    {
        // This code should never be called!
        assert false : "Default implementation. Should never be called!";
    }
}
