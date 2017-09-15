package com.bringholm.nbtutil.v1_1;

import com.bringholm.reflectutil.v1_1.ReflectUtil;
import com.google.common.collect.Maps;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * A class to extract and provide wrappers for various NBT data.
 *
 * The code, relying heavily on NMS, uses reflection, which
 * should make it somewhat version-independent. Wrappers are also
 * provided for all types of NBT so users don't have to deal with
 * any NMS.
 *
 * NOTE: This requires ReflectUtil.java
 *
 * @author AlvinB
 */
@SuppressWarnings({"SimplifiableIfStatement", "WeakerAccess", "unused", "SameParameterValue"})
public class NBTUtil {

    private static final Class<?> TAG_COMPOUND_CLASS = ReflectUtil.getNMSClass("NBTTagCompound").getOrThrow();

    private static final Class<?> ITEM_STACK_CLASS = ReflectUtil.getNMSClass("ItemStack").getOrThrow();
    private static final Method AS_NMS_COPY = ReflectUtil.getMethod(ReflectUtil.getCBClass("inventory.CraftItemStack").getOrThrow(), "asNMSCopy", ItemStack.class).getOrThrow();
    private static final Field ITEM_STACK_TAG = ReflectUtil.getDeclaredFieldByType(ITEM_STACK_CLASS, TAG_COMPOUND_CLASS, 0, true).getOrThrow();
    private static final Method AS_BUKKIT_COPY = ReflectUtil.getMethod(ReflectUtil.getCBClass("inventory.CraftItemStack").getOrThrow(), "asBukkitCopy", ReflectUtil.getNMSClass("ItemStack").getOrThrow()).getOrThrow();
    private static final Method ITEM_STACK_SAVE_TO_NBT = ReflectUtil.getMethodByTypeAndParams(ITEM_STACK_CLASS, TAG_COMPOUND_CLASS, 0, TAG_COMPOUND_CLASS).getOrThrow();

    private static final Method ENTITY_GET_HANDLE = ReflectUtil.getMethod(ReflectUtil.getCBClass("entity.CraftEntity").getOrThrow(), "getHandle").getOrThrow();
    private static final Method ENTITY_SAVE_TO_NBT = ReflectUtil.getMethodByTypeAndParams(ReflectUtil.getNMSClass("Entity").getOrThrow(), TAG_COMPOUND_CLASS, 0, TAG_COMPOUND_CLASS).getOrThrow();
    private static final Method ENTITY_LOAD_FROM_NBT = ReflectUtil.getMethodByPredicate(ReflectUtil.getNMSClass("Entity").getOrThrow(), new ReflectUtil.MethodPredicate()
            .withParams(TAG_COMPOUND_CLASS).withoutModifiers(Modifier.ABSTRACT).withReturnType(void.class), 0).getOrThrow();

    private static final Class<?> TILE_ENTITY_CLASS = ReflectUtil.getNMSClass("TileEntity").getOrThrow();
    private static final Method GET_TILE_ENTITY;
    private static final Method TILE_ENTITY_SAVE_TO_NBT = ReflectUtil.getMethodByTypeAndParams(TILE_ENTITY_CLASS, TAG_COMPOUND_CLASS, 0, TAG_COMPOUND_CLASS).getOrThrow();
    private static final Method TILE_ENTITY_LOAD_FROM_NBT = ReflectUtil.getMethodByTypeAndParams(TILE_ENTITY_CLASS, void.class, 0, TAG_COMPOUND_CLASS).getOrThrow();
    private static final Class<?> CRAFT_BLOCK_ENTITY_STATE_CLASS;

    static {
        if (ReflectUtil.isVersionHigherThan(1, 12)) {
            CRAFT_BLOCK_ENTITY_STATE_CLASS = ReflectUtil.getCBClass("block.CraftBlockEntityState").getOrThrow();
            GET_TILE_ENTITY = ReflectUtil.getDeclaredMethodByPredicate(CRAFT_BLOCK_ENTITY_STATE_CLASS, new ReflectUtil.MethodPredicate()
                    .withReturnType(TILE_ENTITY_CLASS).withName("getTileEntity"), 0, true).getOrThrow();
        } else {
            CRAFT_BLOCK_ENTITY_STATE_CLASS = null;
            GET_TILE_ENTITY = ReflectUtil.getMethodByType(ReflectUtil.getCBClass("block.CraftBlockState").getOrThrow(), TILE_ENTITY_CLASS, 0).getOrThrow();
        }
    }

