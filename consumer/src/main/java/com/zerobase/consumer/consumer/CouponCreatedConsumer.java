package com.zerobase.consumer.consumer;

import com.zerobase.consumer.domain.Coupon;
import com.zerobase.consumer.domain.FailedEvent;
import com.zerobase.consumer.repository.CouponRepository;
import com.zerobase.consumer.repository.FailedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class CouponCreatedConsumer {

  private final CouponRepository couponRepository;

  private final FailedEventRepository failedEventRepository;

  @KafkaListener(topics = "coupon_create", groupId = "group1")
  public void listener(Long userId) {
    try {
      couponRepository.save(new Coupon(userId));
    } catch (Exception e) {
      log.error("failed to create coupon::" + userId + "::" + e.getMessage());
      failedEventRepository.save(new FailedEvent(userId));
    }
  }
}
