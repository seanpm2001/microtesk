/*
 * Copyright (c) 2013 ISPRAS
 * 
 * Institute for System Programming of Russian Academy of Sciences
 * 
 * 25 Alexander Solzhenitsyn st. Moscow 109004 Russia
 * 
 * All rights reserved.
 * 
 * TestEngine.java, May 8, 2013 11:00:02 AM Andrei Tatarnikov
 */

package ru.ispras.microtesk.test;

import ru.ispras.microtesk.model.api.IModel;
import ru.ispras.microtesk.test.block.BlockBuilderFactory;
import ru.ispras.microtesk.test.data.DataGenerator;

public final class TestEngine
{
    private final BlockBuilderFactory blockBuilderFactory;
    private final DataGenerator dataGenerator;

    public static TestEngine getInstance(IModel model)
    {
        return new TestEngine(model);
    }

    private TestEngine(IModel model)
    {
        this.blockBuilderFactory = new BlockBuilderFactory();
        this.dataGenerator = new DataGenerator(model);
    }

    public BlockBuilderFactory getBlockBuilders()
    {
        return blockBuilderFactory;
    }

    public DataGenerator getDataGenerator()
    {
        return dataGenerator;
    }
}