    /**
     * Loads a compressed NBTTagCompound from the specified stream.
     *
     * The stream must start with a compound, and it should be compressed
     * using GZIP compression.
     *
     * NOTE: If an exception occurs doing the reading of the NBT data, it will be
     * rethrown as a RuntimeException.
     *
     * @param inputStream the uncompressed InputStream
     * @return the read NBTTagCompound
     */
    public static NBTTagCompound readCompressedNBTFromStream(InputStream inputStream) {
        try {
            return readUncompressedNBTFromStream(new GZIPInputStream(inputStream));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Loads an NBTTagCompound from the specified stream.
     *
     * The stream must start with a compound, and it should be entirely
     * uncompressed. If the data is compressed, use readCompressedNBTFromStream().
     *
     * NOTE: If an exception occurs doing the reading of the NBT data, it will be
     * rethrown as a RuntimeException.
     *
     * @param inputStream the InputStream
     * @return the read NBTTagCompound
     */
    public static NBTTagCompound readUncompressedNBTFromStream(InputStream inputStream) {
        DataInputStream dataInputStream = (inputStream instanceof DataInputStream ? (DataInputStream) inputStream : new DataInputStream(inputStream));
        return (NBTTagCompound) readTag(dataInputStream, null);
    }

    private static NBTTagBase readTag(DataInputStream inputStream, NBTTagCompound compound) {
        try {
            int tagId = inputStream.readUnsignedByte();
            String name = null;
            // End Tags don't have a name
            if (tagId != 0) {
                name = inputStream.readUTF();
            }
            return readTagValue(inputStream, tagId, name, compound);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static NBTTagBase readTagValue(DataInputStream inputStream, int tagId, String name, NBTTagCompound compound) {
        try {
            NBTTagBase tag;
            switch (tagId) {
                case 0:
                    return new NBTTagEnd();
                case 1:
                    tag = new NBTTagByte(inputStream.readByte());
                    break;
                case 2:
                    tag = new NBTTagShort(inputStream.readShort());
                    break;
                case 3:
                    tag = new NBTTagInt(inputStream.readInt());
                    break;
                case 4:
                    tag = new NBTTagLong(inputStream.readLong());
                    break;
                case 5:
                    tag = new NBTTagFloat(inputStream.readFloat());
                    break;
                case 6:
                    tag = new NBTTagDouble(inputStream.readDouble());
                    break;
                case 7:
                    int length = inputStream.readInt();
                    byte[] value = new byte[length];
                    inputStream.readFully(value);
                    tag = new NBTTagByteArray(value);
                    break;
                case 8:
                    tag = new NBTTagString(inputStream.readUTF());
                    break;
                case 9:
                    int type = inputStream.readUnsignedByte();
                    int listLength = inputStream.readInt();
                    NBTTagList<NBTTagBase> listTag = new NBTTagList<>();
                    for (int i = 0; i < listLength; i++) {
                        listTag.add(readTagValue(inputStream, type, null, null));
                    }
                    tag = listTag;
                    break;
                case 10:
                    NBTTagCompound compoundTag = new NBTTagCompound();
                    NBTTagBase childTag = readTag(inputStream, compoundTag);
                    while (childTag != null && childTag.getId() != 0) {
                        childTag = readTag(inputStream, compoundTag);
                    }
                    tag = compoundTag;
                    break;
                case 11:
                    int arrayLength = inputStream.readInt();
                    int[] array = new int[arrayLength];
                    for (int i = 0; i < arrayLength; i++) {
                        array[i] = inputStream.readInt();
                    }
                    tag = new NBTTagIntArray(array);
                    break;
                default:
                    throw new RuntimeException("NBT tag id " + tagId + " is unknown!");
            }
            if (compound != null) {
                compound.set(name, tag);
            }
            return tag;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Writes a compressed NBTTagCompound to the specified stream.
     *
     * NOTE: If an exception occurs doing the writing of the NBT data, it will be
     * rethrown as a RuntimeException.
     *
     * @param outputStream the uncompressed OutputStream
     */
    public static void writeCompressedNBTToStream(OutputStream outputStream, NBTTagCompound compound) {
        try {
            GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream);
            writeUncompressedNBTToStream(gzipOutputStream, compound);
            gzipOutputStream.finish();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Writes an NBTTagCompound to the specified stream.
     *
     * NOTE: If an exception occurs doing the reading of the NBT data, it will be
     * rethrown as a RuntimeException.
     *
     * @param outputStream the OutputStream
     */
    public static void writeUncompressedNBTToStream(OutputStream outputStream, NBTTagCompound compound) {
        DataOutputStream dataOutputStream = (outputStream instanceof DataOutputStream ? (DataOutputStream) outputStream : new DataOutputStream(outputStream));
        writeTag(dataOutputStream, compound, "");
    }

    private static void writeTag(DataOutputStream outputStream, NBTTagBase tag, String name) {
        try {
            outputStream.writeByte(tag.getId());
            // End tags don't have a name
            if (tag.getId() != 0) {
                outputStream.writeUTF(name);
            }
            writeTagValue(outputStream, tag);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void writeTagValue(DataOutputStream outputStream, NBTTagBase tag) {
        try {
            switch (tag.getId()) {
                case 0:
                    // End tags don't have any data
                    break;
                case 1:
                    outputStream.writeByte(((NBTTagByte) tag).getValue());
                    break;
                case 2:
                    outputStream.writeShort(((NBTTagShort) tag).getValue());
                    break;
                case 3:
                    outputStream.writeInt(((NBTTagInt) tag).getValue());
                    break;
                case 4:
                    outputStream.writeLong(((NBTTagLong) tag).getValue());
                    break;
                case 5:
                    outputStream.writeFloat(((NBTTagFloat) tag).getValue());
                    break;
                case 6:
                    outputStream.writeDouble(((NBTTagDouble) tag).getValue());
                    break;
                case 7:
                    byte[] value = ((NBTTagByteArray) tag).getValue();
                    outputStream.writeInt(value.length);
                    outputStream.write(value);
                    break;
                case 8:
                    outputStream.writeUTF(((NBTTagString) tag).getValue());
                    break;
                case 9:
                    @SuppressWarnings("unchecked") NBTTagList<NBTTagBase> listTag = (NBTTagList<NBTTagBase>) tag;
                    outputStream.write(listTag.getType() & 0xFF);
                    outputStream.writeInt(listTag.size());
                    for (NBTTagBase baseTag : listTag.getContents()) {
                        writeTagValue(outputStream, baseTag);
                    }
                    break;
                case 10:
                    NBTTagCompound compound = (NBTTagCompound) tag;
                    for (Map.Entry<String, NBTTagBase> entry : compound.getContents().entrySet()) {
                        writeTag(outputStream, entry.getValue(), entry.getKey());
                    }
                    writeTag(outputStream, new NBTTagEnd(), null);
                    break;
                case 11:
                    int[] array = ((NBTTagIntArray) tag).getValue();
                    outputStream.writeInt(array.length);
                    for (int i : array) {
                        outputStream.writeInt(i);
                    }
                    break;
                default:
                    throw new RuntimeException("tag id " + tag.getId() + " is unknown");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Gets the complete NBT data from an ItemStack.
     *
     * This is different to #getItemNBT in the sense that it
     * returns all of the data of the ItemStack, including the
     * amount, id and data value. Note that this is in a different
     * format and is not compatible with #setItemNBT.
     *
     * This method is useful when ItemStacks are to be serialized
     * into NBT to save in a Chest for example.
     *
     * @param itemStack the ItemStack
     * @return the compound
     */
    public static NBTTagCompound getCompleteItemNBT(ItemStack itemStack) {
        Object handle = ReflectUtil.invokeMethod(null, AS_NMS_COPY, itemStack).getOrThrow();
        Object tagCompound = ReflectUtil.invokeMethod(handle, ITEM_STACK_SAVE_TO_NBT, new NBTTagCompound().getHandle()).getOrThrow();
        return NBTTagCompound.fromHandle(tagCompound);
    }

    /**
     * Gets the NBT data from an ItemStack
     *
     * @param itemStack the ItemStack
     * @return an NBTTagCompound containing the NBT data.
     */
    public static NBTTagCompound getItemNBT(ItemStack itemStack) {
        Object handle = ReflectUtil.invokeMethod(null, AS_NMS_COPY, itemStack).getOrThrow();
        Object tagCompound = ReflectUtil.getFieldValue(handle, ITEM_STACK_TAG).getOrThrow();
        if (tagCompound == null) {
            return new NBTTagCompound();
        }
        return NBTTagCompound.fromHandle(tagCompound);
    }

    /**
     * Sets the NBT data to an ItemStack
     *
     * This may or may not set the NBT to the
     * ItemStack directly, depending on how the
     * ItemStack was created, which is why an
     * ItemStack is returned. It is recommended
     * that you only use the returned ItemStack,
     * as the ItemStack passed as the argument
     * might not have been affected.
     *
     * @param itemStack the ItemStack
     * @param compound the compound to set
     * @return the modified ItemStack
     */
    public static ItemStack setItemNBT(ItemStack itemStack, NBTTagCompound compound) {
        Object handle = ReflectUtil.invokeMethod(null, AS_NMS_COPY, itemStack).getOrThrow();
        ReflectUtil.setFieldValue(handle, ITEM_STACK_TAG, compound.getHandle()).getOrThrow();
        return (ItemStack) ReflectUtil.invokeMethod(null, AS_BUKKIT_COPY, handle).getOrThrow();
    }

    /**
     * Gets the NBT data from an Entity
     *
     * @param entity the entity
     * @return an NBTTagCompound containing the NBT data.
     */
    public static NBTTagCompound getEntityNBT(Entity entity) {
        Object handle = ReflectUtil.invokeMethod(entity, ENTITY_GET_HANDLE).getOrThrow();
        Object tagCompound = new NBTTagCompound().getHandle();
        ReflectUtil.invokeMethod(handle, ENTITY_SAVE_TO_NBT, tagCompound).getOrThrow();
        return NBTTagCompound.fromHandle(tagCompound);
    }

    /**
     * Sets the NBT data to an Entity
     *
     * @param entity the Entity
     * @param compound the compound to set
     */
    public static void setEntityNBT(Entity entity, NBTTagCompound compound) {
        Object handle = ReflectUtil.invokeMethod(entity, ENTITY_GET_HANDLE).getOrThrow();
        Object tagCompound = compound.getHandle();
        ReflectUtil.invokeMethod(handle, ENTITY_LOAD_FROM_NBT, tagCompound).getOrThrow();
    }

    /**
     * Gets the NBT data from a TileEntity
     *
     * NOTE: If the BlockState is not connected to a TileEntity,
     * null is returned.
     *
     * @param blockState the TileEntity's BlockState
     * @return an NBTTagCompound containing the NBT data.
     */
    public static NBTTagCompound getTileEntityNBT(BlockState blockState) {
        Object handle = null;
        if (!ReflectUtil.isVersionHigherThan(1, 12)) {
            handle = ReflectUtil.invokeMethod(blockState, GET_TILE_ENTITY).getOrThrow();
        } else if (CRAFT_BLOCK_ENTITY_STATE_CLASS.isAssignableFrom(blockState.getClass())) {
            handle = ReflectUtil.invokeMethod(blockState, GET_TILE_ENTITY).getOrThrow();
        }
        if (handle == null) {
            return null;
        }
        Object tagCompound = new NBTTagCompound().getHandle();
        ReflectUtil.invokeMethod(handle, TILE_ENTITY_SAVE_TO_NBT, tagCompound).getOrThrow();
        return NBTTagCompound.fromHandle(tagCompound);
    }

    /**
     * Sets the NBT data to a TileEntity
     *
     * NOTE: If the BlockState is not connected to a TileEntity,
     * nothing will happen.
     *
     * @param blockState the TileEntity's BlockState
     * @param compound the compound to set
     */
    public static void setTileEntityNBT(BlockState blockState, NBTTagCompound compound)  {
        Object handle = null;
        if (!ReflectUtil.isVersionHigherThan(1, 12)) {
            handle = ReflectUtil.invokeMethod(blockState, GET_TILE_ENTITY).getOrThrow();
        } else if (CRAFT_BLOCK_ENTITY_STATE_CLASS.isAssignableFrom(blockState.getClass())) {
            handle = ReflectUtil.invokeMethod(blockState, GET_TILE_ENTITY).getOrThrow();
        }
        if (handle == null) {
            return;
        }
        Object tagCompound = compound.getHandle();
        ReflectUtil.invokeMethod(handle, TILE_ENTITY_LOAD_FROM_NBT, tagCompound).getOrThrow();
    }



    /**
     * The abstract base of all the NBT wrappers.
     */
    public static abstract class NBTTagBase {

        /**
         * Gets the NMS equivalent to this NBT wrapper.
         *
         * @return the NMS NBT tag
         */
        public abstract Object getHandle();

        /**
         * Gets the id of this tag
         *
         * @return the id
         */
        public abstract int getId();

        /**
         * Gets the NBT wrapper equivalent to this NMS NBT tag
         *
         * @param handle the NMS NBT tag
         * @return the NBT Wrapper
         */
        public static NBTTagBase fromHandle(Object handle) {
            switch (handle.getClass().getSimpleName()) {
                case "NBTTagEnd":
                    return new NBTTagEnd();
                case "NBTTagByte":
                    return NBTTagByte.fromHandle(handle);
                case "NBTTagShort":
                    return NBTTagShort.fromHandle(handle);
                case "NBTTagInt":
                    return NBTTagInt.fromHandle(handle);
                case "NBTTagLong":
                    return NBTTagLong.fromHandle(handle);
                case "NBTTagFloat":
                    return NBTTagFloat.fromHandle(handle);
                case "NBTTagDouble":
                    return NBTTagDouble.fromHandle(handle);
                case "NBTTagByteArray":
                    return NBTTagByteArray.fromHandle(handle);
                case "NBTTagString":
                    return NBTTagString.fromHandle(handle);
                case "NBTTagList":
                    return NBTTagList.fromHandle(handle);
                case "NBTTagCompound":
                    return NBTTagCompound.fromHandle(handle);
                case "NBTTagIntArray":
                    return NBTTagIntArray.fromHandle(handle);
            }
            return null;
        }
    }

    /**
     * A wrapper for NBTTagCompound
     *
     * Compounds contain several other tags nested inside them and are
     * usually what the objects using NBT use to store the data.
     */
    public static class NBTTagCompound extends NBTTagBase {
        private static final Constructor<?> TAG_COMPOUND_CONSTRUCTOR = ReflectUtil.getConstructor(ReflectUtil.getNMSClass("NBTTagCompound").getOrThrow()).getOrThrow();
        private static final Field MAP = ReflectUtil.getDeclaredFieldByType(ReflectUtil.getNMSClass("NBTTagCompound").getOrThrow(), Map.class, 0, true).getOrThrow();

        /**
         * Gets the NBT wrapper equivalent to this NMS NBT tag
         *
         * @param handle the NMS NBT tag
         * @return the NBT Wrapper
         */
        public static NBTTagCompound fromHandle(Object handle) {
            NBTTagCompound tagCompound = new NBTTagCompound();
            //noinspection unchecked
            Map<String, ?> map = (Map<String, ?>) ReflectUtil.getFieldValue(handle, MAP).getOrThrow();
            for (Map.Entry<String, ?> entry : map.entrySet()) {
                tagCompound.set(entry.getKey(), NBTTagBase.fromHandle(entry.getValue()));
            }
            return tagCompound;
        }

        private Map<String, NBTTagBase> values = Maps.newHashMap();

        /**
         * Initializes an empty NBTTagCompound
         */
        public NBTTagCompound() {

        }

        /**
         * Initializes an NBTTagCompound using the values in the map provided
         *
         * @param map the map containing the values to carry over
         */
        public NBTTagCompound(Map<String, NBTTagBase> map) {
            values.putAll(map);
        }

        /**
         * Sets a key to a certain value
         *
         * @param key the key
         * @param value the value
         */
        public void set(String key, NBTTagBase value) {
            values.put(key, value);
        }

        /**
         * Sets a key to a certain byte
         *
         * @param key the key
         * @param value the byte
         */
        public void setByte(String key, byte value) {
            this.set(key, new NBTTagByte(value));
        }

        /**
         * Sets a key to a certain byte
         *
         * NOTE: There is no boolean tag, so this is internally stored as a byte with either 1 (representing true)
         * or 0 (representing false) as its value. Any byte value other than zero will be interpreted as true.
         *
         * @param key the key
         * @param value the boolean
         */
        public void setBoolean(String key, boolean value) {
            this.set(key, new NBTTagByte((byte) (value ? 1 : 0)));
        }

        /**
         * Sets a key to a certain short
         *
         * @param key the key
         * @param value the short
         */
        public void setShort(String key, short value) {
            this.set(key, new NBTTagShort(value));
        }

        /**
         * Sets a key to a certain int
         *
         * @param key the key
         * @param value the int
         */
        public void setInt(String key, int value) {
            this.set(key, new NBTTagInt(value));
        }

        /**
         * Sets a key to a certain long
         *
         * @param key the key
         * @param value the long
         */
        public void setLong(String key, long value) {
            this.set(key, new NBTTagLong(value));
        }

        /**
         * Sets a key to a certain float
         *
         * @param key the key
         * @param value the float
         */
        public void setFloat(String key, float value) {
            this.set(key, new NBTTagFloat(value));
        }

        /**
         * Sets a key to a certain double
         *
         * @param key the key
         * @param value the double
         */
        public void setDouble(String key, double value) {
            this.set(key, new NBTTagDouble(value));
        }

        /**
         * Sets a key to a certain byte array
         *
         * @param key the key
         * @param value the byte array
         */
        public void setByteArray(String key, byte[] value) {
            this.set(key, new NBTTagByteArray(value));
        }

        /**
         * Sets a key to a certain String
         *
         * @param key the key
         * @param value the String
         */
        public void setString(String key, String value) {
            this.set(key, new NBTTagString(value));
        }

        /**
         * Sets a key to a certain List
         *
         * @param key the key
         * @param value the List
         */
        public void setList(String key, List<NBTTagBase> value) {
            this.set(key, new NBTTagList<>(value));
        }

        /**
         * Sets a key to a certain int array
         *
         * @param key the key
         * @param value the int array
         */
        public void setIntArray(String key, int[] value) {
            this.set(key, new NBTTagIntArray(value));
        }

        /**
         * Gets the value for a certain key
         *
         * NOTE: If the key doesn't correspond to a value, an exception will be raised.
         *
         * @param key the key
         * @return the value
         */
        public NBTTagBase get(String key) {
            return values.get(key);
        }

        /**
         * Gets the byte value for a certain key
         *
         * NOTE: If the key doesn't correspond to a value or the value is not of
         * type byte, an exception will be raised.
         *
         * @param key the key
         * @return the byte
         */
        public byte getByte(String key) {
            return ((NBTTagByte) this.get(key)).getValue();
        }

        /**
         * Gets the boolean value for a certain key
         *
         * NOTE: If the key doesn't correspond to a value or the value is not of
         * type byte, an exception will be raised.
         *
         * NOTE #2: There is no boolean tag, so this is internally stored as a byte with either 1 (representing true)
         * or 0 (representing false) as its value. Any byte value other than zero will be interpreted as true.
         *
         * @param key the key
         * @return the byte
         */
        public boolean getBoolean(String key) {
            return ((NBTTagByte) this.get(key)).getValue() != 0;
        }

        /**
         * Gets the short value for a certain key
         *
         * NOTE: If the key doesn't correspond to a value or the value is not of
         * type short, an exception will be raised.
         *
         * @param key the key
         * @return the short
         */
        public short getShort(String key) {
            return ((NBTTagShort) this.get(key)).getValue();
        }

        /**
         * Gets the int value for a certain key
         *
         * NOTE: If the key doesn't correspond to a value or the value is not of
         * type int, an exception will be raised.
         *
         * @param key the key
         * @return the int
         */
        public int getInt(String key) {
            return ((NBTTagInt) this.get(key)).getValue();
        }

        /**
         * Gets the long value for a certain key
         *
         * NOTE: If the key doesn't correspond to a value or the value is not of
         * type long, an exception will be raised.
         *
         * @param key the key
         * @return the long
         */
        public long getLong(String key) {
            return ((NBTTagLong) this.get(key)).getValue();
        }

        /**
         * Gets the float value for a certain key
         *
         * NOTE: If the key doesn't correspond to a value or the value is not of
         * type float, an exception will be raised.
         *
         * @param key the key
         * @return the float
         */
        public float getFloat(String key) {
            return ((NBTTagFloat) this.get(key)).getValue();
        }

        /**
         * Gets the double value for a certain key
         *
         * NOTE: If the key doesn't correspond to a value or the value is not of
         * type double, an exception will be raised.
         *
         * @param key the key
         * @return the double
         */
        public double getDouble(String key) {
            return ((NBTTagDouble) this.get(key)).getValue();
        }

        /**
         * Gets the byte array value for a certain key
         *
         * NOTE: If the key doesn't correspond to a value or the value is not of
         * type byte array, an exception will be raised.
         *
         * @param key the key
         * @return the byte array
         */
        public byte[] getByteArray(String key) {
            return ((NBTTagByteArray) this.get(key)).getValue();
        }

        /**
         * Gets the String value for a certain key
         *
         * NOTE: If the key doesn't correspond to a value or the value is not of
         * type String, an exception will be raised.
         *
         * @param key the key
         * @return the String
         */
        public String getString(String key) {
            return ((NBTTagString) this.get(key)).getValue();
        }

        /**
         * Gets the List value for a certain key
         *
         * NOTE: If the key doesn't correspond to a value or the value is not of
         * type List, an exception will be raised.
         *
         * @param key the key
         * @return the List
         */
        public List<NBTTagBase> getList(String key) {
            //noinspection unchecked
            return ((NBTTagList<NBTTagBase>) this.get(key)).getContents();
        }

        /**
         * Gets the int array value for a certain key
         *
         * NOTE: If the key doesn't correspond to a value or the value is not of
         * type int array, an exception will be raised.
         *
         * @param key the key
         * @return the int array
         */
        public int[] getIntArray(String key) {
            return ((NBTTagIntArray) this.get(key)).getValue();
        }

        /**
         * Returns an unmodifiable map with the current contents of this compound
         *
         * @return the map
         */
        public Map<String, NBTTagBase> getContents() {
            return Collections.unmodifiableMap(this.values);
        }

        /**
         * Tells whether the compound currently has a specified key
         *
         * @param key the key
         * @return whether the key exists
         */
        public boolean hasKey(String key) {
            return this.values.containsKey(key);
        }

        /**
         * Tells whether the compound currently has a specified key with a specified type
         *
         * @param key the key
         * @param type the type
         * @return whether the key exists and is of the correct type
         */
        public boolean hasKeyWithType(String key, Class<? extends NBTTagBase> type) {
            return this.values.containsKey(key) && this.values.get(key).getClass() == type;
        }

        /**
         * Gets the NMS equivalent to this NBT wrapper.
         *
         * @return the NMS NBT tag
         */
        @Override
        public Object getHandle() {
            Object nbtTagCompound = ReflectUtil.invokeConstructor(TAG_COMPOUND_CONSTRUCTOR).getOrThrow();
            //noinspection unchecked
            Map<String, Object> handleMap = (Map<String, Object>) ReflectUtil.getFieldValue(nbtTagCompound, MAP).getOrThrow();
            for (Map.Entry<String, NBTTagBase> entry : this.values.entrySet()) {
                handleMap.put(entry.getKey(), entry.getValue().getHandle());
            }
            return nbtTagCompound;
        }

        /**
         * Gets the id of this tag
         *
         * @return the id
         */
        @Override
        public int getId() {
            return 10;
        }

        @Override
        public String toString() {
            return "NBTTagCompound{values=" + this.values + "}";
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof NBTTagCompound)) {
                return false;
            }
            return this.values.equals(((NBTTagCompound) obj).values);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.values);
        }
    }

    /**
     * A wrapper for NBTTagEnd
     *
     * An end tag contains no data and is only used in serialization of NBT. It's only included
     * for completeness purposes.
     */
    public static class NBTTagEnd extends NBTTagBase {
        private static final Constructor<?> TAG_END_CONSTRUCTOR = ReflectUtil.getConstructor(ReflectUtil.getNMSClass("NBTTagCompound").getOrThrow()).getOrThrow();

        /**
         * Gets the NMS equivalent to this NBT wrapper.
         *
         * @return the NMS NBT tag
         */
        @Override
        public Object getHandle() {
            return ReflectUtil.invokeConstructor(TAG_END_CONSTRUCTOR).getOrThrow();
        }

        /**
         * Gets the id of this tag
         *
         * @return the id
         */
        @Override
        public int getId() {
            return 0;
        }

        @Override
        public String toString() {
            return "NBTTagEnd";
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof NBTTagEnd;
        }

        @Override
        public int hashCode() {
            return 0;
        }
    }

    /**
     * A wrapper for NBTTagByte
     *
     * Bytes are an 8 bit data type, like in Java. They are also used for booleans,
     * with 0 representing false and 1 representing true.
     */
    public static class NBTTagByte extends NBTTagBase {
        private static final Constructor<?> TAG_BYTE_CONSTRUCTOR = ReflectUtil.getConstructor(ReflectUtil.getNMSClass("NBTTagByte").getOrThrow(), byte.class).getOrThrow();
        private static final Method GET_VALUE = findValueMethod(ReflectUtil.getNMSClass("NBTTagByte").getOrThrow(), byte.class);

        /**
         * Gets the NBT wrapper equivalent to this NMS NBT tag
         *
         * @param handle the NMS NBT tag
         * @return the NBT Wrapper
         */
        public static NBTTagByte fromHandle(Object handle) {
            byte b = (byte) ReflectUtil.invokeMethod(handle, GET_VALUE).getOrThrow();
            return new NBTTagByte(b);
        }

        private byte value;

        /**
         * Initializes this byte tag with the specified value
         *
         * @param value the byte
         */
        public NBTTagByte(byte value) {
            this.value = value;
        }

        /**
         * Sets the value of this byte tag
         *
         * @param value the byte
         */
        public void setValue(byte value) {
            this.value = value;
        }

        /**
         * Gets the value of this byte tag
         *
         * @return the byte
         */
        public byte getValue() {
            return value;
        }

        /**
         * Gets the NMS equivalent to this NBT wrapper.
         *
         * @return the NMS NBT tag
         */
        @Override
        public Object getHandle() {
            return ReflectUtil.invokeConstructor(TAG_BYTE_CONSTRUCTOR, value).getOrThrow();
        }

        /**
         * Gets the id of this tag
         *
         * @return the id
         */
        @Override
        public int getId() {
            return 1;
        }

        @Override
        public String toString() {
            return "NBTTagByte{value=" + value + "}";
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof NBTTagByte)) {
                return false;
            }
            return this.value == ((NBTTagByte) obj).value;
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.value);
        }
    }

    /**
     * A wrapper for NBTTagShort
     *
     * Shorts are 16 bit numbers, like in Java. They should only be used
     * if you are sure that the number won't exceed 16 bits. If you aren't, use
     * long or int instead.
     */
    public static class NBTTagShort extends NBTTagBase {
        private static final Constructor<?> TAG_SHORT_CONSTRUCTOR = ReflectUtil.getConstructor(ReflectUtil.getNMSClass("NBTTagShort").getOrThrow(), short.class).getOrThrow();
        private static final Method GET_VALUE = findValueMethod(ReflectUtil.getNMSClass("NBTTagShort").getOrThrow(), short.class);

        /**
         * Gets the NBT wrapper equivalent to this NMS NBT tag
         *
         * @param handle the NMS NBT tag
         * @return the NBT Wrapper
         */
        public static NBTTagShort fromHandle(Object handle) {
            short s = (short) ReflectUtil.invokeMethod(handle, GET_VALUE).getOrThrow();
            return new NBTTagShort(s);
        }

        private short value;

        /**
         * Initializes this short tag with the specified value
         *
         * @param value the short
         */
        public NBTTagShort(short value) {
            this.value = value;
        }

        /**
         * Sets the value of this short tag
         *
         * @param value the short
         */
        public void setValue(short value) {
            this.value = value;
        }

        /**
         * Gets the value of this short tag
         *
         * @return the short
         */
        public short getValue() {
            return value;
        }

        /**
         * Gets the NMS equivalent to this NBT wrapper.
         *
         * @return the NMS NBT tag
         */
        @Override
        public Object getHandle() {
            return ReflectUtil.invokeConstructor(TAG_SHORT_CONSTRUCTOR, value).getOrThrow();
        }

        /**
         * Gets the id of this tag
         *
         * @return the id
         */
        @Override
        public int getId() {
            return 2;
        }

        @Override
        public String toString() {
            return "NBTTagShort{value=" + value + "}";
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof NBTTagShort)) {
                return false;
            }
            return this.value == ((NBTTagShort) obj).value;
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.value);
        }
    }

    /**
     * A wrapper for NBTTagInt
     *
     * Ints are 32 bit numbers, like in Java, and are the most common data type for storing integer
     * values.
     */
    public static class NBTTagInt extends NBTTagBase {
        private static final Constructor<?> TAG_INT_CONSTRUCTOR = ReflectUtil.getConstructor(ReflectUtil.getNMSClass("NBTTagInt").getOrThrow(), int.class).getOrThrow();
        private static final Method GET_VALUE = findValueMethod(ReflectUtil.getNMSClass("NBTTagInt").getOrThrow(), int.class);

        /**
         * Gets the NBT wrapper equivalent to this NMS NBT tag
         *
         * @param handle the NMS NBT tag
         * @return the NBT Wrapper
         */
        public static NBTTagInt fromHandle(Object handle) {
            int i = (int) ReflectUtil.invokeMethod(handle, GET_VALUE).getOrThrow();
            return new NBTTagInt(i);
        }

        private int value;

        /**
         * Initializes this int tag with the specified value
         *
         * @param value the int
         */
        public NBTTagInt(int value) {
            this.value = value;
        }

        /**
         * Sets the value of this int tag
         *
         * @param value the int
         */
        public void setValue(int value) {
            this.value = value;
        }

        /**
         * Gets the value of this int tag
         *
         * @return the int
         */
        public int getValue() {
            return value;
        }

        /**
         * Gets the NMS equivalent to this NBT wrapper.
         *
         * @return the NMS NBT tag
         */
        @Override
        public Object getHandle() {
            return ReflectUtil.invokeConstructor(TAG_INT_CONSTRUCTOR, value).getOrThrow();
        }

        /**
         * Gets the id of this tag
         *
         * @return the id
         */
        @Override
        public int getId() {
            return 3;
        }

        @Override
        public String toString() {
            return "NBTTagInt{value=" + value + "}";
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof NBTTagInt)) {
                return false;
            }
            return this.value == ((NBTTagInt) obj).value;
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.value);
        }
    }

