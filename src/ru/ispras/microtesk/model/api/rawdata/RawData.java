/*
 * Copyright (c) 2012 ISPRAS
 * 
 * Institute for System Programming of Russian Academy of Sciences
 * 
 * 25 Alexander Solzhenitsyn st. Moscow 109004 Russia
 * 
 * All rights reserved.
 * 
 * RawData.java, Oct 11, 2012 12:45:55 PM Andrei Tatarnikov
 */

package ru.ispras.microtesk.model.api.rawdata;

import static ru.ispras.microtesk.model.api.rawdata.RawDataAlgorithm.*;

/**
 * The RawData class provides an interface for working with "raw" binary data of
 * arbitrary size. It provides basic methods for accessing and modifying the stored data. 
 * 
 * @author Andrei Tatarnikov
 */

public abstract class RawData implements Comparable<RawData>
{
    /**
     * Number of bits an a byte.
     */

    public final static int BITS_IN_BYTE  = 8;

    /**
     * Returns the number of bits in the stored data array.
     * 
     * @return Number of bits.
     */

    public abstract int getBitSize();

    /**
     * Returns the number of bytes in the data array (full number including incomplete bytes).
     *
     * @return Number of bytes (including incomplete bytes). 
     */

    public abstract int getByteSize();

    /**
     * Returns the binary value stored in the specified byte. For incomplete bytes, the 
     * return value contains only the bits inside the bounds limited by bit size.  
     * 
     * @param index Index of the target byte.
     * @return Binary value stored in the specified byte.
     */

    public abstract byte getByte(int index);

    /**
     * Sets the value of the specified byte in the data array. For incomplete bytes,
     * bits that fall beyond the bound limited by the bits size are ignored. In other words,
     * bits in the target byte that lie beyond the bound retain their values. This requirement
     * is crucial to guarantee correct work of mappings.    
     * 
     * @param index Index of the target byte.
     * @param value Binary value to be stored in the specified byte.
     */

    public abstract void setByte(int index, byte value);

    /**
     * Resets (set to zero) all bytes in the stored data array.
     */

    public final void reset()
    {  
        fill(this, (byte) 0);
    }

    /**
     * Copies data from the specified raw data source to the data array of the current object.
     * If the source data array is shorter than the current one, the rest high bytes are filled 
     * with zeros. If the source array is longer, the result is truncated. 
     * 
     * @param src Source raw data array.
     */

    public final void assign(RawData src)
    {
        assert null != src;
        copy(src, this);
    }

    /***
     * Checks the equality of two raw data object. Objects are considered equal if
     * their sizes match and the refer to equal data (comparison is performed 
     * byte by byte from the high end to the low end).
     * 
     * @param obj A raw data object to be compared with the current object.
     */

    @Override
    public final boolean equals(Object obj)
    {
        if (this == obj) return true;
        if (null == obj) return false;

        if (!(obj instanceof RawData))
            return false;

        final RawData other = (RawData) obj;
        if (getBitSize() != other.getBitSize()) return false;

        return (-1 == mismatch_reverse(this, other));
    }

    /**
     * Compares the current raw data array with the specified data array. Data is
     * compared starting with the highest byte in the array.
     * 
     * @param other A raw data object.
     * @return 0 if data in both object equals, -1 if the data in the current object
     * is less and 1 of it is greater.
     */

    @Override
    public final int compareTo(RawData other)
    {
        assert null != other;
        
        if (this == other)
            return 0;

        final int index = mismatch_reverse(this, other);

        // Objects are equal (no mismatch was found)
        if (-1 == index) 
            return 0;

        // This object is less (the value of the highest mismatch byte is less)
        if (getByte(index) < other.getByte(index))
            return -1;

        // This object is greater.
        return 1;        
    }

    /**
     * Converts the stored "raw" binary data to textual representation.
     *
     * @return Binary string.
     */

    public final String toBinString()
    {
        final StringBuilder sb = new StringBuilder(getBitSize());

        final IAction op = new IAction()
        {
            private int totalBitCount = getBitSize();

            @Override
            public void run(byte v)
            {
                final int highBits = totalBitCount % BITS_IN_BYTE;
                final int bitCount = (highBits == 0) ? BITS_IN_BYTE : highBits;

                for (int mask = 0x1 << (bitCount-1); 0 != mask; mask >>>= 1)
                {
                    sb.append((v & mask) == 0 ? '0' : '1');
                }

                totalBitCount -= bitCount;
            }
        };

        for_each_reverse(this, op);
        return sb.toString();
    }

    /**
     * Returns a copy of stored data packed into an array of bytes.
     *  
     * @return Array of bytes.
     */

    public final byte[] toByteArray()
    {
        final byte[] byteArray = new byte[this.getByteSize()];

        final IAction op = new IAction()
        {
            private int index = 0;

            @Override
            public void run(byte v)
            {
                byteArray[index++] = v;                
            }
        };

        for_each_reverse(this, op);        
        return byteArray;
    }

    /**
     * Creates an instance of a raw data object based on textual representation
     * of binary data. The data size (number of bits) matches the string length.  
     * 
     * @param bs Textual representation of binary data.
     * @return Raw data object.
     */    

    public static RawData valueOf(final String bs)
    {
        return valueOf(bs, bs.length());
    }

    /**
     * Creates an instance of a raw data object based on textual representation
     * of binary data. The data size is specified by a method parameter. If the 
     * length of the string exceeds the data size, data is truncated (characters 
     * signifying higher bits are ignored). If the string length is less than the
     * data size, the higher bits of the data are filled with zeros.  
     * 
     * @param bs Textual representation of binary data.
     * @param bitSize Size of the resulting data array (in bits). 
     * @return Raw data object.
     */

