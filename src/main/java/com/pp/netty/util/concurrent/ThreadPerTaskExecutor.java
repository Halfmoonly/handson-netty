package com.pp.netty.util.concurrent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

/**
 * @Author: PP-jessica
 * @Description:线程创建执行器，netty的执行器中运行的线程都是由这个执行器创建的
 */
public class ThreadPerTaskExecutor implements Executor {

    private static final Logger logger = LoggerFactory.getLogger(ThreadPerTaskExecutor.class);

    private final ThreadFactory threadFactory;

    public ThreadPerTaskExecutor(ThreadFactory threadFactory) {
        if (threadFactory == null) {
            throw new NullPointerException("threadFactory");
        }
        this.threadFactory = threadFactory;
    }

    @Override
    public void execute(Runnable command) {
        //在这里创建线程并启动
        threadFactory.newThread(command).start();
        logger.info("真正执行任务的线程被创建了！");
    }
}

