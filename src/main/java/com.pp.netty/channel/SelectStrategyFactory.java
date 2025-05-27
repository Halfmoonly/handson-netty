package com.pp.netty.channel;


/**
 * @Author: PP-jessica
 * @Description: 选择工厂
 */
public interface SelectStrategyFactory {

    SelectStrategy newSelectStrategy();
}
