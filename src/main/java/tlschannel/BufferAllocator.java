package tlschannel;

import java.nio.ByteBuffer;

/**
 * A factory for {@link ByteBuffer}s. Implementations are free to return heap or
 * direct buffers, or to do any kind of pooling. They are also expected to be
 * thread-safe.
 */
public interface BufferAllocator {

	/**
	 * Allocate a {@link ByteBuffer} with the given initial capacity.
	 */
	ByteBuffer allocate(int size);

	/**
	 * Deallocate the given {@link ByteBuffer}.
	 * 
	 * @param buffer
	 *            the buffer to deallocate, that should have been allocated using
	 *            the same {@link BufferAllocator} instance
	 */
	void free(ByteBuffer buffer);

	long bytesAllocated();

	long bytesDeallocated();

}