    public static RawData valueOf(final String bs, final int bitSize)
    {
        if (null == bs)
            throw new NumberFormatException("null");

        final RawData result = new RawDataStore(bitSize);

        final RawDataAlgorithm.IOperation op = new RawDataAlgorithm.IOperation()
        {
            private int bitsRead = 0;

            @Override
            public byte run()
            {
                byte v = 0;

                do
                {
                    final int bitIndex = bs.length() - bitsRead - 1;

                    if (bitIndex >= 0)
                    {
                        final char c = bs.charAt(bitIndex);

                        if (('0' != c) && ('1' != c))
                            throw new NumberFormatException(bs);

                        v |= (byte)(('0' == c ? 0x0 : 0x1) << (bitsRead % BITS_IN_BYTE));
                    }

                    ++bitsRead;
                }
                while (bitsRead != bitSize && 0 != (bitsRead % BITS_IN_BYTE));

                return v;
            }
        };

        RawDataAlgorithm.generate(result, op);
        return result;        
    }

    /**
     * Creates an instance of a raw data object based on a long value. The data size is
     * specified by a method parameter. If the raw data size is less that the long value
     * size (64 bits), the value is truncated (high bits of the value are ignored). If
     * the raw data size exceeds the long value size, high bits of the raw data store
     * are filled with zeros.
     * 
     * @param value Long value to be converted to a bit array.
     * @param bitSize Size of the resulting data array (in bits).
     * @return Raw data object.
     */

    public static RawData valueOf(final long value, final int bitSize)
    {
        final IOperation op = new IOperation()
        {
            private long v = value; 

            @Override
            public byte run()
            {
                if (0 == v) return 0;

                final byte result = (byte)(v & 0xFFL);
                v >>>= RawData.BITS_IN_BYTE;

                return result;
            }
        };

        final RawDataStore result = new RawDataStore(bitSize);
        generate(result, op);

        return result;
    }

    /**
     * Creates an instance of a raw data object from a byte array.
     * TODO: Needs a detailed description. There might be pitfalls. 
     * 
     * @param data An array of bytes.
     * @param bitSize Size of the resulting data array (in bits).
     * @return Raw data object.
     */

    public static RawData valueOf(final byte[] data, final int bitSize)
    {
        final IOperation op = new IOperation()
        {
            private int index = data.length - 1; 

            @Override
            public byte run()
            {
                if (index < 0) return 0;
                return data[index--];
            }
        };

        final RawDataStore result = new RawDataStore(bitSize);
        generate(result, op);

        return result;
    }

    /**
     * Creates an instance of a raw data object based on a integer value. The data size is
     * specified by a method parameter. If the raw data size is less that the integer value
     * size (32 bits), the value is truncated (high bits of the value are ignored). If
     * the raw data size exceeds the integer value size, high bits of the raw data store
     * are filled with zeros.
     *  
     * @param value Integer value to be converted to a bit array.
     * @param bitSize Size of the resulting data array (in bits).
     * @return Raw data object.
     */

    public static RawData valueOf(final int value, final int bitSize)
    {
        return valueOf(((long)value) & 0xFFFFFFFFL, bitSize);
    }

    /**
     * Converts the stored data to an integer value. If the stored data size
     * exceeds integer size (32 bits), the data is truncated (high bits are cut off).     
     * 
     * @return Integer representation of the stored value.
     * 
     * TODO: Unit tests for this method are needed.
     */

    public final int intValue()
    {
        assert getBitSize() <= Integer.SIZE;

        class Result { public int value = 0; }
        final Result result = new Result();

        final IAction op = new IAction()
        {
            private int bitCount  = 0;

            @Override
            public void run(byte v)
            {
                if (bitCount >= Integer.SIZE)
                {
                    assert false : "Data will be truncated";
                    return; 
                }

                result.value |= (v & 0xFF) << bitCount;
                bitCount     += RawData.BITS_IN_BYTE;
            }
        };       

        for_each(this, op);
        return result.value;
    }

    /**
     * Converts the stored data to an long value. If the stored data size
     * exceeds long size (64 bits), the data is truncated (high bits are cut off).     
     * 
     * @return Long representation of the stored value.
     * 
     * TODO: Unit tests for this method are needed.
     */

    public final long longValue()
    {
        assert getBitSize() <= Long.SIZE;

        class Result { public long value = 0; }
        final Result result = new Result();

        final IAction op = new IAction()
        {
            private int bitCount  = 0;

            @Override
            public void run(byte v)
            {
                if (bitCount >= Long.SIZE)
                {                    
                    assert false : "Data will be truncated";
                    return;
                }

                result.value |= (v & 0xFF) << bitCount;
                bitCount     += RawData.BITS_IN_BYTE;
            }
        };

        for_each(this, op);
        return result.value;
    }

    /**
     * Returns the mask for the specified byte in the byte array. The mask helps determine what
     * bits in the specified byte contain meaningful information and which bits should be ignored.  
     * 
     * @param index Index of the target byte.
     * @return Bit mask for the current byte.
     */

    private byte highByteMask = 0;

    protected final byte getByteBitMask(int index)
    {
        assert (index >= 0) && (index < getByteSize());

        final boolean isHighByte = index == getByteSize() - 1;

        if (!isHighByte)
            return (byte) 0xFF;

        if (0 == highByteMask)
        {
            final int incompleteBitsInHighByte = getBitSize() % BITS_IN_BYTE;
            highByteMask = (0 == incompleteBitsInHighByte) ?
                (byte) 0xFF : (byte) (0xFF >>> (BITS_IN_BYTE - incompleteBitsInHighByte));
        }

        return highByteMask;
    }
}
