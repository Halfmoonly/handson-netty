package com.pp.netty.util.concurrent;

import java.util.concurrent.TimeUnit;

/**
 * @Author: PP-jessica
 * @Description:暂时还不实现任何方法，在源码中，这个抽象类定义了一些方法模版，都是对ScheduledExecutorService这个接口的方法的实现，
 * 但方法内部的具体方法，由子类来实现
 */
public abstract class AbstractEventExecutorGroup implements EventExecutorGroup {
    //举个例子，该方法内部的next方法由MultithreadEventExecutorGroup类来实现，而submit方法，则由EventExecutor的实现类来实现
//    @Override
//    public Future<?> submit(Runnable task) {
//        return next().submit(task);
//    }


    /**
     * @Author: PP-jessica
     * @Description:下面这三个方法也不在该类中，随着代码的进展，代码也会进一步完善
     */
    @Override
    public void shutdownGracefully() {

    }

    @Override
    public boolean isTerminated() {
        return false;
    }

    @Override
    public void awaitTermination(Integer integer, TimeUnit timeUnit) throws InterruptedException {

    }

    @Override
    public void execute(Runnable command) {
        next().execute(command);
    }
}
