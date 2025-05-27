package com.pp.netty.util.concurrent;


/**
 * @Author: PP-jessica
 * @Description:执行器选择工厂接口
 */
public interface EventExecutorChooserFactory {


    EventExecutorChooser newChooser(EventExecutor[] executors);


    interface EventExecutorChooser {

        /**
         * Returns the new {@link EventExecutor} to use.
         */
        EventExecutor next();
    }
}
