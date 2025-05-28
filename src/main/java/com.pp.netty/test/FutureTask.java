package com.pp.netty.test;


import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.concurrent.*;
import java.util.concurrent.locks.LockSupport;

public class FutureTask<V> implements RunnableFuture<V> {

    //状态值，表示的就是该FutureTask执行到哪一步了
    private volatile int state;
    //初始状态
    private static final int NEW          = 0;
    //正在赋值，还没有彻底完成
    private static final int COMPLETING   = 1;
    //已经正常完成了
    private static final int NORMAL       = 2;
    //执行过程中出现异常
    private static final int EXCEPTIONAL  = 3;
    //取消该任务
    private static final int CANCELLED    = 4;
    //中断线程，但是不是直接中断线程，而是设置一个中断变量，线程还未中断
    private static final int INTERRUPTING = 5;
    //任务已经被打断了
    private static final int INTERRUPTED  = 6;

    //用户传进来的要被执行的又返回值的任务
    private Callable<V> callable;

    //返回值要赋值给该成员变量
    private Object outcome;

    private volatile Thread runner;
    //是一个包装线程的对象。并且是链表的头节点
    private volatile WaitNode waiters;


    @SuppressWarnings("unchecked")
    private V report(int s) throws ExecutionException {
        //该方法就是返回执行结果的方法，把执行的结果返回给调用get方法的线程。
        Object x = outcome;
        //如果现在的这个s值等于NORMAL，就意味着我们的任务已经正常完成了，就可以直接返回执行结果
        if (s == NORMAL) {
            return (V)x;
        }
        //如果是已经被取消的状态，就会直接抛出一个异常
        if (s >= CANCELLED) {
            throw new CancellationException();
        }
        throw new ExecutionException((Throwable)x);
    }


    public FutureTask(Callable<V> callable) {
        if (callable == null) {
            throw new NullPointerException();
        }
        this.callable = callable;
        this.state = NEW;
    }


    public FutureTask(Runnable runnable, V result) {
        this.callable = Executors.callable(runnable, result);
        this.state = NEW;
    }

    @Override
    public boolean isCancelled() {
        //是不是被取消了
        return state >= CANCELLED;
    }

    @Override
    public boolean isDone() {
        return state != NEW;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        //该方法是取消该任务。FutureTask只能在刚创建之后取消。
        if (!(state == NEW &&
                UNSAFE.compareAndSwapInt(this, stateOffset, NEW, mayInterruptIfRunning ? INTERRUPTING : CANCELLED))) {
            return false;
        }
        try {
            if (mayInterruptIfRunning) {
                try {
                    Thread t = runner;
                    if (t != null) {
                        t.interrupt();
                    }
                } finally {
                    UNSAFE.putOrderedInt(this, stateOffset, INTERRUPTED);
                }
            }
        } finally {
            finishCompletion();
        }
        return true;
    }


    @Override
    public V get() throws InterruptedException, ExecutionException {
        int s = state;
        //说明这时候没有结果
        if (s <= COMPLETING) {
            //就要等待，这个等待，指的是外部调用get方法的线程等待
            s = awaitDone(false, 0L);
        }
        return report(s);
    }