    /**
     * A wrapper for NBTTagLong
     *
     * Longs are 64 bit numbers, like in Java, and are most commonly used to store timestamps.
     */
    public static class NBTTagLong extends NBTTagBase {
        private static final Constructor<?> TAG_LONG_CONSTRUCTOR = ReflectUtil.getConstructor(ReflectUtil.getNMSClass("NBTTagLong").getOrThrow(), long.class).getOrThrow();
        private static final Method GET_VALUE = findValueMethod(ReflectUtil.getNMSClass("NBTTagLong").getOrThrow(), long.class);

        /**
         * Gets the NBT wrapper equivalent to this NMS NBT tag
         *
         * @param handle the NMS NBT tag
         * @return the NBT Wrapper
         */
        public static NBTTagLong fromHandle(Object handle) {
            long l = (long) ReflectUtil.invokeMethod(handle, GET_VALUE).getOrThrow();
            return new NBTTagLong(l);
        }

        private long value;

        /**
         * Initializes this long tag with the specified value
         *
         * @param value the long
         */
        public NBTTagLong(long value) {
            this.value = value;
        }

        /**
         * Sets the value of this long tag
         *
         * @param value the long
         */
        public void setValue(long value) {
            this.value = value;
        }

        /**
         * Gets the value of this long tag
         *
         * @return the long
         */
        public long getValue() {
            return value;
        }

