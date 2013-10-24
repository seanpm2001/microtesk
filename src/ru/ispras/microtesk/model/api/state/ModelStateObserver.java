/*
 * Copyright (c) 2012 ISPRAS
 * 
 * Institute for System Programming of Russian Academy of Sciences
 * 
 * 25 Alexander Solzhenitsyn st. Moscow 109004 Russia
 * 
 * All rights reserved.
 * 
 * ModelStateObserver.java, Nov 8, 2012 2:03:46 PM Andrei Tatarnikov
 */

package ru.ispras.microtesk.model.api.state;

import java.util.HashMap;
import java.util.Map;

import ru.ispras.microtesk.model.api.exception.ConfigurationException;
import ru.ispras.microtesk.model.api.exception.config.UndeclaredException;
import ru.ispras.microtesk.model.api.memory.ILocationAccessor;
import ru.ispras.microtesk.model.api.memory.Label;
import ru.ispras.microtesk.model.api.memory.Memory;

public final class ModelStateObserver implements IModelStateObserver
{
    private final static String ALREADY_ADDED_ERR_FRMT =
        "The %s item has already been added to the table.";

    private final static String UNDEFINED_ERR_FRMT =
        "The %s resource is not defined in the current model.";

    private final static String BOUNDS_ERR_FRMT =
        "The %d index is invalid for the %s resource.";

    private final Map<String, Memory> memoryMap;
    private final Map<String, Label>   labelMap;

    private final Status controlTransfer;

    public ModelStateObserver(
        Memory[] registers,
        Memory[] memory,
        Label[] labels,
        Status[] statuses
        )
    {
        assert null != registers;
        assert null != memory;
        assert null != labels;
        assert null != statuses;

        memoryMap = new HashMap<String, Memory>();
        addToMemoryMap(memoryMap, registers);
        addToMemoryMap(memoryMap, memory);

        labelMap  = new HashMap<String, Label>();
        addToLabelMap(labelMap, labels);

        controlTransfer = findStatus(Status.CTRL_TRANSFER.getName(), statuses);
    }

    private static void addToMemoryMap(Map<String, Memory> map, Memory[] items)
    {
        for(Memory m : items)
        {
            final Memory prev = map.put(m.getName(), m);
            assert null == prev : String.format(ALREADY_ADDED_ERR_FRMT, m.getName());
        }
    }

    private static void addToLabelMap(Map<String, Label> map, Label[] items)
    {
        for(Label l : items)
        {
            final Label prev = map.put(l.getName(), l);
            assert null == prev : String.format(ALREADY_ADDED_ERR_FRMT, l.getName());
        }
    }

    private static Status findStatus(String name, Status[] statuses)
    {
        for (Status status : statuses)
        {
            if (name.equals(status.getName()))
                return status;
        }

        assert false : String.format("The %s status is not defined in the model.", name);
        return null;
    }

    @Override
    public ILocationAccessor accessLocation(String name) throws ConfigurationException
    {
        return accessLocation(name, 0);
    }

    @Override
    public ILocationAccessor accessLocation(String name, int index) throws ConfigurationException
    {
        if (labelMap.containsKey(name))
        {
            if (0 != index)
                throw new UndeclaredException(String.format(BOUNDS_ERR_FRMT, index, name));

            return labelMap.get(name).access().externalAccess();
        }

        if (!memoryMap.containsKey(name))
            throw new UndeclaredException(String.format(UNDEFINED_ERR_FRMT, name));

        final Memory current = memoryMap.get(name);

        if ((index < 0) || (index >= current.getLength()))
            throw new UndeclaredException(String.format(BOUNDS_ERR_FRMT, index, name));

        return current.access(index).externalAccess(); 
    }

    @Override
    public int getControlTransferStatus()
    {
        return controlTransfer.get();
    }
}
