package com.pp.netty.channel;



/**
 * @Author: PP-jessica
 * @Description:默认的选择工厂类，由这个类创建SelectStrategy，select真正的选择策略是由该SelectStrategy接口的实现类来实现的
 */
public final class DefaultSelectStrategyFactory implements SelectStrategyFactory {
    public static final SelectStrategyFactory INSTANCE = new DefaultSelectStrategyFactory();

    private DefaultSelectStrategyFactory() { }

    @Override
    public SelectStrategy newSelectStrategy() {
        return DefaultSelectStrategy.INSTANCE;
    }
}
