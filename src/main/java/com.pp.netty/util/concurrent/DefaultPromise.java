package com.pp.netty.util.concurrent;

import com.pp.netty.util.internal.StringUtil;

import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import static com.pp.netty.util.internal.ObjectUtil.checkNotNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;


/**
 * @Author: PP-jessica
 * @Description:promise的默认实现类，在netty中，这个可以看作一个不完整的futuretask，他们二者的区别在于futuretask可以作为一个任务
 * 被线程或线程池执行，不能手动设置结果。而该类则不能当做任务被线程或线程池执行，但可以手动把外部线程得到的结果赋值给result属性
 * 那么netty中有没有类似futureTask的类呢？当然是有的，就是PromiseTask，该类继承了DefaultPromise类，可以当作任务被线程池或线程执行。
 * 具体实现可以去看netty的源码，只要你理解了futureTask，会发现PromiseTask非常简单，大部分方法都是调用的父类方法。
 */
public class DefaultPromise<V> extends AbstractFuture<V> implements Promise<V> {

    //每一个promise都要有执行器来执行，对应的执行器要赋值给该属性
    private final EventExecutor executor;
    //执行后得到的结果要赋值给该属性
    private volatile Object result;
    //原子更新器，更新result的值
    private static final AtomicReferenceFieldUpdater<DefaultPromise, Object> RESULT_UPDATER =
            AtomicReferenceFieldUpdater.newUpdater(DefaultPromise.class, Object.class, "result");
    //该属性是为了给result赋值，前提是promise的返回类型为void，这时候把该值赋给result，如果有用户定义的返回值，那么就使用用户
    //定义的返回值
    private static final Object SUCCESS = new Object();
    //当该任务不可取消时，则原子更新器使用该值更新结果
    private static final Object UNCANCELLABLE = new Object();
    //需要通知的监听器
    private Object listeners;
    //一定会出现这样的情况，当外部线程调用该类的get方法时，如果任务还未执行完毕，则外部线程将视情况阻塞。每当一个外部线程阻塞时，该属性便
    //加一，线程继续执行后，该属性减一
    private short waiters;
    //防止并发通知的情况出现，如果为ture，则说明有线程通知监听器了，为false则说明没有。
    private boolean notifyingListeners;

    //构造方法。
    public DefaultPromise(EventExecutor executor) {
        this.executor = checkNotNull(executor, "executor");
    }

    //无参构造方法
    protected DefaultPromise() {
        executor = null;
    }

    /**
     * @Author: PP-jessica
     * @Description:得到传入的执行器
     */
    protected EventExecutor executor() {
        return executor;
    }

    /**
     * @Author: PP-jessica
     * @Description:promise和future的区别就是，promise可以让用户自己设置成功的返回值，也可以设置失败后返回的错误
     */
    @Override
    public Promise<V> setSuccess(V result) {
        if (setSuccess0(result)) {
            return this;
        }
        throw new IllegalStateException("complete already: " + this);
    }

    /**
     * @Author: PP-jessica
     * @Description:该方法和上面的方法作用相同，不过这个方法在设置失败时不会抛出异常，而是直接返回false
     */
    @Override
    public boolean trySuccess(V result) {
        return setSuccess0(result);
    }

    @Override
    public Promise<V> setFailure(Throwable cause) {
        if (setFailure0(cause)) {
            return this;
        }
        throw new IllegalStateException("complete already: " + this, cause);
    }

    @Override
    public boolean tryFailure(Throwable cause) {
        return setFailure0(cause);
    }

    /**
     * @Author: PP-jessica
     * @Description:设置当前的任务为不可取消
     */
    @Override
    public boolean setUncancellable() {
        //用原子更新器更新result的值，这时候result还未被赋值
        if (RESULT_UPDATER.compareAndSet(this, null, UNCANCELLABLE)) {
            return true;
        }
        //走到这里说明设置失败了，意味着任务不可取消，这就对应两种结果，一是任务已经执行成功了，无法取消
        //二就是任务已经被别的线程取消了。
        Object result = this.result;
        //这里的结果只能二选一，执行成功或者已经被取消，不可能出现执行成功了也被取消了，也不可能出现没执行成功也没取消，这样的话
        //就会在前面被原子更新器更新了
        return !isDone0(result) || !isCancelled0(result);
    }

