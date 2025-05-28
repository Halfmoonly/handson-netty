package com.pp.netty.test;

public interface RunnableFuture<V> extends Runnable,Future<V> {

    @Override
    void run();
}
