package tlschannel.async;

import tlschannel.TlsChannel;
import tlschannel.impl.ByteBufferSet;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.CompletionHandler;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import tlschannel.async.AsynchronousTlsChannelGroup.RegisteredSocket;

import static tlschannel.async.AsynchronousTlsChannelGroup.WriteOperation;
import static tlschannel.async.AsynchronousTlsChannelGroup.ReadOperation;

/**
 * An {@link AsynchronousByteChannel} that works using {@link TlsChannel}s.
 */
public class AsynchronousTlsChannel implements ExtendedAsynchronousByteChannel {

    private class FutureReadResult extends CompletableFuture<Integer> {
        ReadOperation op;

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            super.cancel(mayInterruptIfRunning);
            return group.doCancelRead(registeredSocket, op);
        }
    }

    private class FutureWriteResult extends CompletableFuture<Integer> {
        WriteOperation op;

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            super.cancel(mayInterruptIfRunning);
            return group.doCancelWrite(registeredSocket, op);
        }
    }

    private final AsynchronousTlsChannelGroup group;
    private final TlsChannel tlsChannel;
    private final RegisteredSocket registeredSocket;
    private final ExecutorService completionExecutor;

    public AsynchronousTlsChannel(
            AsynchronousTlsChannelGroup channelGroup,
            TlsChannel tlsChannel,
            SocketChannel socketChannel,
            ExecutorService completionExecutor) throws ClosedChannelException, IllegalArgumentException {
        if (!tlsChannel.isOpen() || !socketChannel.isOpen()) {
            throw new ClosedChannelException();
        }
        if (socketChannel.isBlocking()) {
            throw new IllegalArgumentException("socket channel must be in non-blocking mode");
        }
        this.group = channelGroup;
        this.tlsChannel = tlsChannel;
        this.completionExecutor = completionExecutor;
        this.registeredSocket = channelGroup.registerSocket(tlsChannel, socketChannel);
    }

    @Override
    public <A> void read(
            ByteBuffer dst,
            A attach, CompletionHandler<Integer, ? super A> handler) {
        checkReadOnly(dst);
        if (!dst.hasRemaining()) {
            completeWithZeroInt(attach, handler);
            return;
        }
        group.startRead(
                registeredSocket,
                new ByteBufferSet(dst),
                0, TimeUnit.MILLISECONDS,
                c -> completionExecutor.submit(() -> handler.completed((int) c, attach)),
                e -> completionExecutor.submit(() -> handler.failed(e, attach)));
    }

    @Override
    public <A> void read(
            ByteBuffer dst,
            long timeout, TimeUnit unit,
            A attach, CompletionHandler<Integer, ? super A> handler) {
        checkReadOnly(dst);
        if (!dst.hasRemaining()) {
            completeWithZeroInt(attach, handler);
            return;
        }
        group.startRead(
                registeredSocket,
                new ByteBufferSet(dst),
                timeout, unit,
                c -> completionExecutor.submit(() -> handler.completed((int) c, attach)),
                e -> completionExecutor.submit(() -> handler.failed(e, attach)));
    }

    @Override
    public <A> void read(
            ByteBuffer[] dsts, int offset, int length,
            long timeout, TimeUnit unit,
            A attach, CompletionHandler<Long, ? super A> handler) {
        ByteBufferSet bufferSet = new ByteBufferSet(dsts, offset, length);
        if (bufferSet.isReadOnly()) {
            throw new IllegalArgumentException("buffer is read-only");
        }
        if (!bufferSet.hasRemaining()) {
            completeWithZeroLong(attach, handler);
            return;
        }
        group.startRead(
                registeredSocket,
                bufferSet,
                timeout, unit,
                c -> completionExecutor.submit(() -> handler.completed(c, attach)),
                e -> completionExecutor.submit(() -> handler.failed(e, attach)));
    }

    @Override
    public Future<Integer> read(ByteBuffer dst) {
        checkReadOnly(dst);
        if (!dst.hasRemaining()) {
            return CompletableFuture.completedFuture(0);
        }
        FutureReadResult future = new FutureReadResult();
        ReadOperation op = group.startRead(
                registeredSocket,
                new ByteBufferSet(dst),
                0, TimeUnit.MILLISECONDS,
                c -> future.complete((int) c),
                future::completeExceptionally);
        future.op = op;
        return future;
    }

    private void checkReadOnly(ByteBuffer dst) {
        if (dst.isReadOnly()) {
            throw new IllegalArgumentException("buffer is read-only");
        }
    }

    @Override
    public <A> void write(ByteBuffer src, A attach, CompletionHandler<Integer, ? super A> handler) {
        if (!src.hasRemaining()) {
            completeWithZeroInt(attach, handler);
            return;
        }
        group.startWrite(
                registeredSocket,
                new ByteBufferSet(src),
                0, TimeUnit.MILLISECONDS,
                c -> completionExecutor.submit(() -> handler.completed((int) c, attach)),
                e -> completionExecutor.submit(() -> handler.failed(e, attach)));
    }

    @Override
    public <A> void write(
            ByteBuffer src,
            long timeout, TimeUnit unit,
            A attach, CompletionHandler<Integer, ? super A> handler) {
        if (!src.hasRemaining()) {
            completeWithZeroInt(attach, handler);
            return;
        }
        group.startWrite(
                registeredSocket,
                new ByteBufferSet(src),
                timeout, unit,
                c -> completionExecutor.submit(() -> handler.completed((int) c, attach)),
                e -> completionExecutor.submit(() -> handler.failed(e, attach)));
    }

    @Override
    public <A> void write(
            ByteBuffer[] srcs, int offset, int length,
            long timeout, TimeUnit unit,
            A attach, CompletionHandler<Long, ? super A> handler) {
        ByteBufferSet bufferSet = new ByteBufferSet(srcs, offset, length);
        if (!bufferSet.hasRemaining()) {
            completeWithZeroLong(attach, handler);
            return;
        }
        group.startWrite(
                registeredSocket,
                bufferSet,
                timeout, unit,
                c -> completionExecutor.submit(() -> handler.completed(c, attach)),
                e -> completionExecutor.submit(() -> handler.failed(e, attach)));
    }

    @Override
    public Future<Integer> write(ByteBuffer src) {
        if (!src.hasRemaining()) {
            return CompletableFuture.completedFuture(0);
        }
        FutureWriteResult future = new FutureWriteResult();
        WriteOperation op = group.startWrite(
                registeredSocket,
                new ByteBufferSet(src),
                0, TimeUnit.MILLISECONDS,
                c -> future.complete((int) c),
                future::completeExceptionally);
        future.op = op;
        return future;
    }

    private <A> void completeWithZeroInt(A attach, CompletionHandler<Integer, ? super A> handler) {
        completionExecutor.submit(() -> handler.completed(0, attach));
    }

    private <A> void completeWithZeroLong(A attach, CompletionHandler<Long, ? super A> handler) {
        completionExecutor.submit(() -> handler.completed(0L, attach));
    }

    @Override
    public boolean isOpen() {
        return tlsChannel.isOpen();
    }

    @Override
    public void close() throws IOException {
        tlsChannel.close();
        registeredSocket.close();
    }
}