    @Override
    public boolean isSuccess() {
        Object result = this.result;
        //result不为空，并且不等于被取消，并且不属于被包装过的异常类
        return result != null && result != UNCANCELLABLE && !(result instanceof CauseHolder);
    }

    @Override
    public boolean isCancellable() {
        return result == null;
    }

    /**
     * @Author: PP-jessica
     * @Description:获取任务执行时的异常
     */
    @Override
    public Throwable cause() {
        Object result = this.result;
        //如果得到的结果属于包装过的异常类，说明任务执行时是有异常的，直接从包装过的类中得到异常属性即可，如果不属于包装过的异常类，则直接
        //返回null即可
        return (result instanceof CauseHolder) ? ((CauseHolder) result).cause : null;
    }

    @Override
    public Promise<V> addListener(GenericFutureListener<? extends Future<? super V>> listener) {
        //检查监听器不为null
        checkNotNull(listener, "listener");
        //加锁
        synchronized (this) {
            //添加监听器
            addListener0(listener);
        }
        //判断任务是否完成，实际上就是检查result是否被赋值了
        if (isDone()) {
            //唤醒监听器，让监听器去执行
            notifyListeners();
        }
        //最后返回当前对象
        return this;
    }

    /**
     * @Author: PP-jessica
     * @Description:添加多个监听器
     */
    @Override
    public Promise<V> addListeners(GenericFutureListener<? extends Future<? super V>>... listeners) {
        checkNotNull(listeners, "listeners");
        synchronized (this) {
            //遍历传入的监听器，如果有其中任何一个为null，则停止循环
            for (GenericFutureListener<? extends Future<? super V>> listener : listeners) {
                if (listener == null) {
                    break;
                }
                //添加监听器
                addListener0(listener);
            }
        }
        if (isDone()) {
            notifyListeners();
        }

        return this;
    }

    @Override
    public Promise<V> removeListener(GenericFutureListener<? extends Future<? super V>> listener) {
        checkNotNull(listener, "listener");
        synchronized (this) {
            //移除监听器
            removeListener0(listener);
        }

        return this;
    }

    @Override
    public Promise<V> removeListeners(GenericFutureListener<? extends Future<? super V>>... listeners) {
        checkNotNull(listeners, "listeners");
        synchronized (this) {
            for (GenericFutureListener<? extends Future<? super V>> listener : listeners) {
                if (listener == null) {
                    break;
                }
                removeListener0(listener);
            }
        }

        return this;
    }

    /**
     * @Author: PP-jessica
     * @Description:该方法在AbstractFuture抽象类中被调用了，当执行还没有结果的时候，外部线程调用get方法时，会进一步调用
     * 该方法进行阻塞。
     */
    @Override
    public Promise<V> await() throws InterruptedException {
        //如果已经执行完成，直接返回即可
        if (isDone()) {
            return this;
        }
        //如果线程中断，直接抛出异常
        if (Thread.interrupted()) {
            throw new InterruptedException(toString());
        }
        //检查是否死锁，如果是死锁直接抛出异常，在这里可以进一步思考一下，哪些情况会发生死锁
        //如果熟悉了netty之后，就会发现，凡事结果要赋值到promise的任务都是由netty中的单线程执行器来执行的
        //执行每个任务的执行器是和channel绑定的。如果某个执行器正在执行任务，但是还未获得结果，这时候该执行器
        //又来获取结果，一个线程怎么能同时执行任务又要唤醒自己呢，所以必然会产生死锁
        checkDeadLock();
        //wait要和synchronized一起使用，在futurtask的源码中，这里使用了LockSupport.park方法。
        synchronized (this) {
            //如果成功直接返回，不成功进入循环
            while (!isDone()) {
                //waiters字段加一，记录在此阻塞的线程数量
                incWaiters();
                try {
                    //释放锁并等待
                    wait();
                } finally {
                    //等待结束waiters字段减一
                    decWaiters();
                }
            }
        }
        return this;
    }


