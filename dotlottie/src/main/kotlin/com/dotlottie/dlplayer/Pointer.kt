package com.dotlottie.dlplayer

import java.nio.ByteBuffer

/**
 * Pointer class that wraps a native memory address.
 * Provides direct buffer access for native memory via JNI.
 */
class Pointer(private val address: Long) {

    /**
     * Get a ByteBuffer view of the native memory at this address.
     *
     * @param offset Offset from the pointer address
     * @param length Length of the buffer in bytes
     * @return A direct ByteBuffer pointing to the native memory
     */
    fun getByteBuffer(offset: Long, length: Long): ByteBuffer {
        return nativeGetByteBuffer(address + offset, length.toInt())
    }

    /**
     * Get the raw address value
     */
    fun getAddress(): Long = address

    /**
     * Check if this pointer is valid (non-null)
     */
    fun isValid(): Boolean = address != 0L

    private external fun nativeGetByteBuffer(address: Long, length: Int): ByteBuffer

    companion object {
        init {
            System.loadLibrary("dotlottie_player")
            System.loadLibrary("dlplayer")
        }

        /**
         * Create a Pointer from a raw address
         */
        fun fromAddress(address: Long): Pointer? {
            return if (address != 0L) Pointer(address) else null
        }
    }
}
