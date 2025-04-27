package com.pp.netty.util.concurrent;

import com.pp.netty.channel.EventLoopTaskQueueFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;


/**
 * @Author: PP-jessica
 * @Description:单线程执行器，实际上这个类就是一个单线程的线程池，netty中所有任务都是被该执行器执行的
 * 既然是执行器(虽然该执行器中只有一个无限循环的线程工作)，但执行器应该具备的属性也不可少，比如任务队列，拒绝策略等等
 */
public abstract class SingleThreadEventExecutor implements Executor {

    private static final Logger logger = LoggerFactory.getLogger(SingleThreadEventExecutor.class);
    //执行器的初始状态，未启动
    private static final int ST_NOT_STARTED = 1;

    //执行器启动后的状态
    private static final int ST_STARTED = 2;

    private volatile int state = ST_NOT_STARTED;
    //执行器的状态更新器,也是一个原子类，通过cas来改变执行器的状态值
    private static final AtomicIntegerFieldUpdater<SingleThreadEventExecutor> STATE_UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(SingleThreadEventExecutor.class, "state");

    //任务队列的容量，默认是Integer的最大值
    protected static final int DEFAULT_MAX_PENDING_TASKS = Integer.MAX_VALUE;

    private final Queue<Runnable> taskQueue;

    private volatile Thread thread;
    //创建线程的执行器
    private Executor executor;

    private volatile boolean interrupted;

    private final RejectedExecutionHandler rejectedExecutionHandler;

    protected SingleThreadEventExecutor(Executor executor, EventLoopTaskQueueFactory queueFactory, ThreadFactory threadFactory) {
        this(executor,queueFactory,threadFactory,RejectedExecutionHandlers.reject());
    }

    protected SingleThreadEventExecutor(Executor executor,EventLoopTaskQueueFactory queueFactory,ThreadFactory threadFactory,RejectedExecutionHandler rejectedExecutionHandler) {
        if (executor == null) {
            this.executor = new ThreadPerTaskExecutor(threadFactory);
        }
        this.taskQueue = queueFactory == null? newTaskQueue(DEFAULT_MAX_PENDING_TASKS):queueFactory.newTaskQueue(DEFAULT_MAX_PENDING_TASKS);
        this.rejectedExecutionHandler = rejectedExecutionHandler;
    }


    protected Queue<Runnable> newTaskQueue(int maxPendingTasks) {
        return new LinkedBlockingQueue<Runnable>(maxPendingTasks);
    }

    /**
     * @Author: PP-jessica
     * @Description:该方法在nioeventloop中实现，是真正执行轮询的方法
     */
    protected abstract void run();

    /**
     * @Author: PP-jessica
     * @Description:执行器执行任务
     */
    @Override
    public void execute(Runnable task) {
        if (task == null) {
            throw new NullPointerException("task");
        }
        //把任务提交到任务队列中
        addTask(task);
        //启动单线程执行器中的线程
        startThread();
    }

    private void startThread() {
        //暂时先不考虑特别全面的线程池状态，只关心线程是否已经启动
        //如果执行器的状态是未启动，就cas将其状态值变为已启动
        if (state == ST_NOT_STARTED) {
            if (STATE_UPDATER.compareAndSet(this, ST_NOT_STARTED, ST_STARTED)) {
                boolean success = false;
                try {
                    doStartThread();
                    success = true;
                } finally {
                    //如果启动未成功，直接把状态值复原
                    if (!success) {
                        STATE_UPDATER.compareAndSet(this, ST_STARTED, ST_NOT_STARTED);
                    }
                }
            }
        }
    }

    private void doStartThread() {
        //这里的executor是ThreadPerTaskExecutor，runnable -> threadFactory.newThread(command).start()
        //threadFactory中new出来的thread就是单线程线程池中的线程，它会调用nioeventloop中的run方法，无限循环，直到资源被释放
        executor.execute(new Runnable() {
            @Override
            public void run() {
                //Thread.currentThread得到的就是正在执行任务的单线程执行器的线程，这里把它赋值给thread属性十分重要
                //暂时先记住这一点
                thread = Thread.currentThread();
                if (interrupted) {
                    thread.interrupt();
                }
                //线程开始轮询处理IO事件，父类中的关键字this代表的是子类对象，这里调用的是nioeventloop中的run方法
                SingleThreadEventExecutor.this.run();
                logger.info("单线程执行器的线程错误结束了！");
            }
        });
    }

    /**
     * @Author: PP-jessica
     * @Description:判断当前执行任务的线程是否是执行器的线程。这个方法至关重要，现在先有个印象
     */
    public boolean inEventLoop(Thread thread) {
        return thread == this.thread;
    }

    /**
     * @Author: PP-jessica
     * @Description:判断任务队列中是否有任务
     */
    protected boolean hasTasks() {
        logger.info("我没任务了！");
        return !taskQueue.isEmpty();
    }

    private void addTask(Runnable task) {
        if (task == null) {
            throw new NullPointerException("task");
        }
        //如果添加失败，执行拒绝策略
        if (!offerTask(task)) {
            reject(task);
        }
    }


    final boolean offerTask(Runnable task) {
        return taskQueue.offer(task);
    }

    protected void runAllTasks() {
        runAllTasksFrom(taskQueue);
    }

    protected void runAllTasksFrom(Queue<Runnable> taskQueue) {
        //从任务对立中拉取任务,如果第一次拉取就为null，说明任务队列中没有任务，直接返回即可
        Runnable task = pollTaskFrom(taskQueue);
        if (task == null) {
            return;
        }
        for (;;) {
            //执行任务队列中的任务
            safeExecute(task);
            //执行完毕之后，拉取下一个任务，如果为null就直接返回
            task = pollTaskFrom(taskQueue);
            if (task == null) {
                return;
            }
        }
    }

    private void safeExecute(Runnable task) {
        try {
            task.run();
        } catch (Throwable t) {
            logger.warn("A task raised an exception. Task: {}", task, t);
        }
    }

    protected static Runnable pollTaskFrom(Queue<Runnable> taskQueue) {
        return taskQueue.poll();
    }

    protected static void reject() {
        throw new RejectedExecutionException("event executor terminated");
    }

    protected final void reject(Runnable task) {
        rejectedExecutionHandler.rejected(task, this);
    }

    /**
     * @Author: PP-jessica
     * @Description: 中断单线程执行器中的线程
     */
    protected void interruptThread() {
        Thread currentThread = thread;
        if (currentThread == null) {
            interrupted = true;
        } else {
            //中断线程并不是直接让该线程停止运行，而是提供一个中断信号
            //也就是标记，想要停止线程仍需要在运行流程中结合中断标记来判断
            currentThread.interrupt();
        }
    }
}
