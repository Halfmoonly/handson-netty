package com.pp.netty.test;

import java.util.concurrent.*;
import java.util.concurrent.Future;

public class FutureTest {
    public static void main(String[] args) throws InterruptedException, ExecutionException {
        Callable<Integer> callable = new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                //Thread.sleep(5000);
                return 9527;
            }
        };
        FutureTask<Integer> future = new FutureTask<Integer>(callable);
//        Thread t = new Thread(future);
//        future.set(1000);
//        t.start();
        //Thread.sleep(1000);
        //future.cancel(false);

//        Runnable runnable = new Runnable() {
//            @Override
//            public void run() {
//                Integer integer = null;
//                try {
//                    integer = future.get();
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//                System.out.println(integer+"runnable");
//            }
//        };
//        Thread t1 = new Thread(runnable);
//        t1.start();


//        try {
//            System.out.println( future.get(500, TimeUnit.MILLISECONDS)+"==================main函数");
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
        ExecutorService threadPool = Executors.newCachedThreadPool();
        Future<?> otherFuture = threadPool.submit(future);
        //无超时获取结果
        System.out.println(otherFuture.get());
    }
}
