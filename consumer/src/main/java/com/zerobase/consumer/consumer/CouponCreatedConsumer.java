package com.zerobase.consumer.consumer;

import com.zerobase.consumer.domain.Coupon;
import com.zerobase.consumer.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class CouponCreatedConsumer {

  private final CouponRepository couponRepository;

  @KafkaListener(topics = "coupon_create", groupId = "group1")
  public void listener(Long userId) {
    couponRepository.save(new Coupon(userId));
  }
}