    /**
     * @Author: PP-jessica
     * @Description:该方法与上面的方法作用相同，只是不会抛出异常
     */
    @Override
    public Promise<V> awaitUninterruptibly() {
        if (isDone()) {
            return this;
        }
        checkDeadLock();
        boolean interrupted = false;
        synchronized (this) {
            while (!isDone()) {
                incWaiters();
                try {
                    wait();
                } catch (InterruptedException e) {
                    interrupted = true;
                } finally {
                    decWaiters();
                }
            }
        }
        //如果发生异常，则给调用该方法的线程设置中断标志
        if (interrupted) {
            Thread.currentThread().interrupt();
        }

        return this;
    }


    /**
     * @Author: PP-jessica
     * @Description:支持超时时间的await方法
     */
    @Override
    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        return await0(unit.toNanos(timeout), true);
    }

    @Override
    public boolean await(long timeoutMillis) throws InterruptedException {
        return await0(MILLISECONDS.toNanos(timeoutMillis), true);
    }

    @Override
    public boolean awaitUninterruptibly(long timeout, TimeUnit unit) {
        try {
            //不会抛出异常
            return await0(unit.toNanos(timeout), false);
        } catch (InterruptedException e) {
            throw new InternalError();
        }
    }

    @Override
    public boolean awaitUninterruptibly(long timeoutMillis) {
        try {
            return await0(MILLISECONDS.toNanos(timeoutMillis), false);
        } catch (InterruptedException e) {
            throw new InternalError();
        }
    }

    /**
     * @Author: PP-jessica
     * @Description:非阻塞获取执行后的结果，如果结果尚未出来，直接返回null
     */
    @Override
    public V getNow() {
        Object result = this.result;
        if (result instanceof CauseHolder || result == SUCCESS || result == UNCANCELLABLE) {
            return null;
        }
        return (V) result;
    }

    /**
     * @Author: PP-jessica
     * @Description:取消当前的任务
     */
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        //原子更新器得到当前resuli的值，如果为null，说明任务还未执行完成，并且没有被取消
        if (RESULT_UPDATER.get(this) == null &&
                //原子更新器把被包装过的CancellationException赋值给result
                RESULT_UPDATER.compareAndSet(this, null, new CauseHolder(new CancellationException()))) {
            //如果上面的操作成功了就唤醒之前wait的线程
            if (checkNotifyWaiters()) {
                //通知所有监听器执行
                notifyListeners();
            }
            return true;
        }
        //如果取消失败则说明result已经有值了
        return false;
    }

    @Override
    public boolean isCancelled() {
        return isCancelled0(result);
    }

    @Override
    public boolean isDone() {
        return isDone0(result);
    }

    /**
     * @Author: PP-jessica
     * @Description:该方法和下面的方法应该是大家最熟悉的，区别就是是否抛出异常
     * 服务器和客户端经常会调用该方法同步等待结果
     */
    @Override
    public Promise<V> sync() throws InterruptedException {
        await();
        rethrowIfFailed();
        return this;
    }

    @Override
    public Promise<V> syncUninterruptibly() {
        awaitUninterruptibly();
        rethrowIfFailed();
        return this;
    }

    /**
     * @Author: PP-jessica
     * @Description:一个私有的静态内部类，对异常的包装
     */
    private static final class CauseHolder {

        final Throwable cause;

        CauseHolder(Throwable cause) {
            this.cause = cause;
        }
    }

    @Override
    public String toString() {
        return toStringBuilder().toString();
    }

    protected StringBuilder toStringBuilder() {
        StringBuilder buf = new StringBuilder(64)
                .append(StringUtil.simpleClassName(this))
                .append('@')
                .append(Integer.toHexString(hashCode()));

        Object result = this.result;
        if (result == SUCCESS) {
            buf.append("(success)");
        } else if (result == UNCANCELLABLE) {
            buf.append("(uncancellable)");
        } else if (result instanceof CauseHolder) {
            buf.append("(failure: ")
                    .append(((CauseHolder) result).cause)
                    .append(')');
        } else if (result != null) {
            buf.append("(success: ")
                    .append(result)
                    .append(')');
        } else {
            buf.append("(incomplete)");
        }

        return buf;
    }

    /**
     * @Author: PP-jessica
     * @Description:检查是否为死锁的方法
     */
    protected void checkDeadLock() {
        //得到执行器
        EventExecutor e = executor();
        //判断是否为死锁，之前已经解释过这个问题了
        if (e != null && e.inEventLoop(Thread.currentThread())) {
            throw new BlockingOperationException(toString());
        }
    }

    /**
     * @Author: PP-jessica
     * @Description:通知监听器开始执行方法
     */
    protected static void notifyListener(EventExecutor eventExecutor, final Future<?> future, final GenericFutureListener<?> listener) {
        checkNotNull(eventExecutor, "eventExecutor");
        checkNotNull(future, "future");
        checkNotNull(listener, "listener");
        if (eventExecutor.inEventLoop(Thread.currentThread())){
            //如果执行任务的线程是单线程执行器，那么直接通知监听器执行方法
            notifyListener0(future, listener);
        }
        //如果不是执行器的线程，则包装成runnable，交给执行器去通知监听器执行方法
        safeExecute(eventExecutor, new Runnable() {
            @Override
            public void run() {
                notifyListener0(future, listener);
            }
        });
    }

    /**
     * @Author: PP-jessica
     * @Description:通知所有的监听器开始执行方法
     */
    private void notifyListeners() {
        //得到执行器
        EventExecutor executor = executor();
        //如果正在执行方法的线程就是执行器的线程，就立刻通知监听器执行方法
        if (executor.inEventLoop(Thread.currentThread())) {
            notifyListenersNow();
            }
        safeExecute(executor, new Runnable() {
            @Override
            public void run() {
                notifyListenersNow();
            }
        });
    }


    private void notifyListenersNow() {
        Object listeners;
        synchronized (this) {
            //notifyingListeners这个属性如果为ture，说明已经有线程通知监听器了。或者当监听器属性为null
            //这时候直接返回即可。
            if (notifyingListeners || this.listeners == null) {
                return;
            }
            //如果没有通知，把notifyingListeners设置为ture
            notifyingListeners = true;
            listeners = this.listeners;
            //将listeners属性设置为null，代表通知过了已经，这时候所就要被释放了
            this.listeners = null;
        }
        for (;;) {
            //如果listerers是DefaultFutureListeners该类型，则说明有多个监听器，是个监听器数组，要执行 通知多个数组的方法
            if (listeners instanceof DefaultFutureListeners) {
                notifyListeners0((DefaultFutureListeners) listeners);
            } else {
                //说明只有一个监听器。
                notifyListener0(this, (GenericFutureListener<?>) listeners);
            }
            //通知完成后继续上锁
            synchronized (this) {
                //这里再次加锁是因为方法结束之后notifyingListeners的值要重置。
                if (this.listeners == null) {
                    notifyingListeners = false;
                    //重置之后退出即可
                    return;
                }
                //如果走到这里就说明在将要重置notifyingListeners之前，又添加了监听器，这时候要重复上一个synchronized代码块中的内容
                //为下一次循环作准备，而在循环的时候也有可能有其他线程来通知监听器执行方法，但this.listeners = null，而且notifyingListeners
                //为ture，所以线程会被挡在第一个synchronized块之前
                listeners = this.listeners;
                this.listeners = null;
            }
        }
    }


    private void notifyListeners0(DefaultFutureListeners listeners) {
        //得到监听器数组
        GenericFutureListener<?>[] a = listeners.listeners();
        //遍历数组，一次通知监听器执行方法
        int size = listeners.size();
        for (int i = 0; i < size; i ++) {
            notifyListener0(this, a[i]);
        }
    }


    /**
     * @Author: PP-jessica
     * @Description:通知监听器执行它的方法
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void notifyListener0(Future future, GenericFutureListener l) {
        try {
            l.operationComplete(future);
        } catch (Throwable t) {
           throw new RuntimeException(t);
        }
    }


    private void addListener0(GenericFutureListener<? extends Future<? super V>> listener) {
        //listeners为null，则说明在这之前没有添加监听器，直接把该监听器赋值给属性即可
        if (listeners == null) {
            listeners = listener;
            //走到这里说明已经添加了多个监听器，监听器数组被包装在DefaultFutureListeners类中，所以要把监听器添加到数组中
        } else if (listeners instanceof DefaultFutureListeners) {
            ((DefaultFutureListeners) listeners).add(listener);
        } else {
            //这种情况适用于第二次添加的时候，把第一次添加的监听器和本次添加的监听器传入DefaultFutureListeners的构造器函数中
            //封装为一个监听器数组
            listeners = new DefaultFutureListeners((GenericFutureListener<?>) listeners, listener);
        }
    }

    /**
     * @Author: PP-jessica
     * @Description:删除监听器
     */
    private void removeListener0(GenericFutureListener<? extends Future<? super V>> listener) {
        //如果监听器是数组类型的，就从数组中删除
        if (listeners instanceof DefaultFutureListeners) {
            ((DefaultFutureListeners) listeners).remove(listener);
        } else if (listeners == listener) {
            //如果只有一个监听器，则直接把监听器属性置为null
            listeners = null;
        }
    }


    private boolean setSuccess0(V result) {
        //设置成功结果，如果结果为null，则将SUCCESS赋值给result
        return setValue0(result == null ? SUCCESS : result);
    }


    private boolean setFailure0(Throwable cause) {
        //设置失败结果，也就是包装过的异常信息
        return setValue0(new CauseHolder(checkNotNull(cause, "cause")));
    }


    private boolean setValue0(Object objResult) {
        //result还未被赋值时，原子更新器可以将结果赋值给result
        if (RESULT_UPDATER.compareAndSet(this, null, objResult) ||
                RESULT_UPDATER.compareAndSet(this, UNCANCELLABLE, objResult)) {
            //如果有在获得结果时被阻塞的线程，则唤醒这些线程
            if (checkNotifyWaiters()) {
                //得到结果之后就执行监听器的回调方法
                notifyListeners();
            }
            return true;
        }
        return false;
    }


    private synchronized boolean checkNotifyWaiters() {
        if (waiters > 0) {
            notifyAll();
        }
        return listeners != null;
    }


    private void incWaiters() {
        if (waiters == Short.MAX_VALUE) {
            throw new IllegalStateException("too many waiters: " + this);
        }
        ++waiters;
    }


    private void decWaiters() {
        --waiters;
    }


    private void rethrowIfFailed() {
        Throwable cause = cause();
        if (cause == null) {
            return;
        }
        //暂时先不从源码中引入该工具类
        //PlatformDependent.throwException(cause);
    }

    /**
     * @Author: PP-jessica
     * @Description:真正让线程阻塞等待的方法
     */
    private boolean await0(long timeoutNanos, boolean interruptable) throws InterruptedException {
        //执行成功则直接返回
        if (isDone()) {
            return true;
        }
        //传入的时间小于0则直接判断是否执行完成
        if (timeoutNanos <= 0) {
            return isDone();
        }
        //interruptable为true则允许抛出中断异常，为false则不允许，判断当前线程是否被中断了
        //如果都为true则抛出中断异常
        if (interruptable && Thread.interrupted()) {
            throw new InterruptedException(toString());
        }
        //检查死锁
        checkDeadLock();
        //获取当前纳秒时间
        long startTime = System.nanoTime();
        //用户设置的等待时间
        long waitTime = timeoutNanos;
        //是否中断
        boolean interrupted = false;
        try {
            for (;;) {
                synchronized (this) {
                    if (isDone()) {
                        return true;
                    }
                    //如果没有执行完成，则开始阻塞等待，阻塞线程数加一
                    incWaiters();
                    try {
                        //阻塞在这里
                        wait(waitTime / 1000000, (int) (waitTime % 1000000));
                    } catch (InterruptedException e) {
                        if (interruptable) {
                            throw e;
                        } else {
                            //中断标记设置为true
                            interrupted = true;
                        }
                    } finally {
                        //阻塞线程数减一
                        decWaiters();
                    }
                }
                //走到这里说明线程被唤醒了
                if (isDone()) {
                    return true;
                } else {
                    //得到新的等待时间，如果等待时间小于0，表示已经阻塞了用户设定的等待时间。如果waitTime大于0，则继续循环
                    waitTime = timeoutNanos - (System.nanoTime() - startTime);
                    if (waitTime <= 0) {
                        return isDone();
                    }
                }
            }
        } finally {
            //退出方法前判断是否要给执行任务的线程添加中断标记
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }


    private static boolean isCancelled0(Object result) {
        return result instanceof CauseHolder && ((CauseHolder) result).cause instanceof CancellationException;
    }


    private static boolean isDone0(Object result) {
        return result != null && result != UNCANCELLABLE;
    }


    private static void safeExecute(EventExecutor executor, Runnable task) {
        try {
            executor.execute(task);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to submit a listener notification task. Event loop shut down?", t);
        }
    }
}
