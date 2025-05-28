package com.pp.netty.channel;

import com.pp.netty.util.concurrent.Future;
import com.pp.netty.util.concurrent.GenericFutureListener;



/**
 * @Author: PP-jessica
 * @Description:和channel有关的future
 */
public interface ChannelFuture extends Future<Void> {

    Channel channel();

    @Override
    ChannelFuture addListener(GenericFutureListener<? extends Future<? super Void>> listener);

    @Override
    ChannelFuture addListeners(GenericFutureListener<? extends Future<? super Void>>... listeners);

    @Override
    ChannelFuture removeListener(GenericFutureListener<? extends Future<? super Void>> listener);

    @Override
    ChannelFuture removeListeners(GenericFutureListener<? extends Future<? super Void>>... listeners);

    @Override
    ChannelFuture sync() throws InterruptedException;

    @Override
    ChannelFuture syncUninterruptibly();

    @Override
    ChannelFuture await() throws InterruptedException;

    @Override
    ChannelFuture awaitUninterruptibly();

    boolean isVoid();
}
