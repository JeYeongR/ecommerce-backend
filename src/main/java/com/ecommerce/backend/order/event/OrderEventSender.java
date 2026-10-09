package com.ecommerce.backend.order.event;

/** 동기 발행. 실패하면 예외를 던진다. */
interface OrderEventSender {

    void send(OrderCompletedMessage message);
}