        /**
         * Gets the NMS equivalent to this NBT wrapper.
         *
         * @return the NMS NBT tag
         */
        @Override
        public Object getHandle() {
            return ReflectUtil.invokeConstructor(TAG_LONG_CONSTRUCTOR, this.value).getOrThrow();
        }

        /**
         * Gets the id of this tag
         *
         * @return the id
         */
        @Override
        public int getId() {
            return 4;
        }

        @Override
        public String toString() {
            return "NBTTagLong{value=" + this.value + "}";
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof NBTTagLong)) {
                return false;
            }
            return this.value == ((NBTTagLong) obj).value;
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.value);
        }
    }

    /**
     * A wrapper for NBTTagFloat
     *
     * Floats are 32 bit floating point numbers, like in Java. They should only be used if you are sure
     * the number will not exceed 32 bits. If you aren't, use double instead.
     */
    public static class NBTTagFloat extends NBTTagBase {
        private static final Constructor<?> TAG_FLOAT_CONSTRUCTOR = ReflectUtil.getConstructor(ReflectUtil.getNMSClass("NBTTagFloat").getOrThrow(), float.class).getOrThrow();
        private static final Method GET_VALUE = findValueMethod(ReflectUtil.getNMSClass("NBTTagFloat").getOrThrow(), float.class);

        /**
         * Gets the NBT wrapper equivalent to this NMS NBT tag
         *
         * @param handle the NMS NBT tag
         * @return the NBT Wrapper
         */
        public static NBTTagFloat fromHandle(Object handle) {
            float f = (float) ReflectUtil.invokeMethod(handle, GET_VALUE).getOrThrow();
            return new NBTTagFloat(f);
        }

        private float value;

        /**
         * Initializes this float tag with the specified value
         *
         * @param value the float
         */
        public NBTTagFloat(float value) {
            this.value = value;
        }

        /**
         * Sets the value of this float tag
         *
         * @param value the float
         */
        public void setValue(float value) {
            this.value = value;
        }

        /**
         * Gets the value of this float tag
         *
         * @return the float
         */
        public float getValue() {
            return value;
        }

        /**
         * Gets the NMS equivalent to this NBT wrapper.
         *
         * @return the NMS NBT tag
         */
        @Override
        public Object getHandle() {
            return ReflectUtil.invokeConstructor(TAG_FLOAT_CONSTRUCTOR, this.value).getOrThrow();
        }

        /**
         * Gets the id of this tag
         *
         * @return the id
         */
        @Override
        public int getId() {
            return 5;
        }

        @Override
        public String toString() {
            return "NBTTagFloat{value=" + this.value + "}";
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof NBTTagFloat)) {
                return false;
            }
            return this.value == ((NBTTagFloat) obj).value;
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.value);
        }
    }

    /**
     * A wrapper for NBTTagDouble
     *
     * Doubles are 64 bit floating point numbers, like in Java, and are the most
     * common data type to store floating point numbers.
     *
     */
    public static class NBTTagDouble extends NBTTagBase {
        private static final Constructor<?> TAG_DOUBLE_CONSTRUCTOR = ReflectUtil.getConstructor(ReflectUtil.getNMSClass("NBTTagDouble").getOrThrow(), double.class).getOrThrow();
        private static final Method GET_VALUE = findValueMethod(ReflectUtil.getNMSClass("NBTTagDouble").getOrThrow(), double.class);

        /**
         * Gets the NBT wrapper equivalent to this NMS NBT tag
         *
         * @param handle the NMS NBT tag
         * @return the NBT Wrapper
         */
        public static NBTTagDouble fromHandle(Object handle) {
            double d = ((double) ReflectUtil.invokeMethod(handle, GET_VALUE).getOrThrow());
            return new NBTTagDouble(d);
        }

        private double value;

        /**
         * Initializes this double tag with the specified value
         *
         * @param value the double
         */
        public NBTTagDouble(double value) {
            this.value = value;
        }

        /**
         * Sets the value of this double tag
         *
         * @param value the double
         */
        public void setValue(double value) {
            this.value = value;
        }

        /**
         * Gets the value of this double tag
         *
         * @return the double
         */
        public double getValue() {
            return value;
        }

        /**
         * Gets the NMS equivalent to this NBT wrapper.
         *
         * @return the NMS NBT tag
         */
        @Override
        public Object getHandle() {
            return ReflectUtil.invokeConstructor(TAG_DOUBLE_CONSTRUCTOR, this.value).getOrThrow();
        }

        /**
         * Gets the id of this tag
         *
         * @return the id
         */
        @Override
        public int getId() {
            return 6;
        }

        @Override
        public String toString() {
            return "NBTTagDouble{value=" + this.value + "}";
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof NBTTagDouble)) {
                return false;
            }
            return this.value == ((NBTTagDouble) obj).value;
        }
    }

    /**
     * A wrapper for NBTTagByteArray
     *
     * Array of bytes with any length from 0 to roughly 2^31
     */
    public static class NBTTagByteArray extends NBTTagBase {
        private static final Constructor<?> TAG_BYTE_ARRAY_CONSTRUCTOR = ReflectUtil.getConstructor(ReflectUtil.getNMSClass("NBTTagByteArray").getOrThrow(), byte[].class).getOrThrow();
        private static final Method GET_VALUE = findValueMethod(ReflectUtil.getNMSClass("NBTTagByteArray").getOrThrow(), byte[].class);

        /**
         * Gets the NBT wrapper equivalent to this NMS NBT tag
         *
         * @param handle the NMS NBT tag
         * @return the NBT Wrapper
         */
        public static NBTTagByteArray fromHandle(Object handle) {
            byte[] b = (byte[]) ReflectUtil.invokeMethod(handle, GET_VALUE).getOrThrow();
            return new NBTTagByteArray(b);
        }

        private byte[] value;

        /**
         * Initializes this byte array tag with the specified value
         *
         * @param value the byte array
         */
        public NBTTagByteArray(byte[] value) {
            this.value = value;
        }

        /**
         * Sets the value of this byte array tag
         *
         * @param value the byte array
         */
        public void setValue(byte[] value) {
            this.value = value;
        }

        /**
         * Gets the value of this byte array tag
         *
         * @return the byte array
         */
        public byte[] getValue() {
            return value;
        }

        /**
         * Gets the NMS equivalent to this NBT wrapper.
         *
         * @return the NMS NBT tag
         */
        @Override
        public Object getHandle() {
            return ReflectUtil.invokeConstructor(TAG_BYTE_ARRAY_CONSTRUCTOR, (Object) this.value).getOrThrow();
        }

        /**
         * Gets the id of this tag
         *
         * @return the id
         */
        @Override
        public int getId() {
            return 7;
        }

        @Override
        public String toString() {
            return "NBTTagByteArray{value=" + Arrays.toString(this.value) + "}";
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof NBTTagByteArray)) {
                return false;
            }
            return Arrays.equals(this.value, ((NBTTagByteArray) obj).value);
        }

        @Override
        public int hashCode() {
            return Objects.hash((Object) this.value);
        }
    }

    /**
     * A wrapper for NBTTagString
     *
     * A String of UTF-8 characters with any length from 0 to roughly 2^15
     */
    public static  class NBTTagString extends NBTTagBase {
        private static final Constructor<?> TAG_STRING_CONSTRUCTOR = ReflectUtil.getConstructor(ReflectUtil.getNMSClass("NBTTagString").getOrThrow(), String.class).getOrThrow();
        private static final Method GET_VALUE = findValueMethod(ReflectUtil.getNMSClass("NBTTagString").getOrThrow(), String.class);

        /**
         * Gets the NBT wrapper equivalent to this NMS NBT tag
         *
         * @param handle the NMS NBT tag
         * @return the NBT Wrapper
         */
        public static NBTTagString fromHandle(Object handle) {
            String s = (String) ReflectUtil.invokeMethod(handle, GET_VALUE).getOrThrow();
            return new NBTTagString(s);
        }

        private String value;

        /**
         * Initializes this String tag with the specified value
         *
         * @param value the String
         */
        public NBTTagString(String value) {
            this.value = value;
        }

        /**
         * Sets the value of this String tag
         *
         * @param value the String
         */
        public void setValue(String value) {
            this.value = value;
        }

        /**
         * Gets the value of this String tag
         *
         * @return the String
         */
        public String getValue() {
            return value;
        }

        /**
         * Gets the NMS equivalent to this NBT wrapper.
         *
         * @return the NMS NBT tag
         */
        @Override
        public Object getHandle() {
            return ReflectUtil.invokeConstructor(TAG_STRING_CONSTRUCTOR, this.value).getOrThrow();
        }

        /**
         * Gets the id of this tag
         *
         * @return the id
         */
        @Override
        public int getId() {
            return 8;
        }

        @Override
        public String toString() {
            return "NBTTagString{value=" + this.value + "}";
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof NBTTagString)) {
                return false;
            }
            return this.value.equals(((NBTTagString) obj).value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.value);
        }
    }

    /**
     * A wrapper for NBTTagList
     *
     * A List containing NBT tags with a maximum length of roughly 2^31
     *
     * @param <T> the type of this list
     */
    @SuppressWarnings("UnusedReturnValue")
    public static class NBTTagList<T extends NBTTagBase> extends NBTTagBase {
        private static final Constructor<?> TAG_LIST_CONSTRUCTOR = ReflectUtil.getConstructor(ReflectUtil.getNMSClass("NBTTagList").getOrThrow()).getOrThrow();
        private static final Field LIST = ReflectUtil.getDeclaredFieldByType(ReflectUtil.getNMSClass("NBTTagList").getOrThrow(), List.class, 0, true).getOrThrow();
        private static final Method ADD_METHOD = ReflectUtil.getMethodByParams(ReflectUtil.getNMSClass("NBTTagList").getOrThrow(), 0, ReflectUtil.getNMSClass("NBTBase").getOrThrow()).getOrThrow();

        /**
         * Gets the NBT wrapper equivalent to this NMS NBT tag
         *
         * @param handle the NMS NBT tag
         * @return the NBT Wrapper
         */
        public static NBTTagList<NBTTagBase> fromHandle(Object handle) {
            List<?> list = (List<?>) ReflectUtil.getFieldValue(handle, LIST).getOrThrow();
            NBTTagList<NBTTagBase> tagList = new NBTTagList<>();
            for (Object o : list) {
                tagList.add(NBTTagBase.fromHandle(o));
            }
            return tagList;
        }

        private List<T> value;
        private int type = -1;

        /**
         * Initializes this List tag with the specified value
         *
         * @param value the List
         */
        public NBTTagList(List<T> value) {
            this.value = value;
            if (value.size() > 0) {
                type = value.get(0).getId();
            }
            for (NBTTagBase tag : value) {
                if (tag.getId() != type) {
                    throw new RuntimeException("id " + tag.getId() + " does not match List type " + type);
                }
            }
        }

        /**
         * Initializes this List tag with the specified value
         *
         * @param value the contents
         */
        @SafeVarargs
        public NBTTagList(T... value) {
            // Arrays.asList() returns an immutable List, so we make a new, mutable one.
            this.value = new ArrayList<>(Arrays.asList(value));
            if (this.value.size() > 0) {
                type = this.value.get(0).getId();
            }
            for (NBTTagBase tag : value) {
                if (tag.getId() != type) {
                    throw new RuntimeException("id " + tag.getId() + " does not match List type " + type);
                }
            }
        }

        /**
         * Adds an element to the List
         *
         * @param t the element to add
         * @return whether the List was changed (see {@link List#add(Object)})
         */
        public boolean add(T t) {
            if (t == null) {
                throw new IllegalArgumentException("element to add cannot be null");
            }
            if (type == -1) {
                type = t.getId();
            } else if (t.getId() != type) {
                throw new IllegalArgumentException("id " + t.getId() + " does not match List type " + type);
            }
            return value.add(t);
        }

        /**
         * Removes an element from the List
         *
         * @param t the element to remove
         * @return whether the List contained this element (see {@link List#remove(Object)})
         */
        public boolean remove(T t) {
            return value.remove(t);
        }

        /**
         * Gets the element at the specified index
         *
         * @param index the index
         * @return the element
         */
        public T get(int index) {
            return value.get(index);
        }

        /**
         * Sets the element at the specified index
         * @param index the index
         * @param t the element to set
         * @return the element previously at the index
         */
        public T set(int index, T t) {
            if (t == null) {
                throw new IllegalArgumentException("element to add cannot be null");
            }
            if (type == -1) {
                type = t.getId();
            } else if (t.getId() != type) {
                throw new IllegalArgumentException("id " + t.getId() + " does not match List type " + type);
            }
            return value.set(index, t);
        }

        /**
         * Gets the size of the List
         *
         * @return the size
         */
        public int size() {
            return value.size();
        }

        /**
         * Clears the List
         */
        public void clear() {
            this.value.clear();
        }

        /**
         * Adds all elements in the specified collection
         *
         * @param collection the elements
         * @return whether the List was changed (see {@link List#addAll(Collection)})
         */
        public boolean addAll(Collection<? extends T> collection) {
            if (collection == null) {
                throw new IllegalArgumentException("collection to add cannot be null");
            }
            if (type == -1 && collection.size() > 0) {
                type = collection.iterator().next().getId();
            }
            for (T t : collection) {
                if (t.getId() != type) {
                    throw new IllegalArgumentException("id " + t.getId() + " does not match List type " + type);
                }
            }
            return this.value.addAll(collection);
        }

        /**
         * Gets the contents of this List
         *
         * @return an unmodifiable List with the contents of this List tag
         */
        public List<T> getContents() {
            return Collections.unmodifiableList(this.value);
        }

        /**
         * Gets the type of the elements contained in this List
         *
         * @return the id, or -1 if the list does not yet have an id.
         */
        public int getType() {
            return this.type;
        }

        /**
         * Gets the NMS equivalent to this NBT wrapper.
         *
         * @return the NMS NBT tag
         */
        @Override
        public Object getHandle() {
            Object handle = ReflectUtil.invokeConstructor(TAG_LIST_CONSTRUCTOR).getOrThrow();
            for (NBTTagBase baseTag : this.value) {
                ReflectUtil.invokeMethod(handle, ADD_METHOD, baseTag.getHandle()).getOrThrow();
            }
            return handle;
        }

        /**
         * Gets the id of this tag
         *
         * @return the id
         */
        @Override
        public int getId() {
            return 9;
        }

        @Override
        public String toString() {
            return "NBTTagList{value=" + this.value + "}";
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof NBTTagList)) {
                return false;
            }
            return this.value.equals(((NBTTagList) obj).value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.value);
        }
    }

    /**
     * A wrapper for NBTTagIntArray
     *
     * Array of ints with any length from 0 to roughly 2^31
     */
    public static class NBTTagIntArray extends NBTTagBase {
        private static final Constructor<?> TAG_INT_ARRAY_CONSTRUCTOR = ReflectUtil.getConstructor(ReflectUtil.getNMSClass("NBTTagIntArray").getOrThrow(), int[].class).getOrThrow();
        private static final Method GET_VALUE = findValueMethod(ReflectUtil.getNMSClass("NBTTagIntArray").getOrThrow(), int[].class);

        /**
         * Gets the NBT wrapper equivalent to this NMS NBT tag
         *
         * @param handle the NMS NBT tag
         * @return the NBT Wrapper
         */
        public static NBTTagIntArray fromHandle(Object handle) {
            int[] i = (int[]) ReflectUtil.invokeMethod(handle, GET_VALUE).getOrThrow();
            return new NBTTagIntArray(i);
        }

        private int[] value;

        /**
         * Initializes this int array tag with the specified value
         *
         * @param value the int array
         */
        public NBTTagIntArray(int[] value) {
            this.value = value;
        }

        /**
         * Sets the value of this int array tag
         *
         * @param value the int array
         */
        public void setValue(int[] value) {
            this.value = value;
        }

        /**
         * Gets the value of this int array tag
         *
         * @return the int array
         */
        public int[] getValue() {
            return value;
        }

        /**
         * Gets the NMS equivalent to this NBT wrapper.
         *
         * @return the NMS NBT tag
         */
        @Override
        public Object getHandle() {
            return ReflectUtil.invokeConstructor(TAG_INT_ARRAY_CONSTRUCTOR, (Object) this.value).getOrThrow();
        }

        /**
         * Gets the id of this tag
         *
         * @return the id
         */
        @Override
        public int getId() {
            return 11;
        }

        @Override
        public String toString() {
            return "NBTTagIntArray{value=" + Arrays.toString(this.value) + "}";
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof NBTTagIntArray)) {
                return false;
            }
            return Arrays.equals(this.value, ((NBTTagIntArray) obj).value);
        }

        @Override
        public int hashCode() {
            return Objects.hash((Object) this.value);
        }
    }

    private static final List<String> METHOD_NAMES = Arrays.asList("equals", "hashCode", "toString", "getTypeId");

    private static Method findValueMethod(Class<?> clazz, Class<?> type) {
        int index = 0;
        while (true) {
            Method method = ReflectUtil.getMethodByType(clazz, type, index++).getOrThrow();
            if (Modifier.isStatic(method.getModifiers()) || !Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            if (METHOD_NAMES.contains(method.getName())) {
                continue;
            }
            return method;
        }
    }
}

