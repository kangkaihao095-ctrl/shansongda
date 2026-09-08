package com.shansuda.activity.config;

import com.shansuda.common.mq.MqNames;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MqConfig {

    @Bean
    public TopicExchange activityExchange() {
        return new TopicExchange(MqNames.ACTIVITY_EXCHANGE, true, false);
    }

    @Bean
    public Queue couponQueue() {
        return new Queue(MqNames.Q_COUPON, true);
    }

    @Bean
    public Queue seckillQueue() {
        return new Queue(MqNames.Q_SECKILL, true);
    }

    @Bean
    public Queue grabQueue() {
        return new Queue(MqNames.Q_GRAB, true);
    }

    @Bean
    public Binding couponBinding() {
        return BindingBuilder.bind(couponQueue()).to(activityExchange()).with(MqNames.COUPON_SUCCESS);
    }

    @Bean
    public Binding seckillBinding() {
        return BindingBuilder.bind(seckillQueue()).to(activityExchange()).with(MqNames.SECKILL_SUCCESS);
    }

    @Bean
    public Binding grabBinding() {
        return BindingBuilder.bind(grabQueue()).to(activityExchange()).with(MqNames.GRAB_SUCCESS);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        DefaultJackson2JavaTypeMapper mapper = new DefaultJackson2JavaTypeMapper();
        mapper.setTrustedPackages("com.shansuda");
        converter.setJavaTypeMapper(mapper);
        return converter;
    }
}