    @Override
    public V get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        if (unit == null) {
            throw new NullPointerException();
        }
        int s = state;
        //同样判断状态值是什么
        if (s <= COMPLETING &&
                (s = awaitDone(true, unit.toNanos(timeout))) <= COMPLETING) {
            throw new TimeoutException();
        }
        return report(s);
    }


    protected void done() { }


    protected void set(V v) {
        //如果FutureTask正在给结果赋值，还没有真正地完成
        if (UNSAFE.compareAndSwapInt(this, stateOffset, NEW, COMPLETING)) {
            outcome = v;
            //把正常完成的状态值赋给state
            UNSAFE.putOrderedInt(this, stateOffset, NORMAL);
            finishCompletion();
        }
    }


    protected void setException(Throwable t) {
        if (UNSAFE.compareAndSwapInt(this, stateOffset, NEW, COMPLETING)) {
            outcome = t;
            UNSAFE.putOrderedInt(this, stateOffset, EXCEPTIONAL);
            finishCompletion();
        }
    }

    @Override
    public void run() {
        //判断状态是不是new，或者线程赋值失败，就会直接返回
        if (state != NEW ||
                !UNSAFE.compareAndSwapObject(this, runnerOffset,
                        null, Thread.currentThread())) {
            return;
        }
        try {
            Callable<V> c = callable;
            if (c != null && state == NEW) {
                V result;
                boolean ran;
                try {
                    //得到返回结果
                    result = c.call();
                    //改变ran的值
                    ran = true;
                } catch (Throwable ex) {
                    result = null;
                    ran = false;
                    //把出现的异常赋值给成员变量
                    setException(ex);
                }
                if (ran) {
                    //走到这就意味着任务正常结束，可以正常返把执行结果赋值给成员变量
                    set(result);
                }
            }
        } finally {
            runner = null;
            int s = state;
            if (s >= INTERRUPTING) {
                handlePossibleCancellationInterrupt(s);
            }
        }
    }


    protected boolean runAndReset() {
        if (state != NEW ||
                !UNSAFE.compareAndSwapObject(this, runnerOffset,
                        null, Thread.currentThread())) {
            return false;
        }
        boolean ran = false;
        int s = state;
        try {
            Callable<V> c = callable;
            if (c != null && s == NEW) {
                try {
                    c.call(); // don't set result
                    ran = true;
                } catch (Throwable ex) {
                    setException(ex);
                }
            }
        } finally {
            runner = null;
            s = state;
            if (s >= INTERRUPTING) {
                handlePossibleCancellationInterrupt(s);
            }
        }
        return ran && s == NEW;
    }


    private void handlePossibleCancellationInterrupt(int s) {
        if (s == INTERRUPTING) {
            while (state == INTERRUPTING) {
                //让出cpu让其他线程来执行任务
                Thread.yield();
            }
        }

    }


    static final class WaitNode {
        //外部调用get方法的线程会赋值给该成员变量
        volatile Thread thread;
        volatile WaitNode next;
        WaitNode() { thread = Thread.currentThread(); }
    }


    //在这方法中，把等待队列中的线程唤醒，也就是那些调用了get方法，然后阻塞在该方法处的外部线程
    private void finishCompletion() {
        for (WaitNode q; (q = waiters) != null;) {
            if (UNSAFE.compareAndSwapObject(this, waitersOffset, q, null)) {
                for (;;) {
                    Thread t = q.thread;
                    if (t != null) {
                        q.thread = null;
                        //在此处唤醒外部线程
                        LockSupport.unpark(t);
                    }
                    WaitNode next = q.next;
                    if (next == null) {
                        break;
                    }
                    q.next = null;
                    q = next;
                }
                break;
            }
        }

        done();

        callable = null;
    }


    //第一个参数，是允许限时阻塞，如果是false，就一直阻塞，等待执行结果返回才继续运行
    //如果为true，则根据传入的时间，限时阻塞
    private int awaitDone(boolean timed, long nanos)
            throws InterruptedException {
        final long deadline = timed ? System.nanoTime() + nanos : 0L;
        WaitNode q = null;
        boolean queued = false;
        for (;;) {
            //判断当前线程是否被中断，中断了就将该线程从等待队列中移除，这里的线程指的是外部调用get方法的线程
            if (Thread.interrupted()) {
                removeWaiter(q);
                throw new InterruptedException();
            }

            int s = state;
            //这时候说明任务已经完成了
            if (s > COMPLETING) {
                if (q != null) {
                    q.thread = null;
                }
                return s;
            }
            else if (s == COMPLETING) {
                Thread.yield();
            } else if (q == null) {
                //在这里q被创建了，把外部调用get方法的线程封装到WaitNode节点中。
                //该节点会被添加到一个队列中，实际上，所有在此阻塞的外部线程都会被包装成WaitNode节点，
                //添加到队列中。
                q = new WaitNode();
            } else if (!queued) {
                //这里就是在队列头部搞了一个头节点，头节点就是最晚进来的那个线程，当然，线程都被WaitNode包装着
                //头节点实际上就是WaitNode对象。越晚进来的线程会排在链表的头部，谁最晚进来，谁就是头节点
                queued = UNSAFE.compareAndSwapObject(this, waitersOffset,
                        q.next = waiters, q);
            } else if (timed) {
                nanos = deadline - System.nanoTime();
                if (nanos <= 0L) {
                    removeWaiter(q);
                    return state;
                }
                //设置外部线程有时限的阻塞
                LockSupport.parkNanos(this, nanos);
            }
            else {
                //到这里是一直阻塞，可以看到阻塞采用的是 LockSupport.park方式。
                LockSupport.park(this);
            }
        }
    }


    private void removeWaiter(WaitNode node) {
        if (node != null) {
            //把该节点的线程变量设置为null，为什么弄成null呢？因为这是在链表中找到要删除的节点的判断依据
            //如果该节点的thread为null，就说明该节点是一个无效节点，就应该从链表中移除
            node.thread = null;
            retry:
            for (;;) {
                //pred前驱节点，q就是当前节点，s就是后节点
                for (WaitNode pred = null, q = waiters, s; q != null; q = s) {
                    //把当前节点的下一个节点，赋值给s
                    s = q.next;
                    if (q.thread != null) {
                        pred = q;
                    } else if (pred != null) {
                        pred.next = s;
                        if (pred.thread == null) {
                            continue retry;
                        }
                    } else if (!UNSAFE.compareAndSwapObject(this, waitersOffset, q, s)) {
                        continue retry;
                    }
                }
                break;
            }
        }
    }


    private static final Unsafe UNSAFE;
    private static final long stateOffset;
    private static final long runnerOffset;
    private static final long waitersOffset;
    static {
        try {
            //UNSAFE = sun.misc.Unsafe.getUnsafe();
            UNSAFE = getUnsafe();
            Class<?> k = FutureTask.class;
            stateOffset = UNSAFE.objectFieldOffset
                    (k.getDeclaredField("state"));
            runnerOffset = UNSAFE.objectFieldOffset
                    (k.getDeclaredField("runner"));
            waitersOffset = UNSAFE.objectFieldOffset
                    (k.getDeclaredField("waiters"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }

    private  static Unsafe getUnsafe() {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            return (Unsafe) theUnsafe.get(null);
        }catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

}

